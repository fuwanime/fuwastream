# Statistik & akses, Backup, Bahasa — Desain

Tanggal: 2026-11-07 · Tiga sub-proyek, tiga PR berurutan ke fork `SakamotoMrX/fuwanime`:

1. `feat/stats-akses` — Statistik + kemudahan akses
2. `feat/backup` — Backup data yang tahan uninstall
3. `feat/bahasa` — Bahasa Melayu, English, 中文

Urutan ini disengaja: Bahasa menyentuh semua layar, jadi dikerjakan terakhir supaya teks baru dari 1 dan 2 ikut dipindah sekaligus.

## Konteks & tujuan

Permintaan pengguna (2026-11-07): backup data yang selamat walau app dihapus, UI/UX Statistik dan akses umum diperbaiki ("susah diakses, harusnya gampang"), tambah bahasa Melayu, Inggris, Mandarin. **App harus tetap ringan.**

Kondisi sekarang:

- Statistik hanya bisa dibuka lewat ikon 📊 tanpa label di top bar Koleksi (`LibraryScreen.kt:214`).
- Pengaturan hanya bisa dibuka dari ikon ⚙ di top bar Beranda.
- Beberapa aksi penting cuma ikon: Unduh semua & urutan di daftar episode Detail, Anime acak di Jelajah.
- Data hanya ikut Auto Backup Google (`backup_rules.xml`), yang tergantung setelan HP dan tidak dijamin.
- Semua teks (±320 string unik + halaman Privasi/Ketentuan ±10 KB) ditulis langsung dalam Bahasa Indonesia di Kotlin; `strings.xml` hampir kosong; tidak ada `stringResource`.
- Dependensi yang sudah ada dan dipakai ulang: Room, DataStore, Moshi, WorkManager. Tidak ada appcompat.

Keputusan pengguna:

- Akses Statistik: **kartu "Minggu ini" di atas Koleksi**, tetap 4 tab.
- Akses umum: **⚙ Pengaturan juga di Koleksi** dan **ikon penting diberi label**. Tekan-lama di Koleksi tidak diubah.
- Backup: **file JSON di folder pilihan user + backup otomatis harian + pulihkan dari file**. Tanpa Google Drive SDK.
- Bahasa: **default ikut HP + pilihan di Pengaturan**. UI diterjemahkan ke ms, en, zh-CN. Privasi/Ketentuan hanya ID + EN; ms dan zh memakai versi EN.
- Verifikasi: unit test + release build lewat `ci-test.sh`. QA emulator hanya kalau diminta.

**Sukses berarti:**

- Orang yang membuka Koleksi langsung melihat ringkasan minggu ini dan bisa masuk ke Statistik dengan satu tap.
- Pengaturan bisa dibuka dari Beranda dan Koleksi.
- Setelah uninstall lalu install ulang, user bisa memulihkan favorit, riwayat, statistik, riwayat cari, dan pengaturan dari file backup.
- Mengganti bahasa di Pengaturan mengubah seluruh teks app tanpa restart manual; di Android 13+ pilihan juga muncul di Setelan sistem → Bahasa aplikasi.
- Ukuran APK rilis naik kurang dari 150 KB untuk ketiga sub-proyek. Tidak ada dependensi baru.

---

## Sub-proyek 1 — Statistik & akses (`feat/stats-akses`)

### 1.1 Kartu "Minggu ini" di Koleksi

- Kartu di paling atas isi Koleksi (di atas chip Lanjut/Favorit/Unduhan), lebar penuh, gaya sama dengan `StatCard` (surfaceContainerLow + border outlineVariant, radius 18dp).
- Isi: label "Minggu ini", nilai `watchTimeLabel(ms7Hari)` + "· N episode", grafik batang mini 7 hari (Canvas tinggi 32dp; hari ini berwarna primary, lainnya primary 40% alpha), teks "Lihat statistik ›".
- Seluruh kartu bisa diklik (`Modifier.clickable` + `semantics(mergeDescendants)`), deskripsi TalkBack: "Statistik minggu ini, 3 jam 20 menit, 12 episode. Buka statistik".
- Kalau belum ada data sama sekali: kartu tetap tampil dengan teks "Mulai nonton untuk melihat statistikmu" dan tetap membuka Statistik. Kartu tidak tampil saat mode pilih-banyak aktif.
- Ikon 📊 di top bar Koleksi dihapus; diganti ikon ⚙ "Pengaturan" yang memanggil `onSettings` → `Routes.SETTINGS`.

