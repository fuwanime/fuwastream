# Notifikasi rilis anime & manga ongoing — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use superpowers:subagent-driven-development to
> implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pengguna dapat notifikasi tiap ada episode baru dari anime **ongoing** mana pun dan chapter
baru dari manga **ongoing** mana pun (MangaDex, bahasa app), tanpa harus difavoritkan.

**Architecture:** Satu `CoroutineWorker` baru (`data/notify/ReleaseNotifyWorker`) yang memakai feed
global (anime: `latestReleases`; manga: MangaDex `/chapter`), dengan baseline per judul di DataStore
dan notifikasi bergrup (per judul + ringkasan). Worker favorit yang lama tetap, untuk lencana di
Koleksi. Tambah dua saklar di Pengaturan dan deep link manga.

**Tech Stack:** Kotlin 2.2.10, WorkManager, Retrofit + Moshi codegen, DataStore, JUnit4 (JVM murni,
Robolectric hanya bila perlu).

**Spec:** `docs/superpowers/specs/2026-11-08-notifikasi-rilis-design.md`

## Global Constraints

- Tanpa dependensi app baru.
- Verifikasi **CI-only**: `gradle testDebugUnitTest assembleRelease`. Container dev tidak punya
  Android SDK, jadi tidak ada build lokal.
- Teks baru di `res/values*/strings_system.xml` dan `strings.xml` dalam **4 bahasa**, dijaga
  `StringsCompletenessTest`.
- Backup: tambah 2 boolean ke `SETTING_TYPES`; kecualikan prefix `notify_release_` dan
  `notify_manga_` dari backup.
- Tidak mengubah perilaku worker favorit yang sudah ada.
- Commit Bahasa Indonesia konvensional, tanpa baris co-author.

---

### Task 1: Logika murni (parsing & keputusan)

**Files:**
- Create: `app/src/main/java/com/fuwanime/data/notify/ReleaseRules.kt`
- Test: `app/src/test/java/com/fuwanime/ReleaseRulesTest.kt`

**Interfaces — Produces:**
```kotlin
const val RELEASE_EPS = 0.001
/** Angka episode dari label feed ("Episode 5", "Ep. 12.5") atau null bila tidak ada angka. */
fun episodeNumber(label: String): Double?
/** True bila [newest] layak diberitakan terhadap [baseline]; baseline null hanya menyemai. */
fun isNewerRelease(newest: Double, baseline: Double?): Boolean
```

- [ ] **Step 1: Test yang gagal** (`ReleaseRulesTest`, JVM murni):
  - `episodeNumberParses` : "Episode 5" → 5.0; " Episode 12.5 " → 12.5; "Ep 7" → 7.0.
  - `episodeNumberNullWithoutDigit` : "" dan "Movie" → null.
  - `isNewerReleaseSeedsOnNull` : `isNewerRelease(3.0, null) == false`.
  - `isNewerReleaseTrueOnIncrease` : `isNewerRelease(4.0, 3.0) == true`.
  - `isNewerReleaseFalseOnSameOrOlder` : `(3.0, 3.0) == false`, `(2.0, 3.0) == false`.
- [ ] **Step 2: Implementasi** minimal sampai test hijau.

---

### Task 2: Settings + backup

**Files:**
- Modify: `app/src/main/java/com/fuwanime/data/local/SettingsStore.kt`
- Modify: `app/src/main/java/com/fuwanime/data/backup/BackupCodec.kt`
- Test: `app/src/test/java/com/fuwanime/BackupCodecTest.kt` (tambah kasus), atau test baru

**Interfaces — Produces:**
```kotlin
// AppSettings
val notifyOngoingAnime: Boolean = true
val notifyNewChapters: Boolean = true
// SettingsStore
suspend fun releaseBaseline(animeId: String): Double?
suspend fun setReleaseBaseline(animeId: String, episode: Double)
suspend fun mangaBaseline(mangaId: String): Double?
suspend fun setMangaBaseline(mangaId: String, chapter: Double)
```

- [ ] **Step 1:** Tambah 2 field `AppSettings`, 2 `Keys` boolean, 2 setter, dan 4 fungsi baseline
  (`notify_release_<id>`, `notify_manga_<id>`), plus wiring di `toSettings()`.
- [ ] **Step 2:** `SETTING_TYPES += "notify_ongoing_anime"/"notify_new_chapters" to "boolean"`;
  `isBackedUpSetting` kecualikan prefix `notify_release_` & `notify_manga_`.
- [ ] **Step 3:** Test: `settingTypeAccepted` terima boolean baru; `isBackedUpSetting` false untuk
  `notify_release_x`/`notify_manga_x`.

---

### Task 3: Data manga (DTO/API/model/repository)

