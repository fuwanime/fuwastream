# Visual & aksesibilitas (sub-proyek D) — Desain

Tanggal: 2026-11-05 · Branch: `feat/visual-aksesibilitas`

## Konteks & tujuan

Sub-proyek terakhir dari implementasi riset `reports/Riset fitur dan UI UX FuwaStream.md`. A, B dan C sudah di-merge ke fork (PR #23, #24, #25). Dokumen ini mencakup:

- **#10** Font 200% + TalkBack
- **#12** Backdrop Detail berwarna poster + bar frosted
- **#16** Navigasi adaptif untuk layar besar

Keputusan pengguna:

- Warna poster pakai **androidx.palette**; blur pakai **Haze 1.7.3**. Haze 2.x dan MaterialKolor 5.x butuh Kotlin stdlib 2.4, sedangkan compiler proyek 2.2.10.
- Frosted dipasang di **bottom nav**, **top bar Detail**, dan **header Statistik/Pengaturan**.
- Layar besar: **navigation rail + batas lebar konten**, tanpa dua panel.
- **Tanpa QA emulator** (permintaan pengguna). Verifikasi hanya unit test + release build. Tampilan di perangkat tidak diverifikasi.

**Sukses berarti:**

- Detail terasa "milik" anime itu: gradien di bawah header dan tombol putar memakai warna dari posternya. Bagian lain aplikasi tetap lavender/pink.
- Konten terlihat samar di balik bottom bar dan top bar (blur di API 31+, scrim transparan di bawahnya).
- Di tablet/foldable (lebar ≥ 600dp) navigasi menjadi rail di kiri, dan layar baca (Detail, Pengaturan, Statistik, Legal) tidak melar melebihi 840dp.
- Pada skala font 200% tidak ada teks penting yang terpotong oleh tinggi tetap.
- Dengan TalkBack, satu kartu poster dibaca sebagai satu item ("Frieren, Episode 5, 2 baru"), dan aksi long-press (pilih banyak di Koleksi) bisa dijalankan lewat menu aksi TalkBack.

## Bagian 1 — Dependensi

- `androidx.palette:palette-ktx:1.0.0`
- `dev.chrisbanes.haze:haze:1.7.3`
- Tidak menambah `material3-adaptive-navigation-suite`. `NavigationSuiteScaffold` menata konten **di samping** bar, sehingga konten tidak bisa lewat di bawah bar dan blur tidak terlihat. Sebagai gantinya, rail dan bar dipilih sendiri berdasarkan lebar layar.

## Bagian 2 — Warna poster di Detail (#12)

Fungsi murni di `ui/detail/PosterTint.kt`:

- `posterTint(darkVibrant: Int?, vibrant: Int?, dominant: Int?): Int?`
  - Pilih swatch pertama yang ada dengan urutan darkVibrant → vibrant → dominant.
  - Turunkan kecerahannya (kalikan RGB dengan faktor yang sama) sampai luminansi relatif ≤ 0,25.
  - Tanpa swatch → null.
- `onColorFor(color: Int): Int`
  - Mengembalikan putih `0xFFFFFFFF` atau hitam `0xFF000000`, mana yang rasio kontrasnya (WCAG) dengan `color` lebih besar.
- `relativeLuminance(color: Int): Double`, rumus WCAG 2.x.

Pemakaian:

- `DetailScreen` mengambil tint sekali per poster: `produceState<Color?>(null, anime?.poster) { … }`. Isinya `loadBitmap(context, poster, 64)` (helper dari sub-proyek A), lalu `Palette.from(bitmap).generate()` di `Dispatchers.Default`. Gagal → null.
- Bila ada tint:
  - **Gradien:** sebuah lapisan di belakang header, setinggi 420dp, dengan `Brush.verticalGradient(tint × 0,55 alpha → transparan)`. Di atasnya tetap ada backdrop yang memudar ke background.
  - **Tombol putar:** `containerColor` = tint versi terang, yaitu tint yang dinaikkan kecerahannya sampai luminansi ≥ 0,45 dengan `lighten(tint, 0.45)`, fungsi murni di file yang sama. `contentColor` = `onColorFor(container)`.
- Tanpa tint: tampilan sama seperti sekarang.

## Bagian 3 — Bar frosted (Haze, #12)

- Helper `Modifier.frosted(state: HazeState)` di `ui/components/Frosted.kt`:
  - `hazeEffect(state)` dengan `blurRadius = 24.dp`, tint `MaterialTheme.colorScheme.background` alpha 0,72, `backgroundColor = background`.
  - Haze otomatis memakai scrim tanpa blur di API < 31.
- **Bottom nav:**
  - Konten tab tidak lagi diberi padding bawah oleh Scaffold. Bar ditempatkan di atas konten (overlay) dan diberi `.frosted(state)`.
  - Konten NavHost diberi `hazeSource(state)`.
  - Tinggi bar (diukur dengan `onSizeChanged`) dibagikan lewat `LocalNavBarPadding: Dp`.
  - List di 4 tab (Beranda, Jelajah, Jadwal, Koleksi) menambahkannya ke `contentPadding` bawah, sehingga item terakhir tetap bisa digulir sampai terlihat.
  - Saat bar tersembunyi (Detail, Player, dll.) nilainya 0.
- **Top bar Detail:** daftar Detail diberi `hazeSource`. `DetailTopBar` saat `solid` memakai `.frosted(state)` dan bukan warna solid `surfaceContainer`.
- **Statistik & Pengaturan:**
  - `LargeTopAppBar` diberi `colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = Transparent, scrolledContainerColor = Transparent)` plus `.frosted(state)`.
  - `LazyColumn` diberi `hazeSource`. Kedua layar sudah memakai `contentPadding` top dari Scaffold, jadi konten sudah lewat di bawah bar.

## Bagian 4 — Layar besar (#16)

- Fungsi murni `useNavRail(widthDp: Int): Boolean = widthDp >= 600` di `ui/navigation/NavLayout.kt`.
- `FuwaNavHost`:
  - Bila rail: `Row { NavigationRail(...) ; konten }`. Rail memakai item tab dan ikon yang sama, warna `surfaceContainerLow`, tanpa frosted, karena konten tidak lewat di bawahnya.
  - Bila tidak: bottom bar overlay frosted seperti Bagian 3.
  - Rail juga tersembunyi di rute non-tab, sama seperti bar.
- `Modifier.readableWidth()` = `fillMaxWidth().wrapContentWidth(CenterHorizontally).widthIn(max = 840.dp)` dipasang pada konten Detail, Pengaturan, Statistik dan Legal.
- Grid poster sudah `GridCells.Adaptive(108.dp)`, jadi tidak diubah.

## Bagian 5 — Font 200% dan TalkBack (#10)

### Font

- Tombol putar Detail: `.height(52.dp)` → `.heightIn(min = 52.dp)`.
- Audit file UI (`grep -E "\.height\([0-9]+\.dp\)"`): setiap tinggi tetap yang **berisi teks** menjadi `heightIn(min = …)`. Tinggi untuk gambar, garis, progress dan skeleton tetap.
- Judul di hero Beranda, kartu poster, baris riwayat, baris jadwal dan baris episode harus punya `maxLines` + `TextOverflow.Ellipsis`. Tambahkan di tempat yang belum ada.

### TalkBack

- Fungsi murni `posterDescription(title: String, caption: String?, badge: String?, isNew: Boolean, airing: Boolean, selected: Boolean): String` di `ui/components/A11y.kt`:
  - Urutan: judul, caption (bila ada), "Baru" (bila `isNew`), "Sedang tayang" (bila `airing` dan bukan `isNew`), badge (bila ada), "dipilih" (bila `selected`).
  - Dipisah ", ".
- `PosterCard`:
  - `Modifier.semantics(mergeDescendants = true) { contentDescription = posterDescription(...) }` pada root kartu.
  - Bila `onLongClick != null`: `customActions = listOf(CustomAccessibilityAction(longClickLabel) { onLongClick(); true })`.
  - Parameter baru `longClickLabel: String = "Pilih"`.
  - Gambar di dalam kartu tidak lagi punya `contentDescription` sendiri (null), agar tidak dibaca dua kali.
- `HistoryRow` (Koleksi): `semantics(mergeDescendants = true)` + custom action "Pilih" untuk long-press.
- `ScheduleRow` (Jadwal) dan baris episode Detail: `semantics(mergeDescendants = true)`. Tombol unduh di baris episode tetap node sendiri, karena punya `contentDescription` sendiri.

## Error handling

- Palette gagal atau poster tidak termuat → null → tampilan default.
- Haze di API < 31 → scrim, tanpa crash.
- Lebar layar berubah (lipat/buka foldable, rotasi) → `useNavRail` dihitung ulang dari `LocalConfiguration.current.screenWidthDp` setiap komposisi.

## Testing

- **JVM:**
  - `posterTint`: urutan fallback, clamp luminansi ≤ 0,25, null.
  - `lighten`: luminansi ≥ 0,45.
  - `onColorFor`: putih di atas gelap, hitam di atas terang.
  - `relativeLuminance`: hitam 0, putih 1.
  - `posterDescription`: kombinasi.
  - `useNavRail`: 599/600.
- **CI:** `ci-test.sh` (unit test + release build dengan lint NewApi fatal).
- **Tidak diuji di perangkat:** blur, warna, 200% font, TalkBack, rail di tablet. Laporan akhir menyebutkannya.

## Di luar cakupan

- Dua panel daftar–detail.
- Material 3 Expressive.
- Mewarnai ulang seluruh aplikasi per judul.
- Compose UI test / screenshot test.
