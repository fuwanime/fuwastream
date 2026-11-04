# Koleksi & Engagement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Favorit sort + "N baru" badge, multi-select, clearing watched downloads, shortcuts + "Lanjutkan menonton" widget, poster notifications, accurate watch-time stats with a shareable recap card.

**Architecture:** Every decision rule is a pure Kotlin function with a JVM unit test (`LibraryLogic.kt`, `LaunchTarget.kt`, `WatchStats.kt`, `EpochDay`). Android surfaces (Compose, Glance, ShortcutManager, notifications, FileProvider) stay thin and are verified by CI build + the QA emulator. Room goes v2 → v3 with one additive table.

**Tech Stack:** Kotlin 2.2.10, Compose BOM 2025.07, Room 2.7.2, DataStore, WorkManager 2.10, Coil 2.7, new: `androidx.glance:glance-appwidget:1.1.1`.

**Spec:** `docs/superpowers/specs/2026-11-04-koleksi-engagement-design.md`

## Global Constraints

- minSdk 24, targetSdk/compileSdk 36. No `java.time` (no desugaring): dates via `Calendar`/`TimeZone`.
- No local Android toolchain. Test gate: `bash /home/sakamoto/Data/Github/fuwanime-quick-wins/.superpowers/quick-wins/ci-test.sh` (push + `testDebugUnitTest` + `assembleRelease`). QA: `gh workflow run build.yml --repo SakamotoMrX/fuwanime --ref feat/koleksi-engagement -f qa=true`, artifact `smoke-screenshots`.
- Unit tests live flat in `app/src/test/java/com/fuwanime/`, `package com.fuwanime`, JUnit 4.
- Only existing theme tokens/palette: BG `#0B0A0F`, surface `#16141C`, outline `#2A2633`, primary `#D4BBFF`, pink `#FFB0C9`, text `#E8E0EE`, muted `#A39DAE`.
- UI copy in Indonesian exactly as the spec gives it ("Ditambahkan", "Terakhir ditonton", "A–Z", "N baru", "9+ baru", "N dipilih", "Hapus yang sudah ditonton", "Belum ada tontonan", "Jelajahi", "Belum ada statistik", "Bagikan rekap", "Gagal membuat gambar rekap", "N episode baru").
- No `fallbackToDestructiveMigration`. Commits carry no Claude co-author line.

## Review Focus

1. Device on API 24–29: SQLite has no `ON CONFLICT DO UPDATE` → `addWatchTime` must be insert-or-ignore + update in one transaction (Task 6).
2. Episode labels like `"12.5"`, `"OVA"`, `"1-2"`, blank → non-numeric ignored, fractional floored; no crash, no negative badge (Task 1 tests).
3. Shortcut/widget resume for an episode whose history was deleted → app opens on Beranda, no crash (Task 3 test `resumeWithoutHistoryFallsBackHome`).
4. Day boundary/time zone: watch time at 23:59:59 vs 00:00:00 local lands on different days; a non-UTC zone shifts correctly (Task 6 tests).
5. Selection mode: switching tab or back gesture clears the selection instead of deleting/leaving the screen (Task 2, verified in QA scenario 8).

---

### Task 1: Koleksi pure logic

**Files:**
- Create: `app/src/main/java/com/fuwanime/ui/library/LibraryLogic.kt`
- Test: `app/src/test/java/com/fuwanime/LibraryLogicTest.kt`

**Interfaces:**
- Produces:
  - `enum class FavoriteSort(val label: String) { ADDED("Ditambahkan"), LAST_WATCHED("Terakhir ditonton"), TITLE("A–Z") }`
  - `fun sortFavorites(favs: List<FavoriteEntity>, lastWatched: Map<String, Long>, sort: FavoriteSort): List<FavoriteEntity>`
  - `fun newEpisodeCount(latestKnown: Double?, watchedNumbers: List<String>): Int`
  - `fun newBadgeLabel(count: Int): String?` → null for 0, `"N baru"`, `"9+ baru"` above 9
  - `fun watchedDownloads(downloads: List<DownloadItem>, history: Map<String, WatchHistoryEntity>): List<DownloadItem>` (history keyed by episodeId)

