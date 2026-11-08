# Statistik & akses Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Statistik mudah ditemukan (kartu "Minggu ini" di Koleksi), layar Statistik lebih jelas, Pengaturan bisa dibuka dari Koleksi, dan aksi penting punya label.

**Architecture:** Logika murni baru di `ui/stats/WatchStats.kt` (ringkasan 7 hari) dipakai oleh `LibraryViewModel` dan `StatsViewModel`. UI Compose diubah di tempat yang sudah ada; satu helper tooltip baru di `ui/components/A11y.kt`. Tidak ada dependensi baru.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose (BOM 2026.09.00, Material3), Room, JUnit4.

**Spec:** `docs/superpowers/specs/2026-11-07-statistik-backup-bahasa-design.md` — Sub-proyek 1.

## Global Constraints

- Tidak ada dependensi baru; APK rilis naik < 150 KB untuk ketiga sub-proyek.
- Tetap 4 tab (Beranda, Jelajah, Jadwal, Koleksi).
- Tekan-lama di Koleksi tidak diubah.
- Teks tetap Bahasa Indonesia hard-coded di sub-proyek ini (dipindah ke resource di sub-proyek 3).
- Verifikasi: `bash /home/sakamoto/Data/Github/fuwanime-quick-wins/.superpowers/quick-wins/ci-test.sh` (push HEAD ke fork, jalankan `testDebugUnitTest` + `assembleRelease`). Tidak ada toolchain Android lokal. QA emulator hanya bila diminta pengguna.
- Commit tanpa baris co-author Claude.

## Review Focus

- Zona waktu: hari "hari ini" dan batas 7 hari harus dihitung dengan `epochDay` (zona HP), sama seperti `watch_daily` ditulis; episode yang selesai pukul 23.30 tidak boleh pindah hari. → test `weekSummaryUsesEpochDayOfUpdatedAt` di Task 1.
- Data kosong: Koleksi tanpa riwayat sama sekali tetap menampilkan kartu dengan teks ajakan, tidak crash pada `max` dari list kosong. → test `lastNDaysAllZeroWhenEmpty` di Task 1; grafik mini memakai `coerceAtLeast(1)`.
- Font 200%: kartu Statistik bertumpuk satu kolom, tombol berlabel di Detail pindah ke baris sendiri. → test `statColumns` di Task 3.
- Mode pilih-banyak di Koleksi: kartu "Minggu ini" disembunyikan supaya tidak bisa membuka Statistik di tengah seleksi.
- QA script lama men-tap "Statistik" (ikon); setelah ikon dihapus, langkah itu harus men-tap kartu "Minggu ini". → Task 2 langkah qa.sh.

---

### Task 1: Ringkasan 7 hari

**Files:**
- Modify: `app/src/main/java/com/fuwanime/ui/stats/WatchStats.kt`
- Test: `app/src/test/java/com/fuwanime/WatchStatsTest.kt`

**Interfaces:**
- Produces:
  - `data class DayBar(val day: Int, val minutes: Int)`
  - `fun lastNDays(daily: List<WatchDailyEntity>, today: Int, n: Int = 7): List<DayBar>` — urut lama→baru, panjang selalu `n`, hari tanpa data = 0 menit.
  - `data class WeekSummary(val watchedMs: Long, val episodes: Int, val days: List<DayBar>)`
  - `fun weekSummary(history: List<WatchHistoryEntity>, daily: List<WatchDailyEntity>, today: Int): WeekSummary` — jendela `today-6..today`; `episodes` = riwayat `isFinished` dengan `epochDay(updatedAt)` dalam jendela; `watchedMs` = jumlah `watch_daily` dalam jendela.

Ruling: spec menulis parameter `nowMs`; tidak diperlukan karena `today` sudah epoch day — dihapus.

- [ ] **Step 1: Tulis test yang gagal** (tambahkan ke `WatchStatsTest`, pakai helper `day`, `millisOf`, `hist`, `today` yang sudah ada)

