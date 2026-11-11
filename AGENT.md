<!-- skill-routing:start -->
## Skill routing

Invoke the matching skill yourself before starting the work. Project: mobile, Android app (Kotlin, Jetpack Compose, Gradle). Pipeline: superpowers.

| Situation | Invoke |
|---|---|
| New feature idea / "build X" | `superpowers:brainstorming` → `superpowers:writing-plans` → `superpowers:subagent-driven-development` (long plans: `superpowers:executing-plans`) |
| Writing feature or fix code | `superpowers:test-driven-development` |
| Normal bug, failing test | `superpowers:systematic-debugging` |
| Hard, flaky, intermittent or performance bug | `mattpocock-skills:diagnosing-bugs` |
| Several unrelated failures at once | `superpowers:dispatching-parallel-agents` |
| Review a diff before merge | `ponytail:ponytail-review` (correct, safe, lean) |
| Acting on review feedback | `superpowers:receiving-code-review` |
| About to claim done, commit or open a PR | `superpowers:verification-before-completion` |
| Work finished, merge or PR | `superpowers:finishing-a-development-branch` |
| PR description | `mattpocock-skills:pr` |
| Domain terms, GLOSSARY, ADR | `mattpocock-skills:domain-modeling` |
| Module design, where a seam goes | `mattpocock-skills:codebase-design` |
| Whole-repo quality audit | `ponytail:ponytail-audit` |
| Spec, RFC or design doc for humans | `writing:doc-coauthoring` |

User-only commands for this pipeline: `/mattpocock-skills:handoff` (move to a new session), `/mattpocock-skills:grill-me` (stress-test an idea), `/mattpocock-skills:improve-codebase-architecture`, `/mattpocock-skills:wait-what` (explanation did not land).

Rules:
- One pipeline per feature. Its specs and plans live only in `docs/superpowers/`.
- Inside a superpowers plan, superpowers TDD wins over ponytail's "one small test".
- "Review" alone means `ponytail:ponytail-review`; name another review skill to use it instead.
- Brainstorming, specs, plans, grilling, and research reports are written in full. If caveman mode is active, ask the user to type `/caveman off` first.
- `/caveman:caveman-init` pipes a downloaded script into node; leave it unrun.
- Commits and PRs carry no AI co-author lines.

Full guide: `/home/sakamoto/reports/Panduan pakai skill Claude.md`.
<!-- skill-routing:end -->

---

# FuwaStream Agent Guide

FuwaStream (`com.fuwanime.app`) is an ad-free Android anime streaming and manga reading application built with Kotlin, Jetpack Compose (Material 3), Room, and Media3 ExoPlayer.

- **Min SDK**: 24 (Android 7.0+)
- **Compile / Target SDK**: 37
- **Java**: 17
- **Locales**: Indonesian (`id`), Malay (`ms`), English (`en`), Chinese Simplified (`zh`)

---

## 1. Architecture & Core Seams

### Manual DI (`AppContainer`)
The application does not use Dagger or Hilt. All dependencies live as singletons inside `AppContainer` (`app/src/main/java/com/fuwanime/FuwaApp.kt`).
- Access in Compose: `LocalAppContainer.current` or via `(LocalContext.current.applicationContext as FuwaApp).container`.
- Process-wide CoroutineScope: `container.appScope` (`SupervisorJob() + Dispatchers.IO`) for background tasks that outlive screens (e.g. saving watch history, refreshing widget).

### Data Layer
- **Local Persistence (`FuwaDatabase`)**: Room database with DAOs for history, favorites, downloads, daily watch stats, anime metadata links, manga library, manga chapters, and manga downloads.
- **Preferences (`SettingsStore`)**: Jetpack DataStore Preferences for user settings (theme, video quality defaults, Wi-Fi constraints, adult content filter, backup rules).
- **Network Stack**:
  - `AnimeApiService` (Retrofit + Moshi): Primary anime catalogue, schedule, and streams.
  - `KuramanimeSource` (OkHttp + regex scraping): Secondary Indonesian anime provider (`v20.kuramanime.ing`).
  - `MangaDexApi` (Retrofit + Moshi) + `MangaDexDns`: Global manga source, uses custom DNS resolver to bypass Indonesian ISP blocking.
  - `ShinigamiApi` (Retrofit + Moshi): Indonesian manga provider.
  - `KomikuSource` (OkHttp + regex scraping): Indonesian manga archive provider.
  - `AniListApi` & `AniSkipApi`: Anime franchise enrichment and intro/outro timestamp skipping.
  - `GitHubApi`: In-app update checker against GitHub Releases.
- **Cache-First Architecture**: `apiCacheClient` provides a 50MB disk cache with `NetworkStatus` observer and `ForceCache` interceptor for seamless offline fallback.

---

## 2. Screens & Features (Reference: README Visuals)

