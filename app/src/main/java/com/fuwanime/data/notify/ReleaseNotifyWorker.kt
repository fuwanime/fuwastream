package com.fuwanime.data.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fuwanime.FuwaApp
import com.fuwanime.R
import com.fuwanime.data.local.AppSettings
import com.fuwanime.data.local.TitleNotifyMode
import com.fuwanime.data.manga.MangaRelease
import com.fuwanime.util.localizedContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * Periodically posts notifications for new episodes of any **ongoing** anime and new chapters of any
 * ongoing manga, not only the user's favourites. The first run per title only records a baseline, so
 * turning the feature on never greets the user with a burst of stale "new" pings.
 *
 * Favourited, ongoing anime are skipped here: [EpisodeNotifyWorker] already notifies them and writes
 * the Library badge baselines, so handling them in both places would double-notify.
 */
class ReleaseNotifyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as FuwaApp).container
        val settings = container.settingsStore.settings.first()
        // Per-title ALL/auto-download overrides must keep working while the global switches are off.
        val preferences = runCatching { container.libraryRepository.titlePreferences().first() }.getOrDefault(emptyList())
        val animeOverride = preferences.any { it.mediaType == "ANIME" && it.notifyMode == TitleNotifyMode.ALL.name }
        val mangaOverride = preferences.any {
            it.mediaType == "MANGA" && (it.notifyMode == TitleNotifyMode.ALL.name || it.autoDownload)
        }
        val animePass = settings.notifyOngoingAnime || animeOverride
        val mangaPass = settings.notifyNewChapters || mangaOverride
        if (!animePass && !mangaPass) return Result.success()

        if (animePass) runCatching { notifyAnimeReleases(settings) }
        if (mangaPass) runCatching { notifyMangaReleases(settings) }
        return Result.success()
    }

    private suspend fun notifyAnimeReleases(settings: AppSettings) {
        val container = (applicationContext as FuwaApp).container
        val favorites = container.libraryRepository.allFavorites().mapTo(HashSet()) { it.animeId }
        val releases = container.animeRepository.latestReleases(limit = ANIME_FEED).getOrNull().orEmpty()
        val res = localizedContext(applicationContext).resources
        val notifier = EpisodeNotifier(applicationContext)
        val notified = ArrayList<String>()

        for (release in releases) {
            val anime = release.anime
            if (anime.id in favorites) continue
            // The feed implies "recently aired"; only drop it when the API explicitly says it is finished.
            if (anime.status.isNotBlank() && !anime.status.equals("ONGOING", true)) continue
            val mode = titleNotifyMode(container, "ANIME", anime.id)
            val notify = when (mode) {
                TitleNotifyMode.DEFAULT -> settings.notifyOngoingAnime
                TitleNotifyMode.ALL -> true
                TitleNotifyMode.OFF, TitleNotifyMode.FINAL_ONLY -> false
            }
            if (!notify) continue
            val number = episodeNumber(release.episodeLabel) ?: continue

            val baseline = container.settingsStore.releaseBaseline(anime.id)
            container.settingsStore.setReleaseBaseline(anime.id, number)
            if (isNewerRelease(number, baseline)) {
                notifier.notifyAnime(
                    animeId = anime.id,
                    title = anime.title,
                    poster = anime.poster,
                    cover = anime.cover,
                    text = res.getString(R.string.system_new_episode_text, episodeLabelText(number)),
                )
                notified += anime.title
            }
        }
        if (notified.size >= 2) notifier.postSummary(notified)
    }

    private suspend fun notifyMangaReleases(settings: AppSettings) {
        val container = (applicationContext as FuwaApp).container
        val releases = container.mangaRepository.latestOngoingChapters(limit = MANGA_FEED)
        val notifier = MangaNotifier(applicationContext)
        val notified = ArrayList<String>()

        for (release in releases) {
            val label = release.chapter ?: continue
            // Chapter labels may be plain numbers or prefixed ("Chapter 12"); take the number either way.
            val number = episodeNumber(label) ?: continue
            val preference = container.libraryRepository.titlePreferenceValue("MANGA", release.mangaId)
            val mode = preference?.notifyMode
                ?.let { raw -> TitleNotifyMode.entries.firstOrNull { it.name == raw } }
                ?: TitleNotifyMode.DEFAULT
            // The release feed only carries ongoing titles, so there is no final chapter to detect here.
            val notify = when (mode) {
                TitleNotifyMode.DEFAULT -> settings.notifyNewChapters
                TitleNotifyMode.ALL -> true
                TitleNotifyMode.OFF, TitleNotifyMode.FINAL_ONLY -> false
            }
            val autoDownload = preference?.autoDownload == true
            if (!notify && !autoDownload) continue
            val baseline = container.settingsStore.mangaBaseline(release.mangaId)
            container.settingsStore.setMangaBaseline(release.mangaId, number)
            if (isNewerRelease(number, baseline)) {
                if (notify) {
                    notifier.notifyNewChapter(release.mangaId, release.title, release.chapterId, label)
                    notified += release.title
                }
                if (autoDownload) {
                    runCatching { autoDownloadManga(container, release) }
                }
            }
        }
        if (notified.size >= 2) notifier.postSummary(notified)
    }

    private suspend fun autoDownloadManga(container: com.fuwanime.AppContainer, release: MangaRelease) {
        val manga = container.mangaRepository.detail(release.mangaId)
        val chapters = withTimeoutOrNull(20_000) { container.mangaRepository.chapters(release.mangaId) }.orEmpty()
        val chapter = chapters.firstOrNull { it.id == release.chapterId } ?: return
        val res = localizedContext(applicationContext).resources
        container.mangaDownloads.enqueue(manga, listOf(chapter)) { c ->
            c.number?.let { res.getString(R.string.manga_chapter_number, it) } ?: c.title.orEmpty()
        }
    }

    private suspend fun titleNotifyMode(container: com.fuwanime.AppContainer, mediaType: String, id: String): TitleNotifyMode =
        container.libraryRepository.titlePreferenceValue(mediaType, id)?.notifyMode
            ?.let { raw -> TitleNotifyMode.entries.firstOrNull { it.name == raw } }
            ?: TitleNotifyMode.DEFAULT

    companion object {
        private const val WORK_NAME = "release_check"

        /**
         * How many entries of each feed to scan per run. Anime updates are a few dozen a day; manga sources
         * can publish many chapters in a few hours, so the window is wider to avoid missing releases.
         */
        private const val ANIME_FEED = 50
        private const val MANGA_FEED = 100

        /** Schedules a frequent sweep (every 15 min, WorkManager minimum) for near-real-time release checks. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReleaseNotifyWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /** Runs a check immediately from the notification settings screen. */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<ReleaseNotifyWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_now",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
