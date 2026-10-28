<div align="center">
  <h1>FuwaStream</h1>
  <p><strong>Jelajahi anime, ikuti episode, dan lanjutkan tontonan langsung dari Android.</strong></p>
  <p>
    FuwaStream adalah aplikasi Android open-source dengan antarmuka modern, pemutar fleksibel, jadwal anime, koleksi pribadi, dan dukungan download offline.
  </p>
  <p>
    <a href="https://github.com/fuwanime/fuwastream/releases/latest">
      <img src="https://img.shields.io/badge/Download-APK%20Terbaru-7C3AED?style=for-the-badge&logo=android&logoColor=white" alt="Download APK terbaru">
    </a>
    <a href="https://github.com/fuwanime/fuwastream">
      <img src="https://img.shields.io/badge/Source-GitHub-24292F?style=for-the-badge&logo=github&logoColor=white" alt="Source code di GitHub">
    </a>
  </p>
  <p>
    <a href="https://github.com/fuwanime/fuwastream/stargazers"><img src="https://img.shields.io/github/stars/fuwanime/fuwastream?style=flat-square" alt="GitHub Stars"></a>
    <a href="https://github.com/fuwanime/fuwastream/forks"><img src="https://img.shields.io/github/forks/fuwanime/fuwastream?style=flat-square" alt="GitHub Forks"></a>
    <a href="https://github.com/fuwanime/fuwastream/releases"><img src="https://img.shields.io/github/v/release/fuwanime/fuwastream?include_prereleases&style=flat-square" alt="Latest release"></a>
    <a href="./LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square" alt="Lisensi GPL-3.0"></a>
  </p>
</div>

---

## ✨ Fitur

### Temukan anime
- **Beranda personal** — anime unggulan, lanjutkan menonton, anime yang tayang hari ini, musim berjalan, judul populer, dan pintasan genre.
- **Jelajah dan pencarian** — riwayat pencarian, filter genre/status, urutan populer atau A–Z, serta pemuatan daftar berkelanjutan.
- **Jadwal tayang** — lihat jadwal anime berdasarkan hari.

### Nikmati tontonan
- **Pemutar fleksibel** — pilih kualitas yang tersedia, atur kecepatan dari 0,5× hingga 2×, dan pilih mode pas atau isi layar.
- **Kontrol gestur** — ketuk dua kali untuk maju/mundur; saat layar penuh, geser sisi kiri untuk kecerahan dan sisi kanan untuk volume.
- **Lanjutkan menonton** — progres dan posisi terakhir disimpan agar lebih mudah meneruskan episode.
- **Episode berikutnya** — hitung mundur otomatis ke episode selanjutnya.
- **Picture-in-Picture** — tetap menonton dalam jendela kecil saat membuka aplikasi lain.

### Koleksi dan offline
- **Download offline** — simpan episode dengan pilihan kualitas dan opsi hanya menggunakan Wi-Fi.
- **Riwayat tontonan** — lihat progres tontonan dan lanjutkan episode terakhir.
- **Favorit** — simpan anime yang ingin ditonton lagi.
- **Daftar episode** — jelajahi episode dalam beberapa rentang dan cari nomor episode tertentu.

### Sesuaikan pengalaman
- Tema sistem, terang, gelap, dan hitam AMOLED.
- Tampilan Material 3 dengan dukungan Material You.
- Pengaturan kualitas default dan opsi pemutar.

> Kualitas video, jadwal, dan ketersediaan episode bergantung pada data serta sumber pihak ketiga yang digunakan.

## 📥 Download dan instalasi

**Cara paling mudah adalah mengunduh APK dari halaman Releases resmi.**

