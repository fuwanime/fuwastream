# Manga: one title across sources, with a language switch

Date: 2026-11-08 · Branch: `feat/manga-merged-sources`

## Intent

The user sees the same manga several times, once per source (MangaDex, Shinigami, Komiku), each with its own partial chapter list. They want:

1. **One manga, all sources.** Opening a title shows a single chapter list built from all three sources. When a chapter exists in several sources, the better source is used for it.
2. **A language switch** on that title: Indonesian, English, or Raw.

What they said: "Bsa switch ID/EN atw Raw", "3 sumber jadi 1 manga (tiap chapter bisa prefer ke sumber yang lebih baik)". Decisions they made in chat: the switch is **per manga, on the detail page**; source priority is **Shinigami > Komiku > MangaDex**.

Success: opening any entry of a title (from any source) shows the same merged chapter list; language chips list only languages that title has; reading, Prev/Next, the chapter picker, progress and history keep working across chapters from different sources.

## Constraints

- Free sources only (no paid APIs), everything read in the app.
- Mature content stays behind the existing `manga_mature` toggle (MangaDex `contentRating` already respects it).
- No new dependencies. No database schema change.
- Raw is only available from MangaDex uploads in `ja`, `ko`, `zh`, `zh-hk`; Shinigami and Komiku are Indonesian only. Raw will be empty for most titles, so its chip only shows when such chapters exist.

## Design

### 1. Finding the same title on the other sources

`MangaRepository.sources(manga: Manga): List<Manga>` returns the opened entry plus its matches on the other two sources.

- Matching uses the existing exact normalized comparison `sameTitle` over `manga.title + manga.altTitles` (first 3 names), as `ShinigamiSource.find` and `KomikuSource.find` already do.
- MangaDex has no `find` yet: add `MangaRepository.findOnMangaDex(names)`. It searches `browse(RELEVANCE, name, …)` for each name and keeps the first result whose title or alt titles match with `sameTitle`.
- The two lookups run in parallel (`coroutineScope { async }`), each wrapped in `withTimeoutOrNull(8_000)` and `runCatching`. A source that fails, times out, or is switched off is left out; the opened entry is always kept.
- Results are cached in memory per opened manga id (`HashMap<String, List<Manga>>` in the repository) so the reader reuses them.

### 2. One chapter list

`MangaRepository.mergedChapters(manga: Manga): List<MangaChapter>` loads `chapters(id)` for every entry from `sources(manga)` in parallel. A failing source is skipped; if all fail, the first error propagates (shows the existing chapter error state). The result is cached per opened manga id.

Language and source travel on what already exists: `MangaChapter.language`, and the source in the id prefix (`sh:`, `km:`, none = MangaDex).

Pure functions in `MangaModels.kt`, unit tested:

- `languageGroup(language: String): ChapterLang`. An enum `ChapterLang { ID, EN, RAW }`: `id` → ID, `ja|ko|zh|zh-hk` → RAW, anything else → EN.
- `availableLanguages(chapters): List<ChapterLang>`, in the order ID, EN, RAW, counting readable chapters only (`externalUrl == null && pages > 0`).
- `defaultLanguage(available, appLanguage: String): ChapterLang?`. ID when the app language is `id` and ID is available, else EN if available, else the first available.
- `mergeChapters(all: List<MangaChapter>, lang: ChapterLang): List<MangaChapter>`. Keep chapters in `lang`. Group by chapter number, normalized (`toBigDecimalOrNull()?.stripTrailingZeros()`, so "5", "5.0" and "05" are one chapter); unparsable numbers group by their raw string. Within a group take readable before unreadable, then source priority Shinigami (0) > Komiku (1) > MangaDex (2), then newest `publishAt`. Order the result like `pickChapters` does: numeric ascending, then unparsable numbers and oneshots by `publishAt`.

