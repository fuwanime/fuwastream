# Jembatan ID & metadata (sub-proyek C) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Link catalog anime to AniList/MAL lazily per Detail screen, then use the link for score/tags/airing countdown, AniSkip OP/ED/recap skipping, and add watch order, "Tonton selanjutnya", multi-genre Jelajah, "Acak", and a "Musim ini" Jadwal tab.

**Architecture:**
- New package `com.fuwanime.data.meta` contains two Retrofit APIs (AniList GraphQL, AniSkip), pure matching/segment logic, and `AnimeLinkRepository` backed by a new Room table `anime_link` (DB v4).
- UI pieces read it through the existing ViewModels.
- Watch order and "Tonton selanjutnya" use the catalog's own franchise list (`detailWithSeasons`), not AniList.

**Tech Stack:** Kotlin 2.2.10, Compose, Room 2.7 (KSP), Retrofit 2.12 + Moshi 1.15 (codegen), OkHttp 4.12, JUnit4 + MockWebServer.

**Spec:** `docs/superpowers/specs/2026-11-05-jembatan-id-design.md`

## Global Constraints

- No local Android toolchain.
  - Test gate: `bash /home/sakamoto/Data/Github/fuwanime-quick-wins/.superpowers/quick-wins/ci-test.sh` (testDebugUnitTest + assembleRelease with fatal NewApi lint).
  - QA: `gh workflow run build.yml --repo SakamotoMrX/fuwanime --ref feat/jembatan-id -f qa=true`.
- RED runs may be batched: stub production symbols with `TODO()` so tests compile and fail on assertions.
- minSdk 24. No `java.time`; use `Calendar`/`TimeZone`. Tests use `runBlocking` (no coroutines-test dependency).
- Endpoints and clients:
  - AniList endpoint `https://graphql.anilist.co` (POST JSON). AniSkip `https://api.aniskip.com/v2/skip-times/{malId}/{episode}`.
  - Both use `AppContainer.metaHttpClient` = `httpClient.newBuilder()` with interceptors cleared and 10 s connect/read timeouts.
- Room: DB version 4, `MIGRATION_3_4`, table `anime_link`, columns exactly as in the spec table.
- Cache rules:
  - `LINKED` meta refresh after 12 h or once `nextAiringAt` has passed.
  - `NONE` is retried after 7 days.
  - `UNLINKED` is never retried.
- Matching: title search with year tolerance 0, then up to 3 synonyms with tolerance 1, max 4 requests. Network error, 429 or 5xx store nothing.
- AniSkip query types: `op, ed, mixed-op, mixed-ed, recap`. Drop a result when `|episodeLength*1000 − durationMs| > 5000`.
- Copy (exact):
  - Detail footer and dialog: "Info dari AniList", "Bukan anime ini?", dialog "Sembunyikan info AniList dan lewati otomatis dari AniSkip untuk anime ini?" with buttons "Sembunyikan" / "Batal".
  - Season list: "Sedang dilihat".
  - Skip buttons: "Lewati OP", "Lewati ED", "Lewati recap", "+85 dtk".
  - Auto-skip chip: "Intro dilewati" + action "Batalkan".
  - End-of-season card: "Lanjut ke {label}: {judul}".
  - Jelajah: dice `contentDescription` "Anime acak".
  - Jadwal: tab "Musim ini", sections "Baru musim ini (N)" / "Lanjutan (N)". Season names "Musim Dingin" (Jan–Mar), "Musim Semi" (Apr–Jun), "Musim Panas" (Jul–Sep), "Musim Gugur" (Okt–Des).
- Enrichment never blocks the Detail render or the start of playback, and never shows a user-visible error.
- Commits: conventional style, no Claude co-author lines.

## Review Focus

1. **AniList slow or unreachable.** Detail must render immediately from the catalog, and no spinner may wait on enrichment. Pinned by `AnimeLinkRepositoryTest.networkErrorStoresNothingAndReturnsNull`. Task 4 also launches `link()` in a separate coroutine after the detail loads.
2. **Episode numbers that are not integers** ("12.5", "OVA", blank) must never call AniSkip. Pinned by `episodeNumberForSkip` tests (Task 6).
3. **Episode switch while an AniSkip request is in flight**: the old episode's segments must never show in the new episode. Pinned by keying segments by `episodeId` (`segmentsFor(episodeId)` in Task 6) plus a VM-level test of the cache key.
4. **"Bukan anime ini?" then reopening the anime**: no new search, and AniSkip is off for it. Pinned by `AnimeLinkRepositoryTest.unlinkedNeverSearches` and `malIdFor` returning null for UNLINKED (Task 3).
5. **"Musim ini" when some days fail to load**: show what loaded. Show the error only if all 7 failed. Pinned by `seasonTabState` tests (Task 9).

---

### Task 1: Meta HTTP clients and models