- [ ] **Step 1: Write failing tests** `LibraryLogicTest`:
  - `sortAdded_newestFirst`, `sortLastWatched_unwatchedLastByAdded`, `sortTitle_caseInsensitive` (`"bleach"` before `"Naruto"`)
  - `newCount_baseline12watched10_is2`, `newCount_noBaseline_is0`, `newCount_noHistory_is0`, `newCount_nonNumericIgnored` (`listOf("OVA","1-2","","9")` with baseline 11.0 → 2), `newCount_fractionalFloored` (baseline 12.5, watched `"12"` → 0), `newCount_watchedBeyondBaseline_is0`
  - `badge_zeroNull`, `badge_nine` = `"9 baru"`, `badge_ten` = `"9+ baru"`
  - `watchedDownloads_onlyDoneAndFinished` (DONE+finished kept; DONE+unfinished, RUNNING+finished, DONE+no history dropped)
- [ ] **Step 2: Run** `ci-test.sh` → Expected: FAIL, unresolved `sortFavorites` etc.
- [ ] **Step 3: Implement** the five symbols. `isFinished` from `WatchHistoryEntity`; DONE = `DownloadState.DONE`.
- [ ] **Step 4: Run** `ci-test.sh` → Expected: PASS.
- [ ] **Step 5: Commit** `feat(koleksi): logika urut favorit, badge episode baru, unduhan tertonton`

### Task 2: Koleksi UI (sort chips, badge, multi-select, clear watched)

**Files:**
- Modify: `app/src/main/java/com/fuwanime/data/local/FuwaDatabase.kt` (WatchHistoryDao)
- Modify: `app/src/main/java/com/fuwanime/data/local/SettingsStore.kt`
- Modify: `app/src/main/java/com/fuwanime/data/LibraryRepository.kt`
- Modify: `app/src/main/java/com/fuwanime/ui/library/LibraryScreen.kt`
- Modify: `app/src/main/java/com/fuwanime/ui/components/Components.kt` (`PosterCard`)

**Interfaces:**
- Consumes: Task 1 symbols.
- Produces:
  - `WatchHistoryDao.observeAll(): Flow<List<WatchHistoryEntity>>` (`SELECT * FROM watch_history`)
  - `LibraryRepository.allHistory: Flow<List<WatchHistoryEntity>>`
  - `SettingsStore.favoriteSort: Flow<FavoriteSort>`, `suspend fun setFavoriteSort(sort: FavoriteSort)` (key `favorite_sort`, stores `name`, unknown → `ADDED`)
  - `SettingsStore.notifyBaselines: Flow<Map<String, Double>>` — every `notify_baseline_<animeId>` key, prefix stripped
  - `PosterCard(..., badge: String? = null)` — pill top-right, `primary` bg, `labelSmall` ExtraBold, `onPrimary` text; wins over `isNew`
  - `LibraryViewModel`: `favoriteSort`, `favoriteCards: StateFlow<List<Pair<FavoriteEntity, String?>>?>` (sorted + badge label), `selection: StateFlow<Set<String>>`, `toggleSelect(animeId)`, `clearSelection()`, `deleteSelected(page: Int)`, `watched: StateFlow<List<DownloadItem>>`, `deleteWatched()`
  - `LibraryViewModel` takes `SettingsStore` as a third constructor param.

