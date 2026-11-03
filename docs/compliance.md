# Checklist Kepatuhan Play Store

Dokumen ini memetakan setiap persyaratan kepatuhan ke statusnya di kode, beserta alasan untuk
item yang tidak berlaku. Tujuannya agar reviewer dan pengembang melihat hal yang sama.

## Yang diterapkan di aplikasi

| Item | Status | Implementasi |
| --- | --- | --- |
| Kebijakan Privasi | Ada | `ui/legal/LegalScreen.kt` + `docs/legal/privacy.md`, dibuka dari Pengaturan → Privasi & Legal |
| Ketentuan Layanan | Ada | `ui/legal/LegalScreen.kt` + `docs/legal/terms.md` |
| Bisnis/pengembang | Ada | Layar "Info Pengembang": nama, email, repositori, lisensi, kredit |
| Persetujuan usia (anak) | Ada | `ui/legal/AgeGateScreen.kt`, konfirmasi 13+ sekali, tersimpan di DataStore |
| Penghapusan data | Ada | Pengaturan → Data → Hapus semua data (favorit, riwayat, pencarian, pengaturan, file unduhan) |
| Minimisasi data | Ada | Hanya data lokal yang diperlukan fitur; tidak ada akun, telemetri, atau iklan |
| Audit SDK pihak ketiga | Ada | `docs/third-party-sdk-audit.md` — tidak ada SDK pelacak/iklan/crash |
| Alt text | Ada | `contentDescription` pada poster, backdrop, dan thumbnail episode |
| Kontras warna | Ada | Rasio teks ≥ 4.5:1 (WCAG AA) di tema gelap, terang, dan AMOLED; `outline` terang dinaikkan ke 5.4:1 |
| Navigasi keyboard | Ada | Seluruh komponen interaktif memakai `clickable`/Material3 (focusable, aktivasi Enter/D-pad) |
| Tanpa dark pattern | Ada | Payment tidak ada; update banner bisa ditutup; age gate punya tombol "Keluar" yang jelas; tidak ada checkbox tercentang otomatis |
| Tanpa biaya tersembunyi | Ada | Aplikasi gratis, tanpa IAP/langganan/iklan |

## Tidak berlaku (dengan alasan)

| Item | Alasan |
| --- | --- |
| Cookie policy | Aplikasi native, tanpa WebView dan tanpa cookie. Tidak ada yang perlu diatur. |
| Cookie consent banner | Tidak ada cookie maupun pelacak; banner akan menyesatkan pengguna. |
| Form consent check | Tidak ada formulir pendaftaran, kontak, atau newsletter di aplikasi. |
| Refund policy | Tidak ada transaksi, pembelian dalam aplikasi, atau langganan. |
| Unsubscribe link | Tidak ada email marketing atau notifikasi email. |
| Lisensi gambar stok | Aplikasi tidak memuat gambar stok. Poster/thumbnail diambil saat runtime dari API pihak ketiga dan ditampilkan sebagai rujukan katalog; hak tetap milik pemegangnya. Ikon aplikasi dibuat untuk proyek ini (GPL-3.0). |
| No fake reviews | Tidak ada sistem ulasan di aplikasi. Pengembang tidak membeli/memanipulasi ulasan. |

## Catatan untuk Play Console

- **Content rating**: isi kuesioner sebagai aplikasi hiburan dengan konten pihak ketiga; tandai
  kemungkinan konten dewasa pada sebagian judul.
- **Data safety**: lihat `docs/play-data-safety.md`.
- **Privacy policy URL**: gunakan berkas `docs/legal/privacy.md` yang dipublikasikan
  (GitHub Pages atau URL lain yang dapat diakses publik).
- **Target audience**: 13+ (bukan aplikasi anak).
- **Ads**: tidak ada iklan.
- **In-app purchases**: tidak ada.
