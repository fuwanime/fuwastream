# Manga: sumber kedua Shinigami — Desain

Tanggal: 2026-11-07 · Branch: `feat/manga` · Riset: `docs/research/2026-11-07-manga-api-2.md` (OMP/Gemini) + verifikasi langsung.

## Konteks

Uji pengguna build 215: banyak judul populer di MangaDex hanya punya chapter eksternal (MANGA Plus) atau tidak punya chapter, dan pengguna menolak redirect ke website ("semuanya di app"). Verifikasi langsung (2026-11-07):

- `api.shngm.io` (Shinigami): JSON, 200 OK tanpa key, gambar `assets.shngm.id` tanpa Referer, tidak diblokir DNS Telkom. ±1000 judul bahasa Indonesia (manhwa/manga/manhua), bab 1–terbaru.
- MANGA Plus API: membalas "Account Banned … violation of our Terms of Service" untuk klien tidak resmi → **ditolak**.
- Weeb Central / Webtoon: butuh parsing HTML → tidak dipilih (keputusan pengguna).

Keputusan pengguna: tambah **Shinigami** sebagai sumber kedua; tidak ada redirect website; tanpa library baru.

## Desain

- **Id:** judul dan chapter Shinigami memakai awalan `sh:` (`sh:<uuid>`); id MangaDex tetap. Riwayat, favorit, backup tidak berubah skemanya.
- **API** `data/manga/ShinigamiApi.kt` (Retrofit + Moshi):
  - `GET v1/manga/list?page&page_size&sort=popularity|latest&q=` → `data[]` (`manga_id`, `title`, `alternative_title`, `cover_portrait_url`/`cover_image_url`, `description`, `status` (1 = berjalan, 2 = tamat), `release_year`, `taxonomy.Genre[].name`, `taxonomy.Author[].name`), `meta.total_record`.
  - `GET v1/manga/detail/{id}` → `data` (bentuk sama).
  - `GET v1/chapter/{id}/list?page&page_size=100&sort_by=chapter_number&sort_order=asc` → `data[]` (`chapter_id`, `chapter_number` (angka), `chapter_title`, `release_date`), `meta.total_page`.
  - `GET v1/chapter/detail/{chapterId}` → `data.base_url`, `data.chapter.path`, `data.chapter.data[]` → URL `base_url + path + file`.
  - Header `Accept: application/json`, User-Agent browser seluler.
- **Routing:** `MangaRepository` mengarahkan `detail`, `chapters`, `pages` ke Shinigami bila id berawalan `sh:`. Chapter Shinigami: `language = "id"`, `group = "Shinigami"`, `pages = jumlah data` saat dibaca (daftar memakai `pages = 1` sebagai penanda "bisa dibaca").
- **Katalog (tab Manga):** rak Lanjut baca → **Populer Indonesia** (Shinigami `sort=popularity`) → Populer → Terbaru (MangaDex). Pencarian: Shinigami `q` dan MangaDex digabung (Shinigami dulu), duplikat judul tidak disaring.
- **Fallback di detail MangaDex:** bila tidak ada chapter yang bisa dibaca di app, cari Shinigami dengan judul (lalu judul alternatif pertama); kecocokan = judul ternormalisasi (huruf kecil, hanya huruf/angka) sama dengan judul atau salah satu `alternative_title` Shinigami. Bila cocok, tampilkan chapter Shinigami di detail yang sama dan baca dengan id `sh:`.
- **DoH:** host `shngm.io`, `shngm.id` (dan subdomain) ikut di-resolve lewat DNS-over-HTTPS seperti MangaDex.
- **Kredit:** detail/reader menyebut sumber ("Data & gambar dari Shinigami" atau "dari MangaDex").

## Test

Pemetaan DTO Shinigami (fixture JSON asli di `src/test/resources/shinigami/`), URL halaman, status, routing awalan `sh:`, pencocokan judul fallback, host DoH.

## Risiko

Shinigami adalah situs scanlation (tidak resmi): bisa berganti domain, terkena takedown, atau mengubah API. Distribusi tetap GitHub Releases.
