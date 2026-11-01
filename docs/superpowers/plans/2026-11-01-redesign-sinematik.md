# Redesign Sinematik Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle Beranda, Detail, Jelajah and Koleksi to the cinematic mockup without changing navigation, data or the shared-element motion that was signed off.

**Architecture:**
- Theme tokens and the font change once, in `ui/theme/Theme.kt`.
- Shared look lives in `ui/components/Components.kt`: `PosterCard`, `ContinueWatchingCard`, `SectionHeader`, and a new `TagChip`.
- Each screen file is then restyled in place.
- Two pure helpers in `util/Format.kt` carry the only new logic and are unit-tested.

**Tech Stack:** Kotlin 2.2, Jetpack Compose (BOM 2025.07.00), Material 3, Coil 2. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-11-01-redesign-sinematik-design.md`. Mockup: https://claude.ai/artifact/FviJ1TF18guNx5WEmKc2un

## Global Constraints

- **No local Android toolchain.** Every "run" step is the CI gate, run from the repo root.
  - Gate: `bash /home/sakamoto/Github/fuwanime-quick-wins/.superpowers/quick-wins/ci-test.sh`. It pushes the branch and runs `testDebugUnitTest` + `assembleRelease`. PASS means the last line is `CI PASSED (run …)`.
  - The gate takes about 4 minutes, so batch the "run" steps of a task into one gate run.
  - On failure, read the errors: `gh run view <id> --repo SakamotoMrX/fuwanime --log-failed | grep -E "e: |FAILED"`.
- **Branch:** `feat/redesign-sinematik`. It already holds the spec commit.
- **Commits:** carry no Co-Authored-By or Claude lines.
- **Dark scheme values:**

  | Role | Value |
  |---|---|
  | `background`, `surface` | `#0B0A0F` |
  | `surfaceContainerLow` | `#16141C` |
  | `surfaceContainer` | `#1A1721` |
  | `surfaceContainerHigh` | `#1F1C27` |
  | `secondaryContainer` | `#2A2238` |
  | `outlineVariant` | `#2A2633` |
  | `onSurfaceVariant` | `#A39DAE` |
  | `primary` | `#D4BBFF` (unchanged) |

- **Fixed colors**, allowed only on top of images or in the nav, where theme roles do not apply:

  | Use | Value |
  |---|---|
  | Image scrims | `#0B0A0F` at alpha |
  | Nav inactive | `#8A8494` |
  | Nav top line | `#1F1C27` |
  | Live dot | `#FF7A93` |
  | Rank stroke | `#4A4455` |

  Everything else uses `MaterialTheme.colorScheme` roles, so light and dynamic themes keep working.
- **Font:** Plus Jakarta Sans 400/600/700/800, bundled as static TTFs in `app/src/main/res/font/`. The OFL text goes in `app/src/main/assets/licenses/PlusJakartaSans-OFL.txt`.
- **Exact UI copy** (Indonesian, exactly as written):
  - `Tonton`
  - `Lanjut EP N`
  - `Tonton EP N`
  - `Memuat episode…`
  - `Sisa N menit`
  - `Selesai ditonton`
  - `Selengkapnya` / `Tutup`
  - `Season N · Ini`
  - `Episode · N`
  - `Semua anime`
  - `Judul, genre, atau studio`
  - `Riwayat · N`, `Favorit · N`, `Unduhan · N`
  - `HARI INI`, `KEMARIN`
  - `#N MUSIM INI`
  - `BARU`
  - `Tayang`
  - `fuwa` + `stream`
- **Shared-element modifiers stay on the same images:** `sharedKey`, `aboveSharedElements` and `revealWhenLanded` (in `ui/motion/Motion.kt`). Only the hero changes keys (Task 4).

## Review Focus

