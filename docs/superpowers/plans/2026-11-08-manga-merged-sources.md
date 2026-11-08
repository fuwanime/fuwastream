# Merged Manga Sources, Language Switch and Type Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One manga shows chapters from MangaDex, Shinigami and Komiku together, picking the better source per chapter. It gets per-title ID/EN/Raw chips, and a Manga/Manhwa/Manhua filter on the Manga tab.

**Architecture:**
- Pure functions in `MangaModels.kt` decide language groups and the per-chapter merge.
- `MangaRepository` finds the same title on the other sources and caches the combined chapter list per opened id.
- The detail and reader screens filter that list by the chosen language.
- Type filtering is passed through as a server-side parameter to each source.

**Tech Stack:** Kotlin 2.2, Compose Material3, Retrofit + Moshi, OkHttp, coroutines, JUnit 4 + MockWebServer.

**Spec:** `docs/superpowers/specs/2026-11-08-manga-merged-sources-design.md`

## Global Constraints

- Free sources only; everything is read in the app; no new dependencies; no Room schema change.
- Source priority for the same chapter number and language: Shinigami (0) > Komiku (1) > MangaDex (2), after "readable first".
- Raw = MangaDex `ja`, `ko`, `zh`, `zh-hk`. The feed fetch uses `FEED_LANGS = id, en, ja, ko, zh, zh-hk`; catalog browsing keeps `CATALOG_LANGS = id, en`.
- Other-source lookups run in parallel, each inside `withTimeoutOrNull(8_000)` + `runCatching`; failures are dropped silently.
- Mature content stays behind `manga_mature` (unchanged).
- New strings in all 4 locales (`values`, `values-en`, `values-ms`, `values-zh-rCN`).
- Commits carry no Claude co-author lines. Verification is CI-only: `bash /home/sakamoto/Data/Github/fuwanime-quick-wins/.superpowers/quick-wins/ci-test.sh` (unit tests + release build). There is no local Gradle.

## Ruling against the spec

- The spec says delete `pickChapters`, but `ShinigamiSource.chapters` and `KomikuSource.chapters` still use it to de-duplicate inside one source.
- Keep it. Only MangaDex `chapters(id)` stops calling it.
- `chapterLanguageFor` becomes unused and is deleted along with its test.

## Review Focus

1. **Oneshots (no chapter number) present in two sources.** A reasonable person expects them once, not twice. Rule: null-number chapters come only from the best-ranked source that has any. Test in Task 1.
2. **History points at a chapter that is not in the chosen language list.** "Lanjut" should fall back to the first readable chapter, not crash or show nothing. This is already covered by `resumeTargetFallsBackWhenChapterGone`, and the detail must call `resumeTarget` on the shown list (Task 3).
3. **The chosen language disappears after a retry** (a source dropped out). Expected: fall back to the default language. Covered by the `effectiveLanguage` test in Task 1.
4. **The reader opens a chapter id that is not in the merged list** (an old history entry). Expected: the reader still opens it and uses the default language for Prev/Next. Covered by the `readerLanguage` test in Task 1.
5. **Search returns the same title from Shinigami and Komiku.** Expected: one entry, the first one. Covered by the `dedupeByTitle` test in Task 5.

---

### Task 1: Pure language and merge logic

**Files:**
- Modify: `app/src/main/java/com/fuwanime/data/manga/MangaModels.kt` (add below `pickChapters`; delete `chapterLanguageFor`)
- Test: `app/src/test/java/com/fuwanime/MangaMappingTest.kt` (delete the `chapterLanguageFor` assertions at lines ~103-104)

**Interfaces:**
- Produces:
  - `enum class ChapterLang { ID, EN, RAW }`
  - `fun languageGroup(language: String): ChapterLang`
  - `fun sourceRank(chapterId: String): Int`: `sh:` → 0, `km:` → 1, else 2
  - `fun availableLanguages(chapters: List<MangaChapter>): List<ChapterLang>`: readable only, order ID, EN, RAW
  - `fun defaultLanguage(available: List<ChapterLang>, appLanguage: String): ChapterLang?`
  - `fun effectiveLanguage(chosen: ChapterLang?, available: List<ChapterLang>, appLanguage: String): ChapterLang?`: `chosen` if it is in `available`, else `defaultLanguage`
  - `fun mergeChapters(all: List<MangaChapter>, lang: ChapterLang): List<MangaChapter>`
  - `fun readerLanguage(all: List<MangaChapter>, chapterId: String, appLanguage: String): ChapterLang?`: the group of that chapter if it is found, else `defaultLanguage(availableLanguages(all), appLanguage)`

