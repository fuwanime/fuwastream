package com.fuwanime.ui.legal

import androidx.compose.foundation.layout.fillMaxHeight
import com.fuwanime.ui.components.readableWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.fuwanime.BuildConfig

/**
 * Legal copy shown inside the app. Mirrors docs/legal/privacy.md and docs/legal/terms.md in the
 * repository, so the published policy and the in-app one cannot drift apart unnoticed.
 */
object LegalText {

    const val DEVELOPER_NAME = "FuwaStream Community (YuzakiNetwork)"
    const val DEVELOPER_EMAIL = "fuwanimereal@gmail.com"
    const val DEVELOPER_WEB = "https://github.com/fuwanime/fuwastream"

    val privacy = """
        Terakhir diperbarui: 1 November 2026

        RINGKASAN
        FuwaStream tidak memiliki akun, server, iklan, maupun pelacak. Semua data yang kamu buat
        di aplikasi ini disimpan di perangkatmu sendiri dan tidak pernah dikirim ke kami.

        DATA YANG DISIMPAN DI PERANGKAT
        • Riwayat tontonan beserta posisi dan durasi episode (untuk "Lanjutkan Nonton").
        • Daftar anime favorit.
        • Riwayat pencarian (maksimal 12 entri terakhir).
        • Pengaturan aplikasi: tema, kualitas video, kualitas unduhan, opsi pemutar.
        • File episode yang kamu unduh untuk ditonton offline.
        Data ini tersimpan di database lokal dan penyimpanan privat aplikasi. Tidak ada satu pun
        yang dikirim ke pengembang.

        DATA YANG TIDAK KAMI KUMPULKAN
        • Tidak ada akun, email, nomor telepon, atau nama pengguna.
        • Tidak ada analitik, pelacakan perilaku, iklan, maupun SDK pihak ketiga yang memprofilkan.
        • Tidak ada laporan crash otomatis (crash reporter). Jika aplikasi menutup sendiri, tidak ada
        data yang dikirim ke mana pun.
        • Tidak ada cookie. Aplikasi ini bukan browser dan tidak memuat halaman web.

        KONEKSI KE LAYANAN PIHAK KETIGA
        Agar dapat menampilkan konten, aplikasi terhubung langsung dari perangkatmu ke:
        • xyz-api.animein.net — katalog anime, jadwal rilis, poster, dan tautan aliran video.
        • api.github.com — pemeriksaan versi baru aplikasi.
        Saat terhubung, alamat IP dan User-Agent perangkatmu secara teknis terlihat oleh layanan
        tersebut, sebagaimana berlaku pada setiap koneksi internet. Kami tidak menerima, menyimpan,
        atau menjual data tersebut. Penggunaan layanan pihak ketiga tunduk pada kebijakan privasi
        masing-masing layanan.

        IZIN APLIKASI
        • INTERNET — mengambil katalog dan memutar video. Wajib.
        • ACCESS_NETWORK_STATE — mendeteksi Wi-Fi versus data seluler untuk kualitas video dan
        aturan "download hanya lewat Wi-Fi". Wajib.
        • POST_NOTIFICATIONS — notifikasi episode baru dan progres unduhan. Opsional; dapat
        ditolak tanpa memengaruhi fungsi lain.

        CADANGAN PERANGKAT
        Jika cadangan otomatis Android aktif di perangkatmu, data lokal aplikasi (favorit, riwayat,
        pengaturan) dapat ikut dicadangkan oleh layanan cadangan Google sesuai setelan perangkatmu.
        Cadangan itu diatur oleh sistem operasi, bukan oleh aplikasi ini.

        ANAK-ANAK
        Aplikasi ditujukan untuk pengguna berusia 13 tahun ke atas. Kami tidak dengan sengaja
        mengumpulkan data dari anak di bawah 13 tahun, dan memang tidak mengumpulkan data pribadi
        apa pun dari siapa pun.

        KEAMANAN
        Data tersimpan di penyimpanan privat aplikasi yang dilindungi sandbox Android. Tidak ada
        transmisi data pribadi ke server kami karena server kami tidak ada.

        HAK DAN PENGHAPUSAN DATA
        Kamu dapat menghapus data kapan saja langsung di aplikasi:
        Pengaturan → Data → Hapus semua data.
        Tindakan ini menghapus favorit, riwayat tontonan, riwayat pencarian, pengaturan, dan file
        unduhan secara permanen dari perangkat. Menghapus aplikasi juga menghapus seluruh datanya.

        PERUBAHAN KEBIJAKAN
        Perubahan kebijakan akan dirilis bersama versi baru aplikasi dan tercatat di repositori
        publik: github.com/fuwanime/fuwastream.

        KONTAK
        Pertanyaan tentang privasi: fuwanimereal@gmail.com
    """.trimIndent()