### Tonton (Anime Player & Detail) — `docs/readme/watch.webp`
- **Player (`PlayerScreen`, `PlaybackHolder`, `PlaybackService`)**:
  - Media3 ExoPlayer with OkHttp video data source.
  - Multi-quality selection (360p–1080p, Auto) with separate defaults for Wi-Fi vs Cellular.
  - Playback speed control (0.5×–2×), aspect ratio modes (Fit, Fill, Zoom).
  - Fullscreen touch gestures: double-tap seek (±10s), left drag (brightness), right drag (volume).
  - Clean buffering UX: controls hidden while buffering, only central spinner displayed.
  - Auto skip opening via AniSkip data, plus manual `+85s` skip button.
  - Picture-in-Picture (`PipController`) and background audio playback via foreground service.
- **Detail & Schedule (`DetailScreen`, `ScheduleScreen`)**:
  - Episode list with range pagination (1–50, 51–100) and continue-watching resume button.
  - Daily broadcast schedule with airing times and notification worker (`EpisodeNotifyWorker`).

### Baca (Manga Reader & Multi-Source) — `docs/readme/read.webp`, `sources.webp`
- **Multi-Source Aggregator (`MangaRepository`)**:
  - Merges chapters from MangaDex, Shinigami, and Komiku into a single deduplicated title.
  - Chapter provider precedence: Local / Readability > Shinigami > Komiku > MangaDex.
  - Language chips: `ID`, `EN`, `Raw`.
  - Format filters: `Manga`, `Manhwa`, `Manhua` on browse/shelf.
- **Reader (`MangaReaderScreen`)**:
  - Vertical continuous webtoon scroll with chapter switcher, progress persistence, and Data Saver mode.
  - 18+ / NSFW toggle gated behind settings.

### Temukan (Discovery & Browse) — `docs/readme/find.webp`
- **Home (`HomeScreen`)**:
  - Featured seasonal hero banner, "Lanjutkan Nonton" row, latest releases, and Top 10 Popular.
- **Explore (`ExploreScreen`)**:
  - Search anime by title, studio, or genre.
  - Type filters (`Series`, `Film`, `ONA`, `OVA`, `Live Action`) and sort orders (`Populer`, `Terbaru`, `A–Z`).
  - Genre selector bottom sheet (`GenreSheet.kt`).
  - Additive Kuramanime search/browse results.

### Simpan (Library & Downloads) — `docs/readme/save.webp`
- **Library (`LibraryScreen`)**:
  - Tabs: `Riwayat` (History), `Favorit` (Favorites), `Manga` (Followed manga), `Unduhan` (Downloads).
  - Weekly watch summary pill at top.
- **Storage & Downloads (`DownloadRepository`, `MangaDownloads`, `MangaDownloadWorker`)**:
  - Storage options: Internal app storage vs custom directory via Storage Access Framework (SAF / `DocumentFile`).
  - Download dialog prompts storage destination per task or uses default.
  - Auto-sanitized directory structure: `<Title>/<Episode|Chapter>.<ext>`.
  - Batch downloads: entire anime series or manga chapter ranges (e.g. 1–100).
  - Offline reader and offline video player support for downloaded content.
- **Backup & Restore (`BackupRepository`, `BackupWorker`, `BackupCodec`)**:
  - Export and import database records as JSON/zip with conflict resolution.

### Punyamu (Stats & Customization) — `docs/readme/weekly.webp`
- **Stats (`StatsScreen`, `WatchStats`)**:
  - Weekly watching metrics: total hours, completed episodes, active weeks streak, top genres, and shareable visual card.
- **Settings (`SettingsScreen`)**:
  - Themes: System, Light, Dark, AMOLED Black, Material You dynamic colors.
  - In-app updater via GitHub Releases (`UpdateRepository`).
  - One-time 13+ age gate (`AgeGateScreen`) and complete user data deletion.

---

## 3. Engineering Conventions

- **Language Policy**:
  - Conversational replies: Match user's language (Indonesian or English).
  - Code, identifiers, comments, tests, and commit messages: **English only**.
- **Compose Rules**:
  - Use Material 3 (`androidx.compose.material3`).
  - Mark immutable data classes with `@Immutable`.
  - Hoist state; ViewModels expose `StateFlow<UiState>` updated via `.update { ... }`.
  - Handle loading, empty, and error states gracefully in every screen.
- **Strings & Localisation**:
  - String resources live in `res/values/strings.xml`, `values-ms/`, `values-en/`, and `values-zh/`.
  - Always use `%d` for plural integers with explicit locale support; never hardcode UI text.
- **Scraping & Remote Parsers**:
  - Remote HTML changes break scraping easily. Always provide safe fallbacks (`runCatching`, regex alternatives, or empty collections) rather than throwing uncaught runtime exceptions.

---

## 4. Verification & Testing

Always verify before declaring work complete.

```bash
# Run unit tests (JUnit4 + Robolectric)
./gradlew testDebugUnitTest

# Run specific test class
./gradlew testDebugUnitTest --tests "com.fuwanime.MangaRepositoryTest"

# Check Android Lint (fails on fatal API regressions)
./gradlew lintDebug

# Assemble debug APK
./gradlew assembleDebug

# Run smoke test on active Android emulator (requires ADB)
bash .github/scripts/smoke.sh app/build/outputs/apk/debug/app-debug.apk
```