Data: `episodes7` dihitung dari `allHistory` (episode yang `isFinished` dan `updatedAt` dalam 7 hari terakhir), `ms7` dari `watch_daily` untuk 7 hari terakhir. Logika murni baru di `WatchStats.kt`:

```kotlin
data class DayBar(val day: Int, val minutes: Int)
fun lastNDays(daily: List<WatchDailyEntity>, today: Int, n: Int = 7): List<DayBar>  // urut lama→baru, hari kosong = 0
data class WeekSummary(val watchedMs: Long, val episodes: Int, val days: List<DayBar>)
fun weekSummary(history: List<WatchHistoryEntity>, daily: List<WatchDailyEntity>, today: Int, nowMs: Long): WeekSummary
```

`LibraryViewModel` mengekspos `week: StateFlow<WeekSummary?>` dari `combine(allHistory, watchDaily)`.

### 1.2 Layar Statistik

- **Hero**: angka total waktu tonton besar (displaySmall, ExtraBold, primary) dengan label "Total waktu nonton" di atasnya, menggantikan kartu "Jam tonton". Caption "± perkiraan untuk data lama" tetap ada bila `isEstimated`.
- **Kartu**: Episode selesai, Anime, Minggu aktif tetap 3 kartu. Di skala font ≥ 1.5 atau lebar < 360dp, kartu ditumpuk satu kolom (`LocalDensity.current.fontScale`).
- **Tap kartu** → `Snackbar` singkat yang menjelaskan arti angka (mis. "Minggu aktif: jumlah minggu berturut-turut kamu menonton, sampai minggu ini").
- **Grafik 7 hari** (baru) di atas grafik 6 bulan: batang hari ini/bulan ini berwarna primary, lainnya primary 45% alpha; nilai menit kecil di atas batang yang > 0. Label hari pakai singkatan nama hari dari `java.text.DateFormatSymbols` (ikut locale, berguna untuk sub-proyek 3).
- **Grafik 6 bulan**: sama, ditambah nilai di atas batang dan highlight bulan ini.
- **Empty state**: tambah tombol "Cari anime" yang membuka Jelajah (`onExplore`).
- Rekap & bagikan tidak berubah.

### 1.3 Label pada aksi penting

- Detail, header daftar episode: ikon Unduh semua dan Urutan diganti `TextButton` berikon dengan label "Unduh semua" dan "Terbaru"/"Terlama" (label mengikuti urutan aktif). Bila lebar tidak cukup (font besar), baris boleh wrap (`FlowRow`).
- Jelajah: ikon dadu menjadi `AssistChip` "Acak" berikon.
- Ikon yang tetap ikon (top bar Beranda Cari/Pengaturan, kontrol player, ⚙ Koleksi) dibungkus `TooltipBox` Material3 sehingga tekan-lama menampilkan namanya. Satu helper `@Composable fun IconWithTooltip(label: String, onClick: () -> Unit, icon: ImageVector, ...)` di `ui/components/A11y.kt`, dipakai di tempat-tempat tersebut.

### 1.4 Test

- `lastNDays`: hari kosong diisi 0; urutan lama→baru; data di luar jendela diabaikan.
- `weekSummary`: menghitung episode selesai 7 hari terakhir saja; episode belum selesai tidak dihitung; ms dijumlah dari `watch_daily`.
- QA script: ganti pemeriksaan ikon Statistik di Koleksi (bila ada) menjadi tap pada teks "Minggu ini".

---

## Sub-proyek 2 — Backup (`feat/backup`)

### 2.1 Isi file

`fuwanime-backup.json` (UTF-8, Moshi):

```json
{
  "format": 1,
  "app": "fuwanime",
  "createdAt": 1762500000000,
  "favorites": [ FavoriteEntity... ],
  "history": [ WatchHistoryEntity... ],
  "watchDaily": [ { "day": 20399, "watchedMs": 1200000 } ],
  "searches": [ { "query": "frieren", "searchedAt": 1762... } ],
  "settings": [ { "key": "theme_mode", "type": "string", "value": "dark" } ]
}
```

- `settings` berisi semua kunci DataStore **kecuali**: `update_last_check_ms`, `update_dismissed_tag`, `notify_baseline_*`, `backup_*` (kunci milik fitur backup sendiri). Tipe yang didukung: string, boolean, int, long, double. Kunci tak dikenal di file saat pulihkan tetap ditulis sesuai tipenya (aman, DataStore mengabaikan yang tak dipakai).
- Tidak dicadangkan: `downloads` (file video tidak ikut) dan `anime_link` (cache AniList, bisa diambil ulang).
- Ukuran khas < 200 KB.

### 2.2 Lokasi & otomatis

