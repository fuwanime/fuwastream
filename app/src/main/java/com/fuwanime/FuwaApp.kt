package com.fuwanime

import com.fuwanime.data.manga.KomikuSource
import com.fuwanime.data.manga.ShinigamiApi
import com.fuwanime.data.manga.ShinigamiSource
import com.fuwanime.data.manga.MangaDexDns
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
import com.fuwanime.data.AccountRepository
import com.fuwanime.data.QuestKind
import com.fuwanime.data.QuestRepository
import com.fuwanime.data.manga.MangaLibrary
import com.fuwanime.data.local.FuwaDatabase
import com.fuwanime.data.local.SettingsStore
import com.fuwanime.data.manga.MangaDexApi
import com.fuwanime.data.manga.MangaDownloadRepository
import com.fuwanime.data.manga.MangaRepository
import com.fuwanime.data.manga.RateLimitInterceptor
import com.fuwanime.data.meta.AniListApi
import com.fuwanime.data.meta.AniSkipApi
import com.fuwanime.data.meta.AnimeLinkRepository
import com.fuwanime.data.meta.SkipRepository
import com.fuwanime.playback.PlaybackHolder
import java.util.concurrent.TimeUnit
import com.fuwanime.data.remote.AnimeApiService
import com.fuwanime.data.remote.ForceCache
import com.fuwanime.data.remote.KuramanimeSource
import com.fuwanime.data.remote.NetworkStatus
import com.fuwanime.data.remote.apiCacheClient
import com.fuwanime.data.UpdateRepository
import com.fuwanime.data.notify.EpisodeNotifyWorker
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
    // MangaDexDns only changes MangaDex hosts (blocked by Indonesian ISP DNS); everything derived from this client inherits it.
    val httpClient: OkHttpClient = AnimeApiService.createHttpClient().newBuilder().dns(MangaDexDns()).build()

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
    val accountRepository = AccountRepository(database)
    val questRepository = QuestRepository(database, profileRepository)
    val mangaLibrary = MangaLibrary(database)
    init {
        mangaLibrary.questRepository = questRepository
        mangaLibrary.profileRepository = profileRepository
    }
    val animeRepository = AnimeRepository(
        AnimeApiService.create(apiHttpClient),
        AnimeApiService.create(apiHttpClient.newBuilder().addInterceptor(ForceCache).build()),
        kuramanime = KuramanimeSource(httpClient, mature = { settingsStore.matureContent.first() }),
        mature = { settingsStore.matureContent.first() },
    )

    /** For writes that must outlive the screen that started them (e.g. saving progress on exit). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val downloadRepository = DownloadRepository(app, database.downloadDao(), animeRepository, appScope, settingsStore)
    init {
        downloadRepository.questHooks = { kind -> appScope.launch { questRepository.increment(kind) } }
    }

    /** Re-renders the "Lanjutkan" shortcut and widget after history changes. */
    fun refreshHomeSurfaces() {
        appScope.launch { HomeSurfaces.refresh(app, libraryRepository) }
    }

    init {
        // Quest hooks: favorite/search/download events feed the 3-day quest board.
        libraryRepository.questHooks = { kind ->
            appScope.launch { questRepository.increment(kind) }
        }
        libraryRepository.profileBumper = { field ->
            appScope.launch { profileRepository.bumpQuestCounter(field) }
        }
    }

    /** In-app update checker against GitHub Releases. */
    val updateRepository = UpdateRepository(settingsStore)

    /** Video requests get the same UA as the API but none of its JSON headers. */
    val videoHttpClient: OkHttpClient = httpClient.newBuilder().apply { interceptors().clear() }.build()

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

    /** MangaDex asks for a descriptive User-Agent and caps requests per second. */
    val mangaHttpClient: OkHttpClient by lazy {
        metaHttpClient.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                chain.proceed(
                    when {
                        req.url.host == "api.mangadex.org" ->
                            req.newBuilder().header("User-Agent", "FuwaStream/${BuildConfig.VERSION_NAME} (github.com/SakamotoMrX/fuwanime)").build()
                        // Shinigami's API answers browser-like clients.
                        req.url.host == "api.shngm.io" -> req.newBuilder()
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36")
                            .header("Accept", "application/json")
                            .build()
                        else -> req
                    },
                )
            }
            .addInterceptor(RateLimitInterceptor())
            .build()
    }
    val mangaRepository by lazy {
        MangaRepository(
            MangaDexApi.create(mangaHttpClient),
            appLanguage = { app.resources.configuration.locales[0].language },
            shinigami = ShinigamiSource(ShinigamiApi.create(mangaHttpClient)),
            komiku = KomikuSource(mangaHttpClient),
            mature = { settingsStore.mangaMature.first() },
        )
    }

    val mangaDownloadDao by lazy { database.mangaDownloadDao() }

    /** Downloaded manga chapters (app-private files), fetched one at a time by MangaDownloadWorker. */
    val mangaDownloads by lazy { MangaDownloadRepository(app, mangaDownloadDao, settingsStore) }

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
        AppUpdateWorker.schedule(this)
        container.appScope.launch {
            val backup = container.settingsStore.backup.first()
            if (backup.treeUri != null && backup.auto) BackupWorker.schedule(this@FuwaApp)
        }
    }

    // Coil 3 ignores HTTP cache headers by default, matching the old respectCacheHeaders(false).
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.httpClient })) }
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
