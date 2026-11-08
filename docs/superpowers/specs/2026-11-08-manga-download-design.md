# Manga: download chapters and read offline

Date: 2026-11-08 · Branch: `feat/manga-download`

## Intent

The user asked "loh manganya gabisa di download?". Anime episodes can be downloaded; manga chapters cannot. Goal: download chapters in the app and read them later without internet.

Decisions made in chat:
- **Trigger:** per chapter, and per chapter range (the existing 1–100 range chips).
- **Quality:** follows the existing "Hemat data" manga setting; respects the existing "Wi-Fi only" download setting.

Success: a downloaded chapter opens in the reader with no network, from the manga page or from Library; a range download queues every chapter of that range; downloads can be deleted per chapter and per manga.

## Constraints

- No new dependencies (WorkManager, Room, OkHttp and Coil are already in the app).
- Files live only in app-private storage; no storage permission.
- Downloads are not part of backups (backup format unchanged).
- New strings in all 4 locales (`values`, `values-en`, `values-ms`, `values-zh-rCN`).
- Verification is CI-only (`ci-test.sh`: unit tests + release build).

## Design

### 1. Storage and table

- Images: `context.getExternalFilesDir(null)/manga/<safe(mangaId)>/<safe(chapterId)>/<index padded to 3>.<ext>`, where `<ext>` comes from the URL path (`jpg`, `png`, `webp`; default `jpg`). `safe()` keeps `[A-Za-z0-9._-]` and replaces the rest with `_` (ids contain `:`). If external files are unavailable, fall back to `context.filesDir`.
- Room table `manga_downloads` (database **v5 → v6**, `MIGRATION_5_6` creating the table and an index on `mangaId`):

| column | type | meaning |
|---|---|---|
| `chapterId` | TEXT PK | chapter id (source in its prefix) |
| `mangaId` | TEXT | the opened manga id (the title history uses) |
| `mangaTitle`, `coverUrl` | TEXT | for Library |
| `chapterNumber` | TEXT? | for ordering and offline Prev/Next |
| `chapterLabel` | TEXT | as shown ("Ch. 12") |
| `language` | TEXT | chapter language |
| `pageCount` | INT | pages in the chapter (0 until known) |
| `pagesDone` | INT | pages saved so far |
| `bytes` | INTEGER | size on disk |
| `state` | TEXT | `QUEUED`, `RUNNING`, `DONE`, `FAILED` |
| `createdAt` | INTEGER | queue time |

`MangaDownloadDao`: `observeAll(): Flow<List<…>>`, `observeFor(mangaId): Flow<List<…>>`, `get(chapterId)`, `upsert`, `update progress`, `delete(chapterId)`, `deleteFor(mangaId)`.

### 2. Download job

`MangaDownloadRepository` (in `data/manga/`), built in `AppContainer` with the context, the DAO, `MangaRepository`, `SettingsStore` and `httpClient` (the client Coil uses for images, with the DoH DNS).

- `enqueue(manga: Manga, chapters: List<MangaChapter>, label: (MangaChapter) -> String)`: skips chapters already `QUEUED`, `RUNNING` or `DONE`; upserts rows as `QUEUED`; appends one `OneTimeWorkRequest<MangaDownloadWorker>` per chapter to the unique work `"manga-downloads"` with `ExistingWorkPolicy.APPEND_OR_REPLACE`, so chapters download one after another. Input data: `chapterId`. Constraint: `NetworkType.UNMETERED` when `downloadWifiOnly` is on, else `CONNECTED`.
- `MangaDownloadWorker` (`CoroutineWorker`):
  1. Reads the row; missing row → `success` (deleted meanwhile).
  2. Sets state `RUNNING`, calls `setForeground` with a notification (channel `manga_downloads`, low importance) titled "<manga> · <chapter>" with determinate progress `pagesDone/pageCount`.
  3. Fetches fresh page URLs: `mangaRepository.pages(chapterId, dataSaver = settings.mangaDataSaver.first(), forceRefresh = true)`.
  4. For each page index whose file is missing or empty: GET with `httpClient`, write to a `.part` file, rename when complete; update `pagesDone` and `bytes` after each page.
  5. All pages present → state `DONE`, `success`. Any exception → `retry` while `runAttemptCount < 3`, else state `FAILED`, `failure`. Backoff: exponential, 30 s.
