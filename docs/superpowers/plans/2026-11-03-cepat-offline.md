# Cepat & Tahan Offline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Beranda buka instan dari cache, semua layar API tetap tampil saat offline, indikator offline/refresh yang menyatu dengan desain, dan baseline profile untuk cold start lebih cepat.

**Architecture:** Satu `okhttp3.Cache` untuk API dengan dua interceptor (`StoreAll` menyimpan semua respons API kecuali stream, `OfflineFallback` menyajikan cache saat jaringan/Cloudflare gagal). `AnimeRepository` dapat `cachedApi` (FORCE_CACHE) untuk cache-first Beranda; logika feed dipindah ke `HomeFeedLoader` + reducer murni yang bisa dites. `NetworkStatus` (StateFlow) jadi satu-satunya sumber indikator UI.

**Tech Stack:** Kotlin 2.2, Compose BOM 2025.07, OkHttp 4.12 + Retrofit 2.12 + Moshi, AGP 9.1.1, androidx.baselineprofile 1.5.0, profileinstaller 1.4.1, JUnit 4 + MockWebServer 4.12 + kotlinx-coroutines-test 1.10.2.

**Spec:** `docs/superpowers/specs/2026-11-03-cepat-offline-design.md`

## Global Constraints

- Tidak ada toolchain Android lokal. Gate tes = `bash /home/sakamoto/Data/Github/fuwanime-quick-wins/.superpowers/quick-wins/ci-test.sh` dari root repo (push branch + `testDebugUnitTest assembleRelease` di CI). Expected sukses: baris terakhir `build: success`.
- QA emulator: `gh workflow run build.yml --repo SakamotoMrX/fuwanime --ref feat/cepat-offline -f qa=true`, lalu `gh run download <id> --repo SakamotoMrX/fuwanime -n smoke-screenshots -D <dir>`.
- Commit tanpa baris co-author / atribusi Claude.
- Teks UI bahasa Indonesia, persis seperti di task. Indikator hanya memakai token tema yang ada (`primary`, `surfaceContainerHigh`, `outlineVariant`, `onSurfaceVariant`, `Motion.*`), tidak ada warna baru.
- Cache API: `cacheDir/api_cache`, 50 MB (`50L * 1024 * 1024`).
- Path stream yang tidak boleh disimpan: segmen `episode/streamnew/`.
- Tidak ada C++/NDK.
- `minSdk = 24` tetap.

## Rulings terhadap spec (diputuskan saat menulis plan)

1. **`Cache-Control: public, max-age=0` menggantikan `no-cache`.** OkHttp tidak pernah menyajikan entri `no-cache` untuk request `only-if-cached` (CacheStrategy mewajibkan validasi), jadi FORCE_CACHE akan selalu 504. `max-age=0` punya arti sama untuk request biasa (selalu ke jaringan) tapi bisa disajikan dengan `max-stale`. Biaya kalau salah: offline fallback tidak jalan — tertangkap oleh `ApiCacheTest`.
2. **Client API terpisah dari client gambar.** `httpClient` (Coil) tidak dapat cache/interceptor baru; `apiHttpClient = httpClient.newBuilder()…`. Supaya byte gambar tidak masuk cache 50 MB API (Coil punya disk cache sendiri).
3. **`relativeTime` dipecah, bukan fungsi baru `relativeAgeLabel`.** Badan `when` diekstrak jadi `relativeAge(diffMs)`, dipakai `relativeTime` dan pill. Teks mengikuti yang sudah ada: `"N mnt lalu"`, bukan `"N menit lalu"` (konsisten dengan caption Episode Terbaru).
4. **Respons challenge Cloudflare (`cf-mitigated` header, biasanya 403) juga dianggap tidak terjangkau**, sama seperti 503/52x. Spec diam soal ini; user yang kena challenge lebih baik melihat data tersimpan.
5. **Lokasi profil**: default plugin `app/src/release/generated/baselineProfiles/baseline-prof.txt` (dibaca otomatis oleh consumer plugin), bukan `app/src/main/baseline-prof.txt`.
6. **Copy "Belum ada data tersimpan…" hanya di Beranda** (satu-satunya layar dengan `ErrorState` dari feed yang di-cache-first). Layar lain tetap pakai `friendlyMessage()`.

