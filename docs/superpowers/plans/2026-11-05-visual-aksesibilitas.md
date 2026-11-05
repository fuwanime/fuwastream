# Visual & aksesibilitas (sub-proyek D) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Poster-tinted Detail, frosted bars, a navigation rail on wide screens, and a font-200%/TalkBack pass.

**Architecture:**
- Pure colour, description and layout functions with JVM tests.
- Compose wiring in existing screens: one `Frosted.kt` helper over Haze, and `LocalNavBarPadding` so tab lists can scroll under the frosted bottom bar.

**Tech Stack:** Compose BOM 2026.09.00, androidx.palette 1.0.0, Haze 1.7.3, JUnit4.

**Spec:** `docs/superpowers/specs/2026-11-05-visual-aksesibilitas-design.md`

## Global Constraints

- Verification: `bash /home/sakamoto/Data/Github/fuwanime-quick-wins/.superpowers/quick-wins/ci-test.sh` only. **No emulator QA** (user request).
- Batch RED for all pure tests in one CI run, using `TODO()` stubs.
- minSdk 24. Haze 1.7.3 only (2.x needs Kotlin 2.4). Palette 1.0.0.
- Colour thresholds:
  - `posterTint` luminance ≤ 0.25
  - `lighten` target luminance ≥ 0.45
  - gradient tint alpha 0.55, height 420.dp
  - frosted blur 24.dp, background tint alpha 0.72
- Layout: rail at `screenWidthDp >= 600`; readable width max 840.dp.
- TalkBack copy: "Baru", "Sedang tayang", "dipilih", custom action "Pilih". The separator is ", ".
- No Claude co-author lines.

## Review Focus

1. **Last item of each tab list hidden behind the overlay bar.** Every tab list adds `LocalNavBarPadding` to its bottom `contentPadding`, including Koleksi's 3 pages, Jadwal's 8 pages, and Explore's recent-search list and grid. Pinned by review: grep each tab screen's `contentPadding`.
2. **Snackbars/FABs inside tabs sit under the bar.** Check that no tab uses a bottom-aligned element; if one does, pad it by `LocalNavBarPadding`.
3. **Rail mode and insets.** Content beside the rail must still get status/nav bar insets as before, and `LocalNavBarPadding` must be 0 in rail mode.
4. **Detail tint flicker.** Tint arrives after first frame; the gradient must fade in (`animateColorAsState`) rather than pop. Pinned by implementation: `animateColorAsState(tint ?: Color.Transparent)`.
5. **Double-read in TalkBack.** Child images inside merged nodes must have null `contentDescription`.

---

### Task 1: Pure helpers + tests (RED batch)

**Files:**
- Create `app/src/main/java/com/fuwanime/ui/detail/PosterTint.kt`:
  - `relativeLuminance(color: Int): Double`
  - `posterTint(darkVibrant: Int?, vibrant: Int?, dominant: Int?): Int?`
  - `lighten(color: Int, target: Double = 0.45): Int`
  - `onColorFor(color: Int): Int`
- Create `app/src/main/java/com/fuwanime/ui/components/A11y.kt`: `posterDescription(title, caption, badge, isNew, airing, selected): String`
- Create `app/src/main/java/com/fuwanime/ui/navigation/NavLayout.kt`: `useNavRail(widthDp: Int): Boolean`
- Test: `app/src/test/java/com/fuwanime/VisualA11yTest.kt`

Tests (exact values):

```kotlin
@Test fun luminanceEnds() { assertEquals(0.0, relativeLuminance(0xFF000000.toInt()), 1e-6); assertEquals(1.0, relativeLuminance(0xFFFFFFFF.toInt()), 1e-6) }
@Test fun tintPrefersDarkVibrantThenVibrantThenDominant() // posterTint(dv, v, d) uses dv; posterTint(null, v, d) is derived from v (same hue ratio); posterTint(null,null,null) == null
@Test fun tintIsDarkEnough() // posterTint(null, 0xFFFFD54F.toInt() /* amber */, null)!! → relativeLuminance ≤ 0.25; already-dark 0xFF1A237E stays unchanged
@Test fun lightenReachesTarget() // relativeLuminance(lighten(0xFF1A237E.toInt())) ≥ 0.45 and ≤ 1.0
@Test fun onColorPicksReadable() { assertEquals(0xFFFFFFFF.toInt(), onColorFor(0xFF1A237E.toInt())); assertEquals(0xFF000000.toInt(), onColorFor(0xFFFFD54F.toInt())) }
@Test fun descriptionOrder() = assertEquals("Frieren, Episode 5, Baru, 2 baru, dipilih", posterDescription("Frieren", "Episode 5", "2 baru", isNew = true, airing = true, selected = true))
@Test fun descriptionAiringWithoutNew() = assertEquals("Frieren, Sedang tayang", posterDescription("Frieren", null, null, isNew = false, airing = true, selected = false))
@Test fun descriptionSkipsBlankCaption() = assertEquals("Frieren", posterDescription("Frieren", " ", null, false, false, false))
@Test fun railThreshold() { assertFalse(useNavRail(599)); assertTrue(useNavRail(600)) }
```

