# Manga (M1: inti) — Desain

Tanggal: 2026-11-07 · Branch: `feat/manga` (dari `main` setelah `feat/bahasa` di-merge) · Fork: `SakamotoMrX/fuwanime` saja.

## Konteks & tujuan

Pengguna ingin app bisa membaca manga lengkap, gratis, seperti katalog anime sekarang. Riset (OMP/Gemini, `docs/research/2026-11-07-manga-api.md`) menyimpulkan:

- **MangaDex API v5** (`https://api.mangadex.org`) — resmi terdokumentasi, gratis tanpa key untuk membaca, punya chapter Indonesia (`id`) dan Inggris (`en`), gambar lewat MangaDex@Home, bisa dipanggil langsung dari Android.
- AniList GraphQL (sudah dipakai) hanya metadata; tidak dipakai di M1.
- Sumber scraper komik Indonesia, Consumet, ComicK reader: ditolak (tidak stabil, berbayar/402, atau reader sudah ditutup).

Keputusan pengguna:

- Navigasi: **saklar "Anime | Manga"** di atas Beranda dan Jelajah; tetap 4 tab. Koleksi mendapat tab "Manga".
- Reader: **scroll vertikal**.
- Lingkup awal: riwayat & lanjut baca, favorit manga, hemat data, unduh offline. Unduh offline dipisah ke **M2** (spec tersendiri) supaya M1 bisa diverifikasi dulu.
- Semua gratis, tanpa dependensi baru, teks dalam 4 bahasa (`values`, `values-ms`, `values-en`, `values-zh-rCN`).
- Verifikasi: unit test + release build lewat `ci-test.sh`; tanpa QA emulator; merge ke fork setelah CI hijau.

**Sukses berarti:**

- Dari Beranda, ganti ke "Manga", lihat Lanjut baca / Populer / Update terbaru, buka judul, mulai baca chapter berbahasa Indonesia (atau Inggris bila tidak ada), dan kembali lagi ke halaman terakhir yang dibaca.
- Setiap judul yang tampil punya minimal satu chapter `id` atau `en` yang bisa dibaca di app (atau dibuka di situs resmi bila chapter eksternal).
- Mode hemat data mengurangi ukuran gambar.
- APK rilis naik < 200 KB; tidak ada library baru.

## Bagian 1 — Data & jaringan

### 1.1 API

`data/manga/MangaDexApi.kt` (Retrofit, Moshi codegen, pola sama dengan `AniListApi`):

| Kebutuhan | Endpoint | Parameter penting |
|---|---|---|
| Cari / jelajah | `GET /manga` | `title`, `includedTags[]`, `status[]`, `availableTranslatedLanguage[]=id&availableTranslatedLanguage[]=en`, `contentRating[]=safe&contentRating[]=suggestive`, `order[followedCount]=desc` (populer) atau `order[latestUploadedChapter]=desc` (terbaru), `includes[]=cover_art`, `limit=24`, `offset` |
| Detail | `GET /manga/{id}` | `includes[]=cover_art&includes[]=author` |
| Daftar chapter | `GET /manga/{id}/feed` | `translatedLanguage[]=id&translatedLanguage[]=en`, `order[chapter]=asc`, `includes[]=scanlation_group`, `limit=500`, `offset` (ulangi sampai habis) |
| Gambar chapter | `GET /at-home/server/{chapterId}` | — |
| Daftar tag (genre) | `GET /manga/tag` | — (cache di memori per proses) |

- Konten dibatasi `safe` + `suggestive` (app punya age gate 13+; `erotica`/`pornographic` tidak ditampilkan).
- URL cover: `https://uploads.mangadex.org/covers/{mangaId}/{fileName}.256.jpg` (daftar) / `.512.jpg` (detail).
- URL halaman: `{baseUrl}/data/{hash}/{file}` atau `{baseUrl}/data-saver/{hash}/{file}` (hemat data). `baseUrl` di-cache ≤ 14 menit per chapter; kalau gambar gagal dimuat (403/404), ambil ulang `/at-home/server`.
- Judul ditampilkan: `title.en`, lalu `title.ja-ro`, lalu nilai pertama; sinopsis: `description[bahasa app]`, lalu `description.en`.