    val terms = """
        Terakhir diperbarui: 1 November 2026

        1. PENERIMAAN
        Dengan memasang dan menggunakan FuwaStream, kamu menyetujui ketentuan ini. Jika tidak
        setuju, silakan berhenti menggunakan aplikasi dan hapus pemasangannya.

        2. LISENSI APLIKASI
        Kode sumber FuwaStream dirilis di bawah lisensi GNU General Public License v3.0
        (GPL-3.0-or-later). Kamu bebas memeriksa, memodifikasi, dan mendistribusikan ulang kode
        sesuai ketentuan lisensi tersebut. Nama dan logo FuwaStream bukan bagian dari lisensi kode.

        3. KONTEN PIHAK KETIGA
        FuwaStream hanya menampilkan katalog dan tautan aliran video yang disediakan layanan pihak
        ketiga (animein.net). Aplikasi ini tidak meng-host, menyimpan, atau memiliki video apa pun.
        Kami tidak berafiliasi dengan pemilik hak cipta maupun dengan layanan penyedia tersebut.
        Ketersediaan dan legalitas konten sepenuhnya menjadi tanggung jawab penyedia layanan dan
        pengguna di wilayah masing-masing. Kamu bertanggung jawab mematuhi hukum yang berlaku di
        tempatmu.

        4. PENGGUNAAN YANG DILARANG
        Dilarang menggunakan aplikasi ini untuk kegiatan yang melanggar hukum, menyebarkan konten
        terlarang, atau merusak layanan pihak ketiga, termasuk mengakali pembatasan teknis layanan
        tersebut.

        5. BIAYA
        Aplikasi ini gratis. Tidak ada pembelian dalam aplikasi, langganan, iklan, maupun biaya
        tersembunyi. Karena tidak ada transaksi, tidak ada kebijakan pengembalian dana yang berlaku.

        6. TANPA JAMINAN
        Aplikasi disediakan "sebagaimana adanya" tanpa jaminan apa pun, baik tersurat maupun
        tersirat, termasuk kelayakan untuk tujuan tertentu. Konten pihak ketiga dapat berubah,
        tidak tersedia, atau dihentikan sewaktu-waktu tanpa pemberitahuan.

        7. BATASAN TANGGUNG JAWAB
        Sejauh diizinkan hukum, pengembang tidak bertanggung jawab atas kerugian langsung maupun
        tidak langsung yang timbul dari penggunaan atau ketidakmampuan menggunakan aplikasi.

        8. PERUBAHAN KETENTUAN
        Ketentuan ini dapat diperbarui seiring perkembangan aplikasi. Versi terbaru selalu
        disertakan dalam aplikasi dan repositori publik.

        9. KONTAK
        fuwanimereal@gmail.com
    """.trimIndent()

    val about = """
        FuwaStream ${BuildConfig.VERSION_NAME}

        PENGEMBANG
        $DEVELOPER_NAME
        Email: $DEVELOPER_EMAIL
        Repositori: $DEVELOPER_WEB

        LISENSI
        Kode aplikasi: GNU General Public License v3.0 (GPL-3.0-or-later).
        Aplikasi ini memuat komponen open source AndroidX, Jetpack Compose, Media3/ExoPlayer,
        Coil, Retrofit, OkHttp, Moshi, Room, DataStore, dan WorkManager, masing-masing dengan
        lisensinya sendiri.

        KONTEN
        Katalog, poster, thumbnail, dan tautan video dimuat saat aplikasi berjalan dari layanan
        pihak ketiga (animein.net). Aplikasi tidak menyertakan gambar stok, musik, atau aset
        berbayar apa pun. Ikon aplikasi dibuat untuk proyek ini dan dirilis bersama kode di bawah
        GPL-3.0. Hak atas poster dan thumbnail tetap milik pemegang hak masing-masing dan hanya
        ditampilkan sebagai rujukan katalog.

        AFILIASI
        FuwaStream adalah proyek komunitas independen. Tidak berafiliasi dengan layanan penyedia
        konten mana pun, maupun dengan pemegang hak cipta anime yang ditampilkan.
    """.trimIndent()

    fun forKey(key: String): Pair<String, String> = when (key) {
        "privacy" -> "Kebijakan Privasi" to privacy
        "terms" -> "Ketentuan Layanan" to terms
        else -> "Tentang FuwaStream" to about
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalRoute(type: String, onBack: () -> Unit) {
    val (title, body) = LegalText.forKey(type)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxHeight().readableWidth(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                start = 24.dp,
                end = 24.dp,
                bottom = 48.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
