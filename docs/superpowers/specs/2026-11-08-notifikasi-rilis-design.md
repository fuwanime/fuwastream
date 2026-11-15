# Notifications: new episodes and chapters of ongoing titles

Date: 2026-11-08 · Branch: `feat/notif-rilis`

## Intent

The user asked: "every time an ongoing anime/manga releases a new episode/chapter, show the user a
notification". Today `EpisodeNotifyWorker` only watches the user's **favourites** (ongoing ones) and
there is no manga notification at all. Goal: notify for **any ongoing title**, not only favourites,
for both anime and manga.

Decisions made in chat:

- **Content:** anime + manga.
- **Trigger:** a new episode/chapter of an **ongoing** title.
- **Watched titles:** all ongoing titles (global feed), not only favourites.
- **Manga source:** MangaDex, filtered to the app language.

Success: with the toggle on, when an ongoing anime gets a new episode or an ongoing manga gets a new
chapter (in the app language) the user gets a notification even if the title is not favourited; the
first run after install/enable records a baseline silently (no burst); no duplicate with the
favourites worker.

## Constraints

- No new dependencies (WorkManager, Room, OkHttp, Coil, Retrofit and Moshi are already in the app).
- Verification is CI-only (`gradle testDebugUnitTest assembleRelease`); the dev container has no
  Android SDK, so nothing is built locally.
- New strings in all 4 locales (`values`, `values-en`, `values-ms`, `values-zh-rCN`), guarded by
  `StringsCompletenessTest`.
- The notification permission is already requested in `MainActivity`; no change.
- Backup format is unchanged apart from two new booleans; old backups keep restoring.

## Design

### 1. Data sources

- **Anime:** the existing `AnimeRepository.latestReleases(limit)` hits `data/home/list_new_episode`
  in one request. Each `LatestRelease` carries the `Anime` (with `status`) and `episodeLabel`
  ("Episode 5"). This is the same feed the Home screen already uses.
- **Manga:** a new MangaDex `GET /chapter?order[publishAt]=desc&translatedLanguage[]=<app>&
  contentRating[]=…&includes[]=manga`. One request returns the newest chapters across all manga;
  the included `manga` relationship carries `title` and `status`. `RelationshipAttributesDto` gains
  optional `title`/`status` so the included manga can be read (cover/author fields are untouched).

### 2. Settings

| key | field | default |
|---|---|---|
| `notify_ongoing_anime` | `notifyOngoingAnime` | `true` |
| `notify_new_chapters` | `notifyNewChapters` | `true` |

Both are backed up (added to `SETTING_TYPES`).

Bookkeeping keys `notify_release_<animeId>` and `notify_manga_<mangaId>` (doubles) hold the newest
episode/chapter already notified; their prefixes are added to `isBackedUpSetting` so they stay out of
backups, like the existing `notify_baseline_` keys.

### 3. Worker

`ReleaseNotifyWorker` (`CoroutineWorker`, periodic **1 h**, `NetworkType.CONNECTED`) is scheduled
from `FuwaApp.onCreate`, like the existing worker. It returns success immediately when both toggles
are off.

- **Anime branch** (`notifyOngoingAnime`): fetch `latestReleases(limit = 30)`; skip ids that are
  favourites (the favourites worker already handles those, and it writes the Library badge
  baselines); skip entries whose `status` is present and not ongoing; parse the episode number from
  `episodeLabel`; compare it to `notify_release_<id>` → notify when newer, always store the new
  baseline. A null baseline only seeds (first run for that title, no notification).
- **Manga branch** (`notifyNewChapters`): fetch `latestOngoingChapters(limit = 40)`; keep ongoing
  only; for each manga compare the chapter number to `notify_manga_<id>` → notify when newer, else
  seed.
- Both branches reuse the existing grouping: one notification per title, grouped under a summary
  ("N new episodes" / "N new chapters"). Anime reuses `EpisodeNotifier` (channel `new_episodes`);
  manga gets a parallel `MangaNotifier` (channel `new_chapters`).

### 4. Tapping a notification

- Anime: opens the detail screen (existing `MainActivity.EXTRA_OPEN_ANIME_ID`).
- Manga: opens the manga detail. New `LaunchTarget.Manga(id)` + `MainActivity.EXTRA_OPEN_MANGA_ID`,
  parsed by `parseLaunchTarget` and handled by `FuwaNavHost` via `Routes.mangaDetail(id)`.

### 5. Repository addition

`MangaRepository.latestOngoingChapters(limit)` returns `List<MangaRelease(mangaId, title, chapter,
publishAt)>`, one entry per manga (newest first), app-language only, honouring the mature-rating
setting. It uses the new `MangaDexApi.latestChapters`, reusing the existing `FeedDto`/`ChapterDto`,
and takes the first (newest) chapter seen per manga id.

## Alternatives rejected

- **Global unfiltered per-update notifications** (literally "every release"): rejected as spam —
  the anime feed alone lists dozens of titles per few hours and MangaDex spans every language and
  scanlation group. Grouping plus a per-title baseline keep it bounded while still feeling live.
- **Favourites-only:** the user explicitly wanted all ongoing titles.
- **Replacing the favourites worker:** kept, because its per-anime baselines also drive the "new
  episode" badge in Library; removing it would regress that.

## Review focus

- **No duplicate** notification for a favourited ongoing anime (the global branch skips favourites).
- **Baseline seeding:** enabling the toggle must not fire a burst for the whole back-catalogue.
- **Manga dedupe:** a manga appears once even when several of its chapters are in the feed.
- **Backup/settings:** `SETTING_TYPES` and `isBackedUpSetting` cover the new keys, so a restored
  backup with the new booleans does not crash the reader.