## Review Focus

1. Hasil cache yang datang setelah hasil jaringan tidak boleh menimpa data baru → `reduce_lateCacheIgnoredAfterNetwork` (Task 3).
2. URL stream dari cache punya token kedaluwarsa → `streamResponseIsNeverStored` (Task 1).
3. Challenge Cloudflare (403 + `cf-mitigated: challenge`) saat cache ada → cache disajikan → `cloudflareChallengeFallsBackToCache` (Task 1).
4. Jam device mundur / `cacheServedAt` di masa depan → pill tidak menampilkan "data " kosong → `offlineLabel_futureTimestampShowsPlainOffline` (Task 4).
5. Kembali online saat data di layar sudah dari jaringan → tidak refresh lagi → `shouldAutoRefresh_onlyWhenStale` (Task 3).

---

### Task 1: NetworkStatus + interceptor cache API

**Files:**
- Create: `app/src/main/java/com/fuwanime/data/remote/NetworkStatus.kt`
- Create: `app/src/main/java/com/fuwanime/data/remote/ApiCache.kt`
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts` (testImplementation)
- Test: `app/src/test/java/com/fuwanime/ApiCacheTest.kt`

**Interfaces:**
- Produces:
  - `data class NetState(val online: Boolean = true, val cacheServedAt: Long? = null)`
  - `class NetworkStatus { val state: StateFlow<NetState>; fun setOnline(online: Boolean); fun markCacheServed(atMillis: Long); fun markNetworkOk(); fun start(context: Context) }`
  - `class OfflineFallback(private val status: NetworkStatus) : Interceptor`
  - `object StoreAll : Interceptor`
  - `object ForceCache : Interceptor` (mengganti cacheControl request jadi `CacheControl.FORCE_CACHE`)
  - `fun apiCacheClient(base: OkHttpClient, cache: Cache?, status: NetworkStatus): OkHttpClient` — `base.newBuilder()` + `cache(cache)` + `addInterceptor(OfflineFallback(status))` + `addNetworkInterceptor(StoreAll)`

- [ ] **Step 1: Tambah dependency tes**

`libs.versions.toml`: `coroutinesTest` tidak perlu versi baru — pakai `coroutines`. Tambah library `okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }` dan `kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }`. `app/build.gradle.kts`: `testImplementation(libs.okhttp.mockwebserver)`, `testImplementation(libs.kotlinx.coroutines.test)`.

- [ ] **Step 2: Tulis tes yang gagal** — `ApiCacheTest`, client = `apiCacheClient(OkHttpClient(), Cache(tempDir, 1 MB), status)` ke `MockWebServer`; request GET `server.url("/3/2/explore/movie")`.

```kotlin
@Test fun storedResponseServedWhenServerDies() {
    server.enqueue(MockResponse().setBody("A").setHeader("Cache-Control", "no-store"))
    assertEquals("A", get("/3/2/explore/movie").body!!.string())
    server.shutdown()
    assertEquals("A", get("/3/2/explore/movie").body!!.string())
    assertNotNull(status.state.value.cacheServedAt)
}
@Test fun streamResponseIsNeverStored() {
    server.enqueue(MockResponse().setBody("S"))
    get("/3/2/episode/streamnew/9").close()
    server.shutdown()
    assertThrows(IOException::class.java) { get("/3/2/episode/streamnew/9") }
}
@Test fun offlineCacheMissRethrowsOriginalIOException() {
    server.shutdown()
    assertThrows(ConnectException::class.java) { get("/3/2/explore/movie") }
}
@Test fun notFoundIsNotReplacedByCache() { /* enqueue 200 "A", then 404 → second response code == 404 */ }
@Test fun serviceUnavailableFallsBackToCache() { /* 200 "A", then 503 → body "A" */ }
@Test fun cloudflareChallengeFallsBackToCache() { /* 200 "A", then 403 + header "cf-mitigated: challenge" → body "A" */ }
@Test fun successAfterFallbackClearsCacheServedAt() { /* 200, 503 (cache served, non-null), 200 → cacheServedAt == null */ }
@Test fun forceCacheNeverHitsNetwork() { /* client.newBuilder().addInterceptor(ForceCache): miss → code 504, server.requestCount unchanged */ }
```

- [ ] **Step 3: Jalankan gate, pastikan gagal**

Run: `bash /home/sakamoto/Data/Github/fuwanime-quick-wins/.superpowers/quick-wins/ci-test.sh`
Expected: `build: failure`, log berisi `Unresolved reference 'apiCacheClient'` / `'NetworkStatus'`.

- [ ] **Step 4: Implementasi**

- `StoreAll`: proses `chain.proceed`; kalau `request.url.encodedPath` mengandung `episode/streamnew/` → header `Cache-Control: no-store`, selain itu `Cache-Control: public, max-age=0`; hapus `Pragma` dan `Expires`.
- `OfflineFallback`: hanya `request.method == "GET"`; method lain langsung `chain.proceed`.
  - `IOException` dari `chain.proceed` → `chain.proceed(request.newBuilder().cacheControl(CacheControl.FORCE_CACHE).build())`; kalau code 504 → tutup respons itu, lempar exception asli.
  - Respons code 503, 520–529, atau punya header `cf-mitigated` → **jangan tutup dulu**, minta FORCE_CACHE seperti di atas; cache sukses → tutup respons asli, kembalikan respons cache; cache 504 → tutup respons cache, kembalikan respons asli. Jaringan tidak pernah dicoba ulang.
- Saat retry cache sukses → `status.markCacheServed(response.receivedResponseAtMillis)` (pakai `cacheResponse?.receivedResponseAtMillis`). Respons jaringan sukses (`networkResponse != null`, code < 500) → `status.markNetworkOk()`.
- `NetworkStatus.start(context)`: `ConnectivityManager.registerDefaultNetworkCallback` → `onAvailable` = `setOnline(true)`, `onLost` = `setOnline(false)`; nilai awal dari `activeNetwork` + `NET_CAPABILITY_INTERNET`.

- [ ] **Step 5: Jalankan gate, pastikan lolos**

Run: gate di atas. Expected: `build: success`, `ApiCacheTest` 8/8.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/fuwanime/data/remote/NetworkStatus.kt app/src/main/java/com/fuwanime/data/remote/ApiCache.kt app/src/test/java/com/fuwanime/ApiCacheTest.kt
git commit -m "feat(data): cache disk API + fallback offline"
```

