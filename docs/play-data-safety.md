# Jawaban Formulir Data Safety (Play Console)

Isian ini mengikuti kondisi kode saat ini. Perbarui bila ada fitur baru yang mengubah alur data.

## Bagian 1 — Pengumpulan dan pembagian data

**Apakah aplikasi mengumpulkan atau membagikan data pengguna yang diperlukan?**
→ **Tidak**

Alasan: aplikasi tidak memiliki akun, tidak mengirim data ke server pengembang, tidak memakai
analitik/iklan/crash reporting. Seluruh data (favorit, riwayat, pengaturan, unduhan) hanya
tersimpan di perangkat.

Catatan: permintaan HTTP langsung ke layanan pihak ketiga (katalog anime, GitHub, host video)
membawa alamat IP dan User-Agent secara teknis. Ini bukan pengumpulan oleh pengembang dan tidak
dapat dihindari oleh aplikasi klien mana pun; tetap dijelaskan di kebijakan privasi.

## Bagian 2 — Jenis data (jika ditanya per kategori)

| Kategori | Dikumpulkan? | Dibagikan? |
| --- | --- | --- |
| Info pribadi (nama, email, telepon) | Tidak | Tidak |
| Lokasi | Tidak | Tidak |
| Riwayat web | Tidak | Tidak |
| Riwayat aplikasi di perangkat | Tidak | Tidak |
| Kontak | Tidak | Tidak |
| Foto/video | Tidak | Tidak |
| File & dokumen | Tidak | Tidak |
| Audio | Tidak | Tidak |
| ID perangkat | Tidak | Tidak |
| Nomor telepon | Tidak | Tidak |
| Diagnostik/crash | Tidak | Tidak |

## Bagian 3 — Keamanan data

- Data dienkripsi saat transit? **Ya untuk katalog dan pemeriksaan update** (HTTPS).
  Tautan aliran video berasal dari host pihak ketiga dan dapat memakai HTTP bila penyedia
  menyediakannya demikian; aplikasi mengikuti URL yang diberikan API.
- Pengguna dapat meminta penghapusan data? **Ya** — langsung di aplikasi
  (Pengaturan → Data → Hapus semua data).
- Komitmen Play Families? **Tidak** — aplikasi bukan untuk anak-anak (13+).

## Bagian 4 — Izin sensitif

| Izin | Digunakan? | Catatan |
| --- | --- | --- |
| Lokasi | Tidak | — |
| Kamera | Tidak | — |
| Mikrofon | Tidak | — |
| Kontak | Tidak | — |
| SMS | Tidak | — |
| Penyimpanan luas | Tidak | Unduhan memakai penyimpanan privat aplikasi |
| Notifikasi | Ya (opsional) | Hanya setelah konfirmasi usia; dapat ditolak |

## Bagian 5 — Iklan & monetisasi

- Mengandung iklan? **Tidak**
- Pembelian dalam aplikasi? **Tidak**
- Langganan? **Tidak**

## Teks yang bisa ditempel di deskripsi Play Store

> FuwaStream tidak membuat akun dan tidak mengumpulkan data pribadi. Favorit, riwayat tontonan,
> pengaturan, dan unduhan hanya tersimpan di perangkatmu dan dapat dihapus kapan saja dari
> Pengaturan. Konten katalog dan video berasal dari layanan pihak ketiga; aplikasi ini tidak
> meng-host video apa pun.
