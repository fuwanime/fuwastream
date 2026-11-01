# Redesign Sinematik: Beranda, Detail, Jelajah, Koleksi

Tanggal: 2026-11-01
Status: draf, menunggu review
Mockup acuan: https://claude.ai/artifact/FviJ1TF18guNx5WEmKc2un (canvas "FuwaStream Redesign", 4 artboard)

## Tujuan

Membuat empat layar utama terasa lebih rapi dan "premium" dengan gaya sinematik:

- cover anime menjadi pusat layar,
- latar hampir hitam,
- satu warna aksen (ungu `#D4BBFF`) yang dipakai hemat.

Perubahan ini hanya mengubah tampilan. Tidak ada data API baru, tidak ada fitur baru di luar yang tertulis di bawah, dan perilaku navigasi serta animasi shared element yang sudah disetujui ("oke udah pas", build-79 sampai build-85) harus tetap sama.

Redesign dianggap berhasil kalau:

1. Keempat layar di app cocok dengan mockup pada hal-hal yang tertulis di spec ini.
2. Animasi buka/tutup poster, backdrop dan thumbnail tetap mulus, tanpa lock dan tanpa layar kosong.
3. Build CI dan unit test lolos, dan QA emulator (mode `qa`) tidak menemukan regresi transisi.

## Di luar cakupan

- Layar Pemutar, Jadwal dan Setelan. Layar ini hanya ikut berubah lewat token tema dan komponen bersama.
- Merapikan tema terang. Tema terang tetap jalan karena semua komponen memakai peran warna `MaterialTheme`, tapi tidak dipoles.
- Elemen di mockup yang tidak punya fitur di app: ikon notifikasi (lonceng), tombol bagikan dan tombol unduh di atas halaman detail. Bagian "Penyimpangan dari mockup" di bawah menjelaskan alasannya.
- Durasi episode. API tidak memberi durasi, jadi baris episode tetap menampilkan tanggal rilis dan jumlah tonton.

## Fondasi

### Warna (tema gelap)

Ubah `DarkScheme` di `ui/theme/Theme.kt`. Peran yang tidak disebut di sini tetap seperti sekarang.

| Peran | Sekarang | Baru | Dipakai untuk |
|---|---|---|---|
| `background`, `surface` | `#0F0D15` | `#0B0A0F` | latar layar |
| `surfaceContainerLow` | (bawaan) | `#16141C` | kartu, kolom cari, baris aktif |
| `surfaceContainer` | (bawaan) | `#1A1721` | chip, tombol sekunder, nav bar |
| `surfaceContainerHigh` | (bawaan) | `#1F1C27` | placeholder gambar, skeleton |
| `secondaryContainer` | `#7B2949` | `#2A2238` | indikator tab nav dan tab segmented yang aktif |
| `outlineVariant` | (bawaan) | `#2A2633` | garis chip dan tombol bertepi |
| `onSurfaceVariant` | (bawaan) | `#A39DAE` | teks sekunder (kontras 7:1 di atas latar) |
| `primary` | `#D4BBFF` | tetap | aksen tunggal |

Mode AMOLED (`toAmoled()`) dan warna dinamis Android 12+ tetap jalan seperti sekarang.

`secondaryContainer` dipakai `Pill` bawaan. Pill yang perlu warna lain (misalnya "EP N" di kartu) memberi warnanya sendiri.

### Huruf

- Pakai **Plus Jakarta Sans** dengan bobot 400, 600, 700 dan 800. File `.ttf`-nya dibundel di `res/font` supaya jalan offline dan tidak menunggu unduhan. Lisensinya OFL; teks lisensi disimpan di `app/src/main/assets/licenses/PlusJakartaSans-OFL.txt`.
- `AppTypography` memakai font ini di semua gaya, dengan bobot dan letter-spacing yang sekarang.
- Judul besar (`displaySmall`, `headlineMedium`, `headlineSmall`) diberi letter-spacing `-0.02em` sampai `-0.03em` seperti di mockup.

### Bentuk

`AppShapes` tetap. Radius di mockup (10, 12, 14 dp) sudah tercakup oleh `small` (10) dan `medium` (16). Kartu poster memakai `medium`, sedangkan tombol utama dan kolom cari memakai radius 14 dp secara lokal.

### Nav bawah (`FuwaNavHost.kt`)

- Tetap `NavigationBar` M3 dengan empat tab yang sama (Beranda, Jelajah, Jadwal, Koleksi).
- Gayanya diubah lewat `NavigationBarItemDefaults.colors`:
  - Latar: `background` dengan alpha 0.94, plus garis atas 1 dp `#1F1C27`.
  - Tab aktif: indikator `secondaryContainer` (`#2A2238`), ikon `primary`, label tebal `onSurface`.
  - Tab tidak aktif: ikon dan label `#8A8494`.