- Pengaturan → bagian baru **"Cadangan"**:
  - "Folder backup" → `ActivityResultContracts.OpenDocumentTree`; URI disimpan dengan `takePersistableUriPermission(READ|WRITE)` di DataStore `backup_tree_uri`. Subjudul menampilkan nama folder atau "Belum dipilih".
  - "Cadangkan otomatis" (switch, default aktif setelah folder dipilih).
  - "Cadangkan sekarang" + subjudul "Terakhir: 7 Nov 2026 14.05" (`backup_last_ms`) atau pesan galat terakhir.
  - "Pulihkan dari file" → `OpenDocument("application/json", "application/octet-stream", "*/*")`.
- File ditulis ke `<folder>/fuwanime-backup.json` lewat `DocumentFile`-free API: `DocumentsContract.createDocument` bila belum ada, lalu `contentResolver.openOutputStream(uri, "wt")`. Tulis dulu ke `fuwanime-backup.tmp.json` lalu rename (`DocumentsContract.renameDocument`) supaya backup lama tidak rusak bila proses mati; bila rename tidak didukung provider, tulis langsung dengan mode `"wt"`.
- `BackupWorker` (CoroutineWorker) periodik 24 jam, `ExistingPeriodicWorkPolicy.KEEP`, constraint `setRequiresBatteryNotLow(true)`. Dijadwalkan saat folder dipilih dan auto aktif; dibatalkan saat auto dimatikan. Juga satu kali segera setelah folder dipilih.
- Bila izin folder hilang (folder dihapus/izin dicabut): worker gagal tanpa retry, menyimpan `backup_error`, dan Pengaturan menampilkan "Folder tidak bisa diakses — pilih ulang".

### 2.3 Pulihkan

- Parse → validasi: `app == "fuwanime"`, `format <= 1`. Format lebih baru → "Backup dibuat versi app yang lebih baru. Perbarui app dulu." File rusak → "File backup tidak bisa dibaca." Pada galat **tidak ada data yang diubah**.
- Dialog konfirmasi menampilkan ringkasan: "12 favorit, 340 riwayat, tanggal backup 7 Nov 2026".
- **Gabung**, bukan timpa, dalam satu transaksi Room (`withTransaction`):
  - Favorit: tambahkan yang belum ada (`INSERT OR IGNORE`).
  - Riwayat: per `episodeId`, ambil yang `updatedAt` lebih baru.
  - Watch daily: per `day`, ambil `max(lokal, backup)` (bukan dijumlah, supaya pulihkan dua kali tidak menggandakan).
  - Riwayat cari: per `query`, ambil `searchedAt` lebih baru.
  - Pengaturan: nilai dari backup ditulis, kecuali kunci yang dikecualikan di 2.1.
- Setelah sukses: snackbar "Data dipulihkan".

Logika gabung murni (tanpa Room) di `data/backup/BackupMerge.kt` supaya bisa di-unit-test:

```kotlin
fun mergeHistory(local: List<WatchHistoryEntity>, backup: List<WatchHistoryEntity>): List<WatchHistoryEntity> // baris yang perlu di-upsert
fun mergeDaily(local: List<WatchDailyEntity>, backup: List<WatchDailyEntity>): List<WatchDailyEntity>
fun mergeSearches(local: List<SearchHistoryEntity>, backup: List<SearchHistoryEntity>): List<SearchHistoryEntity>
fun newFavorites(local: List<FavoriteEntity>, backup: List<FavoriteEntity>): List<FavoriteEntity>
```

Serialisasi di `data/backup/BackupCodec.kt`: `encode(BackupFile): String`, `decode(String): BackupResult` (`Ok(file)` / `TooNew` / `Corrupt`).

### 2.4 Banner pulihkan setelah install

- Bila Room kosong (tidak ada favorit dan riwayat) dan `backup_prompt_dismissed` belum di-set, Beranda menampilkan kartu tutup-able sekali: "Pernah pakai fuwanime? Pulihkan data dari file backup" → membuka pemilih file yang sama. Tutup atau sukses → `backup_prompt_dismissed = true`.
- Auto Backup Google tetap seperti sekarang.

### 2.5 Test

- `BackupCodec`: round-trip semua bagian; `format: 2` → `TooNew`; JSON terpotong → `Corrupt`; `app` lain → `Corrupt`.
- `BackupMerge`: riwayat lebih baru menang di kedua arah; daily pakai max dan idempoten (gabung dua kali = sekali); favorit lokal tidak tertimpa; pencarian ambil yang lebih baru.
- Filter kunci pengaturan: `update_*`, `notify_baseline_*`, `backup_*` tidak ikut.

---