**Files:**
- Create: `app/src/main/java/com/fuwanime/data/meta/AniListApi.kt`
  - `interface AniListApi { @POST("/") suspend fun query(@Body body: GraphQlRequest): GraphQlResponse }`
  - DTOs `GraphQlRequest(query: String, variables: Map<String, Any?>)` and `GraphQlResponse(data: GraphQlData?)`
  - `GraphQlData(page: AniListPage? @Json(name="Page"), media: AniListMedia? @Json(name="Media"))`
  - `AniListPage(media: List<AniListMedia>)`
  - `AniListMedia(id: Int, idMal: Int?, format: String?, seasonYear: Int?, startDate: FuzzyDate?, title: AniListTitle, synonyms: List<String> = emptyList(), averageScore: Int?, tags: List<AniListTag> = emptyList(), nextAiringEpisode: AiringEpisode?)`
  - `FuzzyDate(year: Int?)`, `AniListTitle(romaji: String?, english: String?, native: String?)`, `AniListTag(name: String, rank: Int?, isMediaSpoiler: Boolean = false)`, `AiringEpisode(episode: Int, airingAt: Long)`
  - Query constants `SEARCH_QUERY` and `BY_ID_QUERY` with the spec's field list
  - `fun create(client: OkHttpClient, baseUrl: String = "https://graphql.anilist.co/"): AniListApi`
- Create: `app/src/main/java/com/fuwanime/data/meta/AniSkipApi.kt`
  - `interface AniSkipApi { @GET("v2/skip-times/{malId}/{episode}") suspend fun skipTimes(@Path("malId") malId: Int, @Path("episode") episode: Int, @Query("types[]") types: List<String>, @Query("episodeLength") episodeLength: Double): AniSkipResponse }`
  - `AniSkipResponse(found: Boolean, results: List<AniSkipResult> = emptyList())`, `AniSkipResult(interval: AniSkipInterval, skipType: String, episodeLength: Double)`, `AniSkipInterval(startTime: Double, endTime: Double)`
  - `val SKIP_TYPES = listOf("op","ed","mixed-op","mixed-ed","recap")`
  - `create(client, baseUrl = "https://api.aniskip.com/")`
- Modify: `app/src/main/java/com/fuwanime/FuwaApp.kt` (`AppContainer.metaHttpClient`, `aniListApi`, `aniSkipApi` by lazy)
- Test: `app/src/test/java/com/fuwanime/MetaApiTest.kt` (MockWebServer)

All DTOs are `@JsonClass(generateAdapter = true)`, matching the existing `ApiModels.kt` style.

**Interfaces:**
- Produces: `AniListApi`, `AniSkipApi`, and the DTO names above. `AniListApi.create(...)`, `AniSkipApi.create(...)`, `SEARCH_QUERY`, `BY_ID_QUERY`, `SKIP_TYPES`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun parsesAniListSearchPage() = runBlocking {
    server.enqueue(MockResponse().setBody(fixture("anilist_search.json"))) // 1 media: id 21459, idMal 31964, title romaji "Boku no Hero Academia", seasonYear 2016, averageScore 77, tags [{name "Super Power", rank 95, isMediaSpoiler false},{name "Twist", rank 60, isMediaSpoiler true}], nextAiringEpisode {episode 6, airingAt 1762500000}
    val r = api.query(GraphQlRequest(SEARCH_QUERY, mapOf("s" to "Boku no Hero Academia")))
    val m = r.data!!.page!!.media.single()
    assertEquals(21459, m.id); assertEquals(31964, m.idMal); assertEquals(2016, m.seasonYear)
    assertEquals(listOf("Super Power", "Twist"), m.tags.map { it.name }); assertTrue(m.tags[1].isMediaSpoiler)
    assertEquals(AiringEpisode(6, 1762500000), m.nextAiringEpisode)
    val sent = server.takeRequest(); assertEquals("POST", sent.method); assertTrue(sent.body.readUtf8().contains("\"s\":\"Boku no Hero Academia\""))
}
@Test fun parsesAniListById() // body {"data":{"Media":{...same media...}}} → r.data.media.id == 21459
@Test fun parsesAniSkip() = runBlocking {
    server.enqueue(MockResponse().setBody("""{"found":true,"results":[{"interval":{"startTime":84.5,"endTime":174.5},"skipType":"op","skipId":"x","episodeLength":1420.1}]}"""))
    val r = skip.skipTimes(31964, 1, SKIP_TYPES, 1420.0)
    assertTrue(r.found); assertEquals(84.5, r.results.single().interval.startTime, 0.0)
    val path = server.takeRequest().path!!
    assertTrue(path.startsWith("/v2/skip-times/31964/1?")); assertTrue(path.contains("types%5B%5D=mixed-op")); assertTrue(path.contains("episodeLength=1420.0"))
}
```

Fixtures go in `app/src/test/resources/anilist_search.json` and `anilist_by_id.json`. `fixture(name)` reads from the classpath.

- [ ] **Step 2: Stub both `create()` with `TODO()` (interfaces + DTOs compile); gate**

Expected: `CI FAILED` with MetaApiTest failures (`NotImplementedError`).

- [ ] **Step 3: Implement `create()` with `Retrofit.Builder().baseUrl(baseUrl).client(client).addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))`, then wire `AppContainer`**
- [ ] **Step 4: Gate** — Expected: MetaApiTest green.
- [ ] **Step 5: Commit** `feat(meta): AniList and AniSkip clients`

### Task 2: Matching, segments and cache rules (pure)

**Files:**
- Create: `app/src/main/java/com/fuwanime/data/meta/MetaLogic.kt`
- Test: `app/src/test/java/com/fuwanime/MetaLogicTest.kt`

**Interfaces:**
- Consumes: DTOs (Task 1).
- Produces:
  - `fun normTitle(s: String?): String`
  - `fun pickMatch(title: String, synonyms: List<String>, airedYear: Int?, candidates: List<AniListMedia>, yearTolerance: Int): AniListMedia?`
  - `fun synonymsOf(alternativeTitles: String): List<String>`, which splits on `,`/`;`, trims, and drops blanks
  - `fun airedYearOf(airedStart: String): Int?`
  - `enum class LinkState { LINKED, NONE, UNLINKED }`
  - `data class AnimeMeta(val score: Int?, val tags: List<String>, val nextEpisode: Int?, val nextAiringAt: Long?)`, with `nextAiringAt` in epoch **ms**
  - `fun metaOf(m: AniListMedia): AnimeMeta`, which takes non-spoiler tags sorted by rank descending (max 6) and multiplies `airingAt` by 1000
  - `fun needsRefresh(state: LinkState, fetchedAt: Long, nextAiringAt: Long?, nowMs: Long): Boolean`
  - `enum class SegmentKind { OP, ED, RECAP }`
  - `data class SkipSegment(val kind: SegmentKind, val startMs: Long, val endMs: Long)`
  - `fun skipSegments(results: List<AniSkipResult>, durationMs: Long): List<SkipSegment>`

- [ ] **Step 1: Write the failing tests**

```kotlin
private fun media(id: Int, romaji: String, year: Int?, english: String? = null, syn: List<String> = emptyList()) =
    AniListMedia(id, idMal = id + 1, format = "TV", seasonYear = year, startDate = FuzzyDate(year), title = AniListTitle(romaji, english, null), synonyms = syn, averageScore = null, nextAiringEpisode = null)