### Task 2: Wiring AppContainer + `cacheOnly` di AnimeRepository

**Files:**
- Modify: `app/src/main/java/com/fuwanime/FuwaApp.kt` (AppContainer)
- Modify: `app/src/main/java/com/fuwanime/data/AnimeRepository.kt:23,56-71,101-113`
- Test: `app/src/test/java/com/fuwanime/CacheOnlyRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 1 `apiCacheClient`, `ForceCache`, `NetworkStatus`.
- Produces:
  - `class AnimeRepository(private val api: AnimeApiService, private val cachedApi: AnimeApiService = api)`
  - `suspend fun explore(page: Int, sort: SortOrder = SortOrder.POPULAR, keyword: String = "", type: String? = null, cacheOnly: Boolean = false): Result<List<Anime>>`
  - `suspend fun latestReleases(limit: Int = 20, cacheOnly: Boolean = false): Result<List<LatestRelease>>`
  - `suspend fun schedule(day: String, cacheOnly: Boolean = false): Result<List<Anime>>`
  - `AppContainer.networkStatus: NetworkStatus`, `AppContainer.apiHttpClient: OkHttpClient`

- [ ] **Step 1: Tulis tes yang gagal**

```kotlin
@Test fun cacheOnlyCallsGoToCachedApi() = runBlocking {
    val net = FakeAnimeApi(); val cache = FakeAnimeApi()
    val repo = AnimeRepository(net, cache)
    repo.explore(0, cacheOnly = true); repo.schedule("Senin", cacheOnly = true); repo.latestReleases(20, cacheOnly = true)
    assertEquals(3, cache.calls.size)
    assertTrue(net.calls.isEmpty())
}
@Test fun defaultCallsGoToNetworkApi() = runBlocking { /* tanpa cacheOnly → net dipanggil, cache tidak */ }
```

Catatan: `FakeAnimeApi.calls` hanya dicatat oleh `explore`; tambahkan pencatatan `calls += "schedule($day)"` dan `calls += "latestEpisodes($limit)"` di `FakeAnimeApi` (test source) lalu assert `cache.calls.size == 3`.

- [ ] **Step 2: Gate → gagal** (Expected: `No parameter with name 'cacheOnly'`).

- [ ] **Step 3: Implementasi**

- Repository: `val source = if (cacheOnly) cachedApi else api` di tiga fungsi itu; fungsi lain tetap `api`.
- AppContainer: `val networkStatus = NetworkStatus().also { it.start(app) }`; `val apiHttpClient = apiCacheClient(httpClient, runCatching { Cache(app.cacheDir.resolve("api_cache"), 50L * 1024 * 1024) }.getOrNull(), networkStatus)`; `animeRepository = AnimeRepository(AnimeApiService.create(apiHttpClient), AnimeApiService.create(apiHttpClient.newBuilder().addInterceptor(ForceCache).build()))`. `httpClient` (Coil) dan `videoHttpClient` tidak berubah. `similarCandidates`, detail, episode tetap lewat `api` → otomatis dapat fallback offline.

- [ ] **Step 4: Gate → lolos** (Expected: `build: success`, semua tes lama tetap hijau).

- [ ] **Step 5: Commit** — `git commit -m "feat(data): cachedApi untuk cache-first + wiring cache di AppContainer"`

### Task 3: HomeFeedLoader + reducer + HomeViewModel cache-first

**Files:**
- Create: `app/src/main/java/com/fuwanime/ui/home/HomeFeedLoader.kt`
- Modify: `app/src/main/java/com/fuwanime/ui/home/HomeViewModel.kt` (pindahkan `fetch` ke loader; tambah field feed)
- Modify: `app/src/main/java/com/fuwanime/ui/home/HomeScreen.kt:129` (factory menambah `c.networkStatus`)
- Test: `app/src/test/java/com/fuwanime/HomeCacheFirstTest.kt`

**Interfaces:**
- Consumes: Task 2 `cacheOnly`, `NetworkStatus.state`.
- Produces:
  - `HomeFeed` + `val syncing: Boolean = false`, `val stale: Boolean = false`, `val hasData: Boolean` (getter: popular/thisSeason/latest ada yang tidak kosong)
  - `data class FeedResult(val popular: List<Anime>?, val today: List<Anime>, val season: List<Anime>, val latest: List<LatestRelease>?, val error: Throwable?)` — `error` non-null hanya kalau popular gagal **dan** season kosong (aturan lama)
  - `class HomeFeedLoader(private val anime: AnimeRepository, private val today: Day) { suspend fun fetch(cacheOnly: Boolean): FeedResult }`
  - `fun HomeFeed.reduce(result: FeedResult, fromCache: Boolean, online: Boolean): HomeFeed?` (null = abaikan)
  - `fun shouldAutoRefresh(feed: HomeFeed): Boolean = feed.stale`
  - `const val OFFLINE_EMPTY = "Belum ada data tersimpan. Sambungkan internet lalu coba lagi."`

- [ ] **Step 1: Tulis tes yang gagal**

```kotlin
@Test fun reduce_cacheShownWhileSyncing() {
    val f = HomeFeed().reduce(result(popular = listOf(a1)), fromCache = true, online = true)!!
    assertFalse(f.loading); assertTrue(f.syncing); assertTrue(f.stale); assertEquals(listOf(a1), f.popular)
}
@Test fun reduce_networkFailureKeepsCachedData() {
    val cached = HomeFeed().reduce(result(popular = listOf(a1)), true, true)!!
    val f = cached.reduce(failure(IOException()), fromCache = false, online = false)!!
    assertNull(f.error); assertEquals(listOf(a1), f.popular); assertFalse(f.syncing); assertTrue(f.stale)
}
@Test fun reduce_networkSuccessReplacesCache() { /* stale=false, syncing=false, popular = data jaringan */ }
@Test fun reduce_noCacheOfflineShowsOfflineCopy() {
    val f = HomeFeed().reduce(failure(UnknownHostException()), false, online = false)!!
    assertEquals(OFFLINE_EMPTY, f.error)
}
@Test fun reduce_noCacheOnlineShowsFriendlyMessage() { /* online=true → error == SocketTimeoutException().friendlyMessage() */ }
@Test fun reduce_lateCacheIgnoredAfterNetwork() {
    val net = HomeFeed().reduce(result(popular = listOf(a2)), false, true)!!
    assertNull(net.reduce(result(popular = listOf(a1)), fromCache = true, online = true))
}
@Test fun reduce_emptyCacheIgnored() { /* FeedResult tanpa data, fromCache → null */ }
@Test fun shouldAutoRefresh_onlyWhenStale() { /* stale feed → true; feed dari jaringan → false */ }
@Test fun loader_cacheOnlyUsesCachedApi() = runBlocking {
    /* HomeFeedLoader(AnimeRepository(net, cache), Day.MONDAY).fetch(true) → net.calls kosong, cache.calls berisi explore + 7 schedule + latestEpisodes */
}
```

- [ ] **Step 2: Gate → gagal** (Expected: `Unresolved reference 'reduce'` / `'HomeFeedLoader'`).

- [ ] **Step 3: Implementasi**

`reduce` mengikuti tabel ini (persis):

| kondisi | hasil |
|---|---|
| `fromCache` dan (`!stale && hasData` → data jaringan sudah ada) | `null` |
| `fromCache` dan hasil tanpa data atau `error != null` | `null` |
| `fromCache` | data hasil, `loading=false, syncing=true, stale=true, error=null` |
| jaringan, `error == null` | data hasil (popular/latest null → pakai yang lama), `loading=false, refreshing=false, syncing=false, stale=false, error=null` |
| jaringan gagal, `hasData` | `copy(loading=false, refreshing=false, syncing=false, error=null)` |
| jaringan gagal, tanpa data | `copy(loading=false, refreshing=false, syncing=false, error = if (online) error.friendlyMessage() else OFFLINE_EMPTY)` |

HomeViewModel:
- Konstruktor menambah `private val networkStatus: NetworkStatus`.
- `init`: luncurkan `fetch(cacheOnly = true)` lalu terapkan `reduce(..., fromCache = true)`; setelah itu `load(refreshing = false)` seperti sekarang. `load` memakai loop `AUTO_RETRIES` yang ada; percobaan yang gagal sebelum yang terakhir tidak diterapkan kecuali `feed.value.hasData`.
- `load(refreshing = false)` saat data sudah ada → set `syncing = true` (bukan `loading`).
- Auto-refresh: `networkStatus.state.map { it.online }.distinctUntilChanged().drop(1).filter { it }` → kalau `shouldAutoRefresh(feed.value)` → `load(refreshing = false)`.

- [ ] **Step 4: Gate → lolos** (Expected: `build: success`, `HomeCacheFirstTest` 9/9).

- [ ] **Step 5: Commit** — `git commit -m "feat(home): Beranda cache-first + refresh otomatis saat online lagi"`

### Task 4: Indikator — RefreshHairline, OfflinePill, label

**Files:**
- Modify: `app/src/main/java/com/fuwanime/util/Format.kt:90-105` (ekstrak `relativeAge`, tambah `offlineLabel`)
- Modify: `app/src/main/java/com/fuwanime/ui/components/Components.kt` (dua composable baru)
- Modify: `app/src/main/java/com/fuwanime/ui/home/HomeScreen.kt` (`HomeTopBar` dapat `syncing`)
- Modify: `app/src/main/java/com/fuwanime/ui/navigation/FuwaNavHost.kt:195-232` (pill di atas `NavigationBar`)
- Test: `app/src/test/java/com/fuwanime/FormatTest.kt`

**Interfaces:**
- Consumes: Task 1 `NetState`, Task 3 `HomeFeed.syncing`.
- Produces:
  - `fun relativeAge(diffMs: Long): String` — `< -60_000` → `""`, `< 60_000` → `"baru saja"`, `< 3_600_000` → `"N mnt lalu"`, `< 86_400_000` → `"N jam lalu"`, selain itu `"N hari lalu"`; `relativeTime` memanggilnya.
  - `fun offlineLabel(cacheServedAt: Long?, nowMs: Long): String` — `"Offline"` kalau null atau `relativeAge` kosong, selain itu `"Offline · data ${relativeAge(nowMs - cacheServedAt)}"`.
  - `@Composable fun RefreshHairline(visible: Boolean, modifier: Modifier = Modifier)`
  - `@Composable fun OfflinePill(state: NetState, modifier: Modifier = Modifier)`

- [ ] **Step 1: Tulis tes yang gagal** (di `FormatTest`)

```kotlin
@Test fun relativeAge_buckets() {
    assertEquals("baru saja", relativeAge(30_000))
    assertEquals("5 mnt lalu", relativeAge(5 * 60_000L))
    assertEquals("3 jam lalu", relativeAge(3 * 3_600_000L))
    assertEquals("2 hari lalu", relativeAge(2 * 86_400_000L))
}
@Test fun offlineLabel_withAndWithoutCache() {
    assertEquals("Offline", offlineLabel(null, now))
    assertEquals("Offline · data 3 jam lalu", offlineLabel(now - 3 * 3_600_000L, now))
}
@Test fun offlineLabel_futureTimestampShowsPlainOffline() {
    assertEquals("Offline", offlineLabel(now + 5 * 60_000L, now))
}
```

- [ ] **Step 2: Gate → gagal** (Expected: `Unresolved reference 'relativeAge'`).

- [ ] **Step 3: Implementasi**

- `RefreshHairline`: `AnimatedVisibility(visible && !LocalReduceMotion.current, enter = fadeIn(tween(Motion.Short)), exit = fadeOut(tween(Motion.Short)))` berisi `LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = primary, trackColor = Color.Transparent)`.
- `HomeTopBar(solid, syncing, …)`: bungkus `Row` yang ada dalam `Column`, `RefreshHairline(syncing)` di bawah `Row` (overlay, tidak menggeser konten list).
- `OfflinePill`: state lokal `showBack` — saat `online` berubah false → true, tampilkan `"Kembali online"` dengan titik 6 dp `primary` selama 1.500 ms. Tampil kalau `!online || showBack`. Gaya: `Surface(shape = RoundedCornerShape(50), color = surfaceContainerHigh.copy(alpha = 0.92f), border = BorderStroke(1.dp, outlineVariant))`, padding 12×6 dp, ikon `Icons.Rounded.CloudOff` 14 dp (offline), teks `labelMedium` `onSurfaceVariant`, isi `offlineLabel(cacheServedAt, now)` dengan `now` di-tick tiap 60 s. Masuk/keluar: `slideInVertically { it / 2 } + fadeIn(tween(Motion.Short))`, keluar kebalikannya.
- FuwaNavHost: di dalam `bottomBar` `AnimatedVisibility` (hanya tab), ganti isinya jadi `Column(horizontalAlignment = CenterHorizontally) { OfflinePill(netState, Modifier.padding(bottom = 8.dp)); NavigationBar(...) }`. `netState` dari `container.networkStatus.state.collectAsStateWithLifecycle()`. Di Detail/Player pill tidak tampil karena `bottomBar` tersembunyi.

- [ ] **Step 4: Gate → lolos** (Expected: `build: success`, `FormatTest` termasuk 3 tes baru).

- [ ] **Step 5: Commit** — `git commit -m "feat(ui): hairline refresh + pill offline"`

### Task 5: QA — skenario offline + ukur startup

**Files:**
- Modify: `.github/scripts/qa.sh` (sebelum `# 5. Tabs.` dan di akhir sebelum `adb logcat -d -b crash`)

