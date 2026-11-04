# Player latar (sub-proyek B) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade Media3/Compose/Coil, add a `MediaSessionService` (lock screen, headset, notification, optional background audio, PiP actions), and polish PiP, aspect mode, and large-screen fullscreen.

**Architecture:**
- `PlaybackHolder` (in `AppContainer`) builds the `ExoPlayer` synchronously and connects a `MediaController` so `PlaybackService` starts and wraps that same player in a `MediaSession`.
- `PlayerRoute` borrows the player from the holder instead of building it.
- Every decision rule (stop action, PiP aspect clamp, pinch threshold, fullscreen, now-playing fields) is a pure function in `ui/player/PlayerPolicy.kt` with JVM tests.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose (BOM 2026.09.00), Media3 1.11.1 (`exoplayer`, `session`, `ui`), Coil 3.6.3, DataStore Preferences, JUnit4.

**Spec:** `docs/superpowers/specs/2026-11-05-player-latar-design.md`

## Global Constraints

- No local Android toolchain. The test gate is `bash /home/sakamoto/Data/Github/fuwanime-quick-wins/.superpowers/quick-wins/ci-test.sh` (pushes HEAD; runs `testDebugUnitTest` + `assembleRelease`; prints `CI PASSED`/`CI FAILED` and failing lines).
- QA gate: `gh workflow run build.yml --repo SakamotoMrX/fuwanime --ref feat/player-latar -f qa=true`; artifact `smoke-screenshots` holds the PNGs and `qa-checks.txt`.
- RED runs may be batched: stub the production symbol (`TODO()` body) so the test file compiles and fails on assertions, and run several tasks' RED in one CI run.
- minSdk 24. Guard API-specific calls with `Build.VERSION.SDK_INT`. There is no `java.time` (minSdk 24).
- Versions: Media3 `1.11.1`, Compose BOM `2026.09.00`, compileSdk/targetSdk `37`, Coil `3.6.3` (`io.coil-kt.coil3`).
- Setting copy:
  - "Putar audio di latar" / "Suara tetap jalan saat layar mati atau aplikasi ditinggal"
  - key `background_audio`, default `false`
  - key `video_resize`, values `FIT`/`ZOOM`, default `FIT`
- Aspect labels: "Pas layar" (FIT) and "Isi layar" (ZOOM), the existing chip text in `PlayerSettingsSheet`.
- PiP action titles: "Mundur 10 detik", "Putar", "Jeda", "Maju 10 detik".
- PiP ratio bounds: max `239:100`, min `100:239`, invalid dimensions → `16:9`.
- Pinch thresholds: `scale >= 1.15f` → ZOOM, `scale <= 0.87f` → FIT.
- Commits: conventional style, no Claude co-author lines.
- Background playback covers only the current episode. Swiping the app from Recents pauses and stops the service.

## Review Focus

1. **Process death or system restart of `PlaybackService` with no player.** `onCreate` must `stopSelf()` without crashing, and `onGetSession` returns null. Pinned by code review plus a QA `alive` check after `am force-stop` while the notification exists.
2. **Re-entering the player while the previous holder is still alive** (episode → back → episode quickly). `acquire` must release the old player and controller first, so there is never a second `ExoPlayer` or a leaked service. Pinned in Task 6 by the QA scenario that opens the player twice in a row and checks a single `com.fuwanime.app` session in `dumpsys media_session`.
3. **Ultrawide or zero-size video entering PiP.** Clamped, never thrown. Pinned by the `clampPipAspect` tests (Task 4).
4. **Pinch starting as a single-finger tap or hold.** Must not trigger 2x boost, double-tap seek, or a brightness drag once the second finger lands. A pinch while locked or in PiP must do nothing. Pinned by code review of the gesture order plus QA (no crash).
5. **Fullscreen exit on a device that ignores orientation requests.** Back from manual fullscreen must return to the portrait layout even if the window stays landscape-sized. Pinned by the `isFullscreen` tests (Task 4): `isFullscreen(landscape = false, manual = false, inPip = false) == false`, and back sets `manual = false`.

---

### Task 1: Media3 1.11.1 + media3-session