@Test fun normStripsAccentsPunctuationAndSeasonWords() = assertEquals("shingekinokyojin2", normTitle("Shingeki no Kyojin: Season 2"))
@Test fun exactTitleAndYear() = assertEquals(1, pickMatch("Jujutsu Kaisen", emptyList(), 2020, listOf(media(1, "Jujutsu Kaisen", 2020)), 0)?.id)
@Test fun matchesThroughSynonym() = assertEquals(2, pickMatch("Boku no Hero Academia S2", listOf("My Hero Academia Season 2"), 2017,
    listOf(media(2, "Boku no Hero Academia 2", 2017, english = "My Hero Academia Season 2")), 1)?.id)
@Test fun yearOffByOneRejectedAtZero() = assertNull(pickMatch("Kimetsu no Yaiba: Yuukaku-hen", emptyList(), 2021, listOf(media(3, "Kimetsu no Yaiba: Yuukaku-hen", 2022)), 0))
@Test fun yearOffByOneAcceptedAtOne() = assertEquals(3, pickMatch("Kimetsu no Yaiba: Yuukaku-hen", emptyList(), 2021, listOf(media(3, "Kimetsu no Yaiba: Yuukaku-hen", 2022)), 1)?.id)
@Test fun unknownYearMatchesOnTitle() = assertEquals(4, pickMatch("Mu Shen Ji", emptyList(), null, listOf(media(4, "Mu Shen Ji", null)), 0)?.id)
@Test fun candidateWithoutYearRejectedWhenYearKnown() = assertNull(pickMatch("Mu Shen Ji", emptyList(), 2024, listOf(media(4, "Mu Shen Ji", null)), 1))
@Test fun noCandidates() = assertNull(pickMatch("X", emptyList(), 2020, emptyList(), 1))
@Test fun firstMatchingCandidateWins() = assertEquals(5, pickMatch("Naruto", emptyList(), 2002, listOf(media(9, "Boruto", 2017), media(5, "NARUTO", 2002), media(6, "Naruto", 2002)), 0)?.id)

@Test fun synonymsSplit() = assertEquals(listOf("OP", "ワンピース", "One Piece"), synonymsOf("OP, ワンピース; One Piece,  ,"))
@Test fun airedYear() { assertEquals(1999, airedYearOf("1999-10-20")); assertNull(airedYearOf("")); assertNull(airedYearOf("unknown")) }

@Test fun metaKeepsSixNonSpoilerTagsByRank() // 8 tags (one spoiler, ranks shuffled) → 6 names, highest rank first, spoiler absent; airingAt 1762500000 → nextAiringAt 1762500000000

@Test fun refreshRules() {
    val h = 3_600_000L; val now = 1_000_000_000_000L
    assertFalse(needsRefresh(LinkState.LINKED, now - 11 * h, null, now)); assertTrue(needsRefresh(LinkState.LINKED, now - 13 * h, null, now))
    assertTrue(needsRefresh(LinkState.LINKED, now - h, now - 1, now))      // next episode already aired
    assertFalse(needsRefresh(LinkState.NONE, now - 6 * 24 * h, null, now)); assertTrue(needsRefresh(LinkState.NONE, now - 8 * 24 * h, null, now))
    assertFalse(needsRefresh(LinkState.UNLINKED, 0, null, now))
}

