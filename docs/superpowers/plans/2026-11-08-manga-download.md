# Manga Chapter Download Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Download manga chapters (single or a whole 1–100 range) into app storage and read them offline.

**Architecture:** A Room table `manga_downloads` tracks each chapter. A sequential WorkManager chain runs a thin `MangaDownloadWorker`, which calls a plain `downloadPages` function that saves the images. The reader prefers saved images, and when offline it builds its state from the download rows. The detail page and Library observe the rows.

**Tech Stack:** Kotlin 2.2, Compose Material3, Room, WorkManager, OkHttp, Coil 3, JUnit 4 + Robolectric + MockWebServer.

**Spec:** `docs/superpowers/specs/2026-11-08-manga-download-design.md`

## Global Constraints

- No new dependencies (not even test ones). WorkManager, Room, OkHttp and Coil are already present.
- Files go in app-private storage only: `getExternalFilesDir(null)/manga/...`, falling back to `filesDir`. No storage permission.
- Downloads are not in backups.
- Quality follows `settingsStore.mangaDataSaver`. The network constraint follows `settingsStore.downloadWifiOnly`.
- The work chain is unique work `"manga-downloads"` with `ExistingWorkPolicy.APPEND_OR_REPLACE`. Retry up to `runAttemptCount < 3` with exponential backoff of 30 s.
- New strings go in all 4 locales (`values`, `values-en`, `values-ms`, `values-zh-rCN`).
- Commits carry no Claude co-author lines.
- Verification is CI-only: `bash /home/sakamoto/Data/Github/fuwanime-quick-wins/.superpowers/quick-wins/ci-test.sh`.

## Review Focus

1. **A chapter is deleted while it downloads.** The worker must stop and must not recreate the folder or the row. Test in Task 2: `downloadPages` stops when `keepGoing()` returns false.
2. **The app is killed mid-page.** No half image may be shown later. Each page is written as `.part` and renamed only when complete. Test in Task 2.
3. **A chapter is queued twice** (a tap plus a range). It downloads once. Covered by the `chaptersToQueue` test in Task 1, and `enqueue` must filter on it.
4. **Reading offline from "Lanjut baca"** (the reader cold-starts without network). The chapter opens from saved files and Prev/Next use the downloaded chapters. Covered by the `offlineChapters` test in Task 1, wired in Task 4.
5. **Two sources both have a chapter 12 downloaded** (merged sources). Offline ordering must not break or duplicate it: `offlineChapters` keeps one per number. Test in Task 1.

---

### Task 1: Table, migration and pure helpers

**Files:**
- Create: `app/src/main/java/com/fuwanime/data/local/MangaDownloadLocal.kt` (entity, state enum, DAO)
- Modify: `app/src/main/java/com/fuwanime/data/local/FuwaDatabase.kt` (entity list, `version = 6`, `abstract fun mangaDownloadDao()`, `MIGRATION_5_6`, add it to `addMigrations`)
- Create: `app/src/main/java/com/fuwanime/data/manga/MangaDownloads.kt` (pure helpers)
- Test: `app/src/test/java/com/fuwanime/MangaDownloadLogicTest.kt`, `app/src/test/java/com/fuwanime/MangaDownloadMigrationTest.kt` (Robolectric)

