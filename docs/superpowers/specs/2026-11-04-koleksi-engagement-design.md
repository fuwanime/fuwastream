# Koleksi & Engagement (sub-proyek A) — Desain

Tanggal: 2026-11-04 · Branch: `feat/koleksi-engagement`

## Konteks & tujuan

Riset `reports/Riset fitur dan UI UX FuwaStream.md` menghasilkan 16 kandidat. User minta semua diimplementasi kecuali #8 ("Masih menonton?" + Data Saver), berurutan dalam 4 sub-proyek:

- **A. Koleksi & engagement** (dokumen ini): #3 ergonomi Koleksi, #7 shortcut + widget, #11 notifikasi bergambar, #15 statistik tonton
- B. Player latar: #13 upgrade Media3/BOM/Coil 3, #1 MediaSession, #9 poles PiP + mode aspek
- C. Jembatan ID & metadata: #4 pemetaan MAL/AniList, #5 AniSkip, #6 urutan tonton + countdown, #14 Jelajah multi-genre/Acak/musim
- D. Visual & aksesibilitas: #10 font 200% + TalkBack, #12 backdrop poster + bar frosted, #16 navigasi/grid adaptif

**#2 backup/restore ditunda** atas keputusan user ("untuk backup data nanti saja").

**Sukses berarti:**
- Favorit bisa diurutkan dan menunjukkan berapa episode baru yang belum ditonton.
- Riwayat dan Favorit bisa dihapus banyak sekaligus; unduhan yang sudah ditonton bisa dibersihkan sekali ketuk.
- Dari layar utama HP: shortcut Cari/Jadwal/Lanjutkan dan widget "Lanjutkan menonton" yang langsung membuka player.
- Notifikasi episode baru menampilkan poster dan dikelompokkan.
- Layar statistik dengan jam tonton yang akurat sejak update ini, dan kartu rekap yang bisa dibagikan sebagai gambar.
- Semua tampilan baru memakai token tema yang ada (gelap sinematik); tidak ada komponen default yang kasar.

## Bagian 1 — Ergonomi Koleksi

**Urutan Favorit.** `enum class FavoriteSort { ADDED, LAST_WATCHED, TITLE }`, disimpan di `SettingsStore` (key `favorite_sort`, default `ADDED`). Chip pilihan di atas grid Favorit: "Ditambahkan", "Terakhir ditonton", "A–Z".
- `ADDED`: `addedAt` menurun (perilaku sekarang).
- `LAST_WATCHED`: `updatedAt` riwayat terbaru per anime menurun; anime tanpa riwayat di akhir, urut `addedAt` menurun.
- `TITLE`: judul, tidak peka huruf besar/kecil.
- Fungsi murni `sortFavorites(favs, lastWatched: Map<String, Long>, sort): List<FavoriteEntity>`.

**Badge "N baru".** Fungsi murni `newEpisodeCount(latestKnown: Double?, watchedNumbers: List<String>): Int`:
- `latestKnown` = baseline yang sudah disimpan `EpisodeNotifyWorker` (`settings.notifyBaseline(animeId)`), jadi tidak ada request tambahan.
- Episode tertinggi yang ditonton = maks `episodeNumber.toDoubleOrNull()` di riwayat anime itu (episode yang sekadar dibuka sudah tersaring oleh ambang 5 detik di `saveProgress`).
- Hasil = `floor(latestKnown) - floor(maxWatched)` bila > 0, selain itu 0. Tanpa baseline atau tanpa riwayat → 0 (badge tidak muncul; anime yang belum pernah ditonton tidak dianggap "baru").
- Badge: pill kecil `primary` di pojok kanan atas poster, teks `labelSmall` "N baru" (maks "9+ baru"). `contentDescription` ikut dibaca TalkBack.

**Pilih banyak (Riwayat & Favorit).** Long-press item → mode pilih. Selama mode pilih: ketuk = toggle, header Koleksi berganti menjadi "N dipilih" + tombol Hapus + tombol tutup; back gesture keluar dari mode pilih. Hapus Riwayat menghapus semua riwayat anime terpilih (`removeFromHistory`); hapus Favorit memanggil `setFavorite(false)`. Konfirmasi dialog untuk ≥ 2 item. Berpindah tab mengosongkan pilihan. State pilihan adalah `Set<String>` animeId di `LibraryViewModel`.