1. **Hero → Detail → Player → back → back.** The hero backdrop must fly into the Detail backdrop and back. The Detail backdrop must still fly into the player and back (nested keys, Task 5). This is covered by the QA run in Task 8 (`qa4*` shots).
2. **History rows with `durationMs == 0`** (old rows, or a stream that never reported a duration). The card and row must fall back to the episode title or `Selesai ditonton`, never show `Sisa 0 menit`. Pinned by `remainingLabel` tests in Task 2.
3. **Day boundary in the device time zone.** A row watched at 23:59 is in `KEMARIN` one minute after midnight. Last year's dates include the year. Pinned by `historyDayLabel` tests in Task 2.
4. **Long titles** (for example "Boruto: Naruto Next Generations", or Japanese titles of 60+ characters). The hero clamps to 2 lines, cards to 2 lines, history rows to 1 line, all with ellipsis. Checked in the Task 8 screenshots.
5. **Light theme / dynamic color.** Restyled components must not hardcode dark hexes outside the allowed list. Checked by grep in Task 8.

---

### Task 1: Theme tokens, font, nav bar

**Files:**
- Create: `app/src/main/res/font/plus_jakarta_sans_regular.ttf`, `plus_jakarta_sans_semibold.ttf`, `plus_jakarta_sans_bold.ttf`, `plus_jakarta_sans_extrabold.ttf`
- Create: `app/src/main/assets/licenses/PlusJakartaSans-OFL.txt`
- Modify: `app/src/main/java/com/fuwanime/ui/theme/Theme.kt` (`DarkScheme` 25-61, `AppTypography` 108-120)
- Modify: `app/src/main/java/com/fuwanime/ui/navigation/FuwaNavHost.kt:186-208` (bottom bar)

**Interfaces:**
- Produces: `val PlusJakarta: FontFamily` (public, in `Theme.kt`). `AppTypography` uses it for every style.

- [ ] **Step 1: Fetch the fonts.** Source: `https://raw.githubusercontent.com/tokotype/PlusJakartaSans/master/fonts/ttf/PlusJakartaSans-{Regular,SemiBold,Bold,ExtraBold}.ttf`. Fetch `OFL.txt` from the same repo root. Save them under the snake_case names above. Then run `file app/src/main/res/font/*.ttf`; each must report "TrueType Font data".
- [ ] **Step 2: Update `DarkScheme`** with the Global Constraints values. Pass `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh` and `outlineVariant` explicitly to `darkColorScheme(...)`. `toAmoled()` stays as is.
- [ ] **Step 3: Update typography.** Define `PlusJakarta` with `Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal)`, plus SemiBold, Bold and ExtraBold. Set `fontFamily = PlusJakarta` on every `AppTypography` style and keep the current weights. Use letter-spacing `(-0.02).em` for `headlineSmall` and `headlineMedium`, and `(-0.03).em` for `displaySmall`.
- [ ] **Step 4: Restyle the nav bar.**
  - `NavigationBar`: `containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.94f)`, with a 1 dp `#1F1C27` top line drawn via `Modifier.drawBehind`.
  - `NavigationBarItemDefaults.colors(...)`:
    - `selectedIconColor = primary`, `selectedTextColor = onSurface`, `indicatorColor = secondaryContainer`
    - `unselectedIconColor` and `unselectedTextColor` = `Color(0xFF8A8494)`
  - Selected label: `FontWeight.Bold`.
- [ ] **Step 5: Run the gate.** Expected: `CI PASSED`.
- [ ] **Step 6: Commit** with message `feat(theme): cinematic dark tokens, Plus Jakarta Sans, nav bar`.

### Task 2: `remainingLabel` and `historyDayLabel`

**Files:**
- Modify: `app/src/main/java/com/fuwanime/util/Format.kt`
- Test: `app/src/test/java/com/fuwanime/FormatTest.kt`

**Interfaces:**
- Produces:
  - `fun remainingLabel(positionMs: Long, durationMs: Long): String?`
  - `fun historyDayLabel(timestampMs: Long, nowMs: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): String`

- [ ] **Step 1: Write failing tests** in `FormatTest`:

```kotlin
@Test fun remainingLabel_roundsUpToWholeMinutes() {
    assertEquals("Sisa 14 menit", remainingLabel(600_000, 1_440_000))
    assertEquals("Sisa 1 menit", remainingLabel(1_439_000, 1_440_000))
    assertEquals("Sisa 1 menit", remainingLabel(-5, 60_000))
}
@Test fun remainingLabel_nullWhenUnknownOrDone() {
    assertNull(remainingLabel(0, 0))
    assertNull(remainingLabel(5_000, -1))
    assertNull(remainingLabel(1_440_000, 1_440_000))
    assertNull(remainingLabel(1_500_000, 1_440_000))
}
@Test fun historyDayLabel_groupsByCalendarDayInZone() {
    val jkt = TimeZone.getTimeZone("Asia/Jakarta")
    // 2026-11-01 00:01 WIB
    val now = 1_793_466_060_000L
    assertEquals("HARI INI", historyDayLabel(now - 30_000, now, jkt))
    assertEquals("KEMARIN", historyDayLabel(now - 120_000, now, jkt))   // 2026-10-31 23:59
    assertEquals("30 OKT", historyDayLabel(now - 30 * 3_600_000L, now, jkt))
    assertEquals("28 DES 2025", historyDayLabel(1_766_880_000_000L, now, jkt))
}
```

  Before relying on the epoch constants, confirm them with `date -d @1793466060`; it should print 2026-10-31 17:01 UTC, which is 2026-11-01 00:01 WIB. Fix the constants if they are off. The asserted labels are the spec.
- [ ] **Step 2: Implement `remainingLabel`** next to `formatDuration`. It returns `null` when `durationMs <= 0` or the remaining time `<= 0`. Otherwise it returns `"Sisa ${ceil(remaining / 60_000)} menit"`. Clamp a negative position to 0.
- [ ] **Step 3: Implement `historyDayLabel`.**
  - Compare calendar dates with `Calendar.getInstance(zone)`.
  - Same date → `HARI INI`. One day before → `KEMARIN`.
  - Otherwise use `"d MMM"`, or `"d MMM yyyy"` when the year differs, and uppercase the result.
  - For the month, use a fixed list `JAN FEB MAR APR MEI JUN JUL AGU SEP OKT NOV DES`, not `SimpleDateFormat` locale names, which vary by JVM.
- [ ] **Step 4: Run the gate.** Expected: `CI PASSED`, with `FormatTest` green.
- [ ] **Step 5: Commit** with message `feat(format): remainingLabel and historyDayLabel`.

### Task 3: Shared components

**Files:**
- Modify: `app/src/main/java/com/fuwanime/ui/components/Components.kt`: `LiveDot` 150, `PosterCard` 156-243, `SectionHeader` 255-281, `PosterRow` 282-316, `ContinueWatchingCard` 317-378

**Interfaces:**
- Consumes: `remainingLabel` (Task 2).
- Produces:
  - `PosterCard(anime, onClick, modifier, width, caption, sharedOrigin, isNew: Boolean = false)`. The existing parameters are unchanged; `isNew` is added last.
  - `PosterRow(..., isNew: (Anime) -> Boolean = { false })`, forwarded to each card.
  - `@Composable fun TagChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false)`

- [ ] **Step 1: `PosterCard`.**
  - Delete the bottom-scrim `Box` and the on-image `type · year` `Text`.
  - The Tayang badge becomes a pill: `Color(0xFF0B0A0F).copy(alpha = 0.78f)` background, `#FF7A93` 6 dp dot, `Tayang` in labelSmall Bold. It keeps `aboveSharedElements().revealWhenLanded(poster)`.
  - Add `isNew`: a `BARU` pill at top-left (`primary` background, `onPrimary` text, labelSmall ExtraBold, 0.06 em). When both badges apply, BARU wins.
  - Under the image: title `labelLarge` SemiBold, 2 lines. Then the caption, or when the caption is null, `listOf(type, displayYear).filter { it.isNotBlank() }.joinToString(" · ")`, in `onSurfaceVariant`, 1 line.
