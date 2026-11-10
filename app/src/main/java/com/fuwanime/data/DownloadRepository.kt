package com.fuwanime.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.fuwanime.data.local.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.fuwanime.R
import com.fuwanime.util.localizedContext
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
    private val appScope: CoroutineScope,
    private val settings: SettingsStore,
) {
    private val manager = context.getSystemService(DownloadManager::class.java)

    /** All downloads with live progress; polls the system once a second while anything is in flight. */
    val downloads: Flow<List<DownloadItem>> = dao.observeAll()
        .flatMapLatest { rows ->
            flow {
                while (true) {
                    val items = rows.map(::status)
                    emit(items)
                    items.filter { it.state == DownloadState.DONE && !isContent(it.entity.filePath) }.forEach { appScope.launch { relocate(it.entity) } }
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
            ?: return Result.failure(IllegalStateException(localizedContext(context).getString(R.string.system_download_no_source)))
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
                // One folder per anime, files named by episode so they list in order.
                val relative = "fuwanime/${cleanFileName(show.title).ifEmpty { safeName(show.id) }}/${episodeFileName(show.title, episode.number, source.quality)}"
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                    ?: error(localizedContext(context).getString(R.string.system_download_no_storage))
                val target = File(dir, relative)
                target.parentFile?.mkdirs()
                target.delete()

                val request = DownloadManager.Request(Uri.parse(source.url))
                    .addRequestHeader("User-Agent", AnimeApiService.USER_AGENT)
                    .setTitle(localizedContext(context).getString(R.string.system_download_title, show.title, episode.number))
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
        if (isContent(entity.filePath)) runCatching { DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(entity.filePath)) }
        else File(entity.filePath).delete()
    }

    private val moving = java.util.Collections.synchronizedSet(HashSet<String>())

    /**
     * Moves a finished episode from the app folder into the folder picked in settings (DownloadManager can only write to
     * the app folder). On any failure the file stays where it is, so nothing is lost.
     */
    private suspend fun relocate(entity: DownloadEntity) = withContext(Dispatchers.IO) {
        val folder = settings.settings.first().downloadFolder ?: return@withContext
        if (isContent(entity.filePath) || !moving.add(entity.episodeId)) return@withContext
        var target: Uri? = null
        try {
            val parent = subFolder(context.contentResolver, Uri.parse(folder), cleanFileName(entity.animeTitle)) ?: return@withContext
            val name = episodeFileName(entity.animeTitle, entity.episodeNumber, entity.quality)
            target = DocumentsContract.createDocument(context.contentResolver, parent, "video/x-matroska", name) ?: return@withContext
            val source = File(entity.filePath)
            context.contentResolver.openOutputStream(target)!!.use { out -> source.inputStream().use { it.copyTo(out) } }
            dao.upsert(entity.copy(filePath = target.toString()))
            source.delete()
        } catch (e: Exception) {
            target?.let { runCatching { DocumentsContract.deleteDocument(context.contentResolver, it) } }
        } finally {
            moving.remove(entity.episodeId)
        }
    }

    private fun isContent(path: String) = path.startsWith("content://")

    /** Size of a moved episode, or -1 when the document is gone or access was revoked. */
    private fun contentSize(path: String): Long = runCatching {
        context.contentResolver.query(Uri.parse(path), arrayOf(DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else -1L
        } ?: -1L
    }.getOrDefault(-1L)

    private fun status(entity: DownloadEntity): DownloadItem {
        if (isContent(entity.filePath)) {
            val size = contentSize(entity.filePath)
            return DownloadItem(entity, if (size > 0) DownloadState.DONE else DownloadState.FAILED, size.coerceAtLeast(0), size.coerceAtLeast(0))
        }
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