1. Buka **[FuwaStream Releases](https://github.com/fuwanime/fuwastream/releases/latest)**.
2. Di bagian **Assets**, unduh file APK FuwaStream yang tersedia.
3. Buka file APK di perangkat Android. Jika diminta, izinkan instalasi aplikasi dari browser atau file manager yang digunakan.
4. Selesaikan instalasi, lalu buka FuwaStream.

**Kompatibilitas:** Android 7.0 (API 24) atau lebih baru. Koneksi internet diperlukan untuk memuat katalog dan streaming; episode yang sudah berhasil diunduh dapat ditonton secara offline melalui **Koleksi → Unduhan**.

> Unduh aplikasi hanya dari halaman rilis resmi proyek ini. Jika instalasi atau pemutaran bermasalah, laporkan lewat [GitHub Issues](https://github.com/fuwanime/fuwastream/issues) beserta versi aplikasi dan detail masalahnya.

## 🧑‍💻 Teknologi

FuwaStream dibangun menggunakan:

- **Kotlin** dan **Jetpack Compose** untuk aplikasi Android.
- **Material 3** untuk komponen dan tema antarmuka.
- **AndroidX Media3 / ExoPlayer** untuk pemutaran media.
- **Room** untuk data lokal seperti riwayat, favorit, dan pencarian.
- **DataStore** untuk preferensi aplikasi.
- **Retrofit, OkHttp, dan Moshi** untuk komunikasi serta pemrosesan data API.

## 🛠️ Menjalankan proyek secara lokal

### Persyaratan

- JDK 17 atau lebih baru.
- Android SDK dengan platform/build tools yang dibutuhkan proyek (compile SDK 36).
- Gradle yang kompatibel dengan konfigurasi proyek.

### Build APK debug

```bash
git clone https://github.com/fuwanime/fuwastream.git
cd fuwastream
gradle assembleDebug
```

APK debug dihasilkan di:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Untuk menjalankan unit test:

```bash
gradle testDebugUnitTest
```

### Build otomatis

GitHub Actions menjalankan build dan test pada workflow proyek. Push ke branch `main` atau tag versi `v*` akan memicu build release dan publikasi APK di halaman [Releases](https://github.com/fuwanime/fuwastream/releases). Build dari `main` diberi nama berdasarkan nomor build, sedangkan tag versi menggunakan nama versi tersebut.

## 📂 Struktur proyek

```text
com.fuwanime
├── data/
│   ├── remote/    # API, DTO, dan sumber data jarak jauh
│   └── local/     # Room dan DataStore
├── ui/            # Screen, ViewModel, theme, components, navigation
└── util/          # Utilitas umum
```

## 🐛 Menemukan bug atau punya ide fitur?

Masukan sangat membantu perkembangan FuwaStream.

- **Laporkan bug:** [Buka GitHub Issues](https://github.com/fuwanime/fuwastream/issues)
- **Usulkan fitur:** jelaskan kebutuhan dan bagaimana fitur tersebut akan membantu pengguna.
- **Kontribusi kode:** fork repository, buat branch untuk perubahanmu, lalu kirim Pull Request dengan penjelasan dan langkah pengujian.

Sebelum membuat issue baru, periksa apakah masalah atau usulan yang sama sudah pernah dilaporkan.

## ⚖️ Catatan konten

FuwaStream merupakan aplikasi klien dan **tidak meng-host file video**. Informasi dan sumber pemutaran berasal dari API atau penyedia pihak ketiga. FuwaStream tidak menjamin ketersediaan maupun status hak atas konten yang disediakan oleh pihak tersebut. Pengguna bertanggung jawab memastikan penggunaan layanan dan konten sesuai dengan hukum yang berlaku serta ketentuan penyedia terkait.

FuwaStream tidak berafiliasi dengan pemilik hak cipta, studio, atau penyedia konten anime mana pun kecuali dinyatakan secara terpisah.

## 📄 Lisensi

Kode sumber FuwaStream dilisensikan di bawah **[GNU General Public License v3.0](./LICENSE)**. Periksa berkas lisensi sebelum mendistribusikan atau memodifikasi proyek. Nama, logo, dan aset brand FuwaStream tidak termasuk dalam lisensi kode sumber.

---

<div align="center">
  <sub>Dibuat dengan ☕ oleh <a href="https://github.com/fuwanime">Fuwa Nime</a>. Kalau FuwaStream berguna buat kamu, pertimbangkan untuk memberi ⭐ pada repository ini.</sub>
</div>