- [ ] **Step 1: Write the failing tests** in `MangaMappingTest` (helper `ch(id, n, lang, at, pages, ext)` already exists):

```kotlin
@Test fun mergePrefersShinigamiThenKomikuThenMangaDex() {
    val r = mergeChapters(listOf(ch("md1", "1", "id"), ch("km:1", "1", "id"), ch("sh:1", "1", "id"), ch("km:2", "2", "id"), ch("md2", "2", "id")), ChapterLang.ID)
    assertEquals(listOf("sh:1", "km:2"), r.map { it.id })
}
@Test fun mergePrefersReadableOverBetterSource() {
    val r = mergeChapters(listOf(ch("sh:1", "1", "id", pages = 0), ch("md1", "1", "id")), ChapterLang.ID)
    assertEquals(listOf("md1"), r.map { it.id })
}
@Test fun mergeTreatsEquivalentNumbersAsOne() =
    assertEquals(1, mergeChapters(listOf(ch("a", "5", "id"), ch("sh:b", "5.0", "id"), ch("km:c", "05", "id")), ChapterLang.ID).size)
@Test fun mergeKeepsOnlyRequestedLanguageInOrder() {
    val r = mergeChapters(listOf(ch("e10", "10", "en"), ch("i1", "1", "id"), ch("e2", "2", "en"), ch("o", null, "en", at = 5), ch("j1", "1", "ja")), ChapterLang.EN)
    assertEquals(listOf("e2", "e10", "o"), r.map { it.id })
}
@Test fun mergeTakesOneshotsFromBestSourceOnly() {
    val r = mergeChapters(listOf(ch("sh:o", null, "id", at = 1), ch("md-o", null, "id", at = 2), ch("md1", "1", "id")), ChapterLang.ID)
    assertEquals(listOf("md1", "sh:o"), r.map { it.id })
}
@Test fun languageGroups() {
    assertEquals(ChapterLang.ID, languageGroup("id"))
    listOf("en", "pt-br").forEach { assertEquals(ChapterLang.EN, languageGroup(it)) }
    listOf("ja", "ko", "zh", "zh-hk").forEach { assertEquals(ChapterLang.RAW, languageGroup(it)) }
}
@Test fun availableLanguagesIgnoreUnreadable() =
    assertEquals(listOf(ChapterLang.ID), availableLanguages(listOf(ch("a", "1", "id"), ch("b", "1", "en", ext = "https://x"), ch("c", "1", "ja", pages = 0))))
@Test fun defaultLanguageFollowsAppThenFallsBack() {
    assertEquals(ChapterLang.ID, defaultLanguage(listOf(ChapterLang.ID, ChapterLang.EN), "id"))
    assertEquals(ChapterLang.EN, defaultLanguage(listOf(ChapterLang.ID, ChapterLang.EN), "en"))
    assertEquals(ChapterLang.RAW, defaultLanguage(listOf(ChapterLang.RAW), "id"))
    assertNull(defaultLanguage(emptyList(), "id"))
}
@Test fun effectiveLanguageFallsBackWhenChosenGone() {
    assertEquals(ChapterLang.EN, effectiveLanguage(ChapterLang.EN, listOf(ChapterLang.ID, ChapterLang.EN), "id"))
    assertEquals(ChapterLang.ID, effectiveLanguage(ChapterLang.RAW, listOf(ChapterLang.ID, ChapterLang.EN), "id"))
}
@Test fun readerLanguageUsesOpenedChapterElseDefault() {
    val all = listOf(ch("a", "1", "id"), ch("b", "1", "en"))
    assertEquals(ChapterLang.EN, readerLanguage(all, "b", "id"))
    assertEquals(ChapterLang.ID, readerLanguage(all, "gone", "id"))
}
```