- [ ] **Step 2: `LiveDot`** color becomes `Color(0xFFFF7A93)`.
- [ ] **Step 3: `ContinueWatchingCard`.**
  - Delete the full dark overlay and the play-circle `Surface`.
  - Add a bottom gradient (transparent → `#0B0A0F` alpha 0.75) with `EP N` in labelMedium Bold at bottom-left.
  - Keep the 3 dp progress bar. All overlays keep `aboveSharedElements().revealWhenLanded(thumb)`.
  - Below the image: anime title (titleSmall, 1 line). Then `remainingLabel(entry.positionMs, entry.durationMs)`; if that is null, `"Selesai ditonton"` when `entry.isFinished`, else the episode title as today.
- [ ] **Step 4: `SectionHeader`.** Title `titleMedium` Bold. "Lihat semua" becomes a `TextButton` with labelLarge `onSurfaceVariant` text and no arrow icon.
- [ ] **Step 5: `TagChip`.**
  - Unselected: `Surface` with `RoundedCornerShape(50)`, `surfaceContainer` background, 1 dp `outlineVariant` border, labelLarge SemiBold `onSurface`.
  - `selected = true`: `primary` background, `onPrimary` text, no border.
  - Min height 36 dp, horizontal padding 14 dp, clickable with `Role.Button`.
- [ ] **Step 6: Run the gate.** Expected: `CI PASSED`.
- [ ] **Step 7: Commit** with message `feat(ui): cinematic PosterCard, ContinueWatchingCard, TagChip`.

### Task 4: Beranda

**Files:**
- Modify: `app/src/main/java/com/fuwanime/ui/home/HomeScreen.kt`: hero 417-525, top bar 377-415, sections 183-254, `UpdateBanner` 269-304
- Modify: `app/src/main/java/com/fuwanime/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/fuwanime/ui/navigation/FuwaNavHost.kt`: pass the new callbacks to `HomeScreen`

**Interfaces:**
- Consumes: `PosterCard`, `PosterRow(isNew)`, `TagChip`, `SectionHeader` (Task 3); `sharedBackdropKey` (Motion.kt); `LibraryRepository.favoriteList` and `setFavorite`.
- Produces:
  - `HomeViewModel.favoriteIds: StateFlow<Set<String>>`
  - `HomeViewModel.toggleFavorite(anime: Anime)`
  - The hero image uses the shared key `sharedBackdropKey(anime.id, "home-hero")`. Task 5 links the Detail side to it.

- [ ] **Step 1: ViewModel.**
  - `favoriteIds` = `library.favoriteList.map { list -> list.map { it.animeId }.toSet() }`, then `stateIn(viewModelScope, WhileSubscribed(5_000), emptySet())`. Check the id field name on `FavoriteEntity`.
  - `toggleFavorite(anime)` launches `library.setFavorite(anime, anime.id !in favoriteIds.value)`.
  - Store `library` as a `private val`.
- [ ] **Step 2: `HeroPager`, image layer.**
  - Pager height 470 dp; keep the auto-advance and the dots (selected dot uses `primary`).
  - Each page shows `NetImage(anime.backdrop)` full-bleed, crop, with `Modifier.sharedKey(sharedBackdropKey(anime.id, "home-hero"))`.
  - Delete the blur background, the centered poster and its `sharedPosterKey(…, "home-hero")`.
  - Gradient: `#0B0A0F` alpha 0.7 at 0 → 0 at 0.24 → 0.1 at 0.46 → 0.85 at 0.78 → `background` at 1.