- [ ] **Step 1: Data layer** — add the DAO query, repository flow and SettingsStore members above.
- [ ] **Step 2: ViewModel** — combine `favorites`, `allHistory` (→ `lastWatched` max `updatedAt` per animeId, watched numbers per animeId), `notifyBaselines`, `favoriteSort` with Task 1 functions. `deleteSelected(0)` → `removeFromHistory` each; `deleteSelected(1)` → `setFavorite(anime, false)` each (via `FavoriteEntity.toAnime()`); then `clearSelection()`.
- [ ] **Step 3: Screen**
  - Favorit: row of `TagChip`s (one per `FavoriteSort`, `selected` = current) above the grid; `PosterCard(badge = label)`.
  - Long-press (`combinedClickable`) on `HistoryRow` and favorite card enters selection; tap toggles while selection is non-empty; selected items get a 2 dp `primary` border + check icon.
  - Header while selecting: close icon, "N dipilih", `Delete` icon. ≥ 2 items → `AlertDialog` "Hapus N item?"; 1 item deletes directly.
  - `BackHandler(enabled = selection.isNotEmpty()) { clearSelection() }`; `LaunchedEffect(pager.currentPage) { clearSelection() }`.
  - Unduhan: when `watched` non-empty, a `TextButton` above the list "Hapus yang sudah ditonton (N • ${formatBytes(sum totalBytes)})" → confirm dialog → `deleteWatched()`.
- [ ] **Step 4: Run** `ci-test.sh` → Expected: PASS (compiles, all tests green).
- [ ] **Step 5: Commit** `feat(koleksi): urutan favorit, badge N baru, pilih banyak, hapus unduhan tertonton`

### Task 3: Launch targets + shortcuts

**Files:**
- Create: `app/src/main/java/com/fuwanime/ui/navigation/LaunchTarget.kt`
- Create: `app/src/main/java/com/fuwanime/ui/navigation/Shortcuts.kt`
- Create: `app/src/main/res/xml/shortcuts.xml`
- Modify: `app/src/main/AndroidManifest.xml` (`<meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts"/>` in the launcher activity)
- Modify: `app/src/main/java/com/fuwanime/MainActivity.kt`, `app/src/main/java/com/fuwanime/ui/navigation/FuwaNavHost.kt`
- Test: `app/src/test/java/com/fuwanime/LaunchTargetTest.kt`

**Interfaces:**
- Produces:
  - `sealed interface LaunchTarget { data class Anime(val id: String); data object Search; data object Schedule; data class Resume(val episodeId: String) }`
  - `fun parseLaunchTarget(animeId: String?, tab: String?, resumeEpisodeId: String?): LaunchTarget?` — priority Resume > Anime > tab; tab `"explore_search"` → Search, `"schedule"` → Schedule, else null; blanks = null
  - `fun resumeRoute(entry: WatchHistoryEntity?): String?` — null when entry null, else `Routes.player(...)` exactly as Koleksi `onResume` builds it (move that builder here as `fun playerRouteFor(e: WatchHistoryEntity, origin: String): String` and reuse it in Koleksi)
  - `MainActivity.EXTRA_OPEN_TAB = "open_tab"`, `MainActivity.EXTRA_RESUME_EPISODE_ID = "resume_episode_id"`
  - `object Shortcuts { fun pushContinue(context: Context, entry: WatchHistoryEntity) }` — dynamic shortcut id `"continue"`, short label = title (≤ 25 chars), long label "Lanjutkan ${title}", icon `R.mipmap.ic_launcher`, intent `ACTION_VIEW` to `MainActivity` with `EXTRA_RESUME_EPISODE_ID`
  - `FuwaNavHost(initialTarget: LaunchTarget?, onInitialTargetConsumed: () -> Unit)` replaces `initialAnimeId`/`onInitialAnimeConsumed`