## Sub-proyek 3 — Bahasa (`feat/bahasa`)

### 3.1 Memindah teks

- Semua teks yang tampil ke user dipindah ke `res/values/strings.xml` (Indonesia = default). Format argumen `%1$s`/`%1$d`; jumlah memakai `<plurals>` (en/ms butuh "1 episode"/"2 episodes"; zh dan id tetap satu bentuk).
- Compose memakai `stringResource`/`pluralStringResource`. Kode non-Compose (notifikasi `EpisodeNotifier`, `PlaybackService`, widget Glance, `shortcuts`, worker) memakai `context.getString`.
- Teks di `ViewModel` (pesan galat/snackbar) diubah menjadi `@StringRes Int` (+ argumen) dan di-resolve di UI, supaya ikut bahasa yang aktif.
- Nama bulan (`MONTH_NAMES`) dan hari diganti `DateFormatSymbols`/`SimpleDateFormat` dengan locale aktif. `watchTimeLabel` memakai string resource (`%1$d jam %2$d mnt`).
- Nama brand "fuwastream", judul anime, genre dari API tidak diterjemahkan.

### 3.2 Terjemahan

- `values-ms`, `values-en`, `values-zh-rCN` dengan kunci yang sama persis.
- Halaman Privasi/Ketentuan dipindah ke string resource `legal_*` (satu kunci per bagian) yang hanya ada di `values` (ID) dan `values-en`. Fallback bawaan Android akan jatuh ke ID untuk ms/zh, jadi LegalScreen mengambil teksnya dari context dengan locale eksplisit: helper `legalLocale(appLocale): Locale` → `id` bila bahasa id, selain itu `en`, lalu `createConfigurationContext(config with that locale).resources`.
- Terjemahan dibuat oleh asisten; catatan di README bahwa ms & zh perlu dicek penutur asli.

### 3.3 Pilihan bahasa

- `res/xml/locales_config.xml` berisi `id, ms, en, zh-CN`; manifest `android:localeConfig`. Di Android 13+ ini memunculkan app di Setelan sistem → Bahasa aplikasi.
- Pengaturan → "Bahasa": Sistem / Bahasa Indonesia / Bahasa Melayu / English / 中文（简体）, nama tiap bahasa ditulis dalam bahasanya sendiri.
- API 33+: `LocaleManager.setApplicationLocales(LocaleList.forLanguageTags(tag))` (kosong = Sistem). Sistem me-recreate activity sendiri.
- API 24–32: tag disimpan di `SharedPreferences("locale")` (sinkron, dibaca sebelum Compose). `MainActivity.attachBaseContext` membungkus context dengan `createConfigurationContext` berlocale itu; setelah ganti, `activity.recreate()`. `FuwaApp` juga menerapkan locale yang sama untuk teks dari notifikasi/worker.
- Fungsi murni untuk ditest: `fun resolveAppLocale(tag: String?, system: Locale): Locale` (null/kosong → system bila salah satu dari id/ms/en/zh, selain itu `id`).

### 3.4 Test

- Kelengkapan kunci: unit test membaca semua `strings.xml` (path dari `src/main/res`) dan memastikan setiap kunci di `values` ada di `values-ms`, `values-en`, `values-zh-rCN` (kecuali `legal_*` yang hanya wajib di `values-en`), dan jumlah placeholder `%n$` sama.
- `resolveAppLocale`: tag eksplisit dipakai; kosong + sistem `ms_MY` → ms; kosong + sistem `fr` → id; `zh-TW` sistem → zh-CN (satu-satunya varian Mandarin).
- `legalLocale`: id → id; ms/en/zh → en.
- Lint `MissingTranslation` dibiarkan aktif sebagai lapis kedua.

---

## Di luar lingkup

- Sinkron cloud/akun, Google Drive API.
- Backup file video unduhan.
- Bahasa selain empat di atas; Mandarin tradisional.
- Perubahan tekan-lama di Koleksi.
- Tab ke-5.

## Risiko

- Provider SAF tertentu (mis. beberapa folder cloud) tidak mendukung rename → jatuh ke tulis langsung; risiko file setengah tertulis bila proses mati di tengah, kecil karena file < 200 KB.
- Doze dapat menunda backup harian; tidak masalah karena tidak ada tenggat.
- Memindah ±320 string berisiko teks tertinggal atau placeholder salah urutan; dimitigasi test kelengkapan kunci dan placeholder, plus lint.
- Terjemahan mesin untuk ms/zh bisa terdengar kaku.
- Perubahan locale di API < 33 me-recreate activity; state layar yang tidak disimpan (mis. scroll) bisa hilang sekali.