**Files:**
- Modify: `gradle/libs.versions.toml` (`media3 = "1.11.1"`; add `media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "media3" }`)
- Modify: `app/build.gradle.kts` (`implementation(libs.media3.session)`)
- Modify: any player code the new APIs break (`ui/player/PlayerScreen.kt`, `ui/player/PlayerViewModel.kt`, `data/DownloadRepository.kt` if it uses Media3)

**Interfaces:**
- Produces: `libs.media3.session` available to Task 6.

- [ ] **Step 1: Bump the version and add the session artifact**
- [ ] **Step 2: Run the gate**

Run: `bash /home/sakamoto/Data/Github/fuwanime-quick-wins/.superpowers/quick-wins/ci-test.sh`
Expected: `CI PASSED`. On `CI FAILED`, fix the compile errors the output names (deprecated or removed APIs) in this task, then rerun.

- [ ] **Step 3: Commit**

```bash
git commit -am "build: upgrade Media3 to 1.11.1 and add media3-session"
```

### Task 2: Compose BOM 2026.09.00 + compileSdk/targetSdk 37

**Files:**
- Modify: `gradle/libs.versions.toml` (`composeBom = "2026.09.00"`)
- Modify: `app/build.gradle.kts` (`compileSdk = 37`, `targetSdk = 37`)
- Modify: `baselineprofile/build.gradle.kts` (its `compileSdk`/`targetSdk`, if set to 36)

- [ ] **Step 1: Bump the BOM and the SDK levels**
- [ ] **Step 2: Run the gate**

Run: the ci-test.sh command.
Expected: `CI PASSED`. If a library rejects the new BOM (dependency-resolution or metadata error), raise only that library (`activityCompose`, `lifecycle`, `navigationCompose`) to the lowest version the error names. If the runner lacks platform 37, add `sdkmanager "platforms;android-37"` before the Gradle step in `.github/workflows/build.yml`. Deprecation warnings are not failures.

- [ ] **Step 3: Commit**

```bash
git commit -am "build: Compose BOM 2026.09.00, compileSdk/targetSdk 37"
```

### Task 3: Coil 3.6.3

**Files:**
- Modify: `gradle/libs.versions.toml`:
  - `coil = "3.6.3"`
  - `coil-compose` → group `io.coil-kt.coil3`
  - add `coil-network-okhttp = { group = "io.coil-kt.coil3", name = "coil-network-okhttp", version.ref = "coil" }`
- Modify: `app/build.gradle.kts` (add `implementation(libs.coil.network.okhttp)`)
- Modify: `app/src/main/java/com/fuwanime/FuwaApp.kt:4-8,65-90`:
  - `ImageLoaderFactory` → `SingletonImageLoader.Factory`, with `override fun newImageLoader(context: PlatformContext): ImageLoader`
  - `components { add(OkHttpNetworkFetcherFactory(callFactory = { container.httpClient })) }`
  - `MemoryCache.Builder().maxSizePercent(context, 0.25)`
  - `DiskCache.Builder().directory(<same dir>.toOkioPath())`, keeping the same size as now
  - `crossfade(true)`
- Modify: `app/src/main/java/com/fuwanime/util/Bitmaps.kt` (`coil3.*` imports; `(result as? SuccessResult)?.image?.toBitmap()`; keep `allowHardware(false)` and the 10 s timeout)
- Modify: `app/src/main/java/com/fuwanime/ui/components/Components.kt`, `app/src/main/java/com/fuwanime/ui/home/HomeScreen.kt` (imports `coil3.compose.AsyncImage`, `coil3.request.ImageRequest`, `coil3.request.crossfade` if used)

- [ ] **Step 1: Migrate the dependency and the 4 files**
- [ ] **Step 2: Run the gate**

Run: the ci-test.sh command.
Expected: `CI PASSED`.

- [ ] **Step 3: Commit**

```bash
git commit -am "build: migrate to Coil 3.6.3 with OkHttp network fetcher"
```

### Task 4: PlayerPolicy pure functions

**Files:**
- Create: `app/src/main/java/com/fuwanime/ui/player/PlayerPolicy.kt`
- Test: `app/src/test/java/com/fuwanime/PlayerPolicyTest.kt`