- [ ] **Step 2: Implement** the Produces list in `MangaModels.kt`.
  - **Grouping key:** `number?.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: number`.
  - **Within a group:** take readable chapters (`externalUrl == null && pages > 0`) if there are any, then the minimum `sourceRank`, then the maximum `publishAt`.
  - **Null-number chapters:** keep only those whose `sourceRank` equals the best rank among null-number chapters in `lang`.
  - **Final order:** reuse `pickChapters`' ordering, i.e. parsable numbers ascending, then odd numbers and oneshots by `publishAt`.
  - Delete `chapterLanguageFor` and its test assertions.
- [ ] **Step 3: Commit** `feat(manga): merge chapters across sources by language`.

### Task 2: Repository finds the title everywhere and merges chapters

**Files:**
- Modify: `app/src/main/java/com/fuwanime/data/manga/MangaRepository.kt`
- Test: `app/src/test/java/com/fuwanime/MangaRepositoryTest.kt`

**Interfaces:**
- Consumes: `sourceRank`, `sameTitle(title, other, alternatives)` (ShinigamiApi.kt), `ShinigamiSource.find(names)`, `KomikuSource.find(names)`
- Produces:
  - `suspend fun findOnMangaDex(names: List<String>): Manga?`: the first 3 non-blank distinct names; `browse(RELEVANCE, name)` → first item where `sameTitle(name, it.title, it.altTitles)`
  - `suspend fun sources(manga: Manga): List<Manga>`: `manga` first, then matches from the other two sources (never the opened source itself), cached in `ConcurrentHashMap<String, List<Manga>>` by `manga.id`
  - `suspend fun mergedChapters(manga: Manga): List<MangaChapter>`: all chapters of all `sources(manga)`, loaded in parallel, every language, cached by `manga.id`. If every source throws, rethrow the first error.
  - `fun clearMerged(mangaId: String)`: drops both caches for that id
  - Remove `findOnShinigami`.
  - MangaDex `chapters(id)` returns every language (no `pickChapters`), feed `langs = FEED_LANGS`.

- [ ] **Step 1: Write the failing tests** (MockWebServer; the existing `repo` has no Shinigami/Komiku):

```kotlin
@Test fun chaptersRequestRawLanguages() = runBlocking {
    server.enqueue(json("feed.json"))  // reuse the existing enqueue helper
    repo.chapters("m1")
    val path = server.takeRequest().path!!
    listOf("id", "en", "ja", "ko", "zh", "zh-hk").forEach { assertTrue(path, path.contains("translatedLanguage%5B%5D=$it")) }
}
@Test fun mergedChaptersKeepEveryLanguageAndCache() = runBlocking {
    // enqueue one feed page holding an "id" and an "en" chapter for number 1
    val m = Manga("m1", "T", null, "", emptyList(), MangaStatus.ONGOING, null, null)
    assertEquals(2, repo.mergedChapters(m).size)
    repo.mergedChapters(m)
    assertEquals(1, server.requestCount)
}
```

- [ ] **Step 2: Implement** the Produces list with `coroutineScope { async }`, `withTimeoutOrNull(8_000)` and `runCatching` per lookup. Update `chaptersPagesThroughFeed` if its expected ids change once `pickChapters` is gone; the expected list becomes every chapter in the fixture.
- [ ] **Step 3: Commit** `feat(manga): find a title on every source and merge its chapters`.

### Task 3: Detail page language chips and merged list

**Files:**
- Modify: `app/src/main/java/com/fuwanime/ui/manga/MangaDetailScreen.kt`
- Modify: `res/values*/strings_manga.xml` (4 locales)

**Interfaces:**
- Consumes: `repo.mergedChapters(manga)`, `repo.clearMerged(id)`, `availableLanguages`, `effectiveLanguage`, `mergeChapters`, `ChapterLang`
- Produces: strings `manga_lang_id` (Indonesia / Indonesian / Indonesia / 印尼语), `manga_lang_en` (Inggris / English / Inggeris / 英语), `manga_lang_raw` (Raw in all four), `manga_credit_merged` (`Data &amp; gambar dari %1$s` / `Data &amp; images from %1$s` / `Data &amp; gambar daripada %1$s` / `数据和图片来自 %1$s`)