- [ ] **Step 1:** Write the tests and `TODO()` stubs. Run the gate. Expected: `CI FAILED` only on VisualA11yTest.
- [ ] **Step 2:** Implement.
  - Darken: multiply R, G and B by the same factor. Binary search on the factor until luminance ≤ 0.25.
  - `lighten`: mix with white, binary search on the mix ratio.
  - Contrast ratio = (L1 + 0.05)/(L2 + 0.05).
- [ ] **Step 3:** Commit `feat(ui): poster tint, a11y description and nav layout helpers`. GREEN is verified with Task 5's gate.

### Task 2: Dependencies + Frosted helper + bottom bar/rail

**Files:**
- `gradle/libs.versions.toml` and `app/build.gradle.kts`: add `palette = "1.0.0"` and `haze = "1.7.3"`, plus libraries `androidx-palette-ktx` (`androidx.palette:palette-ktx`) and `haze` (`dev.chrisbanes.haze:haze`)
- Create `app/src/main/java/com/fuwanime/ui/components/Frosted.kt`: `fun Modifier.frosted(state: HazeState): Modifier` (composable-scoped colours read via a `@Composable` wrapper `rememberFrostedStyle()` or `composed {}`) and `val LocalNavBarPadding = compositionLocalOf { 0.dp }`
- Modify `ui/navigation/FuwaNavHost.kt`:
  - `val rail = useNavRail(LocalConfiguration.current.screenWidthDp)`
  - When there is no rail: `Box { content(hazeSource) ; bottom bar aligned BottomCenter with .frosted(state).onSizeChanged { barHeight = it } }`, with the bar container transparent. Provide `LocalNavBarPadding = barHeightDp` while the bar is visible, else 0.
  - When there is a rail: `Row { AnimatedVisibility(currentTab != null) { NavigationRail(containerColor = surfaceContainerLow) { same items } } ; content }`.
  - Keep the existing hairline, colours, item colours and enter/exit animation for the bar.
- Modify the tab lists to add `LocalNavBarPadding.current` to the bottom of `contentPadding`:
  - `HomeScreen.kt:175`
  - `ExploreScreen.kt:169,298,317`
  - `ScheduleScreen.kt:170,197`
  - `LibraryScreen.kt:270,422,469`
- [ ] **Step 1:** Implement.
- [ ] **Step 2:** Commit `feat(ui): frosted bottom bar and navigation rail on wide screens`.

### Task 3: Detail tint, frosted top bar, readable width

**Files:**
- Modify `ui/detail/DetailScreen.kt`:
  - `val tint by produceState<Color?>(null, anime?.poster)`: `loadBitmap` 64 px, then `withContext(Dispatchers.Default) { Palette.from(bmp).generate() }`, then `posterTint(dv, v, dominant)` converted to `Color`, or null.
  - `animateColorAsState(tint ?: Transparent)`. A gradient Box (420.dp, `verticalGradient(tint.copy(alpha = .55f), Transparent)`) sits behind the header content.
  - The play `Button` gets `colors = ButtonDefaults.buttonColors(containerColor = Color(lighten(tint)), contentColor = Color(onColorFor(lighten(tint))))` when there is a tint, and `Modifier.heightIn(min = 52.dp)`.
  - The LazyColumn gets `hazeSource`. `DetailTopBar(solid, …, haze = state)` uses `.frosted(state)` when solid, instead of the `surfaceContainer` background.
  - The LazyColumn's content gets `readableWidth()`.
- Create `Modifier.readableWidth()` in `ui/components/Frosted.kt`. The same file is fine, or `Layout.kt`.
- Modify `ui/stats/StatsScreen.kt`, `ui/settings/SettingsScreen.kt` (LargeTopAppBar: transparent colours + `.frosted(state)`, LazyColumn `hazeSource` + `readableWidth()`) and `ui/legal/LegalScreen.kt` (`readableWidth()` only).
- [ ] **Step 1:** Implement.
- [ ] **Step 2:** Commit `feat(detail): poster-tinted header and play button, frosted top bars, readable width`.

### Task 4: Font 200% + TalkBack

**Files:**
- `ui/components/Components.kt` `PosterCard`:
  - Add the param `longClickLabel: String = "Pilih"`.
  - The root gets `semantics(mergeDescendants = true) { contentDescription = posterDescription(anime.title, caption, badge, isNew, airing = anime.isOngoing, selected); if (onLongClick != null) customActions = listOf(CustomAccessibilityAction(longClickLabel) { onLongClick(); true }) }`.
  - The inner image `contentDescription` becomes null.
- `ui/library/LibraryScreen.kt` `HistoryRow`: merge + custom action "Pilih".
- `ui/schedule/ScheduleScreen.kt` `ScheduleRow` and `ui/detail/DetailScreen.kt` `EpisodeRow`: `semantics(mergeDescendants = true)`.
- Audit `grep -nE "\.height\([0-9]+\.dp\)"` across `ui/`. Change each fixed height on a container holding `Text` to `heightIn(min = …)`. Add `maxLines` + ellipsis to hero, row and card titles where they're missing.
- [ ] **Step 1:** Implement.
- [ ] **Step 2:** Commit `feat(a11y): merged TalkBack nodes with actions, no clipped text at large font scales`.

### Task 5: Gate

- [ ] Run `ci-test.sh`. Expected: `CI PASSED` (VisualA11yTest green, and all previous tests green).