### 1.2 Klien HTTP

- `metaHttpClient` yang ada (timeout pendek) diturunkan menjadi `mangaHttpClient` dengan header `User-Agent: FuwaStream/<versionName> (github.com/SakamotoMrX/fuwanime)` sesuai ketentuan MangaDex.
- `RateLimitInterceptor`: maksimal 4 permintaan/detik ke `api.mangadex.org` (token bucket sederhana, blocking di thread OkHttp), dan pada `429` tunggu `Retry-After`/`X-RateLimit-Retry-After` (maks 10 dtk) lalu ulang sekali. Server gambar (`*.mangadex.network`, `uploads.mangadex.org`) tidak dibatasi interceptor ini dan tidak diberi header auth.
- Gambar dimuat Coil (sudah ada) dengan `ImageLoader` aplikasi.

### 1.3 Model & pemetaan (logika murni, dites)

`data/manga/MangaModels.kt`:

```kotlin
data class Manga(val id: String, val title: String, val coverUrl: String?, val description: String,
                 val tags: List<String>, val status: MangaStatus, val year: Int?, val author: String?)
data class MangaChapter(val id: String, val number: String?, val title: String?, val language: String,
                        val group: String?, val pages: Int, val externalUrl: String?, val publishAt: Long)
```

Fungsi murni:

- `pickChapters(all: List<MangaChapter>, preferred: String): List<MangaChapter>` — satu entri per nomor chapter: utamakan bahasa `preferred` (bahasa app bila `id`/`en`, selain itu `en`), lalu bahasa lain yang tersedia; di antara duplikat bahasa sama ambil `publishAt` terbaru; chapter tanpa nomor (oneshot) tetap disimpan dengan urutan `publishAt`; urut menaik secara numerik ("10.5" setelah "10").
- `nextChapter(list, currentId)` / `previousChapter(list, currentId)`.
- `pageUrls(atHome, dataSaver: Boolean): List<String>`.
- Pemetaan DTO → model (judul/sinopsis fallback, cover URL, status).

### 1.4 Repository

`data/manga/MangaRepository.kt`: `browse(order, tags, status, query, offset)`, `detail(id)`, `chapters(id)` (semua halaman feed, lalu `pickChapters`), `pages(chapterId, dataSaver)`; galat dibungkus seperti `AnimeRepository` (`UiText`/`friendlyText`). Cache memori sederhana untuk detail & chapter per sesi (LRU 20 judul).

## Bagian 2 — Penyimpanan lokal

Room versi **5**, `MIGRATION_4_5` hanya `CREATE TABLE`:

- `manga_favorite(mangaId PK, title, coverUrl, addedAt)`
- `manga_history(chapterId PK, mangaId, mangaTitle, coverUrl, chapterLabel, page, pageCount, updatedAt)` dengan index `mangaId`, `updatedAt`.

Logika murni `continueReading(history): List<MangaHistoryEntity>` — satu entri terbaru per manga, urut `updatedAt` turun.

Backup (sub-proyek sebelumnya): `BackupFile` **format 2** menambah `mangaFavorites` dan `mangaHistory` (default list kosong). Decoder menerima format 1 dan 2; `FORMAT = 2`. Gabung: favorit = tambah yang belum ada; riwayat = `updatedAt` lebih baru menang.

Pengaturan: kunci DataStore `manga_data_saver` (boolean, default false). Tidak ada pengaturan bahasa chapter terpisah — bahasa chapter mengikuti bahasa app (Bagian 1.3).

## Bagian 3 — UI

### 3.1 Saklar Anime | Manga

- `SegmentedButton` dua opsi di atas konten Beranda dan Jelajah; pilihan disimpan per proses di `FuwaNavHost` (`rememberSaveable`) dan sama untuk kedua tab.
- Mode Manga di **Beranda**: rak "Lanjut baca" (dari `continueReading`, disembunyikan bila kosong), "Populer", "Update terbaru". Kartu poster memakai komponen poster yang ada (rasio sama, `posterDescription`).
- Mode Manga di **Jelajah**: kolom cari + chip status (Berjalan/Tamat) + pilihan tag (dari `/manga/tag`, grup "genre" dan "theme"), grid hasil dengan paging `offset` saat scroll ke bawah.