**Interfaces:**
- Produces:
  - `enum class StopAction { PAUSE, KEEP_PLAYING }`
  - `fun stopAction(inPip: Boolean, backgroundAudio: Boolean): StopAction`
  - `fun clampPipAspect(width: Int, height: Int): Pair<Int, Int>`
  - `enum class VideoResize { FIT, ZOOM }` (in `com.fuwanime.data.local`, file `SettingsStore.kt`, so settings own it; Task 5 persists it)
  - `fun pinchResize(current: VideoResize, scale: Float): VideoResize?`
  - `fun isFullscreen(landscape: Boolean, manual: Boolean, inPip: Boolean): Boolean`
  - `data class NowPlaying(val title: String, val artist: String, val subtitle: String, val artworkUrl: String)`
  - `fun nowPlaying(episode: Episode, anime: Anime?): NowPlaying`

- [ ] **Step 1: Write the failing tests**

```kotlin
class PlayerPolicyTest {
    @Test fun closingPipPauses() = assertEquals(StopAction.PAUSE, stopAction(inPip = true, backgroundAudio = true))
    @Test fun leavingWithSettingOffPauses() = assertEquals(StopAction.PAUSE, stopAction(inPip = false, backgroundAudio = false))
    @Test fun leavingWithSettingOnKeepsPlaying() = assertEquals(StopAction.KEEP_PLAYING, stopAction(inPip = false, backgroundAudio = true))

    @Test fun normalAspectUnchanged() = assertEquals(1920 to 1080, clampPipAspect(1920, 1080))
    @Test fun ultrawideClampedToMax() = assertEquals(239 to 100, clampPipAspect(2400, 1000))
    @Test fun tallClampedToMin() = assertEquals(100 to 239, clampPipAspect(1000, 3000))
    @Test fun zeroSizeFallsBack() = assertEquals(16 to 9, clampPipAspect(0, 0))
    @Test fun negativeSizeFallsBack() = assertEquals(16 to 9, clampPipAspect(-1, 720))

    @Test fun pinchOutZooms() = assertEquals(VideoResize.ZOOM, pinchResize(VideoResize.FIT, 1.15f))
    @Test fun pinchInFits() = assertEquals(VideoResize.FIT, pinchResize(VideoResize.ZOOM, 0.87f))
    @Test fun smallPinchIgnored() { assertNull(pinchResize(VideoResize.FIT, 1.1f)); assertNull(pinchResize(VideoResize.ZOOM, 0.9f)) }
    @Test fun pinchToCurrentModeIsNull() { assertNull(pinchResize(VideoResize.ZOOM, 1.5f)); assertNull(pinchResize(VideoResize.FIT, 0.5f)) }

    @Test fun landscapeIsFullscreen() = assertTrue(isFullscreen(landscape = true, manual = false, inPip = false))
    @Test fun manualPortraitIsFullscreen() = assertTrue(isFullscreen(landscape = false, manual = true, inPip = false))
    @Test fun pipIsNeverFullscreen() = assertFalse(isFullscreen(landscape = true, manual = true, inPip = true))
    @Test fun portraitNotManualIsNotFullscreen() = assertFalse(isFullscreen(landscape = false, manual = false, inPip = false))

    @Test fun nowPlayingUsesEpisodeAndAnime() = assertEquals(
        NowPlaying("Pertemuan", "Frieren", "Episode 3", "https://t/3.jpg"),
        nowPlaying(Episode(id = "e3", number = "3", title = "Pertemuan", thumbnail = "https://t/3.jpg"), anime("Frieren", poster = "https://p.jpg")),
    )
    @Test fun nowPlayingFallsBack() = assertEquals(
        NowPlaying("Frieren", "Frieren", "Episode 3", "https://p.jpg"),
        nowPlaying(Episode(id = "e3", number = "3", title = ""), anime("Frieren", poster = "https://p.jpg")),
    )
    @Test fun nowPlayingWithoutAnimeOrNumber() = assertEquals(
        NowPlaying("Spesial", "", "", ""),
        nowPlaying(Episode(id = "x", number = "", title = "Spesial"), null),
    )
}
```