- [ ] **Step 1: Failing tests** `LaunchTargetTest`: `resumeWins`, `animeWhenNoResume`, `tabSearch`, `tabSchedule`, `unknownTabNull`, `blanksNull`, `resumeWithoutHistoryFallsBackHome` (`resumeRoute(null) == null`).
- [ ] **Step 2: Run** `ci-test.sh` → FAIL (unresolved).
- [ ] **Step 3: Implement** parser + route helpers; `MainActivity` keeps `pendingTarget = mutableStateOf<LaunchTarget?>` set in `onCreate`/`onNewIntent` via `parseLaunchTarget(intent.getStringExtra(...)×3)`; `FuwaNavHost` handles: Anime → detail (as today), Search → `openExplore(null, true)`, Schedule → `navigateTab(Routes.SCHEDULE)`, Resume → `library.history(episodeId)` (existing `WatchHistoryDao.get`) → `resumeRoute` → navigate or nothing. `shortcuts.xml`: `cari` ("Cari", icon `ic_launcher`, extra `open_tab=explore_search`), `jadwal` ("Jadwal", extra `open_tab=schedule`), both `android:targetClass="com.fuwanime.MainActivity"`.
- [ ] **Step 4: Run** `ci-test.sh` → PASS.
- [ ] **Step 5: Commit** `feat: shortcut Cari/Jadwal/Lanjutkan dan routing intent peluncuran`

### Task 4: "Lanjutkan menonton" widget + refresh hook

**Files:**
- Modify: `gradle/libs.versions.toml` (`glance = "1.1.1"`, `androidx-glance-appwidget`), `app/build.gradle.kts`
- Create: `app/src/main/java/com/fuwanime/util/Bitmaps.kt`
- Create: `app/src/main/java/com/fuwanime/widget/ContinueWidget.kt` (`ContinueWidget : GlanceAppWidget`, `ContinueWidgetReceiver : GlanceAppWidgetReceiver`)
- Create: `app/src/main/res/xml/continue_widget_info.xml`, string `widget_continue_desc` = "Lanjutkan anime yang terakhir ditonton"
- Create: `app/src/main/java/com/fuwanime/ui/navigation/HomeSurfaces.kt`
- Modify: `AndroidManifest.xml` (receiver, `APPWIDGET_UPDATE`, meta-data), `PlayerViewModel.kt`, `LibraryScreen.kt` (VM deletes), `MainActivity.kt` (on start)

**Interfaces:**
- Consumes: `Shortcuts.pushContinue`, `MainActivity.EXTRA_RESUME_EPISODE_ID`, `LibraryRepository.continueWatching(limit)`.
- Produces:
  - `suspend fun loadBitmap(context: Context, url: String, maxPx: Int): Bitmap?` — Coil `context.imageLoader.execute(ImageRequest…size(maxPx).allowHardware(false))`, `withTimeoutOrNull(10_000)`, any failure → null
  - `object HomeSurfaces { suspend fun refresh(context: Context) }` — reads `continueWatching(3).first()`, pushes the dynamic shortcut for the first entry (or `ShortcutManagerCompat.removeDynamicShortcuts(listOf("continue"))` when empty), then `ContinueWidget().updateAll(context)`
  - `PlayerViewModel.onPlayerExit(positionMs, durationMs)` = `saveProgress` then `appScope.launch { HomeSurfaces.refresh(app) }`; `PlayerScreen` ON_STOP and STATE_ENDED call it instead of `saveProgress` (5 s tick keeps `saveProgress`)