**Files:**
- Modify: `app/src/main/java/com/fuwanime/data/manga/MangaDexDto.kt`
- Modify: `app/src/main/java/com/fuwanime/data/manga/MangaDexApi.kt`
- Modify: `app/src/main/java/com/fuwanime/data/manga/MangaModels.kt`
- Modify: `app/src/main/java/com/fuwanime/data/manga/MangaRepository.kt`
- Test: `app/src/test/java/com/fuwanime/MangaReleaseMappingTest.kt`

**Interfaces — Produces:**
```kotlin
// RelationshipAttributesDto += title: Any?, status: String?
@GET("chapter") suspend fun latestChapters(limit, byPublishAt, langs, ratings, includes): FeedDto
data class MangaRelease(val mangaId: String, val title: String, val chapter: String?, val publishAt: Long)
suspend fun MangaRepository.latestOngoingChapters(limit: Int = 40): List<MangaRelease>
```

- [ ] **Step 1: Test pemetaan** (`MangaReleaseMappingTest`, JVM murni + fixture JSON kecil): dari
  `FeedDto` → daftar `MangaRelease`, hanya `ongoing`, satu per manga (chapter terbaru), bahasa app.
- [ ] **Step 2:** Implementasi DTO/API/repository sampai hijau.

---

### Task 4: Notifier

**Files:**
- Modify: `app/src/main/java/com/fuwanime/data/notify/EpisodeNotifyWorker.kt`

**Interfaces — Produces:**
```kotlin
// EpisodeNotifier
suspend fun notifyAnime(animeId: String, title: String, poster: String, cover: String, text: String)
// MangaNotifier
suspend fun notifyNewChapter(mangaId: String, title: String, chapterLabel: String)
fun postSummary(titles: List<String>)
```

- [ ] **Step 1:** Generalisasi `notifyNewEpisode(favorite, episode)` agar memanggil `notifyAnime(...)`.
- [ ] **Step 2:** Tambah `MangaNotifier` (channel `new_chapters`, grup + summary id unik, deep link
  `EXTRA_OPEN_MANGA_ID`).

---

### Task 5: Worker + penjadwalan

**Files:**
- Create: `app/src/main/java/com/fuwanime/data/notify/ReleaseNotifyWorker.kt`
- Modify: `app/src/main/java/com/fuwanime/FuwaApp.kt`

**Interfaces — Produces:**
```kotlin
class ReleaseNotifyWorker(context, params) : CoroutineWorker
object / companion { fun schedule(context: Context) }
```

- [ ] **Step 1:** Implementasi worker: cabang anime (skip favorit, hormati status, `episodeNumber`,
  `isNewerRelease`) + cabang manga (`latestOngoingChapters`). Ringkasan bila ≥ 2.
- [ ] **Step 2:** `ReleaseNotifyWorker.schedule(this)` di `FuwaApp.onCreate`, interval 1 jam,
  `NetworkType.CONNECTED`, `KEEP`.

---

### Task 6: Deep link manga

**Files:**
- Modify: `app/src/main/java/com/fuwanime/ui/navigation/LaunchTarget.kt`
- Modify: `app/src/main/java/com/fuwanime/MainActivity.kt`
- Modify: `app/src/main/java/com/fuwanime/ui/navigation/FuwaNavHost.kt`
- Test: `app/src/test/java/com/fuwanime/LaunchTargetTest.kt`

- [ ] **Step 1:** `LaunchTarget.Manga(id)`; `parseLaunchTarget(..., mangaId: String? = null)` dengan
  manga di antara anime dan tab; test `mangaWhenNoAnime`.
- [ ] **Step 2:** `MainActivity.EXTRA_OPEN_MANGA_ID` + kirim ke `parseLaunchTarget`.
- [ ] **Step 3:** `FuwaNavHost` tangani `LaunchTarget.Manga` → `Routes.mangaDetail(id)`.

---

### Task 7: UI Pengaturan + strings

**Files:**
- Modify: `app/src/main/java/com/fuwanime/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values*/strings.xml`, `strings_system.xml` (4 locale)

- [ ] **Step 1:** Dua `SwitchRow` di grup Notifications: "Rilis anime (semua judul)" dan "Chapter
  manga (semua judul)".
- [ ] **Step 2:** Tambah string `mine_notify_releases*`, channel/key teks manga di 4 locale; jaga
  `StringsCompletenessTest` (placeholder sama).

---

### Task 8: Verifikasi

- [ ] **Step 1:** Tinjau ulang `SETTING_TYPES`/`isBackedUpSetting`, locale 4 file, dan tidak ada
  dependensi baru.
- [ ] **Step 2:** Pastikan tidak ada duplikasi notif favorit vs global; baseline menyemai di run
  pertama.
- [ ] **Step 3:** Serahkan ke CI (`testDebugUnitTest assembleRelease`); tidak ada QA lokal.