**Interfaces:**
- Produces:
  - `enum class MangaDownloadState { QUEUED, RUNNING, DONE, FAILED }`
  - `@Entity(tableName = "manga_downloads", indices = [Index("mangaId")]) data class MangaDownloadEntity(@PrimaryKey val chapterId: String, val mangaId: String, val mangaTitle: String, val coverUrl: String?, val chapterNumber: String?, val chapterLabel: String, val language: String, val pageCount: Int, val pagesDone: Int, val bytes: Long, val state: String, val createdAt: Long)`. `state` holds `MangaDownloadState.name`.
  - `@Dao interface MangaDownloadDao`:
    - `observeAll(): Flow<List<MangaDownloadEntity>>` (ORDER BY createdAt)
    - `observeFor(mangaId: String): Flow<List<MangaDownloadEntity>>`
    - `get(chapterId: String): MangaDownloadEntity?`
    - `doneFor(mangaId: String): List<MangaDownloadEntity>`
    - `upsert(e: MangaDownloadEntity)`
    - `progress(chapterId: String, pageCount: Int, pagesDone: Int, bytes: Long)`
    - `setState(chapterId: String, state: String)`
    - `delete(chapterId: String)`
    - `deleteFor(mangaId: String)`
    - `allFor(mangaId: String): List<MangaDownloadEntity>`
  - `FuwaDatabase.MIGRATION_5_6` becomes `internal` so the test can reach it.
  - In `MangaDownloads.kt`:
    - `fun safeName(id: String): String`: keeps `[A-Za-z0-9._-]`, replaces anything else with `_`
    - `fun chapterDir(root: File, mangaId: String, chapterId: String): File`, which is `root/manga/<safe>/<safe>`
    - `fun pageFileName(index: Int, url: String): String`, e.g. `"007.webp"`; ext from the URL path before `?` if it is `jpg|jpeg|png|webp|gif`, else `jpg`
    - `fun chaptersToQueue(chapters: List<MangaChapter>, rows: Map<String, MangaDownloadEntity>): List<MangaChapter>`: drops ids whose row state is QUEUED, RUNNING or DONE
    - `fun offlineChapters(rows: List<MangaDownloadEntity>): List<MangaChapter>`: DONE only; mapped to `MangaChapter(id, chapterNumber, title = chapterLabel, language, group = null, pages = pageCount, externalUrl = null, publishAt = createdAt)`; one per `numberKey` (first by `sourceRank`); ordered numeric-ascending, with null or odd numbers after them by `createdAt`

- [ ] **Step 1: Failing tests** (`MangaDownloadLogicTest`):

```kotlin
@Test fun safePathForPrefixedIds() =
    assertEquals(File("/r/manga/sh_abc/sh_c-1"), chapterDir(File("/r"), "sh:abc", "sh:c-1"))
@Test fun pageFileNames() {
    assertEquals("007.webp", pageFileName(7, "https://x/a/b.webp?token=1"))
    assertEquals("012.png", pageFileName(12, "https://x/p.PNG"))
    assertEquals("001.jpg", pageFileName(1, "https://x/image"))
}
@Test fun queueSkipsActiveAndDone() {
    val rows = mapOf("a" to row("a", "QUEUED"), "b" to row("b", "DONE"), "c" to row("c", "FAILED"), "d" to row("d", "RUNNING"))
    assertEquals(listOf("c", "e"), chaptersToQueue(listOf(ch("a"), ch("b"), ch("c"), ch("d"), ch("e")), rows).map { it.id })
}
@Test fun offlineChaptersDoneOnlyOnePerNumberInOrder() {
    val r = offlineChapters(listOf(row("md12", "DONE", n = "12"), row("sh:12", "DONE", n = "12.0"), row("sh:2", "DONE", n = "2"), row("q", "QUEUED", n = "1")))
    assertEquals(listOf("sh:2", "sh:12"), r.map { it.id })
}
```

with helpers `row(id, state, n = "1")` building a `MangaDownloadEntity` and `ch(id)` building a `MangaChapter`.

- [ ] **Step 2: Failing migration test** (`MangaDownloadMigrationTest`, `@RunWith(RobolectricTestRunner::class)`). Open an empty DB with `FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(ctx).name(null).callback(object : SupportSQLiteOpenHelper.Callback(5) { onCreate {} ; onUpgrade {} }).build()).writableDatabase` and run `FuwaDatabase.MIGRATION_5_6.migrate(db)`. Then query `PRAGMA table_info(manga_downloads)` and assert:
  - the column set is exactly the entity's 12 names
  - `chapterId` has `pk = 1`
  - `coverUrl` and `chapterNumber` have `notnull = 0`, every other column `notnull = 1`
  - the types are `TEXT` for strings and `INTEGER` for Int/Long
