package com.fuwanime

import com.fuwanime.data.manga.KomikuSource
import com.fuwanime.data.manga.KakaMangaSource
import com.fuwanime.data.manga.MangaParkSource
import com.fuwanime.data.manga.ShinigamiApi
import com.fuwanime.data.manga.ShinigamiSource
import com.fuwanime.data.manga.WebtoonSource
import com.fuwanime.data.manga.WT_REFERER
import com.fuwanime.data.manga.isWebtoonImage
import com.fuwanime.data.manga.MangaDns
import kotlinx.coroutines.flow.first
import com.fuwanime.data.backup.BackupWorker
import com.fuwanime.data.backup.BackupRepository
import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath
import com.fuwanime.data.AnimeRepository
import com.fuwanime.data.DownloadRepository
import com.fuwanime.data.LibraryRepository
import com.fuwanime.data.ProfileRepository
import com.fuwanime.data.manga.MangaLibrary
import com.fuwanime.data.local.FuwaDatabase
import com.fuwanime.data.local.SettingsStore
import com.fuwanime.data.manga.MangaDownloadRepository
import com.fuwanime.data.manga.MangaRepository
import com.fuwanime.data.manga.MangaChapterTranslator
import com.fuwanime.data.manga.MangaTranslateV2ChapterTranslator
import com.fuwanime.data.meta.AniListApi
import com.fuwanime.data.meta.AniSkipApi
import com.fuwanime.data.meta.AnimeLinkRepository
import com.fuwanime.data.meta.SkipRepository
import com.fuwanime.playback.PlaybackHolder
import java.util.concurrent.TimeUnit
import com.fuwanime.data.remote.AnimeApiService
import com.fuwanime.data.remote.ForceCache
import com.fuwanime.data.remote.AnimekompiSource
import com.fuwanime.data.remote.KuramanimeSource
import com.fuwanime.data.remote.NetworkStatus
import com.fuwanime.data.remote.apiCacheClient
import com.fuwanime.data.AppInstaller
import com.fuwanime.data.UpdateRepository
import com.fuwanime.data.notify.EpisodeNotifyWorker
import com.fuwanime.data.notify.ReleaseNotifyWorker
import com.fuwanime.data.notify.AppUpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.fuwanime.widget.HomeSurfaces
import okhttp3.Cache
import okhttp3.OkHttpClient

// Code contributor: Andra
/** Manual DI: one instance of each dependency for the whole process. */
class AppContainer(private val app: Application) {
    // MangaDns only changes blocked Indonesian manga hosts; everything derived from this client inherits it.
    val httpClient: OkHttpClient = AnimeApiService.createHttpClient().newBuilder().dns(MangaDns()).build()

    /** Connectivity plus "showing cached data since"; drives the offline indicators. */
    val networkStatus = NetworkStatus().also { it.start(app) }

    /** API-only client with the 50 MB disk cache; images keep [httpClient] and Coil's own cache. */
    private val apiHttpClient = apiCacheClient(
        httpClient,
        runCatching { Cache(app.cacheDir.resolve("api_cache"), 50L * 1024 * 1024) }.getOrNull(),
        networkStatus,
    )
    private val database = FuwaDatabase.create(app)
    val settingsStore = SettingsStore(app)
    val libraryRepository = LibraryRepository(database)
    val profileRepository = ProfileRepository(database)
    val mangaLibrary = MangaLibrary(database)
    val animeRepository = AnimeRepository(
        AnimeApiService.create(apiHttpClient),
        AnimeApiService.create(apiHttpClient.newBuilder().addInterceptor(ForceCache).build()),
        kuramanime = KuramanimeSource(httpClient, mature = { settingsStore.matureContent.first() }),
        animekompi = AnimekompiSource(httpClient, mature = { settingsStore.matureContent.first() }),
        mature = { settingsStore.matureContent.first() },
    )

    /** For writes that must outlive the screen that started them (e.g. saving progress on exit). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val downloadRepository = DownloadRepository(app, database.downloadDao(), animeRepository, appScope, settingsStore)

    /** Re-renders the "Lanjutkan" shortcut and widget after history changes. */
    fun refreshHomeSurfaces() {
        appScope.launch { HomeSurfaces.refresh(app, libraryRepository) }
    }