- Animasi sembunyi/muncul nav tidak diubah.

## Komponen bersama (`ui/components/Components.kt`)

### `PosterCard`

- **Overlay dihapus.** Scrim bawah dan teks "tipe · tahun" yang sekarang menempel di atas gambar dihilangkan. Gambar poster jadi bersih.
- **Badge "Tayang"** tetap di pojok kiri atas, tapi berubah jadi pil gelap (`#0B0A0F` alpha 0.78) dengan titik `#FF7A93`. Titik ini menggantikan `LiveDot` hijau.
- **Teks di bawah gambar:**
  - Judul: 2 baris, `labelLarge` semibold.
  - Baris meta `onSurfaceVariant`. Isinya caption dari pemanggil. Kalau pemanggil tidak memberi caption, isinya "tipe · tahun".
- **Badge "BARU"** baru, opsional (parameter `isNew`). Bentuknya pil `primary` dengan teks `onPrimary`, dipakai baris Episode Terbaru.
- **Animasi:** `sharedKey`, `aboveSharedElements` dan `revealWhenLanded` tetap dipasang di elemen yang masih ada (gambar, badge Tayang, badge BARU).

### `ContinueWatchingCard`

- Ukuran tetap 240 dp, rasio 16:9.
- Lingkaran play di tengah dan overlay gelap penuh dihapus. Diganti gradien bawah tipis dengan label "EP N" di kiri bawah.
- Progress bar 3 dp `primary` tetap.
- Di bawah gambar: judul anime 1 baris, lalu "Sisa X menit" atau "Selesai ditonton".
  - "Sisa X menit" dihitung dari `durationMs - positionMs` di `WatchHistoryEntity`.
  - Kalau durasi tidak diketahui, tampilkan judul episode seperti sekarang.

### `SectionHeader`

- Judul `titleMedium` tebal (18 sp).
- Tautan "Lihat semua" jadi teks kecil `onSurfaceVariant` tanpa panah.
- Subtitle tetap opsional.

### Fungsi baru (murni, dites)

- `remainingLabel(positionMs, durationMs): String?` → "Sisa 14 menit", "Sisa 1 menit", atau `null` kalau durasi ≤ 0. Dipakai kartu Lanjut Nonton, tombol utama Detail dan baris Riwayat.
- `historyDayLabel(timestamp, now): String` → "HARI INI", "KEMARIN", atau tanggal ("28 OKT"). Dipakai untuk mengelompokkan Riwayat.

## Beranda (`ui/home/HomeScreen.kt`)

### Hero

`HeroPager` tetap memakai `HorizontalPager` dengan auto-advance 6 detik dan indikator titik. Isi tiap halamannya diganti.

**Gambar dan tinggi**

- Tinggi turun dari 600 dp ke 470 dp.
- Gambar: `anime.backdrop` (cover, atau poster kalau cover kosong) full-bleed dengan crop.
- Blur poster dan poster kecil di tengah dihapus.
- Gradien: gelap tipis di atas (supaya top bar terbaca), bening di tengah, lalu pekat ke `background` di bawah.

**Teks, rata kiri di bagian bawah**

- Pil aksen "#N MUSIM INI" (bentuk label `primary`, huruf kapital), lalu meta sekunder "tipe · tahun".
- Judul `headlineMedium` ExtraBold, maksimal 2 baris.
- Baris genre/jumlah tonton seperti meta yang sekarang.

**Tombol**

- **"Tonton"**: putih (`onSurface`) dengan teks gelap dan ikon play. Membuka Detail, sama seperti sekarang.
- **"+"**: tombol bertepi 50 dp yang men-toggle favorit lewat `LibraryRepository`. Ikonnya jadi centang saat sudah favorit.
- Tombol Info yang sekarang dihapus. Tombol "+" menggantikan posisinya.

**Animasi**

Gambar hero memakai `sharedBackdropKey(anime.id, "home-hero")`. Detail harus mengaitkan backdrop-nya (bukan posternya) saat dibuka dari origin `home-hero`. Hasilnya: hero mengecil masuk ke backdrop Detail, lalu kembali saat ditutup. Kunci poster `home-hero` yang sekarang dilepas.

### Top bar

- Wordmark jadi "fuwa" (warna `primary`) + "stream" (`onSurface`), ExtraBold 21 sp, huruf kecil.
- Ikon Cari dan Setelan tetap, tanpa lonceng.
- Perilaku latar saat scroll tetap.

### Seksi