MangaDex's own feed fetch adds the raw languages: the feed asks for `id, en, ja, ko, zh, zh-hk` (`FEED_LANGS`), while catalog browsing keeps `CATALOG_LANGS = id, en`. `chapters(id)` for MangaDex then returns every language, not `pickChapters` for one language, because language picking moves to `mergeChapters`. `pickChapters` is deleted together with its tests, since `mergeChapters` covers them.

### 3. Detail page

- `MangaDetailViewModel` loads `repo.mergedChapters(manga)` instead of `chapters(id)`, plus the `findOnShinigami`/`readFrom` fallback, which is removed along with `MangaDetailState.readFrom`.
- State keeps all merged-source chapters and the chosen `ChapterLang` (`rememberSaveable` per manga id, default from `defaultLanguage`). The shown list is `mergeChapters(all, lang)`.
- A row of `TagChip`s `ID · EN · Raw` sits above the range chips and shows only when there are two or more available languages. Labels are new strings `manga_lang_id` ("Indonesia" / "Indonesian" / "Indonesia" / "印尼语"), `manga_lang_en` ("Inggris" / "English" / "Inggeris" / "英语"), `manga_lang_raw` ("Raw" in all four).
- Switching language resets the picked range.
- The "Lanjut" target uses the history's last chapter if it is in the shown list, else the first readable chapter of the shown list (the existing `resumeTarget` on the shown list).
- Info table "Source": every source that contributed chapters, in priority order, joined with " + ", e.g. `Shinigami + Komiku + MangaDex`.
- Credit line at the bottom: the existing per-source credit strings, or a new `manga_credit_merged` "Data & gambar dari %1$s" when more than one source contributed (in 4 locales).
- `onRead(readId, chapterId)` always passes the opened manga id, so history stays under one title.

### 4. Reader

- `MangaReaderViewModel.open` uses `repo.mergedChapters(manga)` (cached, so no second search) and filters with `mergeChapters(all, languageGroup(chapter.language))`, where `chapter` is the requested chapter found in the full list. Prev/Next, the chapter picker and the end card therefore stay in the language being read.
- `repo.pages(chapterId)` already routes by id prefix; nothing changes there.
- `Credit` already shows the source per chapter.

### 5. Search results

In `MangaExploreViewModel`, merged search results drop later entries whose title `sameTitle`-matches an earlier one, comparing title and alt titles. Indonesian results come first, so they are the kept entry. This also runs when appending MangaDex pages in `loadMore`. Home shelves are unchanged.

## Error handling

- Opened source fails on detail: the existing detail error state, unchanged.
- Other-source lookups fail or time out: silently left out, as `findOnShinigami` does today.
- All chapter sources fail: the existing `chaptersError` state with retry; retry clears the cache for that manga.
- The chosen language has no chapters after a source drops out: fall back to `defaultLanguage`.

## Testing

Unit tests (JVM, `MangaUiLogicTest` / `MangaMappingTest`):

- `mergeChapters` prefers Shinigami over Komiku over MangaDex for the same number and language.
- `mergeChapters` prefers a readable MangaDex chapter over an unreadable Shinigami one.
- `mergeChapters` treats "5", "5.0" and "05" as one chapter.
- `mergeChapters` keeps only the requested language, and orders numerics ascending with oneshots last.
- `languageGroup` maps id/en/ja/ko/zh/zh-hk/pt-br correctly.
- `availableLanguages` ignores unreadable chapters; `defaultLanguage` follows the app language and falls back.
- Search dedup keeps the first of two `sameTitle` entries and keeps different titles.
- `findOnMangaDex` relies on `sameTitle`, which is already tested; no network tests.

Verification as usual: `ci-test.sh` (unit tests + release build). No emulator QA unless asked.

## Out of scope

- A stored cross-source title index, background syncing, or user-editable source priority.
- Merging entries on the home shelves.
- Per-chapter source switching by the user.
- Raw from sources other than MangaDex.