`anime(title, poster)` builds an `Anime` with the fixture helper in `Fixtures.kt` if one exists, else with the constructor's required fields. Subtitle rule: `"Episode $number"` when `number` is not blank, else `""`. Artwork: the episode thumbnail if not blank, else the anime poster, else `""`.

- [ ] **Step 2: Stub every Produces symbol with `TODO()` and run the gate**

Run: the ci-test.sh command.
Expected: `CI FAILED` with `PlayerPolicyTest` failures (`NotImplementedError`), no compile errors.

- [ ] **Step 3: Implement the functions in `PlayerPolicy.kt`**

`clampPipAspect`: when either dimension is ≤ 0, return `16 to 9`. Otherwise `r = width.toFloat() / height`: `r > 2.39f` → `239 to 100`; `r < 100f / 239f` → `100 to 239`; else `width to height`.

- [ ] **Step 4: Run the gate**

Expected: `CI PASSED`, with `PlayerPolicyTest` all green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/fuwanime/ui/player/PlayerPolicy.kt app/src/main/java/com/fuwanime/data/local/SettingsStore.kt app/src/test/java/com/fuwanime/PlayerPolicyTest.kt
git commit -m "feat(player): pure policy for stop action, PiP aspect, pinch, fullscreen, now playing"
```

### Task 5: Settings: background audio + video resize

**Files:**
- Modify: `app/src/main/java/com/fuwanime/data/local/SettingsStore.kt`:
  - `AppSettings.backgroundAudio: Boolean = false`
  - `AppSettings.videoResize: VideoResize = VideoResize.FIT`
  - keys `background_audio`, `video_resize`
  - `suspend fun setBackgroundAudio(enabled: Boolean)`, `suspend fun setVideoResize(mode: VideoResize)`
  - make `Preferences.toSettings()` `internal` so tests can call it; an unknown `video_resize` string → `FIT`
- Modify: `app/src/main/java/com/fuwanime/ui/settings/SettingsScreen.kt:182` (a `SwitchRow` right below "Picture-in-Picture otomatis", icon `Icons.Rounded.Headphones`, copy from Global Constraints) and its ViewModel (`setBackgroundAudio`)
- Test: `app/src/test/java/com/fuwanime/PlayerSettingsTest.kt`

**Interfaces:**
- Consumes: `VideoResize` (Task 4)
- Produces: `AppSettings.backgroundAudio`, `AppSettings.videoResize`, `SettingsStore.setVideoResize(VideoResize)`, `SettingsStore.setBackgroundAudio(Boolean)`

- [ ] **Step 1: Write the failing tests**

```kotlin
class PlayerSettingsTest {
    @Test fun defaults() {
        val s = emptyPreferences().toSettings()
        assertFalse(s.backgroundAudio)
        assertEquals(VideoResize.FIT, s.videoResize)
    }
    @Test fun readsStoredValues() {
        val s = mutablePreferencesOf(booleanPreferencesKey("background_audio") to true, stringPreferencesKey("video_resize") to "ZOOM").toSettings()
        assertTrue(s.backgroundAudio)
        assertEquals(VideoResize.ZOOM, s.videoResize)
    }
    @Test fun unknownResizeFallsBackToFit() =
        assertEquals(VideoResize.FIT, mutablePreferencesOf(stringPreferencesKey("video_resize") to "BOGUS").toSettings().videoResize)
}
```

`toSettings` is a member extension of `SettingsStore` today. Move it to a top-level `internal fun Preferences.toSettings(): AppSettings` in the same file, with the keys object made `internal` at file level, so the test needs no `Context`.

- [ ] **Step 2: Run the gate (batched with Task 4 RED is fine)**

Expected: `CI FAILED` on `PlayerSettingsTest` (unresolved fields before Step 3 → stub the fields first so it compiles and fails on assertions, e.g. read `background_audio` as constant `true`).

- [ ] **Step 3: Implement the fields, keys, setters, and Settings row**
- [ ] **Step 4: Run the gate**

Expected: `CI PASSED`.

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(settings): background audio toggle and remembered video resize"
```