**Interfaces:**
- Consumes: Task 3/4 (teks "Episode Terbaru", "Offline", "Kembali online").
- Produces: artifact `qa6a-offline-home.png`, `qa6b-back-online.png`, `startup.txt` (baris `median_total_time_ms=<N>`).

- [ ] **Step 1: Tambah skenario 6 (offline)**

```bash
# 6. Offline: data tersimpan + pill, lalu kembali online.
adb shell svc wifi disable; adb shell svc data disable
fresh; shot qa6a-offline-home 2
adb shell svc wifi enable; adb shell svc data enable
shot qa6b-back-online 3; alive
```

- [ ] **Step 2: Tambah langkah startup** (fungsi `startup()`): `am force-stop`, `cmd package compile -f -m speed-profile $PKG`, lalu 5× (`am force-stop`; `sleep 2`; `am start -W -n $PKG/com.fuwanime.MainActivity` → ambil `TotalTime`), tulis median ke `$OUT/startup.txt` sebagai `median_total_time_ms=<N>` dan `echo` ke log.

- [ ] **Step 3: Jalankan QA**

Run: `gh workflow run build.yml --repo SakamotoMrX/fuwanime --ref feat/cepat-offline -f qa=true` lalu download artifact.
Expected: run sukses; `qa6a-offline-home.png` menampilkan Beranda berisi poster + pill "Offline · data …"; `qa6b-back-online.png` menampilkan pill "Kembali online" atau pill sudah hilang; `startup.txt` ada. Catat angka median ini sebagai **sebelum profile**.

