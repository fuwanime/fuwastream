package com.fuwanime.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.fuwanime.data.local.DownloadDao
import com.fuwanime.data.local.DownloadEntity
import com.fuwanime.data.remote.AnimeApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import java.io.File

enum class DownloadState { QUEUED, RUNNING, PAUSED, DONE, FAILED }

data class DownloadItem(
    val entity: DownloadEntity,
    val state: DownloadState,
    val downloadedBytes: Long,
    val totalBytes: Long,
) {
    val progress: Float get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    val inProgress: Boolean get() = state == DownloadState.QUEUED || state == DownloadState.RUNNING || state == DownloadState.PAUSED
}

/**
 * Offline episodes. Files are fetched by the system DownloadManager (notification, retries and
 * background work for free) into the app's private Movies folder, and tracked in Room.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadRepository(
    private val context: Context,
    private val dao: DownloadDao,
    private val anime: AnimeRepository,
    appScope: CoroutineScope,
) {
    private val manager = context.getSystemService(DownloadManager::class.java)

    /** All downloads with live progress; polls the system once a second while anything is in flight. */
    val downloads: Flow<List<DownloadItem>> = dao.observeAll()
        .flatMapLatest { rows ->
            flow {
                while (true) {
                    val items = rows.map(::status)
                    emit(items)
                    if (items.none { it.inProgress }) break
                    delay(1_000)
                }
            }
        }
        .flowOn(Dispatchers.IO)
        .distinctUntilChanged()
        .shareIn(appScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    fun forAnime(animeId: String): Flow<Map<String, DownloadItem>> =
        downloads.map { list -> list.filter { it.entity.animeId == animeId }.associateBy { it.entity.episodeId } }

    fun forEpisode(episodeId: String): Flow<DownloadItem?> =
        downloads.map { list -> list.firstOrNull { it.entity.episodeId == episodeId } }

    /** The finished download for this episode, if its file is still on disk. */
    suspend fun completed(episodeId: String): DownloadItem? = withContext(Dispatchers.IO) {
        dao.get(episodeId)?.let(::status)?.takeIf { it.state == DownloadState.DONE }
    }

    /** Resolves the stream links and queues the source closest to [preferredHeight]. */
    suspend fun download(show: Anime, episode: Episode, preferredHeight: Int, wifiOnly: Boolean): Result<Unit> {
        val stream = anime.stream(episode.id).getOrElse { return Result.failure(it) }
        val source = stream.sources.closestTo(preferredHeight)
            ?: return Result.failure(IllegalStateException("Belum ada sumber video untuk episode ini."))
        val merged = stream.episode?.let { info ->
            episode.copy(thumbnail = episode.thumbnail.ifBlank { info.thumbnail }, number = episode.number.ifBlank { info.number })
        } ?: episode
        return enqueue(show, merged, source, wifiOnly)
    }

    suspend fun enqueue(show: Anime, episode: Episode, source: StreamSource, wifiOnly: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                dao.get(episode.id)?.let { existing ->
                    val current = status(existing)
                    if (current.inProgress || current.state == DownloadState.DONE) return@runCatching
                    removeFiles(existing)
                }
                val relative = "fuwanime/${safeName(show.id)}_${safeName(episode.id)}_${safeName(source.quality)}.mkv"
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                    ?: error("Penyimpanan tidak tersedia.")
                val target = File(dir, relative)
                target.parentFile?.mkdirs()
                target.delete()

                val request = DownloadManager.Request(Uri.parse(source.url))
                    .addRequestHeader("User-Agent", AnimeApiService.USER_AGENT)
                    .setTitle("${show.title} • Episode ${episode.number}")
                    .setDescription("FuwaNime • ${source.quality}")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MOVIES, relative)
                    .setAllowedOverMetered(!wifiOnly)
                    .setAllowedOverRoaming(!wifiOnly)

                val id = manager.enqueue(request)
                dao.upsert(
                    DownloadEntity(
                        episodeId = episode.id,
                        animeId = show.id,
                        animeTitle = show.title,
                        animePoster = show.poster,
                        episodeNumber = episode.number,
                        episodeTitle = episode.title,
                        episodeThumbnail = episode.thumbnail,
                        quality = source.quality,
                        systemId = id,
                        filePath = target.absolutePath,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
        }

    suspend fun delete(episodeId: String) = withContext(Dispatchers.IO) {
        dao.get(episodeId)?.let(::removeFiles)
        dao.delete(episodeId)
    }

    /** Cancels every download, deletes its files, and clears the tracking table. */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.all().forEach(::removeFiles)
        dao.clear()
    }

    private fun removeFiles(entity: DownloadEntity) {
        runCatching { manager.remove(entity.systemId) }
        File(entity.filePath).delete()
    }

    private fun status(entity: DownloadEntity): DownloadItem {
        val file = File(entity.filePath)
        manager.query(DownloadManager.Query().setFilterById(entity.systemId))?.use { c ->
            if (c.moveToFirst()) {
                val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val state = when (c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_PENDING -> DownloadState.QUEUED
                    DownloadManager.STATUS_RUNNING -> DownloadState.RUNNING
                    DownloadManager.STATUS_PAUSED -> DownloadState.PAUSED
                    DownloadManager.STATUS_SUCCESSFUL ->
                        if (file.exists()) DownloadState.DONE else DownloadState.FAILED
                    else -> DownloadState.FAILED
                }
                return DownloadItem(entity, state, done, total)
            }
        }
        // The system forgets finished downloads after a while; the file on disk is the source of truth.
        val size = if (file.exists()) file.length() else 0L
        return DownloadItem(entity, if (size > 0) DownloadState.DONE else DownloadState.FAILED, size, size)
    }

    private fun safeName(value: String) = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