- [ ] **Step 3: `HeroPager`, content.** Bottom-left column with 20 dp side padding:
  - Row: a `#N MUSIM INI` label (`primary` background, `onPrimary` labelSmall ExtraBold), then `type · year` in bodyMedium.
  - Title: `headlineMedium` ExtraBold, 2 lines, ellipsis.
  - Meta: the existing line (views, genres).
  - Buttons:
    - `Tonton`: `Button` with `onSurface` container, `surface` content, height 50, radius 14, play icon, calls `onOpen`.
    - Favorite: `OutlinedIconButton` 50 dp, radius 14, `Icons.Rounded.Add` or `Check`. `contentDescription` is `"Tambah ke Koleksi"` / `"Hapus dari Koleksi"`. Calls `onToggleFavorite`.
  - `HeroPager` gains the parameters `favoriteIds: Set<String>` and `onToggleFavorite: (Anime) -> Unit`.
- [ ] **Step 4: Top bar.** `Wordmark` renders `fuwa` in `primary` and `stream` in `onSurface`, lowercase, ExtraBold 21 sp, letter-spacing −0.03 em. Keep the Search and Settings buttons and the scroll behavior.
- [ ] **Step 5: Sections.**
  - **Episode Terbaru**: `PosterRow(..., isNew = { true })`. The width stays the `PosterRow` default of 124 dp; the spec's 112 dp is not worth a width parameter. Caption `"Episode N · ${relativeTime(...)}"`, dropping the `" · "` part when `relativeTime` is blank.
  - **Terpopuler**: becomes a private `RankedRow(items: List<Anime>, onClick: (Anime) -> Unit, origin: String)`, a `LazyRow` of the first 10 items. Each cell is a 158×160 dp `Box`:
    - Rank number: 120 sp ExtraBold, `drawStyle = Stroke(width = 2.dp in px)`, color `#4A4455`, at bottom-start with offset y = 14 dp.
    - Poster: `NetImage`, 106×152 dp, top-end, radius 12, `sharedKey(sharedPosterKey(anime.id, origin))`, `contentDescription = anime.title`.
    - Keep `origin = "home-popular"`, or whatever the current origin string is.
  - **Jelajahi Genre**: the `SuggestionChip`s become `TagChip`.
- [ ] **Step 6: `UpdateBanner`.** `surfaceContainerLow` container with a 1 dp `primary.copy(alpha = 0.4f)` border.
- [ ] **Step 7: Run the gate.** Expected: `CI PASSED`.
- [ ] **Step 8: Commit** with message `feat(home): cinematic hero, ranked popular row, new chips`.

### Task 5: Detail

**Files:**
- Modify: `app/src/main/java/com/fuwanime/ui/detail/DetailScreen.kt`:
  - actions 215-274
  - genres 275-281
  - seasons 284-299
  - `Header` 381-453
  - `Synopsis` 455-481
  - `InfoGrid` 483-506
  - `EpisodeHeader` 508-566
  - `EpisodeRow` 568-639

**Interfaces:**
- Consumes: `remainingLabel`, `TagChip`, `PosterRow`, and the hero key `sharedBackdropKey(id, "home-hero")` from Task 4.

- [ ] **Step 1: Nested backdrop keys in `Header`.** Wrap the backdrop `NetImage` in a `Box` with `Modifier.sharedKey(sharedBackdropKey(anime.id, "home-hero").takeIf { origin == "home-hero" })`. The image inside keeps its existing `sharedKey(sharedBackdropKey(anime.id, entryKey).takeIf { linkBackdrop })`.
  - `origin` is already `null` while exiting or scrolled away, so the hero link follows the same gating as the poster.
  - If QA (Task 8) shows the nested pair misbehaving, fall back: put the hero key on the image only when `origin == "home-hero"`, and put the `entryKey` key there otherwise.
- [ ] **Step 2: `Header` text column.**
  - Title `headlineSmall` ExtraBold, 3 lines.
  - First alternative title in bodyMedium `onSurfaceVariant`, 2 lines.
  - Meta text: `listOf(displayYear, type, statusLabel(status), "${episodeCount} EP")`, blanks dropped, joined with `" · "`. Use the latest episode number from `DetailUiState.latestEpisode`; drop the part when it is unknown.
  - Remove `StatusPill` and the type `Pill` from the header. Increase the poster shadow elevation to 16 dp.