### 3.2 Detail manga (`Routes.MANGA_DETAIL`)

- Cover + judul + penulis + status + tahun, sinopsis (bisa diperluas), chip tag, tombol favorit.
- Tombol utama: "Mulai baca" (chapter pertama) atau "Lanjut Ch. X" (riwayat terakhir).
- Daftar chapter: nomor, judul, bahasa (badge `ID`/`EN`), grup, tanda sudah dibaca (dari riwayat), ikon tautan untuk chapter eksternal (membuka Custom Tabs via `Intent.ACTION_VIEW`; tanpa dependensi browser).
- Atribusi di bawah daftar: "Data & gambar dari MangaDex" + tautan ke halaman judul di mangadex.org.
- State loading/kosong/galat memakai `MessageState`/`ErrorState` yang ada.

### 3.3 Reader (`Routes.MANGA_READER`)

- Layar penuh hitam, `LazyColumn` gambar Coil lebar penuh (tinggi mengikuti rasio gambar; placeholder tinggi tetap 1.4× lebar sampai dimuat), pinch-zoom tidak ada di M1.
- Tap di tengah → tampil/sembunyi bar atas (judul + chapter, kembali) dan bar bawah (halaman `n/total`, chapter sebelumnya/berikutnya).
- Akhir chapter: kartu "Chapter berikutnya" + tombol; atribusi "Scanlation: {grup} · via MangaDex" di awal dan akhir chapter.
- Posisi halaman disimpan ke `manga_history` saat halaman pertama yang terlihat berubah (debounce 1 dtk) dan saat keluar.
- Gambar gagal → tile dengan tombol "Muat ulang" (ambil ulang `baseUrl`).
- Mode hemat data dari Pengaturan memilih `data-saver`.

### 3.4 Koleksi & Pengaturan

- Koleksi: tab baru **Manga** (setelah Favorit) berisi dua bagian: "Lanjut baca" (riwayat per manga, bisa dihapus per item) dan "Favorit".
- Pengaturan: grup baru **Manga** berisi "Hemat data saat membaca" (switch, kunci `manga_data_saver`).
- "Hapus semua data" juga menghapus `manga_favorite` dan `manga_history`.

### 3.5 Teks

Semua teks baru di `res/values*/strings_manga.xml` (4 bahasa, kunci `manga_*`, plural untuk jumlah chapter). Test kelengkapan terjemahan yang ada menjaganya.

## Bagian 4 — Test

- Pemetaan DTO → model (judul fallback, cover URL, status) dari JSON contoh MangaDex (fixture di `src/test/resources/mangadex/`).
- `pickChapters`: preferensi bahasa, duplikat grup, oneshot, urutan numerik "10.5", chapter eksternal tetap ada.
- `nextChapter`/`previousChapter` di batas awal/akhir.
- `pageUrls` data vs data-saver.
- `continueReading`.
- `RateLimitInterceptor` dengan MockWebServer (sudah ada sebagai testImplementation): 429 + `Retry-After` → satu ulangan.
- Backup: decode file format 1 tetap `Ok` dengan daftar manga kosong; round-trip format 2.
- Migrasi Room 4→5 tidak diuji perangkat (tidak ada toolchain); SQL ditinjau di review.

## Di luar lingkup M1

- Unduh chapter offline (M2).
- Mode geser per halaman, kanan-ke-kiri, pinch-zoom.
- Login MangaDex, sinkron follow list, komentar.
- Skor AniList, rekomendasi.
- Notifikasi chapter baru.

## Risiko

- Judul hilang dari MangaDex karena DMCA → detail menampilkan galat "tidak ditemukan"; riwayat tetap ada.
- Rate limit `/at-home` (±40/menit) bila pengguna loncat banyak chapter cepat → interceptor + cache `baseUrl`.
- Chapter `en` jauh lebih banyak daripada `id`; pengguna Indonesia akan sering mendapat bahasa Inggris.
- Distribusi tetap lewat GitHub Releases (bukan Play Store).
- Ketentuan MangaDex: tanpa iklan, wajib atribusi MangaDex dan grup scanlation — dipenuhi di detail dan reader.