```kotlin
@Test fun lastNDaysFillsGapsOldestFirst() {
    val daily = listOf(WatchDailyEntity(today, 600_000), WatchDailyEntity(today - 2, 1_200_000), WatchDailyEntity(today - 10, 999_000))
    val bars = lastNDays(daily, today)
    assertEquals((today - 6..today).toList(), bars.map { it.day })
    assertEquals(listOf(0, 0, 0, 0, 20, 0, 10), bars.map { it.minutes })
}

@Test fun lastNDaysAllZeroWhenEmpty() {
    assertEquals(List(7) { 0 }, lastNDays(emptyList(), today).map { it.minutes })
}

@Test fun weekSummaryCountsOnlyFinishedEpisodesInWindow() {
    val history = listOf(
        hist("a", "1", 950, 1000, millisOf(today - 1)),  // selesai, dalam jendela
        hist("a", "2", 950, 1000, millisOf(today - 8)),  // selesai, di luar jendela
        hist("b", "1", 100, 1000, millisOf(today)),      // belum selesai
    )
    val daily = listOf(WatchDailyEntity(today, 600_000), WatchDailyEntity(today - 2, 1_200_000), WatchDailyEntity(today - 10, 999_000))
    val week = weekSummary(history, daily, today)
    assertEquals(1, week.episodes)
    assertEquals(1_800_000L, week.watchedMs)
    assertEquals(7, week.days.size)
}

@Test fun weekSummaryUsesEpochDayOfUpdatedAt() {
    // Selesai tepat di hari pertama jendela (today-6) masih dihitung; today-7 tidak.
    val history = listOf(hist("a", "1", 950, 1000, millisOf(today - 6)), hist("a", "2", 950, 1000, millisOf(today - 7)))
    assertEquals(1, weekSummary(history, emptyList(), today).episodes)
}
```

Catatan: `epochDay(millis)` memakai zona default; `millisOf` memberi pukul 12.00 UTC sehingga hasilnya sama di zona ±11 jam.

- [ ] **Step 2: Jalankan test, pastikan gagal**

Run: `bash /home/sakamoto/Data/Github/fuwanime-quick-wins/.superpowers/quick-wins/ci-test.sh`
Expected: FAIL kompilasi — `lastNDays`, `weekSummary` tidak dikenal.

- [ ] **Step 3: Implementasi `DayBar`, `lastNDays`, `WeekSummary`, `weekSummary` di `WatchStats.kt`** sesuai Interfaces (menit = `watchedMs / 60_000`).

- [ ] **Step 4: Jalankan test, pastikan lulus**

Run: `bash .../ci-test.sh`
Expected: `testDebugUnitTest` hijau, `assembleRelease` sukses.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/fuwanime/ui/stats/WatchStats.kt app/src/test/java/com/fuwanime/WatchStatsTest.kt
git commit -m "feat(stats): ringkasan 7 hari terakhir"
```

---

### Task 2: Kartu "Minggu ini" + ⚙ di Koleksi

**Files:**
- Modify: `app/src/main/java/com/fuwanime/ui/library/LibraryScreen.kt` (ViewModel ±106-170, header ±203-219)
- Modify: `app/src/main/java/com/fuwanime/ui/navigation/FuwaNavHost.kt:331` (tambah `onSettings`)
- Modify: `.github/scripts/qa.sh:153,231`

**Interfaces:**
- Consumes: `weekSummary`, `WeekSummary`, `DayBar` (Task 1); `watchTimeLabel(ms)` dari `StatsScreen.kt`; `epochDay` dari `util`.
- Produces: `LibraryRoute(..., onStats: () -> Unit, onSettings: () -> Unit)`; `LibraryViewModel.week: StateFlow<WeekSummary?>`.

- [ ] **Step 1: `LibraryViewModel.week`** = `combine(library.allHistory, library.watchDaily) { h, d -> weekSummary(h, d, epochDay(System.currentTimeMillis())) }.stateIn(viewModelScope, WhileSubscribed(5_000), null)`.

- [ ] **Step 2: Header Koleksi**: hapus `IconButton(onClick = onStats) { Insights … }`; tambah ikon ⚙ (`Icons.Rounded.Settings`, label "Pengaturan") memanggil `onSettings`, dibungkus `IconWithTooltip` bila Task 4 sudah ada — pada task ini cukup `IconButton` biasa; Task 4 menggantinya. Urutan ikon kanan: Hapus riwayat (bila tampil), lalu ⚙ paling kanan.

- [ ] **Step 3: `@Composable private fun WeekCard(week: WeekSummary, onClick: () -> Unit)`** di `LibraryScreen.kt`, ditampilkan di antara header dan `SegmentedTabs` bila `!selecting && week != null`, padding horizontal 16dp, bawah 8dp.
  - Gaya: `RoundedCornerShape(18.dp)`, `surfaceContainerLow`, border 1dp `outlineVariant`, padding 16dp, `clickable(onClick)`, `semantics(mergeDescendants = true) { contentDescription = … }`.
  - Isi kiri: "Minggu ini" (labelMedium, onSurfaceVariant); bila `week.watchedMs > 0 || week.episodes > 0`: "${watchTimeLabel(week.watchedMs)} · ${week.episodes} episode" (titleMedium, Bold); selain itu "Mulai nonton untuk melihat statistikmu" (bodyMedium).
  - Isi kanan bawah: Canvas 7 batang, tinggi 32dp, lebar 84dp, batang terakhir (hari ini) `primary`, lainnya `primary.copy(alpha = 0.4f)`, tinggi relatif ke `max(minutes).coerceAtLeast(1)`, batang 0 menit digambar setinggi 3dp; lalu teks "Lihat statistik ›" (labelLarge, primary).
  - contentDescription: "Statistik minggu ini, ${watchTimeLabel} , ${episodes} episode. Buka statistik" (atau "Statistik. Belum ada tontonan minggu ini. Buka statistik").

- [ ] **Step 4: Nav**: di `FuwaNavHost.kt` panggilan `LibraryRoute` tambah `onSettings = { nav.navigate(Routes.SETTINGS) }`.

- [ ] **Step 5: qa.sh**: baris 153 dan 231 ganti `tap "Statistik"` menjadi `tap "Minggu ini"` (fungsi `tap` mencocokkan teks node).

- [ ] **Step 6: Verifikasi**

Run: `bash .../ci-test.sh`
Expected: hijau.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/fuwanime/ui/library/LibraryScreen.kt app/src/main/java/com/fuwanime/ui/navigation/FuwaNavHost.kt .github/scripts/qa.sh
git commit -m "feat(koleksi): kartu Minggu ini ke Statistik dan Pengaturan di Koleksi"
```