- Manifest: `FOREGROUND_SERVICE_DATA_SYNC` permission and the WorkManager `SystemForegroundService` declared with `foregroundServiceType="dataSync"` (`tools:node="merge"`), required on Android 14+.
- `delete(chapterId)` / `deleteFor(mangaId)`: cancel nothing in flight except by removing the row (the worker sees no row on its next page and stops with `success`); delete the folder; delete the row(s).
- `localPages(chapterId): List<String>?`: when the row is `DONE`, the `file://` URIs of the saved images in index order, else null.

### 3. Detail page

- Each chapter cell shows a small status mark next to the date: a down-arrow while queued, a progress ring while running, a filled check-circle-outline when done, a warning icon when failed. The marks have content descriptions.
- Long-press on a cell opens a `DropdownMenu`: **Unduh** (when not downloaded or failed) or **Hapus unduhan** (when queued, running or done). The same two items are exposed as `customActions` semantics for TalkBack.
- Beside the range chips, a text button **"Unduh 1–100"** (the selected range's label; "Unduh semua" when there are no ranges) queues every chapter of the shown range that is not queued, running or done. It shows a snackbar "N chapter masuk antrean unduhan" (plural string), or nothing when N = 0.
- The detail screen observes `observeFor(mangaId)` as a `Map<chapterId, row>`.

### 4. Reader

- `MangaReaderViewModel.open`: if `localPages(chapterId)` is not null, it uses those instead of `repo.pages` (no network).
- Offline: if `repo.detail` or `repo.mergedChapters` fails and the chapter is downloaded, the reader builds its state from downloads instead: `manga` from the row (`Manga(id = mangaId, title = mangaTitle, coverUrl = coverUrl, …)`), `chapters` = that manga's `DONE` rows mapped to `MangaChapter` and ordered with the existing chapter ordering, so Prev/Next move between downloaded chapters. Only when the chapter is not downloaded does the existing error state show.
- Progress saving is unchanged.

### 5. Library → Manga

`MangaLibraryTab` gains an **"Unduhan"** section (shown when any download exists), one row per manga: cover, title, subtitle "12 chapter · 340 MB" (plural string + `Formatter.formatShortFileSize`), tap → manga detail, trailing delete icon → confirm dialog "Hapus semua unduhan <title>?" → `deleteFor(mangaId)`. Chapters still downloading count separately: "12 chapter · 2 dalam antrean".

## Error handling

- No storage: `enqueue` returns a failure shown as a snackbar ("Penyimpanan tidak tersedia").
- A page URL fails repeatedly: the chapter ends `FAILED`; the cell shows the warning mark and long-press offers **Unduh** again (resumes, keeping saved pages).
- MangaDex at-home URLs expire: each attempt refetches them (`forceRefresh = true`).
- The user deletes while a chapter downloads: the worker stops at its next page when the row is gone, and the folder is removed.

## Testing

Unit tests (JVM):
- `downloadDir`/`safe()` builds the expected path for `sh:abc` ids.
- `pageFileName(index, url)` pads to 3 digits and keeps `webp`/`png`, defaults to `jpg`, ignores query strings.
- `chaptersToQueue(range, rows)` skips queued/running/done and keeps failed/missing.
- `offlineChapters(rows)` keeps only `DONE` rows, orders by chapter number, maps fields.
- `MIGRATION_5_6` (Robolectric): run it on an empty database from `FrameworkSQLiteOpenHelperFactory`, then check `PRAGMA table_info(manga_downloads)` names, types, NOT NULL flags and primary key against the entity. The schema is not exported (`exportSchema = false`), and there is no `room-testing`, so this is the check that a wrong migration would otherwise only show as a crash on upgrade.
- The page-saving loop lives in a plain `suspend fun downloadPages(urls, dir, client, onPage)` (no WorkManager), so it is tested with MockWebServer and a temp folder. Serving 3 images writes 3 files and reports 3 pages. A second run after deleting one file requests only that file. A failed response leaves no `.part` file and throws. The worker itself stays a thin wrapper, since `work-testing` is not a dependency and none is added.

## Out of scope

Pause/resume per chapter, choosing a storage location, storage limits, a whole-manga button, downloads in backups, downloading anime-style through DownloadManager.