@Test fun segmentsMapKindsAndFilterLength() {
    fun r(type: String, s: Double, e: Double, len: Double) = AniSkipResult(AniSkipInterval(s, e), type, len)
    val segs = skipSegments(listOf(r("mixed-op", 80.0, 170.0, 1420.0), r("op", 10.0, 20.0, 1420.0), r("ed", 1300.0, 1390.0, 1400.0), r("recap", 0.0, 30.0, 1423.0), r("ed", 1330.0, 1400.0, 1422.0)), 1_420_000)
    assertEquals(listOf(SkipSegment(SegmentKind.RECAP, 0, 30_000), SkipSegment(SegmentKind.OP, 80_000, 170_000), SkipSegment(SegmentKind.ED, 1_330_000, 1_400_000)), segs)
}
@Test fun segmentsDropInvertedIntervals() = assertTrue(skipSegments(listOf(AniSkipResult(AniSkipInterval(50.0, 40.0), "op", 1420.0)), 1_420_000).isEmpty())
```

In `segmentsMapKindsAndFilterLength`, the first ED is dropped because its length differs by 20 s. The first OP-kind result in list order wins (`mixed-op` comes first). The output is sorted by start.

- [ ] **Step 2: Stub all with `TODO()`; gate** — Expected: MetaLogicTest failures only.
- [ ] **Step 3: Implement per the spec's `norm` rule**: NFKD via `java.text.Normalizer`; drop non-ASCII marks; lowercase; remove `\b(season|the|part|cour)\b`; remove `[^a-z0-9]`. Note that "native" titles normalise to empty and are ignored.
- [ ] **Step 4: Gate** — green.
- [ ] **Step 5: Commit** `feat(meta): title matching, AniSkip segments, cache rules`

### Task 3: Room v4 `anime_link` and AnimeLinkRepository

**Files:**
- Create: `app/src/main/java/com/fuwanime/data/local/AnimeLink.kt`
  - `@Entity(tableName = "anime_link") data class AnimeLinkEntity(@PrimaryKey val animeId: String, val state: String, val anilistId: Int?, val malId: Int?, val score: Int?, val tags: String, val nextEpisode: Int?, val nextAiringAt: Long?, val fetchedAt: Long)`
  - `@Dao interface AnimeLinkDao { @Query("SELECT * FROM anime_link WHERE animeId = :id") suspend fun get(id: String): AnimeLinkEntity?; @Upsert suspend fun put(e: AnimeLinkEntity); @Query("DELETE FROM anime_link") suspend fun clear() }`
  - `@Upsert` compiles to INSERT + UPDATE in Room, which works on minSdk 24.
- Modify: `app/src/main/java/com/fuwanime/data/local/FuwaDatabase.kt`
  - Add the entity, `version = 4`, and `animeLinkDao()`.
  - `MIGRATION_3_4`: `CREATE TABLE IF NOT EXISTS anime_link (animeId TEXT NOT NULL, state TEXT NOT NULL, anilistId INTEGER, malId INTEGER, score INTEGER, tags TEXT NOT NULL, nextEpisode INTEGER, nextAiringAt INTEGER, fetchedAt INTEGER NOT NULL, PRIMARY KEY(animeId))`
  - Add it to `addMigrations`.
- Modify: `app/src/main/java/com/fuwanime/data/LibraryRepository.kt` (`clearAll()` also calls `db.animeLinkDao().clear()`)
- Create: `app/src/main/java/com/fuwanime/data/meta/AnimeLinkRepository.kt`
- Modify: `FuwaApp.kt` (`AppContainer.animeLinkRepository`)
- Test: `app/src/test/java/com/fuwanime/AnimeLinkRepositoryTest.kt` (fake DAO in memory + MockWebServer AniList)

**Interfaces:**
- Consumes: Tasks 1–2.
- Produces: `class AnimeLinkRepository(private val dao: AnimeLinkDao, private val api: AniListApi, private val now: () -> Long = System::currentTimeMillis)` with:
  - `suspend fun link(anime: Anime): AnimeMeta?`: null when not LINKED or on any failure; never throws
  - `suspend fun unlink(animeId: String)`, which stores UNLINKED and clears the ids
  - `suspend fun malIdFor(animeId: String): Int?`, which reads only and returns null unless LINKED

- [ ] **Step 1: Write the failing tests** (fake `AnimeLinkDao` backed by a `MutableMap`; MockWebServer serving AniList JSON)

```kotlin
@Test fun linksOnTitleSearchAndStores()            // 1 response with exact match → meta returned; dao row LINKED, malId set; 1 request
@Test fun fallsBackToSynonymsWithTolerance()        // title search → no match; synonym search → year+1 match → LINKED; 2 requests
@Test fun storesNoneAfterAllSearchesMiss()          // anime with 3+ synonyms, 4 empty pages → NONE; exactly 4 requests (4th synonym never searched)
@Test fun networkErrorStoresNothingAndReturnsNull()  // server returns 503 → null, dao empty
@Test fun rateLimitStoresNothing()                   // 429 → null, dao empty
@Test fun unlinkedNeverSearches()                    // dao row UNLINKED → null, 0 requests; malIdFor → null
@Test fun freshNoneSkipsSearch()                     // NONE fetched 1 day ago → null, 0 requests
@Test fun staleLinkedRefreshesById()                 // LINKED fetched 13 h ago → 1 request using BY_ID_QUERY with "id": anilistId; dao row updated fetchedAt
@Test fun freshLinkedUsesCacheWithoutRequest()       // LINKED fetched 1 h ago → meta from dao, 0 requests
@Test fun refreshFailureStillReturnsCachedMeta()     // LINKED stale + 503 → cached meta returned, row unchanged
```

Each test builds `Anime(id = "a1", title = ..., alternativeTitles = ..., airedStart = "2017-04-01")`.

- [ ] **Step 2: Stub repository methods `TODO()`; gate** — Expected: AnimeLinkRepositoryTest failures.
- [ ] **Step 3: Implement**
  - Requests run inside `withContext(Dispatchers.IO)`, catching `Exception` (rethrow `CancellationException`).
  - Store tags with `"\n".join`.
  - `HttpException` 429/5xx and `IOException` are failures: they store nothing.
  - A response with `errors` and no `data` is also a failure.
- [ ] **Step 4: Gate** — green.
- [ ] **Step 5: Commit** `feat(meta): anime_link table (DB v4) and lazy AniList linking`

### Task 4: Detail — score, countdown, tags, AniList footer, watch order

**Files:**
- Create: `app/src/main/java/com/fuwanime/ui/detail/DetailMeta.kt` (pure: `airingLabel`, `scoreLabel`, `watchOrder`)
- Modify: `app/src/main/java/com/fuwanime/ui/detail/DetailViewModel.kt`:
  - `DetailUiState.meta: AnimeMeta?`
  - The constructor takes `links: AnimeLinkRepository`.
  - After the detail loads, `viewModelScope.launch { _state.update { it.copy(meta = links.link(anime)) } }`.
  - `fun unlinkMeta()`.
  - `seasons = watchOrder(anime, d.seasons)` replaces `relatedSeasons`.
- Modify: `app/src/main/java/com/fuwanime/ui/detail/DetailScreen.kt` (VM factory passes `c.animeLinkRepository`; chips row; tags; footer + dialog; "Sedang dilihat" card state)
- Test: `app/src/test/java/com/fuwanime/DetailMetaTest.kt`. Update `RelatedSeasonsTest.kt` if it covers `relatedSeasons`; keep `relatedSeasons` if other callers use it.

**Interfaces:**
- Consumes: `AnimeMeta`, `AnimeLinkRepository.link/unlink` (Task 3).
- Produces:
  - `fun airingLabel(episode: Int, airingAtMs: Long, nowMs: Long, tz: TimeZone = TimeZone.getDefault()): String`
  - `fun scoreLabel(score: Int?): String?`
  - `fun watchOrder(current: Anime, seasons: List<SeasonEntry>): List<SeasonEntry>`
  - `fun nextInFranchise(currentId: String, ordered: List<SeasonEntry>): SeasonEntry?`, used by Task 7

- [ ] **Step 1: Write the failing tests**

```kotlin
private val jakarta = TimeZone.getTimeZone("Asia/Jakarta")
@Test fun airingUnderAnHour() = assertEquals("Ep 6 · 45m", airingLabel(6, now + 45 * MIN, now, jakarta))
@Test fun airingUnderADay() = assertEquals("Ep 6 · 2j 14m", airingLabel(6, now + 134 * MIN, now, jakarta))
@Test fun airingLater() = assertEquals("Ep 6 · Sab 23.00", airingLabel(6, sat2300Jakarta, now, jakarta)) // sat2300Jakarta built with Calendar(jakarta)
@Test fun scoreFormatsOneDecimalWithComma() { assertEquals("★ 8,4", scoreLabel(84)); assertNull(scoreLabel(0)); assertNull(scoreLabel(null)) }
@Test fun watchOrderSortsByAiredStartIncludingCurrent() // seasons S2(2021-12-05), Movie(2020-10-16), S1(2019-04-06, = current) → [S1, Movie, S2]
@Test fun watchOrderPutsUndatedLastInSourceOrder()     // undated entries keep their relative order at the end
@Test fun watchOrderAddsCurrentWhenSourceOmitsIt()     // current not in list → inserted by its airedStart
@Test fun watchOrderEmptyWhenNoOtherEntry()            // only current → empty list
@Test fun nextInFranchiseIsFollowingEntry()             // [S1, Movie, S2], current Movie → S2; current S2 → null; unknown id → null
```

The "now" for `airingUnderADay` is any instant. For `airingLater`, `now` is a Wednesday 10:00 in Jakarta.

- [ ] **Step 2: Stub; gate** — Expected: DetailMetaTest failures.
- [ ] **Step 3: Implement logic + UI**
  - The chips sit in the existing info row after year/type, using the `TagChip` style from `ui/components`.
  - Tags go after the genres in the same flow row, non-clickable.
  - The footer is `labelSmall` muted text with a "Bukan anime ini?" `TextButton`, which opens an `AlertDialog` with the copy from Global Constraints. Confirm calls `vm.unlinkMeta()`.
  - The current entry's card is drawn with a "Sedang dilihat" badge and no click.
- [ ] **Step 4: Gate** — green.
- [ ] **Step 5: Commit** `feat(detail): AniList score, countdown, tags and watch order`

### Task 5: AniSkip in the player — skip actions

**Files:**
- Modify: `app/src/main/java/com/fuwanime/ui/player/SkipIntro.kt`:
  - add `data class SkipSegmentAction(val kind: SegmentKind, override val targetMs: Long) : SkipAction`
  - extend `skipActionAt(positionMs, durationMs, mark, jumpAhead = true, segments: List<SkipSegment> = emptyList())`
  - `shouldAutoSkip` also accepts `SkipSegmentAction(kind = OP)`
  - add `fun skipLabel(action: SkipAction): String`
  - add `fun episodeNumberForSkip(number: String): Int?` (a positive integer only)
- Test: `app/src/test/java/com/fuwanime/SkipIntroTest.kt` (extend)

**Interfaces:**
- Consumes: `SkipSegment`, `SegmentKind` (Task 2).
- Produces: the new `skipActionAt` params, `SkipSegmentAction`, `skipLabel`, `episodeNumberForSkip`.

- [ ] **Step 1: Write the failing tests**

```kotlin
private val op = SkipSegment(SegmentKind.OP, 80_000, 170_000); private val ed = SkipSegment(SegmentKind.ED, 1_300_000, 1_390_000)
@Test fun userMarkBeatsAniSkipOp() = assertEquals(SkipAction.SkipOp(60_000), skipActionAt(30_000, 1_420_000, OpMark(10_000, 60_000), segments = listOf(op)))
@Test fun userMarkIgnoresAniSkipOpEvenInsideIt() = assertNull(skipActionAt(100_000, 1_420_000, OpMark(10_000, 60_000), segments = listOf(op)))
@Test fun aniSkipOpWithoutMark() = assertEquals(SkipSegmentAction(SegmentKind.OP, 170_000), skipActionAt(100_000, 1_420_000, null, segments = listOf(op)))
@Test fun aniSkipEdWithMark() = assertEquals(SkipSegmentAction(SegmentKind.ED, 1_390_000), skipActionAt(1_310_000, 1_420_000, OpMark(10_000, 60_000), segments = listOf(ed)))
@Test fun lastSecondOfSegmentHidden() = assertNull(skipActionAt(169_500, 1_420_000, null, segments = listOf(op)))
@Test fun genericJumpOnlyWithoutAniSkipOp() {
    assertEquals(SkipAction.JumpAhead(95_000), skipActionAt(10_000, 1_420_000, null, segments = listOf(ed)))
    assertNull(skipActionAt(10_000, 1_420_000, null, segments = listOf(op)))   // OP known but not yet reached: no generic jump
}
@Test fun autoSkipCoversAniSkipOpNotEd() {
    assertTrue(shouldAutoSkip(true, SkipSegmentAction(SegmentKind.OP, 1), "e1", null))
    assertFalse(shouldAutoSkip(true, SkipSegmentAction(SegmentKind.ED, 1), "e1", null))
}
@Test fun labels() { assertEquals("Lewati OP", skipLabel(SkipAction.SkipOp(1))); assertEquals("Lewati ED", skipLabel(SkipSegmentAction(SegmentKind.ED, 1))); assertEquals("Lewati recap", skipLabel(SkipSegmentAction(SegmentKind.RECAP, 1))); assertEquals("+85 dtk", skipLabel(SkipAction.JumpAhead(1))) }
@Test fun episodeNumbers() { assertEquals(12, episodeNumberForSkip("12")); assertEquals(1, episodeNumberForSkip(" 01 ")); assertNull(episodeNumberForSkip("12.5")); assertNull(episodeNumberForSkip("OVA")); assertNull(episodeNumberForSkip("")); assertNull(episodeNumberForSkip("0")) }
```

The existing SkipIntroTest cases must still pass unchanged; `segments` defaults to empty.

- [ ] **Step 2: Stub; gate** — Expected: the new cases fail.
- [ ] **Step 3: Implement** with the priority order from spec Bagian 3.
- [ ] **Step 4: Gate** — green.
- [ ] **Step 5: Commit** `feat(player): AniSkip-aware skip actions`

### Task 6: AniSkip in the player — fetch, buttons, auto-skip chip, seekbar markers

**Files:**
- Create: `app/src/main/java/com/fuwanime/data/meta/SkipRepository.kt`: `class SkipRepository(private val api: AniSkipApi)` with `suspend fun segments(malId: Int, episode: Int, durationMs: Long): List<SkipSegment>`. It returns an empty list on any failure and caches in memory keyed `"$malId/$episode"`.
- Modify: `FuwaApp.kt` (`AppContainer.skipRepository`)
- Modify: `app/src/main/java/com/fuwanime/ui/player/PlayerViewModel.kt`:
  - constructor `links: AnimeLinkRepository`, `skips: SkipRepository`
  - `val segments: StateFlow<Pair<String, List<SkipSegment>>>` (episodeId to segments)
  - `fun onDurationKnown(episodeId: String, durationMs: Long)`, which fetches once per episodeId when `malIdFor(animeId)` and `episodeNumberForSkip(number)` are both non-null
  - `fun segmentsFor(episodeId: String): List<SkipSegment>` (returns empty unless the pair's key equals `episodeId`)
- Modify: `app/src/main/java/com/fuwanime/ui/player/PlayerScreen.kt`:
  - call `vm.onDurationKnown` from the 250 ms poll when duration first becomes > 0 for the episode
  - pass `segmentsFor(state.episode.id)` into `skipActionAt`
  - the button text uses `skipLabel`
  - the auto-skip `LaunchedEffect` keys on `skip?.targetMs` for `SkipOp` or OP `SkipSegmentAction`
  - add the auto-skip chip (below)
  - add seekbar markers (below)
- Test: `app/src/test/java/com/fuwanime/SkipRepositoryTest.kt` (MockWebServer) + `PlayerSegmentsTest` for `segmentsFor` keying, if the VM is testable without Android. Otherwise extract `segmentsFor(current: Pair<String, List<SkipSegment>>, episodeId)` as a top-level pure function and test that.

**Interfaces:**
- Consumes: Tasks 1–3, 5.
- Produces: `SkipRepository`, `segmentsFor(current: Pair<String, List<SkipSegment>>, episodeId: String): List<SkipSegment>` (top-level in `SkipIntro.kt`).

**Auto-skip chip:**
- A `Surface` pill at bottom-start with the text "Intro dilewati" and a `TextButton` "Batalkan". It is visible for 5 000 ms after an auto-skip.
- Batalkan seeks to the segment/mark start. `autoSkipHandledFor` already stops a repeat for that episode.

**Seekbar markers:**
- A `Canvas` behind the `Slider` track, the same width.
- For each segment (and the user's OP mark), draw a rect from `start/duration` to `end/duration`, 4.dp tall, `MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun repoFiltersAndCaches() = runBlocking { /* enqueue one AniSkip body; call segments(31964,1,1_420_000) twice → same list, server.requestCount == 1 */ }
@Test fun repoFailureIsEmpty() = runBlocking { /* 500 → emptyList() */ }
@Test fun repoNotFoundIsEmpty() = runBlocking { /* {"found":false,"results":[]} → emptyList() */ }
@Test fun segmentsForOtherEpisodeIsEmpty() { assertEquals(emptyList<SkipSegment>(), segmentsFor("e1" to listOf(op), "e2")); assertEquals(listOf(op), segmentsFor("e1" to listOf(op), "e1")) }
```

- [ ] **Step 2: Stub; gate** — Expected: failures.
- [ ] **Step 3: Implement repo, VM wiring, UI**
- [ ] **Step 4: Gate** — green.
- [ ] **Step 5: Commit** `feat(player): AniSkip OP/ED/recap buttons, auto-skip undo chip, seekbar markers`

### Task 7: "Tonton selanjutnya" at the end of a season

**Files:**
- Modify: `app/src/main/java/com/fuwanime/ui/player/PlayerViewModel.kt`:
  - add `PlayerUiState.nextSeason: SeasonEntry?`
  - once the episode list shows no `next`, load `repo.detailWithSeasons(animeId)` once and set `nextSeason = nextInFranchise(animeId, watchOrder(anime, seasons))`; failures leave it null
- Modify: `app/src/main/java/com/fuwanime/ui/player/PlayerScreen.kt`:
  - `PlayerRoute(sharedKey, onBack, onOpenAnime: (Anime) -> Unit)`
  - `EndOverlay(next, nextSeason, autoPlay, onNext, onReplay, onOpenSeason)`
  - when `next == null && nextSeason != null`, show a card with the poster (`PosterCard` small, or `AsyncImage` 72×100) and the text "Lanjut ke {label}: {judul}"
  - tapping the card calls `onOpenAnime(nextSeason.anime)`
- Modify: `app/src/main/java/com/fuwanime/ui/navigation/FuwaNavHost.kt` (the player composable passes `onOpenAnime = { a -> nav.popBackStack(); openAnime(a, "player") }`)

**Interfaces:**
- Consumes: `nextInFranchise`, `watchOrder` (Task 4).

- [ ] **Step 1:** RED is `nextInFranchise` from Task 4 (already green). This task's UI is verified in QA scenario 10 (Task 9). Ledger it.
- [ ] **Step 2: Implement**
- [ ] **Step 3: Gate** — `CI PASSED`.
- [ ] **Step 4: Commit** `feat(player): next-season card at the end of a season`

### Task 8: Jelajah multi-genre and "Acak"

**Files:**
- Modify: `app/src/main/java/com/fuwanime/ui/explore/ExploreViewModel.kt`:
  - `ExploreFilters.genres: Set<String> = emptySet()` replaces `genre`
  - the saved-state key `genre` maps to `setOfNotNull(...)`
  - `fun toggleGenre(g: String)` replaces `setGenre`
  - `activeCount` counts `genres.size`
  - the client filter uses `matchesGenres`
  - `clearFilters` empties genres
- Create: `app/src/main/java/com/fuwanime/ui/explore/ExploreLogic.kt`: `fun matchesGenres(anime: Anime, genres: Set<String>): Boolean`, `fun pickRandom(results: List<Anime>, random: Random = Random.Default): Anime?`
- Modify: `app/src/main/java/com/fuwanime/ui/explore/ExploreScreen.kt` (genre chips toggle via `filters.genres.contains(g)`; result title `genres.joinToString(" + ")`; dice `IconButton(Icons.Rounded.Casino, "Anime acak")` in the bar, enabled when results are not empty, calling `onAnimeClick(pickRandom(results)!!, "explore")`)
- Modify: `app/src/test/java/com/fuwanime/TypeFilterTest.kt:36` (`ExploreFilters(genres = setOf("Action"), …)`)
- Test: `app/src/test/java/com/fuwanime/ExploreLogicTest.kt`

**Interfaces:**
- Produces: `ExploreFilters.genres`, `matchesGenres`, `pickRandom`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun emptySetMatchesAll() = assertTrue(matchesGenres(anime(genres = listOf("Drama")), emptySet()))
@Test fun allGenresRequired() { assertTrue(matchesGenres(anime(genres = listOf("Action", "Fantasy", "Comedy")), setOf("action", "Fantasy"))); assertFalse(matchesGenres(anime(genres = listOf("Action")), setOf("Action", "Fantasy"))) }
@Test fun isekaiFromSynopsis() = assertTrue(matchesGenres(anime(genres = listOf("Action"), synopsis = "Dia terlempar ke isekai"), setOf("Action", "Isekai")))
@Test fun randomFromResults() { assertNull(pickRandom(emptyList())); assertEquals("b", pickRandom(listOf(anime("a"), anime("b")), Random(seedThatPicksIndex1))?.id) }
```