---

### Task 3: Layar Statistik

**Files:**
- Modify: `app/src/main/java/com/fuwanime/ui/stats/StatsScreen.kt`
- Modify: `app/src/main/java/com/fuwanime/ui/stats/WatchStats.kt` (tambah `statColumns`)
- Modify: `app/src/main/java/com/fuwanime/ui/navigation/FuwaNavHost.kt:374`
- Test: `app/src/test/java/com/fuwanime/WatchStatsTest.kt`

**Interfaces:**
- Consumes: `lastNDays`, `DayBar` (Task 1).
- Produces: `fun statColumns(fontScale: Float, widthDp: Int): Int`; `StatsRoute(onBack: () -> Unit, onExplore: () -> Unit)`; `StatsViewModel.week: StateFlow<List<DayBar>>`.

- [ ] **Step 1: Test yang gagal**

```kotlin
@Test fun statColumnsStackOnLargeFontOrNarrowScreen() {
    assertEquals(2, statColumns(1.0f, 400))
    assertEquals(2, statColumns(1.3f, 360))
    assertEquals(1, statColumns(1.5f, 400))
    assertEquals(1, statColumns(2.0f, 600))
    assertEquals(1, statColumns(1.0f, 359))
}
```

- [ ] **Step 2: Jalankan, pastikan gagal** (ci-test.sh → `statColumns` tidak dikenal).

- [ ] **Step 3: Implementasi `statColumns`** di `WatchStats.kt`: 1 bila `fontScale >= 1.5f || widthDp < 360`, selain itu 2.

- [ ] **Step 4: `StatsViewModel.week`** = `library.watchDaily.map { lastNDays(it, epochDay(System.currentTimeMillis())) }.stateIn(…, emptyList())`.

- [ ] **Step 5: UI `StatsRoute`**
  - **Hero** item pertama menggantikan kartu "Jam tonton": Column dengan "Total waktu nonton" (labelLarge, onSurfaceVariant), `watchTimeLabel(stats.totalWatchedMs)` (displaySmall, ExtraBold, primary), caption "± perkiraan untuk data lama" bila `isEstimated`.
  - **Kartu**: Episode selesai, Anime, Minggu aktif ("berturut-turut"). Kolom = `statColumns(LocalDensity.current.fontScale, LocalConfiguration.current.screenWidthDp)`; 2 kolom → baris berisi 2 + 1 (kartu ketiga `Modifier.weight(1f)` dengan spacer setengah, atau `FlowRow` `maxItemsInEachRow = 2`); 1 kolom → tumpuk.
  - **Tap kartu** → `snackbar.showSnackbar(penjelasan)`; `StatCard` mendapat parameter `onClick: () -> Unit` (`clickable`). Penjelasan:
    - Episode selesai: "Episode yang ditonton sampai minimal 92%."
    - Anime: "Jumlah judul berbeda yang pernah kamu tonton."
    - Minggu aktif: "Minggu berturut-turut kamu menonton, sampai minggu ini."
  - **Grafik 7 hari** (item baru sebelum grafik bulanan), judul "7 hari terakhir": pakai komponen batang yang sama dengan bulanan (refactor `MonthlyChart` menjadi `BarChart(title: String, labels: List<String>, minutes: List<Int>)`). Label hari: `DateFormatSymbols.getInstance().shortWeekdays[dow]` di mana `dow` = `Calendar` UTC dari `day * 86_400_000L`. Batang terakhir `primary`, lainnya `primary.copy(alpha = 0.45f)`; di atas batang dengan menit > 0 tulis angka menit (labelSmall). contentDescription: "${label} ${minutes} menit" digabung koma.
  - **Grafik 6 bulan**: pakai `BarChart` yang sama, judul "6 bulan terakhir", highlight bulan terakhir.
  - **Empty state**: `MessageState(..., action = "Cari anime" to onExplore)` — cek signature `MessageState` di `ui/components/Components.kt`; bila belum ada parameter aksi, tambahkan `FilledTonalButton` di bawahnya dalam Column yang sama.
