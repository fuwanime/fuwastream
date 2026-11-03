# Cepat & Tahan Offline (sub-proyek 1) — Desain

Tanggal: 2026-11-03 · Branch: `feat/cepat-offline`

## Konteks & tujuan

User minta "perluas infra + fitur UI/UX", semua kelompok yang diusulkan, secepat mungkin, dan indikator kecepatan/offline tidak boleh mengurangi estetika redesign sinematik. Pekerjaan dipecah jadi 4 sub-proyek, masing-masing spec → plan → implementasi → rilis build-N sendiri:

1. **Cepat & tahan offline** (dokumen ini)
2. Kualitas & rilis: lint + Compose UI test di CI, crash report lokal, about/changelog in-app, layar error lebih jelas, gabung legal/age gate upstream
3. Player: pecah `PlayerScreen.kt` (1.382 baris), lalu gestur brightness/volume, kunci layar, kecepatan putar, sleep timer, preview seekbar
4. Koleksi personal: status Rencana/Ditonton/Selesai, pengingat tayang, saran pencarian, rekomendasi dari riwayat (Room v3)

**Keputusan C++/NDK: tidak dipakai.** Bottleneck app ini jaringan (9 request saat Beranda cold start, tanpa disk cache), JIT Compose saat startup, dan decode gambar/video yang sudah native (Skia, MediaCodec). C++ tidak mempercepat satu pun dari itu; NDK menambah ukuran APK, waktu CI, dan kesulitan debug. Yang benar-benar mempercepat: disk cache API (dokumen ini) dan baseline profile (dokumen ini). R8 + `shrinkResources` sudah aktif.

**Sukses berarti:**
- Buka app tanpa internet setelah pernah online → Beranda langsung tampil data terakhir, tanpa layar "Gagal memuat".
- Cold start dengan internet → Beranda tampil dari cache tanpa skeleton, lalu diperbarui diam-diam.
- Jelajah, Jadwal, Detail, daftar episode tetap tampil saat offline kalau pernah dibuka.
- Indikator memakai token desain yang ada; tidak ada spinner/banner default yang kasar.
- Median cold start (`am start -W` TotalTime) di emulator QA turun setelah baseline profile.

**Asumsi:** data tetap per-device (tanpa akun/server sendiri); API tetap `xyz-api.animein.net`.

## Bagian 1 — Lapisan cache & alur data

```
AppContainer
  apiCache   = okhttp3.Cache(cacheDir/api_cache, 50 MB)
  networkStatus: NetworkStatus            StateFlow<NetState(online: Boolean, cacheServedAt: Long?)>
  httpClient
    + OfflineFallback (application interceptor)
    + StoreAll        (network interceptor)
  api        = Retrofit(httpClient)
  cachedApi  = Retrofit(httpClient.newBuilder() + interceptor FORCE_CACHE)   // cache yang sama, tidak pernah ke jaringan
```

- **`StoreAll`** (network interceptor): untuk respons API, ganti header `Cache-Control` jadi `no-cache` (disimpan, selalu divalidasi ulang). Path `episode/streamnew/*` → `no-store` (URL stream punya token yang kedaluwarsa).
- **`OfflineFallback`** (application interceptor), hanya untuk GET ke `API_HOST`:
  - `IOException` dari jaringan → ulangi request dengan `CacheControl.FORCE_CACHE`.
  - Respons 503 atau 520–529 (Cloudflare tidak terjangkau) → perlakukan sama seperti offline.
  - Cache miss (FORCE_CACHE menghasilkan 504): kalau pemicunya `IOException` → lempar ulang exception asli; kalau pemicunya respons 503/52x → kembalikan respons asli itu. Dengan begitu `friendlyMessage()` tetap bekerja.
  - Saat menyajikan cache: set `NetworkStatus.cacheServedAt = response.receivedResponseAtMillis`. Request jaringan sukses → `cacheServedAt = null`.
  - 4xx dan 5xx lain tidak disentuh.
- **`NetworkStatus.online`**: dari `ConnectivityManager.registerDefaultNetworkCallback` (onAvailable/onLost), nilai awal dari `activeNetwork` + capability `INTERNET`.
- **Offline gratis di semua layar API**: Jelajah, Jadwal, Detail, daftar episode tidak diubah; interceptor yang menangani.
- **Cache-first, hanya Beranda**:
  - `AnimeRepository(api, cachedApi)`. `explore`, `schedule`, `latestReleases` dapat parameter `cacheOnly: Boolean = false` yang memilih `cachedApi`.
  - `HomeViewModel.init`: `fetch(cacheOnly = true)` diam-diam → kalau ada data, tampilkan dengan `loading = false, refreshing = true` → lalu fetch jaringan seperti sekarang.
  - Jaringan gagal saat sudah ada data di layar → data tetap, tanpa layar error, `error = null`.
  - Hasil cache yang datang setelah hasil jaringan dibuang (pakai penanda "sudah dapat dari jaringan").
  - Saat `networkStatus.online` berubah false → true dan data di layar berasal dari cache → refresh diam-diam.
- **Tidak di-cache**: stream, cek update GitHub (punya throttle 6 jam sendiri, client terpisah), unduhan episode (`videoHttpClient` tanpa interceptor ini).