    /** In-app update checker against GitHub Releases. */
    val updateRepository = UpdateRepository(settingsStore)

    /** Video requests get the same UA as the API but none of its JSON headers. */
    val videoHttpClient: OkHttpClient = httpClient.newBuilder().apply {
        interceptors().clear()
        // Animekompi's /api/episodes/stream proxy refuses requests that do not look like they come from its own page.
        addInterceptor { chain ->
            val req = chain.request()
            chain.proceed(
                if (req.url.host.endsWith("animekompi.sbs")) {
                    req.newBuilder()
                        .header("Referer", AnimekompiSource.BASE + "/")
                        .header("Origin", AnimekompiSource.BASE)
                        .build()
                } else {
                    req
                },
            )
        }
    }.build()

    /** Downloads a release APK and opens the system installer, so updating needs no browser. */
    val appInstaller by lazy { AppInstaller(app, videoHttpClient) }

    val playbackHolder by lazy { PlaybackHolder(app, videoHttpClient) }

    /** AniList and AniSkip: no catalog User-Agent or JSON headers, and short timeouts so enrichment gives up fast. */
    val metaHttpClient: OkHttpClient by lazy {
        httpClient.newBuilder().apply { interceptors().clear() }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
    val animeLinkRepository by lazy { AnimeLinkRepository(database.animeLinkDao(), AniListApi.create(metaHttpClient)) }
    val skipRepository by lazy { SkipRepository(AniSkipApi.create(metaHttpClient)) }

    /** Shinigami's API answers browser-like clients; Comix, Komiku and Webtoon share this client. */
    val mangaHttpClient: OkHttpClient by lazy {
        metaHttpClient.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                chain.proceed(
                    when {
                        req.url.host == "api.shngm.io" -> req.newBuilder()
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36")
                            .header("Accept", "application/json")
                            .build()
                        // Webtoon's panel CDN rejects requests without its own Referer (HTTP 403).
                        isWebtoonImage(req.url.toString()) -> req.newBuilder()
                            .header("Referer", WT_REFERER)
                            .build()
                        else -> req
                    },
                )
            }
            .build()
    }
    /** Coil's client: same as [httpClient] plus the Referer Webtoon's image CDN demands (else thumbnails and panels get HTTP 403). */
    val imageHttpClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                chain.proceed(
                    if (isWebtoonImage(req.url.toString())) req.newBuilder().header("Referer", WT_REFERER).build() else req,
                )
            }
            .build()
    }
    val mangaRepository by lazy {
        MangaRepository(
            appLanguage = { app.resources.configuration.locales[0].language },
            shinigami = ShinigamiSource(ShinigamiApi.create(mangaHttpClient)),
            komiku = KomikuSource(mangaHttpClient),
            webtoon = WebtoonSource(mangaHttpClient),
            kakaManga = KakaMangaSource(mangaHttpClient),
            mangaPark = MangaParkSource(mangaHttpClient),
            mature = { settingsStore.matureContent.first() },
        )
    }

    val mangaDownloadDao by lazy { database.mangaDownloadDao() }

    /** Downloaded manga chapters (app-private files), fetched one at a time by MangaDownloadWorker. */
    val mangaDownloads by lazy { MangaDownloadRepository(app, mangaDownloadDao, settingsStore) }

    /** Manga chapter translator: MangaTranslate only (no fallback source). */
    val mangaTranslator: MangaChapterTranslator by lazy {
        MangaTranslateV2ChapterTranslator(app, videoHttpClient)
    }

    /** Daily backup file in a folder the user picked; survives an uninstall. */
    val backupRepository by lazy { BackupRepository(app, libraryRepository, settingsStore) }
}

class FuwaApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        EpisodeNotifyWorker.schedule(this)
        ReleaseNotifyWorker.schedule(this)
        AppUpdateWorker.schedule(this)
        container.appScope.launch {
            val backup = container.settingsStore.backup.first()
            if (backup.treeUri != null && backup.auto) BackupWorker.schedule(this@FuwaApp)
        }
    }

    // Coil 3 ignores HTTP cache headers by default, matching the old respectCacheHeaders(false).
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.imageHttpClient })) }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache").toOkioPath())
                .maxSizeBytes(256L * 1024 * 1024)
                .build()
        }
        .crossfade(true)
        .build()
}