Choose the seed in the test by checking `Random(seed).nextInt(2) == 1`, or assert membership instead.

- [ ] **Step 2: Stub; gate** — Expected: ExploreLogicTest fails. `TypeFilterTest` compiles after the field rename.
- [ ] **Step 3: Implement**
- [ ] **Step 4: Gate** — green.
- [ ] **Step 5: Commit** `feat(explore): multi-genre filter and random pick`

### Task 9: Jadwal "Musim ini" + QA scenario 10

**Files:**
- Create: `app/src/main/java/com/fuwanime/ui/schedule/SeasonLogic.kt`:
  - `data class AnimeSeason(val name: String, val year: Int, val startMonth: Int)`
  - `fun seasonOf(year: Int, month: Int): AnimeSeason` (month 1–12)
  - `fun splitBySeason(all: List<Anime>, season: AnimeSeason): Pair<List<Anime>, List<Anime>>`
  - `fun seasonTabState(days: Collection<DaySchedule>): SeasonTab`, where `sealed interface SeasonTab { Loading; Error(message); Ready(all: List<Anime>) }`. It is Loading while any day is loading and none is ready, Error only when every day has an error, and otherwise Ready (dedupe by id).
- Modify: `app/src/main/java/com/fuwanime/ui/schedule/ScheduleScreen.kt` (an 8th pager page/tab "Musim ini"; on select, `Day.entries.forEach(vm::ensureLoaded)`; header `"${season.name} ${season.year}"`; two sections with counts; error and retry reloads all days)
- Modify: `.github/scripts/qa.sh` (scenario 10, below)
- Test: `app/src/test/java/com/fuwanime/SeasonLogicTest.kt`