### Task 6: PlaybackHolder + PlaybackService + PlayerRoute wiring

**Files:**
- Create: `app/src/main/java/com/fuwanime/playback/PlaybackHolder.kt`
- Create: `app/src/main/java/com/fuwanime/playback/PlaybackService.kt`
- Modify: `app/src/main/java/com/fuwanime/FuwaApp.kt` (`AppContainer.playbackHolder: PlaybackHolder` by lazy)
- Modify: `app/src/main/AndroidManifest.xml`:
  - permissions `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`
  - `<service android:name=".playback.PlaybackService" android:exported="true" android:foregroundServiceType="mediaPlayback">` with the intent-filter `androidx.media3.session.MediaSessionService`
- Modify: `app/src/main/java/com/fuwanime/ui/player/PlayerScreen.kt`:
  - move `buildPlayer` into the holder
  - `remember { holder.acquire(context) }`
  - `onDispose` calls `holder.release()` instead of `player.release()`
  - `setMediaItem` uses `MediaItem.Builder().setUri(url).setMediaMetadata(...)` from `nowPlaying(state.episode, state.anime)`
  - `ON_STOP` pauses only when `stopAction(inPip, settings.backgroundAudio) == PAUSE`
- Modify: `.github/scripts/qa.sh` (new scenario 9, below)

**Interfaces:**
- Consumes: `stopAction`, `nowPlaying` (Task 4); `AppSettings.backgroundAudio` (Task 5); `libs.media3.session` (Task 1)
- Produces:
  - `class PlaybackHolder(private val app: Application) { val player: ExoPlayer?; fun acquire(context: Context): ExoPlayer; fun release() }`
  - `class PlaybackService : MediaSessionService()`
  - `PlaybackService` constants `ACTION_MEDIA_BUTTON` targets for Task 7: the service handles `Intent.ACTION_MEDIA_BUTTON` through Media3's default handling, so Task 7 only needs the component name

**Holder rules:**
- `acquire` calls `release()` first if a player exists.
- It builds the player (current `buildPlayer` body plus `.setWakeMode(C.WAKE_MODE_NETWORK)`), stores it, then builds `MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()` and keeps the future. Failure is logged with tag `FuwaPlayback`.
- `release()` calls `MediaController.releaseFuture(future)`, `player.stop()`, `player.release()`, sets `player = null`, then `app.stopService(Intent(app, PlaybackService::class.java))`.

**Service rules:**
- `onCreate`: `val p = (application as FuwaApp).container.playbackHolder.player ?: return stopSelf()`. Then build the session with `setSessionActivity(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT))`.
- `onGetSession`: returns `session` (null when none).
- `onTaskRemoved`: pause the player, then `stopSelf()`.
- `onDestroy`: `session?.release()`, `session = null`, then `super.onDestroy()`. Never release the player here.

- [ ] **Step 1: Add the QA scenario first (the RED for this task)**

Append after scenario 8 in `qa.sh`:

```bash
# 9. Media session: lock-screen/headset control, background audio setting, notification.
session_state() { adb shell dumpsys media_session | grep -A40 "package=$PKG" | grep -o "PlaybackState {state=[0-9]*" | head -1 | grep -o "[0-9]*$"; }
fresh
tap "Tonton"; sleep 5; scroll_to "Episode 1" && tap "Episode 1"; sleep 20
echo "session playing: $(session_state)" | tee -a "$OUT/qa-checks.txt"            # 3
adb shell input keyevent KEYCODE_MEDIA_PAUSE; sleep 2
echo "session after media pause: $(session_state)" | tee -a "$OUT/qa-checks.txt"   # 2
adb shell input keyevent KEYCODE_MEDIA_PLAY; sleep 3
echo "media notification: $(adb shell dumpsys notification --noredact | grep -c "pkg=$PKG.*MediaStyle\|android.mediaSession")" | tee -a "$OUT/qa-checks.txt"
adb shell input keyevent KEYCODE_SLEEP; sleep 6
echo "screen off, setting off: $(session_state)" | tee -a "$OUT/qa-checks.txt"     # 2
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard; sleep 2
adb shell input keyevent KEYCODE_BACK; sleep 2; adb shell input keyevent KEYCODE_BACK; sleep 2
tap "Pengaturan"; sleep 2; scroll_to "Putar audio di latar" && tap "Putar audio di latar"; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1
tap "Tonton"; sleep 5; scroll_to "Episode 1" && tap "Episode 1"; sleep 20
adb shell input keyevent KEYCODE_SLEEP; sleep 6
echo "screen off, setting on: $(session_state)" | tee -a "$OUT/qa-checks.txt"      # 3
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard; sleep 2
echo "sessions for app: $(adb shell dumpsys media_session | grep -c "package=$PKG")" | tee -a "$OUT/qa-checks.txt"  # 1
shot qa9a-player-after-wake 1
adb shell input keyevent KEYCODE_BACK; sleep 2; alive
```

