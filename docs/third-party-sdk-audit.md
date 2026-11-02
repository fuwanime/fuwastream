# Audit SDK & Dependency Pihak Ketiga

Audit ini mencatat setiap dependency runtime aplikasi, tujuannya, dan apakah ia mengumpulkan
atau mengirim data pengguna. Sumber: `gradle/libs.versions.toml` + `app/build.gradle.kts`.

## Ringkasan

- **Tidak ada** SDK analitik (Firebase Analytics, Google Analytics, Mixpanel, Amplitude, dll).
- **Tidak ada** SDK iklan (AdMob, AppLovin, Unity Ads, dll).
- **Tidak ada** SDK crash reporting (Crashlytics, Sentry, Bugsnag, dll).
- **Tidak ada** SDK identitas/sosial (Google Sign-In, Facebook SDK, dll).
- Semua dependency berlisensi open source (Apache-2.0 / MIT / BSD).

## Dependency runtime

| Dependency | Versi | Fungsi | Data keluar |
| --- | --- | --- | --- |
| AndroidX Core KTX | 1.18.0 | Fondasi Android | — |
| Lifecycle (runtime, compose, viewmodel) | 2.9.2 | Siklus hidup & ViewModel | — |
| Activity Compose | 1.10.1 | Activity + Compose | — |
| Compose BOM / UI / Material3 | 2025.07.00 | Antarmuka | — |
| Material Icons Extended | (BOM) | Ikon | — |
| Navigation Compose | 2.9.3 | Navigasi layar | — |
| Room (runtime, ktx, compiler) | 2.7.2 | Database lokal (favorit, riwayat, unduhan) | — (lokal) |
| DataStore Preferences | 1.1.7 | Penyimpanan pengaturan & sesi usia | — (lokal) |
| Kotlin Coroutines | 1.10.2 | Konkurensi | — |
| Coil Compose | 2.7.0 | Pemuatan gambar poster/thumbnail | Mengambil gambar dari API pihak ketiga (IP, UA) |
| Retrofit + converter-moshi | 2.12.0 | Klien HTTP katalog | Permintaan ke API pihak ketiga |
| OkHttp (+ logging interceptor) | 4.12.0 | Transport HTTP | Header permintaan (UA) |
| Moshi | 1.15.2 | Parsing JSON | — |
| Media3 ExoPlayer (+ HLS, OkHttp datasource, UI) | 1.8.0 | Pemutaran video | Streaming langsung dari sumber video pihak ketiga |
| WorkManager | 2.10.0 | Pemeriksaan episode baru (6 jam) | Permintaan ke API pihak ketiga |
| JUnit | 4.13.2 | Unit test (test-only) | — |

## Permukaan jaringan

| Tujuan | Dipanggil oleh | Data |
| --- | --- | --- |
| `https://xyz-api.animein.net` | Retrofit (katalog), WorkManager (notifikasi), DownloadManager (unduhan) | IP, User-Agent, permintaan katalog/stream |
| `https://api.github.com` | UpdateRepository | IP, User-Agent, path repositori |
| Host video pihak ketiga | ExoPlayer / DownloadManager | IP, User-Agent, permintaan byte-range |

Tidak ada endpoint milik pengembang. Tidak ada telemetri.

## Temuan

1. **OkHttp logging interceptor** terpasang hanya untuk build debug; tidak aktif di rilis.
2. **DownloadManager** mengunduh langsung dari host video pihak ketiga; file disimpan di
   penyimpanan privat aplikasi (`getExternalFilesDir(Movies)`).
3. **Coil** menyimpan cache gambar di `cacheDir/image_cache` (maks 256 MB) — dapat dihapus lewat
   Pengaturan sistem atau "Hapus semua data".
4. Tidak ada dependency yang meminta akses ke kontak, lokasi, kamera, mikrofon, atau penyimpanan
   eksternal publik.