**Interfaces:**
- Consumes: the existing `DaySchedule`. Make it `internal` if it is private.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun seasons() { assertEquals(AnimeSeason("Musim Dingin", 2026, 1), seasonOf(2026, 3)); assertEquals(AnimeSeason("Musim Semi", 2026, 4), seasonOf(2026, 4)); assertEquals(AnimeSeason("Musim Panas", 2026, 7), seasonOf(2026, 9)); assertEquals(AnimeSeason("Musim Gugur", 2026, 10), seasonOf(2026, 12)) }
@Test fun splitByAiredStart() { /* autumn 2026: "2026-10-04" new, "2026-12-31" new, "2026-09-30" continuing, "2025-10-05" continuing, "" continuing; duplicates by id collapse; each half sorted by title */ }
@Test fun tabReadyWithPartialFailure()  // 2 days ready, 5 error → Ready with the 2 days' anime
@Test fun tabErrorOnlyWhenAllFail()     // 7 errors → Error
@Test fun tabLoadingUntilFirstReady()   // 1 loading, 6 not loaded → Loading
```

QA scenario 10, appended before `# 6. Offline`:

```bash
# 10. Sub-project C: AniList meta on Detail, AniSkip button, multi-genre + random, Musim ini.
fresh
tap "Tonton"; sleep 8; shot qa10a-detail-meta 1
check "anilist footer" "$(find_xy "Info dari AniList*" | wc -l)"
scroll_to "Episode 1" && tap "Episode 1"; sleep 25
check "aniskip button" "$( [ -n "$(find_xy "Lewati OP")" ] && echo 1 || echo 0 )"; shot qa10b-player-skip 0
adb shell input keyevent KEYCODE_BACK; sleep 2; adb shell input keyevent KEYCODE_BACK; sleep 2
tap "Jelajah"; sleep 3; tap "Action"; sleep 1; tap "Fantasy"; sleep 4; shot qa10c-two-genres 1
tap "Anime acak"; sleep 4; shot qa10d-random-detail 1; adb shell input keyevent KEYCODE_BACK; sleep 2
tap "Jadwal"; sleep 2; scroll_tab "Musim ini"; sleep 8; shot qa10e-musim-ini 1
alive
```

`check` comes from scenario 9. If "Musim ini" is off-screen in the scrollable tab row, add `scroll_tab() { for _ in 1 2 3; do tap "$1" && return; adb shell input swipe 900 300 200 300 300; sleep 1; done; }`. Genre chip labels must match what the Jelajah screen shows; read `qa5a-explore.png` from the latest QA artifact first, and pick two present genres.

- [ ] **Step 2: Stub; gate** — Expected: SeasonLogicTest fails.
- [ ] **Step 3: Implement**
- [ ] **Step 4: Gate + QA**

Expected:
- `CI PASSED`.
- QA: `QA run passed`.
- `anilist footer: 1`, `aniskip button:` 1, or 0 with the reason noted (title not covered).
- qa10a shows score/tags, qa10c shows two genres selected, qa10d shows a Detail, qa10e shows "Musim Gugur 2026" with both sections.
- `upgrade: history kept` (v3 → v4 migration).

- [ ] **Step 5: Commit** `feat(schedule): Musim ini tab` and `test(qa): sub-project C scenario`