- [ ] **Step 1: Dependency + receiver/provider xml** — `minWidth 110dp`, `minHeight 110dp`, `targetCellWidth 2`, `targetCellHeight 2`, `resizeMode horizontal|vertical`, `updatePeriodMillis 0`, `widgetCategory home_screen`, `description @string/widget_continue_desc`.
- [ ] **Step 2: Widget UI** — `SizeMode.Responsive(setOf(DpSize(110.dp,110.dp), DpSize(250.dp,110.dp), DpSize(250.dp,250.dp)))`: small → 1 item (poster fill + title + "Ep N" + progress), wide → up to 2 in a row, tall → up to 3 rows. Item click `actionStartActivity(Intent(MainActivity, EXTRA_RESUME_EPISODE_ID))`; header "Lanjutkan" click opens app. Empty → "Belum ada tontonan" + "Jelajahi" (opens with `EXTRA_OPEN_TAB=explore_search`). Colors from Global Constraints, corner 24 dp, `LinearProgressIndicator(color = #D4BBFF, background = #2A2633)`. Posters via `loadBitmap(maxPx = 160)` in `provideGlance` before `provideContent`; null → `#16141C` box.
- [ ] **Step 3: Refresh hook** — `HomeSurfaces`, `onPlayerExit`, `LibraryViewModel.removeHistory/deleteSelected/clearHistory` call `HomeSurfaces.refresh` after the delete, `MainActivity.onStart` launches it in `lifecycleScope`.
- [ ] **Step 4: Run** `ci-test.sh` → PASS (release build incl. R8 keeps Glance receiver).
- [ ] **Step 5: Commit** `feat: widget Lanjutkan menonton (Glance) + refresh saat keluar player`

### Task 5: Poster notifications + grouping

**Files:**
- Modify: `app/src/main/java/com/fuwanime/data/notify/EpisodeNotifyWorker.kt`
- Test: `app/src/test/java/com/fuwanime/NotifyGroupTest.kt`

**Interfaces:**
- Consumes: `loadBitmap`.
- Produces: `fun groupSummaryTitle(count: Int): String` = `"$count episode baru"`; `suspend fun EpisodeNotifier.notifyNewEpisode(favorite, episode)`; `fun EpisodeNotifier.postSummary(titles: List<String>)`; constants `GROUP_KEY = "new_episodes"`, `SUMMARY_ID = 0x5EED`.

- [ ] **Step 1: Failing test** `summaryTitle_three` → `"3 episode baru"`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** — poster `loadBitmap(favorite.poster, 256)` → `setLargeIcon`; cover `loadBitmap(favorite.cover.ifBlank { favorite.poster }, 1024)` → `BigPictureStyle().bigPicture(cover).bigLargeIcon(null as Bitmap?)`; both null → current text notification. All get `setGroup(GROUP_KEY)`. Worker collects titles of notified favorites; `≥ 2` → `postSummary` (`InboxStyle` lines = titles, `setGroupSummary(true)`, title `groupSummaryTitle(n)`). Permission check unchanged and applied to the summary too.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(notif): poster dan pengelompokan episode baru`

### Task 6: Watch-time tracking (Room v3)

**Files:**
- Create: `app/src/main/java/com/fuwanime/util/EpochDay.kt`
- Modify: `FuwaDatabase.kt` (entity, DAO, version 3, `MIGRATION_2_3`), `LibraryRepository.kt`, `PlayerViewModel.kt`, `PlayerScreen.kt` (5 s loop)
- Test: `app/src/test/java/com/fuwanime/EpochDayTest.kt`

**Interfaces:**
- Produces:
  - `fun epochDay(millis: Long, tz: TimeZone = TimeZone.getDefault()): Int` = `floorDiv(millis + tz.getOffset(millis), 86_400_000)`
  - `@Entity(tableName = "watch_daily") data class WatchDailyEntity(@PrimaryKey val day: Int, val watchedMs: Long)`
  - `WatchDailyDao`: `@Query("INSERT OR IGNORE INTO watch_daily(day, watchedMs) VALUES(:day, 0)") insertZero(day)`, `@Query("UPDATE watch_daily SET watchedMs = watchedMs + :ms WHERE day = :day") add(day, ms)`, `@Transaction suspend fun addWatchTime(day: Int, ms: Long)` (both), `observeAll(): Flow<List<WatchDailyEntity>>`
  - `MIGRATION_2_3`: `CREATE TABLE IF NOT EXISTS watch_daily (day INTEGER NOT NULL, watchedMs INTEGER NOT NULL, PRIMARY KEY(day))`
  - `LibraryRepository.addWatchTime(ms: Long)` (day = `epochDay(System.currentTimeMillis())`), `LibraryRepository.watchDaily: Flow<List<WatchDailyEntity>>`
  - `PlayerViewModel.addWatchTime(ms: Long)`

- [ ] **Step 1: Failing tests** `EpochDayTest`: `utcEpochZero` (0 → 0), `lastSecondVsMidnight` (Asia/Jakarta: 2026-11-04 23:59:59 vs 2026-11-05 00:00:00 differ by 1), `jakartaShiftsUtc` (2026-11-04 20:00 UTC → Jakarta day = UTC day + 1), `negativeFloors` (-1 ms UTC → -1).
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** symbols; register migration next to `MIGRATION_1_2`; loop: `if (player.isPlaying) { vm.saveProgress(...); vm.addWatchTime(5_000) }`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(stats): catat waktu tonton harian (Room v3)`