- [ ] **Step 1: ViewModel.**
  - `load()` calls `repo.mergedChapters(manga)` and no longer does the `findOnShinigami`/`readFrom` branch; remove `readFrom` from both state classes.
  - The retry path (`load()` called again) first calls `repo.clearMerged(id)`.
- [ ] **Step 2: UI.**
  - `var chosen by rememberSaveable(mangaId) { mutableStateOf<ChapterLang?>(null) }`, `val available = availableLanguages(all)`, `val lang = effectiveLanguage(chosen, available, Locale.getDefault().language)`.
  - `inApp = lang?.let { mergeChapters(all, it) }.orEmpty()`, readable only, as today.
  - `target = resumeTarget(inApp, history)`.
  - When `available.size >= 2`, a `LazyRow` of `TagChip`s goes above the range chips. Selecting a chip sets `chosen` and `picked = -1`.
  - `readId` is always `mangaId`.
- [ ] **Step 3: Source label and credit.**
  - Sources are the distinct `sourceRank` values among `all`, sorted, mapped 0 → "Shinigami", 1 → "Komiku", 2 → "MangaDex", and joined with " + ".
  - The InfoTable gets that string.
  - The credit uses `manga_credit_merged` with the same string when there is more than one source, else the existing per-source strings.
- [ ] **Step 4: Commit** `feat(manga): one chapter list per title with ID/EN/Raw chips`.

### Task 4: Reader follows the merged list in the opened language

**Files:**
- Modify: `app/src/main/java/com/fuwanime/ui/manga/MangaReaderScreen.kt` (`MangaReaderViewModel.open`)

**Interfaces:**
- Consumes: `repo.mergedChapters(manga)`, `readerLanguage`, `mergeChapters`

- [ ] **Step 1:** In `open`, replace `repo.chapters(mangaId)` with:
  - `val all = repo.mergedChapters(manga)`
  - `val lang = readerLanguage(all, chapterId, Locale.getDefault().language)`
  - `val chapters = lang?.let { mergeChapters(all, it) } ?: all`

  `state.chapters` keeps the filtered list, so Prev/Next, the picker and the end card follow it. `readerChapter(chapters, chapterId, savedLabel)` still runs on that list, and its fallback covers ids that are missing from it.
- [ ] **Step 2: Commit** `feat(manga): reader stays in the language being read`.

### Task 5: One entry per title in search results

**Files:**
- Modify: `app/src/main/java/com/fuwanime/ui/manga/MangaExplore.kt`
- Test: `app/src/test/java/com/fuwanime/MangaUiLogicTest.kt`

**Interfaces:**
- Produces: `fun dedupeByTitle(list: List<Manga>): List<Manga>`. It keeps the first entry. A later entry is dropped when `sameTitle(kept.title, it.title, it.altTitles)` or `sameTitle(it.title, kept.title, kept.altTitles)`.

- [ ] **Step 1: Failing tests:**

```kotlin
private fun m(id: String, title: String, alt: List<String> = emptyList()) = Manga(id, title, null, "", emptyList(), MangaStatus.ONGOING, null, null, alt)
@Test fun dedupeKeepsFirstOfSameTitle() =
    assertEquals(listOf("sh:1", "x"), dedupeByTitle(listOf(m("sh:1", "Solo Leveling"), m("km:1", "solo leveling"), m("x", "Berserk"))).map { it.id })
@Test fun dedupeMatchesAltTitles() =
    assertEquals(listOf("a"), dedupeByTitle(listOf(m("a", "Ore dake Level Up na Ken"), m("b", "Solo Leveling", listOf("Ore dake Level Up na Ken")))).map { it.id })
```

- [ ] **Step 2: Implement** the function, and use it where `loadFirst` builds `results` and where `loadMore` appends (on `old + page.items`).
- [ ] **Step 3: Commit** `feat(manga): one search result per title across sources`.

### Task 6: Manga / Manhwa / Manhua filter