- [ ] **Step 3: Implement** the entity, DAO, `MIGRATION_5_6` (`CREATE TABLE IF NOT EXISTS manga_downloads (...)` matching Room's DDL for the entity, plus `CREATE INDEX IF NOT EXISTS index_manga_downloads_mangaId ON manga_downloads (mangaId)`), and the helpers. `offlineChapters` reuses `numberKey` and `sourceRank` from `MangaModels.kt`.
- [ ] **Step 4: Commit** `feat(manga): downloads table and helpers`.

### Task 2: Page saver, repository and worker

**Files:**
- Modify: `app/src/main/java/com/fuwanime/data/manga/MangaDownloads.kt` (`downloadPages`, `MangaDownloadRepository`)
- Create: `app/src/main/java/com/fuwanime/data/manga/MangaDownloadWorker.kt`
- Modify: `app/src/main/java/com/fuwanime/FuwaApp.kt` (`AppContainer.mangaDownloads`), `app/src/main/AndroidManifest.xml`
- Modify: `res/values*/strings_manga.xml`
- Test: `app/src/test/java/com/fuwanime/MangaDownloadPagesTest.kt`

**Interfaces:**
- Consumes: Task 1's DAO and helpers; `MangaRepository.pages(chapterId, dataSaver, forceRefresh = true)`; `container.httpClient`; `settingsStore.mangaDataSaver`, `settingsStore.downloadWifiOnly`.
- Produces:
  - `suspend fun downloadPages(urls: List<String>, dir: File, client: OkHttpClient, keepGoing: suspend () -> Boolean = { true }, onPage: suspend (done: Int, bytes: Long) -> Unit): Boolean`
    - Runs on IO.
    - Skips files that already exist and are non-empty. Writes `<name>.part` and renames on success.
    - Throws `IOException` on a non-2xx response, after deleting the `.part`.
    - Returns false when `keepGoing()` is false before a page.
  - `class MangaDownloadRepository(context: Context, dao: MangaDownloadDao, settings: SettingsStore)`:
    - `val all: Flow<List<MangaDownloadEntity>>`
    - `fun forManga(mangaId: String): Flow<Map<String, MangaDownloadEntity>>`
    - `suspend fun enqueue(manga: Manga, chapters: List<MangaChapter>, label: (MangaChapter) -> String): Int` (the number queued)
    - `suspend fun delete(chapterId: String)`
    - `suspend fun deleteFor(mangaId: String)`
    - `suspend fun localPages(chapterId: String): List<String>?` (`file://` URIs sorted by file name, when DONE)
    - `suspend fun offline(mangaId: String): List<MangaChapter>` (`offlineChapters(dao.doneFor(mangaId))`)
    - `suspend fun row(chapterId: String): MangaDownloadEntity?`
    - `fun root(): File`
  - `class MangaDownloadWorker(context, params) : CoroutineWorker`, with input key `"chapterId"`. It gets its dependencies via `(applicationContext as FuwaApp).container`.
  - `AppContainer.mangaDownloads: MangaDownloadRepository` (lazy).
  - Strings: `manga_download_channel` (Unduhan manga / Manga downloads / Muat turun manga / 漫画下载) and `manga_download_notif` (`%1$s · %2$s`, same in all 4).

- [ ] **Step 1: Failing tests** (`MangaDownloadPagesTest`, MockWebServer + `TemporaryFolder`):

```kotlin
@Test fun savesEveryPageAndReportsProgress() = runBlocking {
    repeat(3) { server.enqueue(MockResponse().setBody("img$it")) }
    val urls = (1..3).map { server.url("/p$it.jpg").toString() }
    val done = mutableListOf<Int>()
    assertTrue(downloadPages(urls, dir, OkHttpClient()) { d, _ -> done += d })
    assertEquals(listOf("001.jpg", "002.jpg", "003.jpg"), dir.list()!!.sorted())
    assertEquals(3, done.last())
}
@Test fun resumesOnlyMissingPages() = runBlocking {
    /* first run 3 pages, delete 002.jpg, enqueue 1 response, run again */
    assertEquals(4, server.requestCount)
}
@Test fun failedPageLeavesNoPartFile() = runBlocking {
    server.enqueue(MockResponse().setResponseCode(500))
    assertThrows(IOException::class.java) { runBlocking { downloadPages(listOf(server.url("/p.jpg").toString()), dir, OkHttpClient()) { _, _ -> } } }
    assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".part") })
}
@Test fun stopsWhenDeleted() = runBlocking {
    repeat(3) { server.enqueue(MockResponse().setBody("x")) }
    var n = 0
    assertFalse(downloadPages((1..3).map { server.url("/p$it.jpg").toString() }, dir, OkHttpClient(), keepGoing = { n++ < 1 }) { _, _ -> })
    assertEquals(1, server.requestCount)
}
```

- [ ] **Step 2: Implement `downloadPages`, the repository and the worker.**
  - **enqueue:** filter with `chaptersToQueue`; upsert QUEUED rows (`chapterLabel = label(c)`, `pageCount = 0`); build one request per chapter with constraints, `setBackoffCriteria(EXPONENTIAL, 30, SECONDS)` and input data. Chain them with `WorkManager.getInstance(context).beginUniqueWork("manga-downloads", APPEND_OR_REPLACE, first).then(...)…enqueue()`. A single request uses `enqueueUniqueWork`.
  - **worker:**
    1. A missing row returns `success`.
    2. Otherwise `setState(RUNNING)`, then `setForeground(ForegroundInfo(id, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC))`. The notification channel is created if absent, as `EpisodeNotifyWorker` does, with importance LOW.
    3. Fetch `urls` and call `progress(pageCount = urls.size, 0, 0)`.
    4. Run `downloadPages(urls, chapterDir(root, …), client, keepGoing = { dao.get(chapterId) != null })` with an `onPage` that updates progress and the notification.
    5. If it returns false, delete the folder and return `success`. Otherwise `setState(DONE)` and return `success`.
    6. If it throws, return `retry` while `runAttemptCount < 2`, else `setState(FAILED)` and return `failure`.
  - **delete / deleteFor:** remove the row(s) first, then `deleteRecursively` the folder(s).
  - **Manifest:** add `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />` and `<service android:name="androidx.work.impl.foreground.SystemForegroundService" android:foregroundServiceType="dataSync" tools:node="merge" />`, plus the `xmlns:tools` namespace if it is missing.
- [ ] **Step 3: Commit** `feat(manga): download chapters in a background queue`.

### Task 3: Detail page download actions

**Files:**
- Modify: `app/src/main/java/com/fuwanime/ui/manga/MangaDetailScreen.kt`
- Modify: `res/values*/strings_manga.xml`

**Interfaces:**
- Consumes: `MangaDownloadRepository.forManga`, `.enqueue`, `.delete`; `MangaDownloadState`.
- Produces strings (id / en / ms / zh):
  - `manga_download` (Unduh / Download / Muat turun / 下载)
  - `manga_download_delete` (Hapus unduhan / Delete download / Padam muat turun / 删除下载)
  - `manga_download_range` (`Unduh %1$s` / `Download %1$s` / `Muat turun %1$s` / `下载 %1$s`)
  - `manga_download_all` (Unduh semua / Download all / Muat turun semua / 全部下载)
  - plural `manga_download_queued` (`%d chapter masuk antrean unduhan` / `%d chapters queued for download` / `%d bab dalam baris gilir` / `已将 %d 章加入下载队列`)
  - state descriptions `manga_download_state_queued|running|done|failed` (Dalam antrean, Mengunduh, Sudah diunduh, Gagal diunduh / Queued, Downloading, Downloaded, Download failed / Dalam baris gilir, Memuat turun, Sudah dimuat turun, Gagal dimuat turun / 排队中, 下载中, 已下载, 下载失败)

- [ ] **Step 1: ViewModel.** Pass `c.mangaDownloads` to `MangaDetailViewModel`. Add `downloads: StateFlow<Map<String, MangaDownloadEntity>>`, `fun download(chapters: List<MangaChapter>, label: (MangaChapter) -> String, onQueued: (Int) -> Unit)` and `fun deleteDownload(chapterId: String)`.
- [ ] **Step 2: Cell.** `ChapterCell` gains `download: MangaDownloadEntity?`, `onDownload` and `onDelete`.
  - **Status mark (14 dp, beside the date):** `Icons.Rounded.Download` for queued, a `CircularProgressIndicator(progress = pagesDone/pageCount)` for running, `Icons.Rounded.DownloadDone` for done, `Icons.Rounded.ErrorOutline` for failed. Each carries the state description.
  - **Long-press:** the `Surface` uses `combinedClickable(onClick, onLongClick = { menuOpen = true })` and anchors a `DropdownMenu`. It shows **Unduh** when the row is null or FAILED, otherwise **Hapus unduhan**. `semantics { customActions = listOf(CustomAccessibilityAction(label) { …; true }) }` exposes the same item to TalkBack.
- [ ] **Step 3: Range button.** In the range-chips row (or alone above the grid when there are no ranges), add a `TextButton`:
  - label `manga_download_range` with `rangeLabel(...)`, or `manga_download_all` when there are no ranges
  - it calls `download(shown, …)`
  - when the count is above 0, it shows a snackbar with `manga_download_queued`; the detail route hosts a `SnackbarHostState`, adding one if absent
- [ ] **Step 4: Commit** `feat(manga): download a chapter or a whole range from the manga page`.

### Task 4: Reader reads saved pages and works offline

**Files:**
- Modify: `app/src/main/java/com/fuwanime/ui/manga/MangaReaderScreen.kt` (`MangaReaderViewModel`), and its `fuwaViewModel` factory, which passes `c.mangaDownloads`

**Interfaces:**
- Consumes: `localPages(chapterId)`, `offline(mangaId)`, `row(chapterId)`.

- [ ] **Step 1:** In `open`:
  - `pagesJob` returns `downloads.localPages(chapterId) ?: repo.pages(chapterId, dataSaver)`, still wrapped in `runCatching`.
  - Wrap the manga and chapter loading in `try`. On a non-cancellation exception, call `val row = downloads.row(chapterId)`. If `row?.state == DONE`, use:
    - `manga = Manga(row.mangaId, row.mangaTitle, row.coverUrl, "", emptyList(), MangaStatus.UNKNOWN, null, null)`
    - `chapters = downloads.offline(mangaId)`

    Otherwise rethrow, which keeps the existing error state.
- [ ] **Step 2: Commit** `feat(manga): read downloaded chapters offline`.

### Task 5: Library "Unduhan" section

**Files:**
- Modify: `app/src/main/java/com/fuwanime/ui/manga/MangaLibraryTab.kt`
- Modify: `res/values*/strings_manga.xml`
- Test: `app/src/test/java/com/fuwanime/MangaDownloadLogicTest.kt`

**Interfaces:**
- Consumes: `MangaDownloadRepository.all`, `.deleteFor`.
- Produces:
  - `data class DownloadedManga(val mangaId: String, val title: String, val coverUrl: String?, val done: Int, val pending: Int, val bytes: Long)`
  - `fun groupDownloads(rows: List<MangaDownloadEntity>): List<DownloadedManga>`, newest group first by max `createdAt`
  - strings:
    - `manga_library_downloads` (Unduhan / Downloads / Muat turun / 下载)
    - plural `manga_download_count` (`%d chapter` / `%d chapters` / `%d bab` / `%d 章`)
    - plural `manga_download_pending` (`%d dalam antrean` / `%d queued` / `%d dalam baris gilir` / `%d 个排队中`)
    - `manga_download_delete_all_title` (`Hapus semua unduhan %1$s?` / `Delete all downloads of %1$s?` / `Padam semua muat turun %1$s?` / `删除《%1$s》的全部下载?`)
    - `manga_download_delete_all` (Hapus / Delete / Padam / 删除)

- [ ] **Step 1: Failing test:**

```kotlin
@Test fun groupDownloadsCountsDoneAndPending() {
    val g = groupDownloads(listOf(row("a", "DONE", m = "m1", bytes = 10), row("b", "QUEUED", m = "m1"), row("c", "DONE", m = "m2", bytes = 5)))
    assertEquals(listOf("m1", "m2").toSet(), g.map { it.mangaId }.toSet())
    assertEquals(DownloadedManga("m1", "T", null, 1, 1, 10), g.first { it.mangaId == "m1" })
}
```

(`row` gains `m` and `bytes` parameters.)
- [ ] **Step 2: Implement** `groupDownloads` (in `MangaDownloads.kt`).
  - `MangaShelfViewModel` gets `downloads: StateFlow<List<DownloadedManga>?>` and `deleteDownloads(mangaId)`.
  - The empty-state check counts downloads too.
  - The section sits after Favorites and uses `ShelfRow`. Its subtitle joins `manga_download_count` with `Formatter.formatShortFileSize(context, bytes)` using " · ", plus `manga_download_pending` when pending > 0.
  - A tap calls `onOpen(mangaId)`. A trailing delete icon opens an `AlertDialog` that confirms with `deleteFor`.
- [ ] **Step 3: Commit** `feat(manga): downloads section in the library`.

### Task 6: Verify and ship

- [ ] **Step 1:** Run `ci-test.sh`. Expected: `CI PASSED`. On failure, read `gh run view <id> -R SakamotoMrX/fuwanime --log-failed`, fix it, and re-run.
- [ ] **Step 2:** Final whole-branch review by a fresh reviewer on the most capable model. Fix Critical and Important findings with tests, then re-run CI.
- [ ] **Step 3:** Open a PR on SakamotoMrX/fuwanime and merge it. Then push `main` to `sync/manga-download`, open a PR to fuwanime/fuwastream and merge it. Download the release APK into `/home/sakamoto/Data/Github/fuwanime-builds`.