## Bagian 2 — Indikator (estetika tetap)

Komponen baru di `ui/components/Components.kt`, hanya memakai token yang ada.

1. **`RefreshHairline(visible: Boolean)`**: `LinearProgressIndicator` indeterminate 2 dp, warna primary, track transparan, tepat di bawah top bar Beranda. Muncul hanya saat refresh diam-diam dengan data sudah di layar (cache-first, auto-refresh saat online lagi). Fade `Motion.Short`, tidak menggeser layout. Reduce motion aktif → tidak ditampilkan. Pull-to-refresh tetap memakai indikatornya sendiri.
2. **`OfflinePill`**: pill melayang 8 dp di atas `NavigationBar`, tengah, tampil di keempat tab (dipasang di `FuwaNavHost`). Di rute overlay (Detail, Player) NavigationBar tidak ada, jadi pill juga tidak tampil; video tidak pernah tertutup.
   - Latar `surfaceContainerHigh` alpha 0.92, garis 1 dp `outlineVariant`, radius 50, ikon `CloudOff` 14 dp, teks `labelMedium` `onSurfaceVariant`.
   - Teks: `"Offline · data ${relativeAgeLabel(now - cacheServedAt)}"` kalau sudah menyajikan cache, selain itu `"Offline"`.
   - Saat online lagi: berubah jadi `"Kembali online"` dengan titik primary selama 1,5 detik, lalu hilang (slide + fade, gaya yang sama dengan label poster).
3. **Layar error**: `ErrorState` hanya muncul kalau tidak ada cache sama sekali. Saat offline, pesannya `"Belum ada data tersimpan. Sambungkan internet lalu coba lagi."`.

`relativeAgeLabel(ageMs: Long): String` di `util/Format.kt`: `< 1 menit` → `"baru saja"`, `< 60 menit` → `"N menit lalu"`, `< 24 jam` → `"N jam lalu"`, selain itu `"N hari lalu"`.

## Bagian 3 — Baseline profile & ukur startup

- Modul baru `:baselineprofile` (`com.android.test` + plugin `androidx.baselineprofile`). `BaselineProfileGenerator` (UiAutomator): cold start → tunggu teks "Episode Terbaru" → scroll Beranda + geser hero → buka satu poster → back → tab Jelajah, Jadwal, Koleksi.
- `:app`: plugin baselineprofile (consumer) + `androidx.profileinstaller`. APK dipasang dari GitHub Releases (sideload), jadi profileinstaller yang mengompilasi profil saat pertama dibuka.
- Versi plugin/benchmark diverifikasi kompatibel dengan AGP 9.1.1 saat menulis plan.
- CI: input `workflow_dispatch` baru `profile=true` (manual). Menjalankan generator di emulator API 34, meng-upload `baseline-prof.txt` sebagai artifact; file di-commit ke `app/src/main/baseline-prof.txt`. Build biasa tidak menjalankan generator.
- `qa.sh`: langkah `startup` baru — 5× `am force-stop` + `am start -W`, median `TotalTime` ditulis ke `startup.txt` di artifact. Dibandingkan sebelum (main) vs sesudah.
- **Fallback**: kalau generator tidak bisa jalan di emulator CI, bagian cache dan indikator tetap dirilis; baseline profile pindah ke sub-proyek 2.

## Bagian 4 — Error handling & testing

**Error handling**
- Cache korup → ditangani `okhttp3.Cache` (entri buruk dilewati).
- Direktori cache gagal dibuat → client tanpa cache, perilaku seperti sekarang.
- Data cache tidak pernah menimpa data jaringan yang lebih baru (Bagian 1).

**Testing** (TDD, unit test JVM; tambah `com.squareup.okhttp3:mockwebserver:4.12.0` sebagai `testImplementation`)
- `ApiCacheTest` (MockWebServer + client asli dengan `Cache` di direktori temp):
  - respons tersimpan disajikan saat server mati, `cacheServedAt` terisi
  - `episode/streamnew/*` tidak pernah tersimpan
  - offline + cache miss → `IOException` asli
  - 404 dari server tidak diganti cache
  - 503 dari server → fallback ke cache
  - request sukses setelah fallback → `cacheServedAt` kembali null
- `HomeCacheFirstTest` (dua `FakeAnimeApi`: jaringan dan cache):
  - cache ada + jaringan gagal → feed tampil, `error == null`
  - cache ada + jaringan sukses → data jaringan yang dipakai
  - cache kosong + jaringan gagal → error seperti sekarang
  - hasil cache yang telat tidak menimpa data jaringan
- `FormatTest`: `relativeAgeLabel` untuk 30 detik, 5 menit, 3 jam, 2 hari.
- QA emulator: skenario baru — `svc wifi disable` + `svc data disable`, `fresh`, screenshot Beranda (data + pill offline), nyalakan jaringan, screenshot "Kembali online"; plus langkah `startup`.

## Di luar cakupan

- Cache-first di Jelajah/Jadwal/Detail (offline-nya sudah dapat dari interceptor).
- Prefetch di background, cache stream, pengaturan ukuran cache di Setting.
- C++/NDK.