**Files:**
- Modify: `data/manga/MangaModels.kt` (enum), `MangaDexApi.kt`, `ShinigamiApi.kt` (`ShinigamiApi.list`, `ShinigamiSource.browse`), `KomikuSource.kt` (`browse`), `MangaRepository.kt` (`browse`, `popularIndonesia`, `popularKomiku`)
- Modify: `ui/manga/MangaExplore.kt` (state, filter bar, `loadFirst`), `ui/manga/MangaTabScreen.kt` (tiles)
- Test: `MangaMappingTest.kt`, `MangaRepositoryTest.kt`

**Interfaces:**
- Produces:
  - `enum class MangaType(val label: String, val mangaDex: List<String>, val slug: String)`: `MANGA("Manga", listOf("ja"), "manga")`, `MANHWA("Manhwa", listOf("ko"), "manhwa")`, `MANHUA("Manhua", listOf("zh", "zh-hk"), "manhua")`
  - `MangaDexApi.mangaList(…, @Query("originalLanguage[]") originalLanguages: List<String> = emptyList())`
  - `ShinigamiApi.list(…, @Query("format") format: String? = null)`, `ShinigamiSource.browse(sort, query, page, format: String? = null)`
  - `KomikuSource.browse(order, page, tipe: String? = null)`: adds `&tipe=$tipe` when set
  - `MangaRepository.browse(…, type: MangaType? = null)`, `popularIndonesia(page, type: MangaType? = null)`, `popularKomiku(page, type: MangaType? = null)`
  - `MangaExploreState.type: MangaType?` (part of `filtered`), `MangaExploreViewModel.setType(t: MangaType?)`

- [ ] **Step 1: Failing tests:**

```kotlin
@Test fun mangaTypeMapsToSources() {
    assertEquals(listOf("ko"), MangaType.MANHWA.mangaDex)
    assertEquals(listOf("zh", "zh-hk"), MangaType.MANHUA.mangaDex)
    assertEquals("manhua", MangaType.MANHUA.slug)
}
// MangaRepositoryTest
@Test fun browseSendsOriginalLanguageForType() = runBlocking {
    server.enqueue(json("manga_list.json"))
    repo.browse(MangaOrder.POPULAR, type = MangaType.MANHWA)
    assertTrue(server.takeRequest().path!!.contains("originalLanguage%5B%5D=ko"))
}
```

- [ ] **Step 2: Implement** the source parameters and `MangaType`.
- [ ] **Step 3: Explore.**
  - Include `type` in the `filters` flow and in `applied`.
  - In `loadFirst`, when `type != null && tags.isEmpty() && status == null`, add Indonesian results before MangaDex: `popularIndonesia(1, type).items + popularKomiku(1, type).items` (each in `runCatching`), plus `searchIndonesia(q)` filtered to `it.type equalsIgnoreCase type.label` when `q` is not blank.
  - Pass `type` to `repo.browse` (in `loadMore` too).
  - Dedupe with `dedupeByTitle`.
  - `clearFilters` resets `type`.
- [ ] **Step 4: UI.**
  - `MangaFilterBar`: three `FilterChip`s labelled `MangaType.label` after the status chips. A tap on the selected chip clears it.
  - `MangaTabScreen.GenreTiles`: three tiles (Manhwa, Manhua, Manga) first, calling `exploreVm.setType`, with the label as text. These tiles show even if the tag list is empty.
- [ ] **Step 5: Commit** `feat(manga): Manga / Manhwa / Manhua filter across sources`.

### Task 7: Verify and ship

- [ ] **Step 1:** Run `bash /home/sakamoto/Data/Github/fuwanime-quick-wins/.superpowers/quick-wins/ci-test.sh`. Expected: `CI PASSED`. On a failure, read the job log (`gh run view <id> -R SakamotoMrX/fuwanime --log-failed`), fix it, and re-run.
- [ ] **Step 2:** Final whole-branch review (fresh reviewer), fix Critical/Important issues, re-run CI.
- [ ] **Step 3:** Open the PR on SakamotoMrX/fuwanime and merge it. Then push `main` to `sync/manga-merged-sources`, open a PR to fuwanime/fuwastream and merge it. Download the release APK into `/home/sakamoto/Data/Github/fuwanime-builds`.