### Task 7: Stats aggregation

**Files:**
- Create: `app/src/main/java/com/fuwanime/ui/stats/WatchStats.kt`
- Test: `app/src/test/java/com/fuwanime/WatchStatsTest.kt`

**Interfaces:**
- Consumes: `WatchDailyEntity`, `epochDay`.
- Produces:
  - `data class MonthBar(val label: String, val minutes: Int)` — label `"Jan" … "Des"` (Indonesian: Jan, Feb, Mar, Apr, Mei, Jun, Jul, Agu, Sep, Okt, Nov, Des)
  - `data class WatchStats(val totalWatchedMs: Long, val isEstimated: Boolean, val episodesFinished: Int, val animeWatched: Int, val topGenres: List<String>, val monthly: List<MonthBar>, val activeWeeks: Int)`
  - `fun computeStats(history: List<WatchHistoryEntity>, daily: List<WatchDailyEntity>, favorites: List<FavoriteEntity>, todayEpochDay: Int): WatchStats`
  - `fun weekIndex(epochDay: Int): Int` = `floorDiv(epochDay + 3, 7)` (Monday start)

- [ ] **Step 1: Failing tests** `WatchStatsTest`:
  - `totalAddsEstimateForOldHistory` (daily starts day D; history entry updated before D with position 600 000 → total = daily sum + 600 000, `isEstimated` true)
  - `noEstimateWhenAllHistoryAfterDaily` → `isEstimated` false
  - `emptyDailyEstimatesAll`
  - `monthlySixMonthsWithZeros` (today 2026-11-04; data only Sep → 6 bars Jun..Nov, Sep non-zero, others 0)
  - `activeWeeks_currentEmptyDoesNotBreak` (last week + week before active, this week empty → 2)
  - `activeWeeks_gapBreaks` (this week + 2 weeks ago → 1)
  - `weekIndex_mondayStarts` (1970-01-05 epochDay 4 → 1; 1970-01-04 epochDay 3 → 0)
  - `topGenresCaseInsensitiveTop3` (`"Action, Comedy"`, `"action"`, `"Drama"`, `"comedy"` → `[Action, Comedy, Drama]`, first-seen casing)
  - `counts` (episodesFinished counts `isFinished`, animeWatched = distinct animeId)
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement.** Month of an epoch day via `Calendar(UTC)` at `day * 86_400_000L`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(stats): agregasi statistik tonton`

### Task 8: Stats screen + share card

**Files:**
- Create: `app/src/main/java/com/fuwanime/ui/stats/StatsScreen.kt` (`StatsRoute`, `StatsViewModel`, `RecapCard`)
- Create: `app/src/main/res/xml/share_paths.xml` (`<cache-path name="share" path="share/"/>`)
- Modify: `AndroidManifest.xml` (`androidx.core.content.FileProvider`, authority `${applicationId}.share`, `exported=false`, `grantUriPermissions=true`)
- Modify: `FuwaNavHost.kt` (route `stats`, not a tab, opaque page like Settings), `LibraryScreen.kt` (header `Icons.Rounded.Insights` "Statistik" → `onStats`)

**Interfaces:**
- Consumes: `computeStats`, `LibraryRepository.watchDaily`, `allHistory`, `favoriteList`, `epochDay`.
- Produces: `StatsRoute(onBack: () -> Unit)`; `LibraryRoute(..., onStats: () -> Unit)`.

- [ ] **Step 1: Screen** — top bar "Statistik" + back; 2×2 stat cards (Jam tonton = `formatDuration`-style "N jam M mnt" with "± perkiraan untuk data lama" caption when `isEstimated`, Episode selesai, Anime, Minggu aktif); Canvas bar chart (bars `primary`, track `outline`, month labels `labelSmall` muted, bar height ∝ minutes, max bar = tallest); `TagChip`s for top genres; empty history → `MessageState(Icons.Rounded.Insights, "Belum ada statistik", message = "Tonton satu episode dulu, nanti angkanya muncul di sini.")`.
- [ ] **Step 2: Recap card + share** — `RecapCard(stats, monthYear)` 4:5, gradient `#0B0A0F`→`#1A1721` with lavender (`#D4BBFF` 20%) and pink (`#FFB0C9` 13%) radial glows, wordmark "fuwa" primary + "stream" text color (Plus Jakarta Sans ExtraBold), big hours number, episodes/anime/top genre lines, "November 2026"-style month-year. Shown as preview; `rememberGraphicsLayer()` + `drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }`. "Bagikan rekap" → `layer.toImageBitmap().asAndroidBitmap()` → `cacheDir/share/rekap.png` (IO dispatcher) → `FileProvider.getUriForFile` → `Intent.createChooser(ACTION_SEND, type image/png, EXTRA_STREAM, FLAG_GRANT_READ_URI_PERMISSION)`. Any exception → snackbar "Gagal membuat gambar rekap".
- [ ] **Step 3: Run** `ci-test.sh` → PASS.
- [ ] **Step 4: Commit** `feat(stats): layar statistik dan kartu rekap yang bisa dibagikan`