- [ ] **Step 3: Stats line**, under the header: `buildAnnotatedString` with bold `compactNumber(views)` + `" ditonton"`, then a 20 dp gap, then bold `compactNumber(favorites)` + `" favorit"`, in bodyMedium `onSurfaceVariant`. Remove the icon row.
- [ ] **Step 4: Genres.** The `AssistChip`s become `TagChip`.
- [ ] **Step 5: Actions.**
  - Main `Button`: height 52, radius 14, `primary`. Content is a play icon plus a two-line `Column`.
    - Line 1: `Lanjut EP N`, `Tonton EP N` or `Memuat episode…`, in titleSmall ExtraBold.
    - Line 2: the `remainingLabel` of the resume entry, labelSmall at alpha 0.75, shown only when non-null.
  - Delete the resume `LinearProgressIndicator` (263-274).
  - Favorite toggle: 52 dp, radius 14, `surfaceContainer` container, `outlineVariant` border, `primary` icon when checked. Keep the `FavoriteIcon` pop.
- [ ] **Step 6: `Synopsis`.** Remove the "Sinopsis" heading. Use 3 lines. Show a separate `TextButton` labelled `Selengkapnya` / `Tutup` in labelLarge Bold `onSurface`. Keep `animateContentSize`.
- [ ] **Step 7: `InfoGrid`** container becomes `surfaceContainerLow`.
- [ ] **Step 8: Season & Terkait.** In the item whose `id == anime.id`:
  - Draw a 2 dp `primary` border over the poster and set the poster alpha to 0.55.
  - The caption becomes `"${label} · Ini"` in `primary`.
  - If the row has no such item, nothing is highlighted.
  - Implement it as a `highlightId: String? = null` parameter on `PosterRow`, forwarded to `PosterCard(highlighted: Boolean = false)`. Add both parameters to Task 3's components here.
- [ ] **Step 9: `EpisodeHeader`.** Title row "Episode" titleMedium Bold + `" · N"` bodyMedium `onSurfaceVariant`. Range chips become `TagChip(selected = page == selectedPage)`.
- [ ] **Step 10: `EpisodeRow`.**
  - Thumbnail 124×70 dp, radius 10.
  - When finished: image alpha 0.4 and a centered `Icons.Rounded.Check` (22 dp, `onSurface`). Delete the trailing `CheckCircle`.
  - The BARU badge uses the same pill style as `PosterCard`.
  - Keep the resume tint (`surfaceContainerLow`), the primary title, the progress bar and `EpisodeDownloadButton`.
- [ ] **Step 11: Run the gate.** Expected: `CI PASSED`.
- [ ] **Step 12: Commit** with message `feat(detail): cinematic header, two-line play button, episode rows`.

### Task 6: Jelajah

**Files:**
- Modify: `app/src/main/java/com/fuwanime/ui/explore/ExploreScreen.kt`: search 115-143, `FilterBar` 190-257, `ResultsGrid` 259-332

**Interfaces:**
- Consumes: `TagChip`, `PosterCard`; the ViewModel setters `setType`, `setGenre`, `setSort`, `setStatus`; the enums `TypeFilter`, `StatusFilter`, `SortOrder` (all unchanged).

- [ ] **Step 1: Search field.**
  - Height 50 dp, `RoundedCornerShape(14.dp)`, `surfaceContainerLow` container, 1 dp border `#24212C`.
  - Placeholder `Judul, genre, atau studio`.
  - Keep the clear button and the recent-searches behavior.
- [ ] **Step 2: Split `FilterBar` into two `LazyRow`s.**
  - Row 1: `TypeFilter.entries.map { TagChip(it.label, { setType(it) }, selected = it == filters.type) }`.
  - Row 2: the genre chips as `TagChip(selected = genre == filters.genre)`. Tapping the selected genre clears it (`setGenre(null)`), as today.
  - Delete the Type dropdown chip and the divider.