**Hapus unduhan yang sudah ditonton.** Fungsi murni `watchedDownloads(downloads, history: Map<episodeId, WatchHistoryEntity>): List<DownloadEntity>` — hanya unduhan selesai (bukan yang sedang berjalan) dengan riwayat `isFinished`. Tombol "Hapus yang sudah ditonton (N • ukuran)" di atas daftar Unduhan, hanya tampil bila N > 0, dengan dialog konfirmasi.

## Bagian 2 — Shortcut & widget

**Shortcut.**
- Statis (`res/xml/shortcuts.xml`): "Cari" → tab Jelajah dengan fokus ke kolom cari; "Jadwal" → tab Jadwal.
- Dinamis: "Lanjutkan <judul>" untuk riwayat terbaru, didorong lewat `ShortcutManagerCompat.pushDynamicShortcut` setiap `saveProgress` yang berasal dari keluar player (ON_STOP/ended), bukan dari tick 5 detik. Ikon = ikon app (tanpa unduh poster).
- `MainActivity` menerima extra baru: `EXTRA_OPEN_TAB` (`"explore_search"`, `"schedule"`) dan `EXTRA_RESUME_EPISODE_ID`. Resume membuka player untuk episode itu dengan posisi tersimpan (alur `onResume(WatchHistoryEntity)` yang sudah ada di Koleksi).

**Widget "Lanjutkan menonton"** (Jetpack Glance, `glance-appwidget`):
- Isi: sampai 3 anime terbaru dari `observeLatestPerAnime`, masing-masing poster, judul, "Ep N", dan bar progres. Ukuran kecil (2×2) menampilkan 1 item, lebih lebar menampilkan sampai 3 (`SizeMode.Responsive`).
- Ketuk item → `MainActivity` dengan `EXTRA_RESUME_EPISODE_ID`. Ketuk area kosong/header → buka app.
- Kosong: "Belum ada tontonan" + tombol "Jelajahi".
- Gaya: latar `#16141C`, sudut 24 dp, teks `#E8E0EE`/`#A39DAE`, progres `#D4BBFF`. Tidak memakai dynamic color.
- Poster dimuat lewat Coil ke `Bitmap` kecil (≤ 160 px lebar) saat `provideGlance`; gagal → kotak `surface` polos.
- Refresh: `updateAll()` dipanggil bersamaan dengan push shortcut dinamis (keluar player), saat hapus riwayat, dan saat app dibuka. Tidak ada periodic update.

## Bagian 3 — Notifikasi bergambar

Di `EpisodeNotifier.notifyNewEpisode`:
- Muat poster (`favorite.poster`) sebagai `largeIcon` dan cover (`favorite.cover`, fallback poster) untuk `BigPictureStyle` lewat Coil `ImageLoader.execute` dengan timeout 10 detik. Gagal/timeout → notifikasi teks seperti sekarang.
- `setGroup("new_episodes")`. Bila worker memposting ≥ 2 notifikasi dalam satu run, posting juga ringkasan grup "N episode baru" (`InboxStyle` berisi judul-judul) dengan `setGroupSummary(true)`.
- `notifyNewEpisode` menjadi `suspend` (dipanggil dari `CoroutineWorker`).

## Bagian 4 — Statistik & kartu rekap

**Pencatatan waktu tonton.**
- Room v2 → v3, tabel baru `watch_daily(day TEXT PRIMARY KEY /* yyyy-MM-dd, zona perangkat */, watchedMs INTEGER NOT NULL)`. Migrasi `MIGRATION_2_3` hanya `CREATE TABLE`.
- DAO `addWatchTime(day, ms)`: upsert yang menambahkan (`INSERT ... ON CONFLICT(day) DO UPDATE SET watchedMs = watchedMs + :ms`).
- Loop 5 detik di `PlayerScreen` yang sudah ada: bila `player.isPlaying`, panggil juga `vm.addWatchTime(5_000)`. Waktu dinding, bukan dikali kecepatan putar.