Urutan tetap: hero, banner update, Lanjutkan Nonton, Episode Terbaru, Tayang Hari Ini, Sedang Tayang, Terpopuler, Jelajahi Genre. Yang berubah per seksi:

- **Lanjutkan Nonton**: memakai `ContinueWatchingCard` versi baru.
- **Episode Terbaru**:
  - Poster 112×160 dp dengan badge BARU.
  - Caption satu baris "Episode N · 2 jam lalu", dipotong dengan elipsis kalau tidak muat.
- **Terpopuler**:
  - Jadi baris peringkat. Angka 1–10 besar (120 sp, ExtraBold) bergaris tepi `#4A4455` di belakang kiri poster 106×152 dp.
  - Tanpa judul di bawah. Judul tetap ada sebagai `contentDescription`.
  - "Lihat semua" tetap ke Jelajah.
- **Tayang Hari Ini, Sedang Tayang**: hanya ikut gaya `PosterCard` baru.
- **Jelajahi Genre**: chip bertepi `outlineVariant`, radius penuh, latar `surfaceContainer`.

Banner update tidak dipindah. Warnanya disesuaikan ke `surfaceContainerLow` dengan tepi `primary` tipis.

## Detail (`ui/detail/DetailScreen.kt`)

### Header

- Backdrop 280 dp + `fadeToBackground` tetap.
- Poster 116 dp di kiri tetap, dengan bayangan yang lebih dalam.
- Kolom kanan:
  - Judul `headlineSmall` ExtraBold.
  - Judul alternatif pertama sebagai subjudul.
  - Baris meta "tahun · tipe · status · N EP". `StatusPill` dan `Pill` tipe digabung ke baris teks ini.
- Statistik pindah ke bawah header sebagai teks: "**2,1 jt** ditonton   **29 rb** favorit", memakai `compactNumber`. Ikonnya dihapus.

### Genre

Tetap `FlowRow` yang membuka Jelajah. Gaya chip sama dengan chip genre di Beranda.

### Aksi

**Tombol utama**

- Lebar penuh, tinggi 52 dp, warna `primary`, radius 14 dp.
- Label dua baris: baris atas "Lanjut EP N" / "Tonton EP N" / "Memuat episode…", baris bawah "Sisa X menit" kalau ada riwayat.
- Progress bar resume yang sekarang ada di bawah tombol dihapus, karena sisa waktunya sudah tertulis di label.

**Favorit**

- Tombol 52 dp bertepi `outlineVariant` di latar `surfaceContainer`.
- Ikon hati penuh berwarna `primary` saat aktif.
- Animasi pop tetap.

**Yang tidak ada di mockup ini**

Tombol unduh dan bagikan tidak ditambahkan. Unduh massal tetap lewat ikon di header Episode.

### Sinopsis

- Tiga baris.
- Teks tombol "Selengkapnya" / "Tutup" tebal di bawahnya.
- `animateContentSize` tetap.
- Judul "Sinopsis" dihapus.

### Info

`InfoGrid` tetap, dengan latar `surfaceContainerLow`.

### Season & Terkait

- Season yang sedang dibuka ditandai bingkai 2 dp `primary`, poster redup 55%, dan caption "Season N · Ini" berwarna `primary`.
- Kalau daftar tidak memuat season yang sedang dibuka, tidak ada yang ditandai.

### Anime Serupa

Hanya ikut gaya `PosterCard`.

### Episode

- `EpisodeHeader`: judul "Episode · N" (jumlah episode `onSurfaceVariant`). Ikon unduh massal, cari dan urut tetap.
- Chip rentang episode memakai gaya chip baru.
- `EpisodeRow`:
  - Thumbnail 124×70 dp, radius 10.
  - **Sudah ditonton:** thumbnail redup 40% dengan centang di tengah. Ikon `CheckCircle` di kanan dihapus.
  - **Episode resume:** baris berlatar `surfaceContainerLow` dan judul berwarna `primary`, seperti sekarang.
  - Badge BARU memakai gaya baru.
  - Tombol unduh per episode tetap di kanan.

## Jelajah (`ui/explore/ExploreScreen.kt`)

**Kolom cari**

- Tinggi 50 dp, radius 14 dp (bukan pil), latar `surfaceContainerLow`, tepi `#24212C`.
- Placeholder "Judul, genre, atau studio".
- Riwayat pencarian tetap muncul saat kolom fokus dan kosong.

**Filter**

`FilterBar` dipecah jadi dua baris.

1. **Baris Tipe**:
   - Chip satu-pilihan untuk semua nilai `TypeFilter` (Semua, Series, Film, Live Action, ONA, OVA, Special).
   - Chip terpilih berlatar `primary` dengan teks `onPrimary`. Yang lain bertepi.
   - Baris ini menggantikan dropdown Tipe.