- [ ] **Step 4: Commit** — `git commit -m "qa: skenario offline + ukur cold start"`

### Task 6: Baseline profile

**Files:**
- Create: `baselineprofile/build.gradle.kts`, `baselineprofile/src/main/AndroidManifest.xml` (kosong), `baselineprofile/src/main/java/com/fuwanime/baselineprofile/BaselineProfileGenerator.kt`
- Modify: `settings.gradle.kts` (`include(":baselineprofile")`), `build.gradle.kts` (plugin `com.android.test` + baselineprofile `apply false`), `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Modify: `.github/workflows/build.yml` (input `profile` + job `profile`)
- Create (hasil generator): `app/src/release/generated/baselineProfiles/baseline-prof.txt`

**Interfaces:**
- Consumes: teks UI "Episode Terbaru", "Jelajah", "Jadwal", "Koleksi".
- Produces: task Gradle `:app:generateBaselineProfile`; artifact `baseline-profile` di CI.

- [ ] **Step 1: Konfigurasi**

- Versi: `baselineprofile = "1.5.0"`, `profileinstaller = "1.4.1"`, `uiautomator = "2.4.0"`, `androidxJunit = "1.3.0"`; plugin `androidx-baselineprofile = { id = "androidx.baselineprofile", version.ref = "baselineprofile" }`, `android-test = { id = "com.android.test", version.ref = "agp" }`; library `androidx-benchmark-macro-junit4` (versi `baselineprofile`), `androidx-profileinstaller`, `androidx-uiautomator`, `androidx-junit`.
- `:app`: plugin `androidx.baselineprofile`, `implementation(libs.androidx.profileinstaller)`, `baselineProfile(project(":baselineprofile"))`.
- `:baselineprofile`: `com.android.test` + `androidx.baselineprofile`; `namespace = "com.fuwanime.baselineprofile"`, `compileSdk = 36`, `minSdk = 28`, `targetProjectPath = ":app"`; `baselineProfile { useConnectedDevices = true }`; dependency `benchmark-macro-junit4`, `uiautomator`, `androidx-junit`.

- [ ] **Step 2: Generator**

`BaselineProfileGenerator` (`@RunWith(AndroidJUnit4::class)`, `@get:Rule val rule = BaselineProfileRule()`), `rule.collect(packageName = "com.fuwanime.app")`: `pressHome(); startActivityAndWait()`; tunggu `By.text("Episode Terbaru")` 15 s; fling list ke bawah 2×; swipe hero kiri 1×; klik poster pertama di bawah "Episode Terbaru", tunggu 3 s, `pressBack()`; klik `By.text("Jelajah")`, `By.text("Jadwal")`, `By.text("Koleksi")`, masing-masing tunggu idle.

- [ ] **Step 3: Gate build biasa → lolos**

Run: gate. Expected: `build: success` (modul baru terkompilasi; build biasa tidak menjalankan generator). Kalau plugin 1.5.0 tidak kompatibel dengan AGP 9.1.1 (error konfigurasi plugin) → `Ruling:` di ledger, revert Task 6, lanjut Task 7 tanpa profile (fallback spec).

- [ ] **Step 4: Job CI `profile`**

`build.yml`: input `profile` (boolean, default false). Job `profile` (`if: github.event_name == 'workflow_dispatch' && inputs.profile`): checkout, setup Java 21 + Gradle 9.3.1 (sama dengan job build), KVM, `reactivecircus/android-emulator-runner@v2` (api-level 34, x86_64, google_apis, pixel_6, opsi emulator sama) dengan `script: gradle :app:generateBaselineProfile --stacktrace`, lalu upload `app/src/release/generated/baselineProfiles/` sebagai artifact `baseline-profile`.

- [ ] **Step 5: Generate + commit profile**

Run: `gh workflow run build.yml --repo SakamotoMrX/fuwanime --ref feat/cepat-offline -f profile=true`, tunggu sukses, `gh run download <id> -n baseline-profile -D app/src/release/generated/baselineProfiles`.
Expected: `baseline-prof.txt` ada dan berisi baris `Lcom/fuwanime/`. Kalau job gagal di emulator → ledger `Ruling:` + fallback seperti Step 3.

- [ ] **Step 6: Commit** — `git commit -m "perf: baseline profile + job CI generator"`

### Task 7: Verifikasi akhir

- [ ] **Step 1: QA ulang** — jalankan QA (Task 5 Step 3). Expected: semua skenario lolos, tidak ada `not found` baru untuk tombol yang sebelumnya ketemu, `startup.txt` median **lebih kecil** dari angka "sebelum profile". Kalau tidak turun, laporkan angkanya apa adanya (jangan klaim lebih cepat).
- [ ] **Step 2: Lihat screenshot** — `qa5d-home.png` (Beranda normal, tanpa pill), `qa6a-offline-home.png` (data + pill), `qa4a-detail.png` (Detail normal).
- [ ] **Step 3: PR** — `gh pr create --repo SakamotoMrX/fuwanime --base main --head feat/cepat-offline`, body Indonesia singkat (Ringkasan, Bukti dengan link run QA + angka startup sebelum/sesudah, Risiko merge), tanpa atribusi.