**Agregasi** — fungsi murni `computeStats(history, daily, favorites, today): WatchStats`:
- `totalWatchedMs` = jumlah `watch_daily`, ditambah perkiraan untuk masa sebelum update: jumlah `positionMs` riwayat yang `updatedAt`-nya sebelum hari pertama `watch_daily` (atau semua riwayat bila `watch_daily` kosong). `isEstimated = true` bila bagian perkiraan > 0; UI menampilkan "± perkiraan untuk data lama".
- `episodesFinished` = jumlah riwayat `isFinished`; `animeWatched` = jumlah animeId unik di riwayat.
- `topGenres` = 3 genre terbanyak dari `favorites.genres` (dipisah koma, di-trim, tidak peka huruf besar/kecil).
- `monthly` = 6 bulan terakhir termasuk bulan ini: menit per bulan dari `watch_daily`; bulan tanpa data = 0.
- `activeWeeks` = jumlah minggu berturut-turut (Senin–Minggu) sampai minggu ini yang punya `watch_daily` > 0; minggu ini yang belum ada tontonan tidak memutus hitungan (mulai dari minggu lalu).

**Layar Statistik** — rute baru `stats`, dibuka dari ikon `Insights` di header Koleksi. Isi: 4 kartu angka (Jam tonton, Episode selesai, Anime, Minggu aktif), bar chart 6 bulan (Canvas, batang `primary`, label bulan singkat Indonesia), chip 3 genre teratas, tombol "Bagikan rekap". Kosong (tanpa riwayat) → `MessageState` "Belum ada statistik".

**Kartu rekap** — composable 1080×1350 (4:5): gradien latar banner (`#0B0A0F`→`#1A1721` + glow lavender/pink), wordmark "fuwa" lavender + "stream", angka jam tonton besar, episode/anime/genre teratas, bulan-tahun. Dirender ke bitmap via `rememberGraphicsLayer()` + `toImageBitmap()`, disimpan ke `cacheDir/share/rekap.png`, dibagikan lewat `FileProvider` (authority `${applicationId}.share`, `res/xml/share_paths.xml` hanya `cache-path share/`) dengan `ACTION_SEND` `image/png`. Gagal render/simpan → snackbar "Gagal membuat gambar rekap".

## Error handling

- Semua fitur gagal secara lunak: poster widget/notifikasi gagal → tanpa gambar; shortcut API tidak tersedia → diabaikan (`ShortcutManagerCompat` menangani).
- Migrasi Room diuji; tanpa `fallbackToDestructiveMigration` agar riwayat user tidak hilang.
- Extra intent yang tidak dikenal atau episodeId yang tidak ada di riwayat → app terbuka biasa di Beranda.

## Testing

TDD, unit test JVM:
- `FavoriteSortTest`: tiga urutan, anime tanpa riwayat di akhir untuk `LAST_WATCHED`, A–Z tidak peka huruf.
- `NewEpisodeCountTest`: baseline 12 + tonton 10 → 2; tanpa baseline → 0; tanpa riwayat → 0; nomor non-angka diabaikan; tonton melebihi baseline → 0.
- `WatchedDownloadsTest`: hanya selesai + `isFinished`; unduhan berjalan tidak ikut.
- `WatchStatsTest`: total + perkiraan data lama, 6 bulan dengan bulan kosong, `activeWeeks` (minggu ini kosong tidak memutus, celah memutus), top genre.
- `WatchDailyDaoTest` / migrasi: kalau Robolectric/`MigrationTestHelper` tidak tersedia di setup JVM, query penambahan diuji lewat fake dan migrasi diverifikasi di QA (app lama v2 → build baru tidak crash, riwayat tetap ada).
- QA emulator (`qa.sh`): screenshot Favorit dengan chip urutan + badge, mode pilih, layar Statistik, kartu rekap; `dumpsys shortcut` memuat shortcut dinamis; widget ditambahkan lewat `appwidget` shell bila memungkinkan, kalau tidak hanya diverifikasi build + preview layout.

## Di luar cakupan

- Backup/restore (ditunda user), sinkronisasi cloud.
- Widget "Jadwal hari ini".
- Status tonton Rencana/Ditonton/Selesai dan pengingat tayang (sub-proyek 4 yang sudah direncanakan).
- Streak harian, XP.