- [ ] **Step 3: Results header**, above the grid.
  - Left: `filters.genre ?: filters.type.takeIf { it != TypeFilter.ALL }?.label ?: "Semua anime"` in titleMedium Bold. Check the actual "all" entry name in `TypeFilter`.
  - Right: a `FilledTonalButton` with a sort icon, `filters.sort.label` and a chevron.
  - The button opens one `DropdownMenu`:
    - An "Urutkan" label, then each `SortOrder`, with a check on the current one.
    - A `HorizontalDivider`.
    - A "Status" label, then each `StatusFilter`, with a check on the current one.
  - Delete the separate Sort and Status chips.
- [ ] **Step 4: Grid caption.** Pass `caption = null`, so the card shows `type · year`, and remove the `compactNumber` caption. Drop the import if it becomes unused.
- [ ] **Step 5: Run the gate.** Expected: `CI PASSED`, with `TypeFilterTest` still green.
- [ ] **Step 6: Commit** with message `feat(explore): type and genre chip rows, combined sort/status menu`.

### Task 7: Koleksi

**Files:**
- Modify: `app/src/main/java/com/fuwanime/ui/library/LibraryScreen.kt`: tabs 130-145, `HistoryList` 160-217

**Interfaces:**
- Consumes: `historyDayLabel` and `remainingLabel` (Task 2); `WatchHistoryEntity.updatedAt`, `positionMs`, `durationMs`, `isFinished`, `progress`.

- [ ] **Step 1: Segmented control.** Replace `PrimaryTabRow` with a private `SegmentedTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit)`.
  - Container: `surfaceContainerLow`, radius 14, padding 4 dp.
  - Three equal-weight 40 dp buttons with radius 10. Selected: `secondaryContainer` background, `primary` text, labelLarge Bold. Others: `onSurfaceVariant` text, SemiBold.
  - Each button sets `Modifier.semantics { role = Role.Tab; selected = … }`.
  - Titles: `Riwayat · n`, `Favorit · n`, `Unduhan · n`.
  - Keep the pager sync (`animateScrollToPage` on tap, selected = `pagerState.currentPage`).
- [ ] **Step 2: Group the history.** In the `LazyColumn`, group the list by `historyDayLabel(it.updatedAt)`, keeping the order. Emit a header item per group: labelMedium Bold, 0.08 em letter-spacing, `#8A8494`, padding 14/6 dp. The header key is `"day-$label"`.
- [ ] **Step 3: History row.**
  - Thumbnail 132×74 dp, radius 12, with an `EP N` pill at top-left (`#0B0A0F` alpha 0.78) and a 3 dp `primary` progress bar.
  - Title: anime title, titleSmall Bold, 1 line, ellipsis.
  - Subtitle: `remainingLabel(...) ?: "Selesai ditonton"` when `isFinished`, else the existing `pos / dur` text.
  - Keep the close (X) button, the tap targets and the date line removal: the day header replaces the date.
- [ ] **Step 4: Run the gate.** Expected: `CI PASSED`.
- [ ] **Step 5: Commit** with message `feat(library): segmented tabs, history grouped by day`.

### Task 8: Setting toggle for the "+85 dtk" button

Added at the user's request on 2026-11-01. This is outside the spec, so the decision is pinned here.

The new switch hides only the generic **+85 dtk** jump that shows when an anime has no OP mark. A marked OP still shows **Lewati OP**, and the existing "Lewati OP otomatis" switch keeps working. The default is on, so nothing changes until the user turns the switch off.

**Files:**
- Modify: `app/src/main/java/com/fuwanime/data/local/SettingsStore.kt`: field 34, key 54, read 75, setter 90
- Modify: `app/src/main/java/com/fuwanime/ui/player/SkipIntro.kt:20-28`
- Modify: `app/src/main/java/com/fuwanime/ui/player/PlayerScreen.kt:760` (the `skipActionAt` call)
- Modify: `app/src/main/java/com/fuwanime/ui/settings/SettingsScreen.kt:165-174`, next to "Lewati OP otomatis"
- Test: `app/src/test/java/com/fuwanime/SkipIntroTest.kt`