2. **Baris Genre**: chip genre yang sekarang, dengan gaya chip baru.

**Header hasil**

- Teks "Semua anime" atau nama genre/tipe yang dipilih.
- Di kanan: tombol ringkas "Populer ▾" yang membuka menu urutan, plus menu status di menu yang sama (bagian "Status": Semua/Tayang/Tamat).
- Dropdown Sort dan Status yang terpisah sekarang dihapus.

**Grid**

- Tetap `GridCells.Adaptive(108.dp)` (3 kolom di HP), memakai `PosterCard` baru.
- Caption "X ditonton" diganti "tipe · tahun" (bawaan kartu), karena jumlah tonton tidak membantu memilih di grid.
- Paging, skeleton, state kosong dan error tetap.

## Koleksi (`ui/library/LibraryScreen.kt`)

**Tab**

- `PrimaryTabRow` diganti kontrol segmented: wadah `surfaceContainerLow` radius 14 dp, tiga tombol sama lebar 40 dp.
- Tombol aktif berlatar `secondaryContainer` dengan teks `primary`.
- Hitungan tetap tampil: "Riwayat · 5".
- Swipe `HorizontalPager` tetap. Kontrol segmented mengikuti halaman pager.

**Riwayat**

- Dikelompokkan per hari dengan judul kecil kapital (`historyDayLabel`): HARI INI, KEMARIN, atau tanggal.
- Baris:
  - Thumbnail 132×74 dp dengan pil "EP N" di kiri atas dan progress bar 3 dp.
  - Judul anime tebal 1 baris.
  - "Sisa X menit" atau "Selesai ditonton".
  - Tombol hapus (X) di kanan, tetap seperti sekarang.
- Ketuk judul membuka Detail, ketuk thumbnail memutar. Tidak berubah.

**Favorit dan Unduhan**

Hanya ikut gaya `PosterCard` dan warna baru. Struktur dan aksinya tetap.

## Penyimpangan dari mockup (disengaja)

| Di mockup | Di app | Alasan |
|---|---|---|
| Ikon lonceng di Beranda | Ikon Setelan | App belum punya pusat notifikasi; Setelan hanya bisa dibuka dari sini |
| "Tonton EP 5" di hero | "Tonton" (membuka Detail) | Data hero tidak membawa episode terbaru |
| Bagikan & unduh di Detail atas | Tidak ada | Belum ada fitur bagikan; unduh sudah per episode dan massal |
| Durasi "24 mnt" di episode | Tanggal rilis · tonton | API tidak memberi durasi |
| Menu tiga titik di Riwayat | Tombol hapus (X) | Satu-satunya aksi yang ada adalah hapus |
| Hanya 3 seksi di Beranda | Semua seksi tetap | Tidak membuang konten yang sudah ada |

## Risiko

- **Animasi shared element.** Hero beralih dari kunci poster ke kunci backdrop, dan overlay label di `PosterCard` berkurang. Keduanya menyentuh mekanisme yang baru distabilkan. Mitigasi:
  - `MotionTest` diperbarui untuk kunci baru.
  - Satu run QA emulator (animasi menyala, rekaman layar) untuk buka/tutup dari hero, kartu poster, Lanjut Nonton dan Season.
- **Ukuran APK** naik sekitar 300–400 KB karena empat file font.
- **Tema terang** belum dipoles. Kontrasnya tetap aman karena memakai peran warna bawaan, tapi tampilannya belum dicek.

## Pengujian

- Unit test JVM untuk `remainingLabel` dan `historyDayLabel`, termasuk tepi: durasi 0, sisa < 1 menit, pergantian hari tepat tengah malam, dan tanggal tahun lalu.
- `MotionTest` diperbarui kalau ada fungsi kunci shared yang berubah.
- CI: `testDebugUnitTest` + `assembleRelease` lewat workflow `build.yml`.
- QA emulator (`qa=true`): rekaman buka/tutup Detail dari hero, poster, Lanjut Nonton dan Season, lalu pindah tab. Hasilnya diperiksa sebelum merge ke `main`.
- Tidak ada test UI Compose. Repo belum punya, dan menambah infrastrukturnya di luar cakupan.

## Pembagian kerja

Satu branch `feat/redesign-sinematik`, satu PR ke `main` fork, sehingga terbit satu release build-N. Urutan pengerjaannya:

1. Fondasi: warna, font, nav bar.
2. Komponen bersama dan dua fungsi murni beserta test-nya.
3. Beranda.
4. Detail.
5. Jelajah.
6. Koleksi.
7. QA emulator, lalu PR.
