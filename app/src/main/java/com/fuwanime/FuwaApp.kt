package com.fuwanime

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
import com.fuwanime.data.local.FuwaDatabase
import com.fuwanime.data.local.SettingsStore
import com.fuwanime.playback.PlaybackHolder
import com.fuwanime.data.remote.AnimeApiService
import com.fuwanime.data.remote.ForceCache
import com.fuwanime.data.remote.NetworkStatus
import com.fuwanime.data.remote.apiCacheClient
import com.fuwanime.data.UpdateRepository
import com.fuwanime.data.notify.EpisodeNotifyWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.fuwanime.widget.HomeSurfaces
import okhttp3.Cache
import okhttp3.OkHttpClient

/** Manual DI: one instance of each dependency for the whole process. */
class AppContainer(private val app: Application) {
    val httpClient: OkHttpClient = AnimeApiService.createHttpClient()

    /** Connectivity plus "showing cached data since"; drives the offline indicators. */
    val networkStatus = NetworkStatus().also { it.start(app) }

    /** API-only client with the 50 MB disk cache; images keep [httpClient] and Coil's own cache. */
    private val apiHttpClient = apiCacheClient(
        httpClient,
        runCatching { Cache(app.cacheDir.resolve("api_cache"), 50L * 1024 * 1024) }.getOrNull(),
        networkStatus,
    )
    val animeRepository = AnimeRepository(
        AnimeApiService.create(apiHttpClient),
        AnimeApiService.create(apiHttpClient.newBuilder().addInterceptor(ForceCache).build()),
    )
    private val database = FuwaDatabase.create(app)
    val libraryRepository = LibraryRepository(database)
    val settingsStore = SettingsStore(app)

    /** For writes that must outlive the screen that started them (e.g. saving progress on exit). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val downloadRepository = DownloadRepository(app, database.downloadDao(), animeRepository, appScope)

    /** Re-renders the "Lanjutkan" shortcut and widget after history changes. */
    fun refreshHomeSurfaces() {
        appScope.launch { HomeSurfaces.refresh(app, libraryRepository) }
    }

    /** In-app update checker against GitHub Releases. */
    val updateRepository = UpdateRepository(settingsStore)

    /** Video requests get the same UA as the API but none of its JSON headers. */
    val videoHttpClient: OkHttpClient = httpClient.newBuilder().apply { interceptors().clear() }.build()

    val playbackHolder by lazy { PlaybackHolder(app, videoHttpClient) }
}

class FuwaApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        EpisodeNotifyWorker.schedule(this)
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