- [ ] **Step 6: Nav**: `StatsRoute(onBack = { nav.popBackStack() }, onExplore = { nav.navigateTab(Routes.explore()) })`.

- [ ] **Step 7: Verifikasi** — ci-test.sh hijau.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/fuwanime/ui/stats app/src/main/java/com/fuwanime/ui/navigation/FuwaNavHost.kt app/src/test/java/com/fuwanime/WatchStatsTest.kt
git commit -m "feat(stats): hero waktu nonton, grafik 7 hari, kartu bisa dijelaskan, tombol Cari anime"
```

---

### Task 4: Label & tooltip untuk aksi penting

**Files:**
- Modify: `app/src/main/java/com/fuwanime/ui/components/A11y.kt`
- Modify: `app/src/main/java/com/fuwanime/ui/detail/DetailScreen.kt:651-660`
- Modify: `app/src/main/java/com/fuwanime/ui/explore/ExploreScreen.kt:268`
- Modify: `app/src/main/java/com/fuwanime/ui/home/HomeScreen.kt:420-421`
- Modify: `app/src/main/java/com/fuwanime/ui/library/LibraryScreen.kt` (⚙ dari Task 2)
- Modify: `app/src/main/java/com/fuwanime/ui/player/PlayerScreen.kt:985-990,1039`

**Interfaces:**
- Produces: `@Composable fun IconWithTooltip(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, tint: Color = LocalContentColor.current)` — `TooltipBox(TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState())` membungkus `IconButton(onClick, enabled) { Icon(icon, label, tint = tint) }`. `@OptIn(ExperimentalMaterial3Api::class)` bila diperlukan.

- [ ] **Step 1: Tambah `IconWithTooltip`** di `A11y.kt`.

- [ ] **Step 2: Detail**: Row header episode — judul "Episode" tetap; ganti ikon Unduh massal dan Urutan menjadi `TextButton` berikon (`Icons.Rounded.Download` "Unduh semua"; `Icons.Rounded.SwapVert` dengan label `if (state.newestFirst) "Terbaru" else "Terlama"`). Ikon Cari episode tetap ikon tapi via `IconWithTooltip`. Tata letak: bila `statColumns(fontScale, screenWidthDp) == 2`, satu `Row` seperti sekarang (judul `weight(1f)`, tombol rata kanan); bila `== 1`, judul di baris sendiri dan tombol-tombol di `Row` di bawahnya, rata kiri.

- [ ] **Step 3: Jelajah**: ganti `IconButton(Casino)` dengan `AssistChip(onClick = { onRandom?.invoke() }, enabled = onRandom != null, label = { Text("Acak") }, leadingIcon = { Icon(Icons.Rounded.Casino, null, Modifier.size(18.dp)) })`.

- [ ] **Step 4: Tooltip**: ganti `IconButton`+`Icon` menjadi `IconWithTooltip` di: top bar Beranda (Cari, Pengaturan, dengan `tint` yang sama), ⚙ Koleksi, player (Kunci layar, Picture-in-Picture, Pengaturan video, tombol layar penuh — label sama dengan contentDescription yang ada, `tint = Color.White`). Perilaku `onClick` tidak berubah (player tetap memanggil `onInteract()` di tempat yang sama).

- [ ] **Step 5: Verifikasi** — ci-test.sh hijau (termasuk lintVital rilis).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/fuwanime/ui
git commit -m "feat(a11y): label pada Unduh semua, Urutan, Acak; tooltip pada ikon"
```

---

### Task 5: Review, verifikasi, merge

- [ ] **Step 1:** Review seluruh branch (`ponytail:ponytail-review`) terhadap spec Sub-proyek 1 dan Review Focus di atas; perbaiki temuan Critical/Important dengan test RED→GREEN.
- [ ] **Step 2:** `ci-test.sh` terakhir hijau; catat jumlah test dan ukuran APK rilis dibanding main (artefak `app-release.apk` dari run sebelumnya vs sekarang) — naik < 50 KB.
- [ ] **Step 3:** PR `feat/stats-akses` → `main` di `SakamotoMrX/fuwanime` (deskripsi lewat `mattpocock-skills:pr`, Bahasa Indonesia, tanpa atribusi Claude), merge setelah CI hijau.