Run: `gh workflow run build.yml --repo SakamotoMrX/fuwanime --ref feat/player-latar -f qa=true`, then download `smoke-screenshots` and read `qa-checks.txt`.
Expected (RED): `session playing:` empty (there is no session yet).

- [ ] **Step 2: Implement the holder, service, manifest, container field, and PlayerRoute wiring**
- [ ] **Step 3: Run the gate, then QA**

Expected:
- ci-test.sh: `CI PASSED`.
- `qa-checks.txt`:
  - `session playing: 3`
  - `session after media pause: 2`
  - `media notification:` ≥ 1
  - `screen off, setting off: 2`
  - `screen off, setting on: 3`
  - `sessions for app: 1`
- The run is `QA run passed`, and qa9a shows the player.

- [ ] **Step 4: Commit**

```bash
git add -A app/src/main .github/scripts/qa.sh
git commit -m "feat(player): MediaSessionService with lock-screen/headset controls and optional background audio"
```

### Task 7: PiP polish (clamp, actions, early hide)

**Files:**
- Create: `app/src/main/res/drawable/ic_pip_replay10.xml`, `ic_pip_play.xml`, `ic_pip_pause.xml`, `ic_pip_forward10.xml` (24dp white vector paths from Material Symbols `replay_10`, `play_arrow`, `pause`, `forward_10`)
- Modify: `app/src/main/java/com/fuwanime/ui/player/PipController.kt`:
  - `setAutoEnter(enabled: Boolean, width: Int = 16, height: Int = 9, isPlaying: Boolean = false)` uses `clampPipAspect`
  - `buildParams()` adds `setActions(listOf(rewind, playPause, forward))` on API 26+
  - new `isEnteringPip: State<Boolean>`, set by `fun onPipUiState(transitioning: Boolean)`
- Modify: `app/src/main/java/com/fuwanime/MainActivity.kt` (`override fun onPictureInPictureUiStateChanged(state: PictureInPictureUiState)` on API 35+: `pip.onPipUiState(state.isTransitioningToPip)`)
- Modify: `app/src/main/java/com/fuwanime/ui/player/PlayerScreen.kt`:
  - pass `playback.isPlaying` into `setAutoEnter`
  - `VideoSurface` hides controls when `inPip || pip.isEnteringPip`

**Interfaces:**
- Consumes: `clampPipAspect` (Task 4); `PlaybackService` component (Task 6)

**Action rules:**
- Each `RemoteAction(Icon.createWithResource(activity, R.drawable.X), title, title, PendingIntent.getService(activity, requestCode, Intent(Intent.ACTION_MEDIA_BUTTON).setClass(activity, PlaybackService::class.java).putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode)), FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT))`.
- Request codes: 1 = rewind (`KEYCODE_MEDIA_REWIND`), 2 = play/pause (`KEYCODE_MEDIA_PLAY_PAUSE`), 3 = forward (`KEYCODE_MEDIA_FAST_FORWARD`).
- The play/pause icon and title follow `isPlaying`: "Jeda" with `ic_pip_pause` while playing, "Putar" with `ic_pip_play` otherwise.

- [ ] **Step 1: RED is the clamp tests from Task 4, already green.** This task's new behaviour is platform UI, verified in QA: add to scenario 9, before the final `BACK`:

```bash
adb shell input keyevent KEYCODE_HOME; sleep 4
echo "pip actions: $(adb shell dumpsys activity activities | grep -c 'Mundur 10 detik\|Maju 10 detik')" | tee -a "$OUT/qa-checks.txt"
shot qa9b-pip 0; adb shell am start -n "$PKG/com.fuwanime.MainActivity" >/dev/null; sleep 3
```

Expected before the implementation: `pip actions: 0`.

- [ ] **Step 2: Implement**
- [ ] **Step 3: Gate + QA**

Expected: `CI PASSED`; `pip actions:` ≥ 1, or, if dumpsys does not print action titles on API 34, qa9b shows three buttons in the PiP window. Record which one was used as a ledger ruling.

- [ ] **Step 4: Commit**

```bash
git commit -am "feat(player): PiP actions, clamped aspect ratio, early control hide"
```

### Task 8: Remembered aspect mode, pinch, large-screen fullscreen

**Files:**
- Modify: `app/src/main/java/com/fuwanime/ui/player/PlayerViewModel.kt` (`fun setVideoResize(mode: VideoResize)` → `settingsStore.setVideoResize`)
- Modify: `app/src/main/java/com/fuwanime/ui/player/PlayerScreen.kt`:
  - drop `var resizeMode`; derive `resizeMode` from `settings.videoResize` (FIT → `RESIZE_MODE_FIT`, ZOOM → `RESIZE_MODE_ZOOM`)
  - `PlayerSettingsSheet.onResize` → `vm.setVideoResize(...)`
  - `var manualFullscreen by rememberSaveable { mutableStateOf(false) }`
  - `val fullscreen = isFullscreen(landscape, manualFullscreen, inPip)`
  - `setFullscreen(enable)` also sets `manualFullscreen = enable`
  - in `VideoSurface`, a pinch `pointerInput` placed **before** the tap and drag layers on the same Box, plus a centred pill indicator

**Interfaces:**
- Consumes: `pinchResize`, `isFullscreen` (Task 4); `AppSettings.videoResize`, `SettingsStore.setVideoResize` (Task 5)
- Produces: `VideoSurface(..., resize: VideoResize, onResize: (VideoResize) -> Unit, ...)` (replaces the `resizeMode: Int` param)

**Pinch gesture:**

```kotlin
awaitEachGesture {
    awaitFirstDown(requireUnconsumed = false)
    var startDist = 0f
    do {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val pressed = event.changes.filter { it.pressed }
        if (pressed.size >= 2) {
            val d = (pressed[0].position - pressed[1].position).getDistance()
            if (startDist == 0f) startDist = d
            else pinchResize(resize, d / startDist)?.let { onResize(it); indicator = it; startDist = d }
            event.changes.forEach { it.consume() } // stops tap/double-tap/hold/drag for this gesture
        }
    } while (event.changes.any { it.pressed })
}
```

It is skipped (`return@pointerInput`) when `locked` or `inPip`. The indicator shows "Pas layar"/"Isi layar" in the `LevelIndicator` style, centred, and clears after 1 000 ms.

- [ ] **Step 1: RED via QA:** add to scenario 9 (before its final `BACK`):

```bash
tap "Pengaturan pemutar" || tap "Pengaturan"; sleep 1; tap "Isi layar"; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 2
scroll_to "Episode 1" && tap "Episode 1"; sleep 15
tap "Pengaturan pemutar" || tap "Pengaturan"; sleep 1; shot qa9c-resize-remembered 1   # "Isi layar" chip selected
adb shell input keyevent KEYCODE_BACK; sleep 1
```

Use the settings button's real content description from `Controls` (grep `onOpenSettings` in `PlayerScreen.kt`). Expected before the implementation: qa9c shows "Pas layar" selected.

- [ ] **Step 2: Implement**
- [ ] **Step 3: Gate + QA**

Expected: `CI PASSED`. qa9c shows the "Isi layar" chip selected. `QA run passed`. Scenarios 1–8 still pass, and qa4b shows the player.

- [ ] **Step 4: Commit**

```bash
git commit -am "feat(player): remembered aspect mode, pinch to switch, fullscreen on large screens"
```