**Interfaces:**
- Produces:
  - `AppSettings.jumpAheadButton: Boolean = true` (DataStore key `"jump_ahead_button"`)
  - `SettingsStore.setJumpAheadButton(enabled: Boolean)`
  - `skipActionAt(positionMs: Long, durationMs: Long, mark: OpMark?, jumpAhead: Boolean = true): SkipAction?`

- [ ] **Step 1: Write the failing test** in `SkipIntroTest`:

```kotlin
@Test fun jumpAheadOff_hidesGenericJumpButKeepsMarkedOp() {
    val duration = 1_440_000L
    assertNull(skipActionAt(5_000, duration, null, jumpAhead = false))
    assertNull(skipActionAt(120_000, duration, null, jumpAhead = false))
    assertEquals(SkipAction.SkipOp(150_000), skipActionAt(60_000, duration, OpMark(60_000, 150_000), jumpAhead = false))
}
```

- [ ] **Step 2: Implement the parameter.** In `skipActionAt`, return `null` before the `JumpAhead` branch when `!jumpAhead`. The marked-OP branch must stay above that check.
- [ ] **Step 3: Wire the setting.**
  - Add the field, key, read and setter in `SettingsStore`, following the `autoSkipOp` pattern.
  - In `PlayerScreen.kt:760`, pass `jumpAhead = settings.jumpAheadButton`.
  - In `SettingsScreen`, add `SwitchRow(Icons.Rounded.FastForward, "Tombol +85 detik", "Lompat 85 detik di awal episode yang OP-nya belum ditandai", s.jumpAheadButton) { vm.update { setJumpAheadButton(it) } }` directly below "Lewati OP otomatis".
- [ ] **Step 4: Run the gate.** Expected: `CI PASSED`, with `SkipIntroTest` green.
- [ ] **Step 5: Commit** with message `feat(player): setting to hide the +85s jump button`.

### Task 9: QA run, light-theme check, PR

**Files:**
- Modify: `.github/scripts/qa.sh`: copy that changed in Tasks 4-6

- [ ] **Step 1: Update QA copy.**
  - `tap "Cari judul anime*"` becomes `tap "Judul, genre, atau studio*"`.
  - `tap "Tonton Sekarang"` becomes `tap "Tonton"`.
  - `tap "Tonton Ep*" || tap "Lanjutkan Ep*"` becomes `tap "Tonton EP*" || tap "Lanjut EP*"`.
- [ ] **Step 2: Hex grep.** Run `grep -nE "Color\(0x" app/src/main/java/com/fuwanime/ui/{components,home,detail,explore,library}/*.kt`. Every hit must be one of the allowed fixed colors in Global Constraints. Replace any other hit with a theme role.
- [ ] **Step 3: Run the QA workflow.**
  - Commit and push.
  - Start it with `gh workflow run build.yml --repo SakamotoMrX/fuwanime --ref feat/redesign-sinematik -f qa=true` and watch it with `gh run watch <id> --exit-status`.
  - Download the output: `gh run download <id> --repo SakamotoMrX/fuwanime -n smoke-screenshots -D <scratch>/qa`.
  - Expected: the run succeeds and every `qa*.png` shot plus the recording exist.
- [ ] **Step 4: Review the shots against the mockup and the Review Focus list.**
  - `qa1*`: poster flies back, and the labels appear only after landing.
  - `qa4*`: hero → detail → player → back.
  - `qa5*`: tabs.
  - Fix what is wrong, then rerun Step 3.
- [ ] **Step 5: Final review.** Run `ponytail:ponytail-review` on `git diff main...HEAD`.
- [ ] **Step 6: PR.** Write the description with `mattpocock-skills:pr`, in Indonesian, short, with no attribution. Open the PR into `main` of `SakamotoMrX/fuwanime`. Merge only after the user says so.