### Task 9: QA scenarios + upgrade check

**Files:**
- Modify: `.github/scripts/qa.sh`, `.github/workflows/build.yml` (only if the QA job needs `GH_TOKEN` for `gh release download`)

- [ ] **Step 1: Scenario 8 (Koleksi)** after 5b (two favourites exist): Koleksi → "Favorit*" → tap "Terakhir ditonton" → shot `qa8a-fav-sort`; long-press first poster → shot `qa8b-select`; key BACK → shot `qa8c-select-cleared` (still on Koleksi); tap the Statistik icon (content-desc "Statistik") → shot `qa8d-stats`; scroll to "Bagikan rekap" → shot `qa8e-recap`.
- [ ] **Step 2: Shortcuts + widget** — after scenario 4 (an episode was played): `adb shell dumpsys shortcut | grep -c 'continue'` ≥ 1 written to `qa-checks.txt`; `adb shell cmd appwidget` is not available for binding, so record `dumpsys appwidget | grep -c ContinueWidgetReceiver` (provider registered) to `qa-checks.txt`.
- [ ] **Step 3: Upgrade v2 → v3** — new scenario 0 before install: `gh release download --repo SakamotoMrX/fuwanime --pattern '*.apk' -D old/` (latest release), install it, launch, wait 10 s, `adb install -r` the new APK, launch, Koleksi → shot `qa0-upgraded`, `alive` (no crash). If the release download fails, log `upgrade: skipped` and continue.
- [ ] **Step 4: Run QA** `gh workflow run build.yml --repo SakamotoMrX/fuwanime --ref feat/koleksi-engagement -f qa=true` → Expected: "QA run passed", screenshots qa8a–qa8e and qa0 present, `qa-checks.txt` counts ≥ 1. Inspect each screenshot.
- [ ] **Step 5: Commit** `test(qa): skenario Koleksi, statistik, shortcut, widget, upgrade Room`
