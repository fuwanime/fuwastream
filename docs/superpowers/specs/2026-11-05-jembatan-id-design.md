# Jembatan ID & metadata (sub-proyek C) — Desain

Tanggal: 2026-11-05 · Branch: `feat/jembatan-id`

## Konteks & tujuan

Sub-proyek ketiga dari implementasi riset `reports/Riset fitur dan UI UX FuwaStream.md` (urutan A → D; A dan B sudah di-merge ke fork lewat PR #23 dan #24). Dokumen ini mencakup:

- **#4** Pemetaan ID katalog → AniList/MAL
- **#5** AniSkip: lewati OP/ED/recap
- **#6** Urutan tonton, "Tonton selanjutnya", countdown tayang, skor, tag
- **#14** Jelajah multi-genre, "Acak", tab "Musim ini" di Jadwal

### Hasil spike pemetaan ID (5 Nov 2026, 200 judul katalog)

Sampel: 200 judul teratas dari `explore` (urut views), dicari di AniList GraphQL.

| Ukuran | Hasil |
|---|---|
| Cocok persis judul/sinonim + tahun `aired_start` sama (cari judul) | 161/200 |
| + cari pakai sinonim, toleransi tahun ±1 | +35 → **196/200 (98%)** |
| Spot-check 15 kecocokan persis | 15/15 benar |
| Kecocokan yang punya `idMal` | 160/161 |
| AniSkip punya data ep 1 (60 judul tercocok) | 57/60 |
| Fribb/anime-lists | 7,5 MB, 39.664 entri, **hanya ID tanpa judul**; cocok slug anime-planet 131/200 |

Implikasi:
- Pencocokan memakai pencarian AniList, lazy per layar Detail, dengan hasil disimpan di Room. Ini sesuai ketentuan AniList (tanpa pengumpulan massal).
- Fribb tidak dipakai.
- Field `year` katalog adalah tahun terbaru, bukan tahun mulai (One Piece = 2026). Pencocokan memakai tahun dari `aired_start`.

Temuan lain: daftar franchise di detail sumber ("Season & Terkait") sudah membawa `aired_start` per entri. Karena itu urutan tonton dan "Tonton selanjutnya" memakai data katalog sendiri, bukan relasi AniList, sehingga tidak ada risiko salah cocok balik.

### Keputusan pengguna

- **Salah atau tidak yakin cocok:** sembunyikan enrichment, plus tombol "Bukan anime ini?" untuk melepas tautan. Tanpa layar pilih manual.
- **AniSkip:** tombol + penanda seekbar. Setelan lama "Lewati OP otomatis" (default mati) juga berlaku untuk OP dari AniSkip, dengan chip "Intro dilewati · Batalkan". Tanda OP buatan pengguna selalu menang.
- **Metadata di Detail:** countdown tayang, skor + tag, "Tonton selanjutnya", urutan tonton.
- **Multi-genre:** AND.
- **Acak:** ikon di Jelajah, ikut filter aktif.
- **Jadwal:** tab ke-8 "Musim ini".

**Sukses berarti:**
- Detail anime populer menampilkan skor, tag, dan countdown episode berikutnya (untuk yang sedang tayang).
- Player menawarkan "Lewati OP/ED/Recap" dari data komunitas tanpa pengguna menandai apa pun.
- Di akhir episode terakhir sebuah season muncul kartu season berikutnya.
- Jelajah bisa memfilter beberapa genre sekaligus dan membuka anime acak.
- Jadwal punya ringkasan musim berjalan.
- Semua enrichment bersifat tambahan. Saat AniList atau AniSkip mati atau lambat, semua layar berfungsi seperti sekarang.

## Bagian 1 — Jembatan ID (#4)

### Data

Tabel Room baru `anime_link` (database v3 → v4, `MIGRATION_3_4` dengan `CREATE TABLE IF NOT EXISTS`):

| Kolom | Tipe | Arti |
|---|---|---|
| `animeId` | TEXT PK | ID katalog |
| `state` | TEXT | `LINKED`, `NONE` (dicari, tidak ketemu), `UNLINKED` (dilepas pengguna) |
| `anilistId` | INTEGER? | |
| `malId` | INTEGER? | |
| `score` | INTEGER? | `averageScore` AniList (0–100) |
| `tags` | TEXT | nama tag non-spoiler, dipisah `\n`, maks 6, urut rank |
| `nextEpisode` | INTEGER? | `nextAiringEpisode.episode` |
| `nextAiringAt` | INTEGER? | epoch detik `nextAiringEpisode.airingAt` |
| `fetchedAt` | INTEGER | epoch ms terakhir dicari atau diperbarui |

`clearAll()` di `LibraryRepository` ikut mengosongkan tabel ini.

### Pencocokan

Fungsi murni `pickMatch(title: String, synonyms: List<String>, airedYear: Int?, candidates: List<AniListMedia>, yearTolerance: Int): AniListMedia?`:
- `norm(s)`: NFKD → ASCII, huruf kecil, buang kata `season|the|part|cour`, buang semua non-alfanumerik.
- Kandidat cocok bila irisan `norm(title + synonyms)` dengan `norm(romaji, english, native, synonyms kandidat)` tidak kosong, **dan** (`airedYear` null **atau** tahun kandidat `seasonYear ?: startDate.year` ada dan selisihnya ≤ `yearTolerance`).
- Mengembalikan kandidat cocok pertama sesuai urutan AniList.

Alur `AnimeLinkRepository.link(anime)`:
1. Baca baris yang ada:
   - `UNLINKED` → selesai (tidak ada enrichment).
   - `LINKED` → pakai, dan perbarui meta bila perlu (lihat Cache).
   - `NONE` dengan umur < 7 hari → selesai.
2. Cari judul katalog (`perPage: 6`) dengan `yearTolerance = 0`. Bila tidak cocok, cari hingga 3 sinonim pertama dengan `yearTolerance = 1`. Maksimal 4 request, berhenti di kecocokan pertama.
3. Simpan `LINKED` dengan ID + meta dari respons yang sama, atau `NONE`.
4. Error jaringan, HTTP 429, atau 5xx: tidak menyimpan apa pun, mengembalikan "tidak ada data" untuk kunjungan ini.

`airedYear` = 4 karakter pertama `Anime.airedStart` bila berupa angka, selain itu null. Sinonim = `Anime.alternativeTitles` dipisah `,` atau `;`, di-trim, yang kosong dibuang.

### Cache

- `LINKED`: ID permanen. Meta diperbarui dengan query by-id bila `fetchedAt` lebih tua dari 12 jam, **atau** `nextAiringAt` sudah lewat.
- `NONE`: dicoba ulang setelah 7 hari.
- `UNLINKED`: permanen.

### Klien

- `AniListApi`: POST `https://graphql.anilist.co`, body `{"query", "variables"}`, Moshi. Dua query:
  - `search(s)`: `Page(perPage:6){media(search:$s,type:ANIME){...fields}}`
  - `byId(id)`: `Media(id:$id){...fields}`
  - Fields: `id idMal format seasonYear startDate{year} title{romaji english native} synonyms averageScore tags{name rank isMediaSpoiler} nextAiringEpisode{episode airingAt}`
- Memakai `metaHttpClient` baru di `AppContainer`: `httpClient.newBuilder()` dengan interceptor dibersihkan (tanpa User-Agent katalog), timeout 10 detik.

### UI

Footer kecil di bawah info Detail bila `LINKED`: "Info dari AniList · Bukan anime ini?". Mengetuk "Bukan anime ini?" menampilkan dialog konfirmasi ("Sembunyikan info AniList dan lewati otomatis dari AniSkip untuk anime ini?"). Konfirmasi menyimpan `UNLINKED`, dan semua enrichment hilang dari layar.

## Bagian 2 — Metadata di Detail (#6)

`DetailViewModel` meminta `link(anime)` setelah detail termuat. Hasilnya `AnimeMeta(score: Int?, tags: List<String>, nextEpisode: Int?, nextAiringAt: Long?)` masuk ke `DetailUiState.meta`. Bila null, tidak ada yang tampil dan tata letak tidak bergeser.

- **Skor:** chip "★ 8,4" (`score / 10.0`, satu desimal, koma desimal). Tidak tampil bila null atau 0.
- **Countdown:** hanya bila `nextAiringAt` di masa depan. Fungsi murni `airingLabel(episode: Int, airingAtMs: Long, nowMs: Long, tz: TimeZone): String`:
  - < 60 menit → "Ep 6 · 45m"
  - < 24 jam → "Ep 6 · 2j 14m"
  - selain itu → "Ep 6 · Sab 23.00" (hari singkat `Day.short`, jam 24 jam dengan titik)
- **Tag:** maks 6 chip non-interaktif setelah genre.
- **Urutan tonton:** "Season & Terkait" diurutkan fungsi murni `watchOrder(current: Anime, seasons: List<SeasonEntry>): List<SeasonEntry>`:
  - Entri **termasuk anime yang sedang dibuka** diurutkan menurut `airedStart` naik.
  - Entri tanpa `airedStart` di akhir, urut seperti dari sumber.
  - Kartu entri yang sedang dibuka diberi label "Sedang dilihat" dan tidak bisa diketuk.
  - Sekarang anime yang sedang dibuka dibuang dari daftar (`relatedSeasons`). Daftar tampil bila ada ≥ 1 entri lain.
- **Tonton selanjutnya:** fungsi murni `nextInFranchise(currentId: String, ordered: List<SeasonEntry>): SeasonEntry?`, yaitu entri setelah `currentId` di `watchOrder`, atau null.
  - `PlayerViewModel` memuat franchise lewat `detailWithSeasons(animeId)` (cache yang sudah ada) dan menaruhnya di `PlayerUiState.nextSeason`.
  - `EndOverlay`: bila tidak ada episode berikutnya (`next == null`) dan `nextSeason != null`, tampil kartu "Lanjut ke {label}: {judul}" dengan poster. Ketuk → buka Detail anime itu (rute Detail yang ada), menggantikan player.

## Bagian 3 — AniSkip (#5)

### Data

- `AniSkipApi`: GET `https://api.aniskip.com/v2/skip-times/{malId}/{episode}`, query `types[]=op&types[]=ed&types[]=mixed-op&types[]=mixed-ed&types[]=recap&episodeLength={detik}`. Respons `{found, results:[{interval:{startTime,endTime}, skipType, episodeLength}]}`. Memakai `metaHttpClient`.
- Fungsi murni `skipSegments(results, durationMs): List<SkipSegment>`:
  - `SkipSegment(kind: SegmentKind, startMs, endMs)`, dengan `enum class SegmentKind { OP, ED, RECAP }`.
  - Pemetaan: `op`/`mixed-op` → OP, `ed`/`mixed-ed` → ED, `recap` → RECAP.
  - Buang hasil yang `|episodeLength × 1000 − durationMs| > 5 000`, atau `endMs ≤ startMs`.
  - Satu segmen per jenis (yang pertama), urut `startMs`.
- `PlayerViewModel`:
  - Begitu durasi player diketahui (> 0) dan episode berganti, panggil sekali per episode bila `malId` ada (dari `anime_link`, `LINKED`) dan nomor episode bisa dibaca sebagai bilangan bulat.
  - Hasil di `StateFlow<List<SkipSegment>>`, cache di memori per `episodeId`.
  - Gagal atau tidak ditemukan → daftar kosong.

### Perilaku

- `skipActionAt(positionMs, durationMs, mark, jumpAhead, segments)` diperluas. Prioritas:
  1. Tanda OP pengguna valid → seperti sekarang (`SkipOp`). Segmen OP AniSkip diabaikan.
  2. Posisi di dalam segmen AniSkip (`start ≤ pos < end − 1 000`) → `SkipSegmentAction(kind, targetMs = endMs)`. OP hanya dipakai bila tidak ada tanda pengguna.
  3. Tidak ada tanda dan tidak ada segmen OP AniSkip → lompatan generik +85 detik seperti sekarang (`JumpAhead`, tunduk pada setelan `jumpAheadButton`).
- Label tombol: "Lewati OP" (tanda pengguna atau OP AniSkip), "Lewati ED", "Lewati recap".
- **Auto-skip:** `shouldAutoSkip` berlaku untuk `SkipOp` **dan** `SkipSegmentAction(kind = OP)`, sekali per episode, saat `autoSkipOp` aktif. ED dan recap tidak pernah otomatis.
- **Chip "Intro dilewati · Batalkan":** muncul 5 detik setiap kali auto-skip terjadi. "Batalkan" kembali ke awal segmen dan tidak auto-skip lagi untuk episode itu.
- **Penanda seekbar:** segmen AniSkip dan tanda OP pengguna digambar sebagai pita tipis di track slider (warna `primary` 40% alpha).

## Bagian 4 — Jelajah & Jadwal (#14)

### Multi-genre

- `ExploreFilters.genre: String?` → `genres: Set<String>`. Fungsi murni `matchesGenres(anime, genres): Boolean`: semua genre terpilih harus ada di `anime.genres` (tidak peka huruf besar/kecil). Aturan khusus "Isekai" (sinopsis mengandung "isekai") tetap berlaku per genre.
- Chip genre di Jelajah menjadi toggle multi-pilih.
- Judul hasil: genre digabung dengan " + ".
- `activeCount` menghitung jumlah genre.
- Navigasi dari chip genre di Detail tetap membuka Jelajah dengan satu genre (saved state key `genre` dibaca sebagai set satu elemen).

### Acak

- Ikon dadu (`Icons.Rounded.Casino`, `contentDescription` "Anime acak") di bar Jelajah.
- Ketuk: buka Detail satu anime acak dari **hasil yang sudah dimuat** di bawah filter aktif.
- Nonaktif bila hasil kosong.
- Fungsi murni `pickRandom(results: List<Anime>, random: Random): Anime?`.

### Tab "Musim ini" di Jadwal

- Tab ke-8 setelah Minggu, label "Musim ini". Membuka tab memuat ketujuh hari (memakai `ensureLoaded` yang ada).
- Fungsi murni:
  - `seasonOf(year: Int, month: Int): AnimeSeason`. `data class AnimeSeason(val name: String, val year: Int, val startMonth: Int)`: Jan–Mar "Musim Dingin", Apr–Jun "Musim Semi", Jul–Sep "Musim Panas", Okt–Des "Musim Gugur".
  - `splitBySeason(all: List<Anime>, season: AnimeSeason): Pair<List<Anime>, List<Anime>>`: gabungan 7 hari dedupe per `id`. "Baru musim ini" = `airedStart` (yyyy-MM-dd) jatuh di 3 bulan musim itu. "Lanjutan" = sisanya. Masing-masing urut judul.
- Tampilan: header "Musim Gugur 2026", lalu dua seksi berjudul "Baru musim ini (N)" dan "Lanjutan (N)" dengan kartu yang sama seperti tab hari.
- Error dan loading memakai state hari yang ada. Bila semua hari gagal → `ErrorState` dengan coba lagi.

## Error handling

- AniList atau AniSkip timeout, gagal, atau 429: diam-diam tanpa enrichment (log saja). Tidak ada snackbar atau error yang terlihat pengguna.
- Respons AniList yang tidak sesuai skema: diperlakukan sebagai gagal, tidak menyimpan `NONE`.
- Migrasi Room v4 harus menjaga semua tabel lama (diuji lewat skenario QA upgrade-from-release).
- Semua permintaan enrichment berjalan di `Dispatchers.IO` dan tidak memblokir tampilan detail atau mulainya pemutaran.

## Testing

- **JVM:**
  - `pickMatch`: persis + tahun; sinonim ("Boku no Hero Academia S2" → "Boku no Hero Academia 2" lewat sinonim "My Hero Academia Season 2"); tahun beda 1 ditolak di toleransi 0 dan diterima di 1; tanpa kandidat → null; `airedYear` null.
  - `norm`.
  - Parsing respons AniList dan AniSkip dengan MockWebServer + fixture JSON.
  - `skipSegments`: filter episodeLength, pemetaan jenis, satu per jenis.
  - `skipActionAt` dengan segmen: tanda pengguna menang, OP AniSkip, ED, generik hanya tanpa segmen OP.
  - `shouldAutoSkip` untuk OP AniSkip dan bukan ED.
  - `watchOrder`, `nextInFranchise`.
  - `airingLabel`: tiga rentang + zona waktu.
  - `matchesGenres` AND + aturan Isekai.
  - `pickRandom` kosong/tidak kosong.
  - `seasonOf` (empat musim, batas bulan), `splitBySeason` (dedupe, batas tanggal, `airedStart` kosong → Lanjutan).
  - Aturan cache (`needsRefresh(link, nowMs)`: 12 jam, `nextAiringAt` lewat, `NONE` 7 hari, `UNLINKED` tidak pernah).
- **QA emulator** (skenario baru di `qa.sh`):
  - Detail anime populer → screenshot skor/tag/footer AniList.
  - Putar episode 1 → setelah ±10 detik tombol "Lewati OP" ada (dari AniSkip) bila judul tercakup.
  - Jelajah: pilih dua genre → screenshot; ketuk dadu → Detail terbuka.
  - Jadwal: tab "Musim ini" → screenshot.
  - Skenario lama tetap lolos, termasuk upgrade-from-release (migrasi v4).

## Di luar cakupan

- Layar pilih kandidat manual.
- Sinkronisasi tracker (AniList/MAL login).
- Relasi AniList (sekuel dicari dari franchise katalog).
- Pemilih musim lain selain musim berjalan.
- Pre-fetch metadata untuk daftar (hanya per Detail; menghormati ketentuan AniList).
- Penanda ED/recap buatan pengguna.
