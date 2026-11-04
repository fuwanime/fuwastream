# Player latar (sub-proyek B) — Desain

Tanggal: 2026-11-05 · Branch: `feat/player-latar`

## Konteks & tujuan

Sub-proyek kedua dari implementasi riset `reports/Riset fitur dan UI UX FuwaStream.md` (urutan A → D, #8 dikecualikan, #2 ditunda). A (Koleksi & engagement) sudah di-merge ke fork (PR #23). Dokumen ini mencakup:

- **#13** Upgrade dependensi: Media3, Compose BOM + compileSdk 37, Coil 3
- **#1** `MediaSessionService`: kontrol layar kunci/headset/notifikasi, audio latar, aksi PiP
- **#9** Poles PiP, mode aspek yang diingat + pinch-to-zoom, fullscreen di layar besar Android 16

Keadaan sekarang:
- `PlayerRoute` (`ui/player/PlayerScreen.kt`) membuat `ExoPlayer` sendiri lewat `buildPlayer()` dan me-release-nya saat dispose.
- `ON_STOP` selalu menjeda.
- Tidak ada `MediaSession`.
- `resizeMode` disimpan di `remember` lokal, jadi selalu kembali ke Fit.
- Fullscreen = `landscape && !inPip` dengan `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`. Android 16 mengabaikan kunci orientasi ini di perangkat sw ≥ 600dp, sehingga tombol fullscreen tidak melakukan apa-apa di tablet/foldable.

**Sukses berarti:**
- Episode yang diputar muncul di notifikasi media dan layar kunci (judul, episode, gambar). Tombol headset/Bluetooth dan tombol di layar kunci bisa menjeda, melanjutkan, dan menggeser ±10 detik.
- Dengan setelan "Putar audio di latar" aktif, audio tetap jalan saat layar dimatikan atau aplikasi ditinggalkan tanpa PiP. Dengan setelan mati (default), perilaku sama seperti sekarang: dijeda.
- Jendela PiP punya tombol mundur 10 dtk / putar-jeda / maju 10 dtk.
- Video ultrawide tidak pernah membuat PiP crash.
- Mode aspek (Pas/Penuhi) diingat antar-sesi dan bisa diganti dengan pinch dua jari.
- Tombol fullscreen bekerja di tablet dan foldable.
- Tidak ada regresi di player, gambar, atau build setelah upgrade dependensi.

## Bagian 1 — Upgrade dependensi (#13)

Setiap upgrade satu commit sendiri agar regresi mudah di-bisect, dan setiap commit harus lolos CI (unit test + `assembleRelease`).

1. **Media3** `1.8.0` → `1.11.1`. Tambah artefak `media3-session` (`androidx.media3:media3-session`, version.ref `media3`).
2. **Compose BOM** `2025.07.00` → `2026.09.00`; `compileSdk` dan `targetSdk` 36 → 37. Jika BOM baru memaksa versi lain yang tidak kompatibel (mis. `activity-compose`, `lifecycle`, `navigation-compose`, Kotlin), naikkan seminimal mungkin sampai build hijau, dan catat versinya di commit.
3. **Coil** `io.coil-kt:coil-compose:2.7.0` → `io.coil-kt.coil3:coil-compose:3.6.3` + `io.coil-kt.coil3:coil-network-okhttp:3.6.3`. Migrasi mekanis di 4 file:
   - `FuwaApp.kt`: `ImageLoaderFactory` → `SingletonImageLoader.Factory`, `newImageLoader(context)`. OkHttp lewat `OkHttpNetworkFetcherFactory(callFactory = { container.httpClient })`. `MemoryCache`/`DiskCache` memakai API Coil 3 (`maxSizePercent(context, 0.25)`, `directory(...toOkioPath())`). Crossfade tetap aktif.
   - `util/Bitmaps.kt`: `SuccessResult.image.toBitmap()`; `allowHardware(false)` tetap.
   - `ui/components/Components.kt`, `ui/home/HomeScreen.kt`: ganti import ke `coil3.*`.

Perilaku yang terlihat pengguna tidak berubah. Ukuran cache disk dan memori sama seperti sekarang.

## Bagian 2 — Pemutaran latar (#1)

### Kepemilikan player

Pendekatan yang dipilih: **service memiliki session, UI meminjam instance `ExoPlayer` yang sama di proses yang sama.** `MediaController` dipakai hanya untuk membuat service berjalan agar Media3 mengurus foreground service, notifikasi media, dan layar kunci.

- **`PlaybackHolder`** (package `com.fuwanime.playback`, satu instance di `AppContainer`):
  - `fun acquire(context: Context): ExoPlayer` membuat player secara sinkron dengan logika `buildPlayer()` yang sekarang (dipindahkan ke sini), ditambah `setWakeMode(C.WAKE_MODE_NETWORK)` agar streaming tetap berjalan saat layar mati. Lalu ia menghubungkan `MediaController` ke `SessionToken(context, ComponentName(context, PlaybackService::class.java))`, yang otomatis memulai service. Jika sudah ada player aktif, `acquire` me-release yang lama dulu (hanya satu player sekaligus).
  - `val player: ExoPlayer?` adalah player aktif, dibaca oleh service.
  - `fun release()` me-release controller, `stop()` + `release()` player, set `player = null`, lalu `context.stopService(...)` agar service tidak tertinggal.
  - Semua panggilan di main thread.
- **`PlayerRoute`** memanggil `holder.acquire(context)` di tempat `buildPlayer(context)` sekarang, dan `holder.release()` di `onDispose` (menggantikan `player.release()`). Sisa `PlayerScreen` tetap memakai `ExoPlayer` seperti sekarang, termasuk `seekSafely`, `effectivePosition`, fallback sumber, dan listener.
- **`PlaybackService : MediaSessionService`**:
  - `onCreate`: jika `holder.player` null, panggil `stopSelf()` dan selesai. Jika tidak, bangun `MediaSession.Builder(this, player)` dengan `setSessionActivity(PendingIntent)` yang membuka `MainActivity` (singleTask, sehingga kembali ke player yang sedang terbuka).
  - `onGetSession` mengembalikan session tersebut.
  - `onTaskRemoved`: `player.pause()`, lalu `stopSelf()`. Swipe aplikasi dari Recents menghentikan pemutaran.
  - `onDestroy`: `session.release()` (tidak me-release player; itu tugas holder).
- **Manifest:** permission `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`. Service `android:exported="true"`, `android:foregroundServiceType="mediaPlayback"`, intent-filter `androidx.media3.session.MediaSessionService`.

### Metadata

`setMediaItem` di `PlayerRoute` memberi `MediaMetadata`:
- `title`: judul episode (fallback: judul anime)
- `artist`: judul anime
- `subtitle`: "Episode N"
- `artworkUri`: thumbnail episode (fallback: poster)

Notifikasi dan layar kunci membaca metadata ini. Gambar dimuat oleh bitmap loader bawaan Media3; jika gagal, notifikasi tampil tanpa gambar.

### Setelan & perilaku saat aplikasi berhenti

- Setelan baru **"Putar audio di latar"**, subjudul "Suara tetap jalan saat layar mati atau aplikasi ditinggal". `AppSettings.backgroundAudio: Boolean = false`, key DataStore `background_audio`, `SettingsStore.setBackgroundAudio`. Barisnya ditaruh tepat di bawah "Picture-in-Picture otomatis".
- Fungsi murni di `ui/player/PlayerPolicy.kt`:

  ```kotlin
  enum class StopAction { PAUSE, KEEP_PLAYING }
  fun stopAction(inPip: Boolean, backgroundAudio: Boolean): StopAction
  ```

  - `inPip = true` (jendela PiP ditutup) → `PAUSE`
  - `backgroundAudio = false` → `PAUSE`
  - selain itu → `KEEP_PLAYING`
- `LifecycleEventEffect(ON_STOP)` tetap selalu memanggil `vm.onPlayerExit(...)` (simpan progres + refresh widget/shortcut), lalu `player.pause()` hanya jika `stopAction(...) == PAUSE`.
- Selama audio latar berjalan, loop simpan progres 5 detik dan `addWatchTime` tetap jalan (keduanya memakai `delay`, bukan frame).
- **Cakupan latar = episode yang sedang diputar.** Saat episode habis di latar, pemutaran berhenti. Autoplay ke episode berikutnya terjadi setelah pengguna kembali ke aplikasi, karena logika autoplay ada di komposisi.

### Aksi PiP

`PipController.buildParams()` menambah tiga `RemoteAction`: "Mundur 10 detik", "Putar"/"Jeda" (berganti sesuai `isPlaying`), "Maju 10 detik". Ikonnya empat drawable vektor baru (`ic_pip_replay10`, `ic_pip_play`, `ic_pip_pause`, `ic_pip_forward10`, dari path Material Symbols) lewat `Icon.createWithResource`. Setiap aksi adalah `PendingIntent.getService` ke `PlaybackService` dengan `Intent.ACTION_MEDIA_BUTTON` + `KeyEvent` (`KEYCODE_MEDIA_REWIND`, `KEYCODE_MEDIA_PLAY_PAUSE`, `KEYCODE_MEDIA_FAST_FORWARD`). Media3 menangani media button di service tanpa receiver tambahan. Increment seek memakai `setSeekBackIncrementMs/ForwardIncrementMs(10_000)` yang sudah ada. `setAutoEnter` mendapat parameter `isPlaying` agar ikon putar/jeda diperbarui. Untuk perangkat yang belum mendukung aksi (pra-API 26), tidak ada perubahan.

## Bagian 3 — PiP, mode aspek, layar besar (#9)

### Rasio PiP

Fungsi murni `clampPipAspect(width: Int, height: Int): Pair<Int, Int>` di `PlayerPolicy.kt`:
- Dimensi ≤ 0 → `16 to 9`
- Rasio > 2.39 → `239 to 100`
- Rasio < 1/2.39 → `100 to 239`
- Selain itu → `width to height` apa adanya

`PipController.setAutoEnter` memakai hasilnya (menggantikan fallback 16:9 untuk rasio di luar rentang).

**Sembunyikan kontrol lebih awal:** di API 35+, `MainActivity.onPictureInPictureUiStateChanged` meneruskan `isTransitioningToPip()` ke `PipController` (state `isEnteringPip`). `VideoSurface` memperlakukan `inPip || isEnteringPip` sebagai PiP untuk menyembunyikan kontrol.

### Mode aspek yang diingat + pinch

- `AppSettings.videoResize: VideoResize` (`enum class VideoResize { FIT, ZOOM }`, default `FIT`), key `video_resize`, `SettingsStore.setVideoResize`. `PlayerRoute` membaca dari settings; pilihan di sheet pengaturan player ("Pas layar"/"Penuhi layar") menulis ke settings. Pemetaan ke `AspectRatioFrameLayout.RESIZE_MODE_FIT/ZOOM` hanya di UI.
- **Pinch:** dua jari di area video. Fungsi murni `pinchResize(current: VideoResize, scale: Float): VideoResize?`:
  - `scale ≥ 1.15` → `ZOOM` (null jika sudah `ZOOM`)
  - `scale ≤ 0.87` → `FIT` (null jika sudah `FIT`)
  - selain itu → null
  - `scale` adalah rasio jarak antar-jari kumulatif sejak gesture mulai.
- Gesture dimulai hanya saat pointer kedua turun. Selama pinch, double-tap seek, drag kecerahan/volume, dan hold-2x tidak terpicu untuk gesture itu. Pinch tidak bekerja saat kontrol terkunci atau di PiP.
- Saat mode berganti lewat pinch, indikator pill singkat ("Pas layar" / "Penuhi layar") tampil di tengah ±1 detik, memakai gaya `LevelIndicator`/`SeekBubble` yang ada.

### Fullscreen di layar besar

- State `manualFullscreen` (rememberSaveable) di `PlayerRoute`. Fungsi murni `isFullscreen(landscape: Boolean, manual: Boolean, inPip: Boolean): Boolean = (landscape || manual) && !inPip`.
- Tombol fullscreen: `manualFullscreen = true` + `SENSOR_LANDSCAPE` (ponsel tetap berputar seperti sekarang). Keluar fullscreen (tombol atau back): `manualFullscreen = false` + `PORTRAIT`.
- Di perangkat yang mengabaikan kunci orientasi (sw ≥ 600dp, Android 16), layout fullscreen tetap tampil di orientasi apa pun karena `manual = true`.
- Memutar ponsel ke landscape tetap otomatis masuk fullscreen seperti sekarang.

## Error handling

- `MediaController` gagal terhubung: tangkap di future listener, catat log. Player tetap bisa diputar di aplikasi; hanya notifikasi/layar kunci yang hilang.
- `PlaybackService` dimulai tanpa player (mis. dibangkitkan sistem setelah proses mati): `stopSelf()` tanpa crash.
- `RemoteAction`/`setPictureInPictureParams` tetap dibungkus `runCatching` seperti sekarang.
- Artwork gagal dimuat: notifikasi tanpa gambar.
- Upgrade dependensi yang gagal build diperbaiki di commit upgrade itu sendiri, bukan ditunda.

## Testing

- **Unit test (JVM)**, file `PlayerPolicyTest.kt`:
  - `stopAction`: tiga kasus (PiP ditutup, setelan mati, setelan nyala)
  - `clampPipAspect`: 16:9 apa adanya, 2.4:1 → 239:100, 1:3 → 100:239, 0×0 → 16:9
  - `pinchResize`: ambang naik, ambang turun, zona mati, sudah di mode tujuan → null
  - `isFullscreen`: landscape, manual di portrait, PiP menang
- **Settings:** default `backgroundAudio = false` dan `videoResize = FIT` (via fungsi pemetaan prefs yang sudah ada jika bisa dites di JVM; jika tidak, diverifikasi lewat QA).
- **CI:** setiap commit upgrade lolos `testDebugUnitTest` + `assembleRelease`.
- **QA emulator** (`qa.sh`, skenario baru):
  - Putar episode → `dumpsys media_session` menunjukkan session FuwaStream dengan state PLAYING dan metadata judul.
  - `input keyevent KEYCODE_MEDIA_PAUSE` → state PAUSED; `KEYCODE_MEDIA_PLAY` → PLAYING.
  - Setelan audio latar ON → tekan Home (Auto-PiP OFF) → setelah 5 detik state masih PLAYING.
  - Setelan OFF → Home → PAUSED.
  - Notifikasi media ada di `dumpsys notification`.
  - Pilih "Penuhi layar", tutup player, buka lagi → screenshot menunjukkan mode zoom.
  - Skenario lama (smoke, upgrade-from-release, Koleksi) tetap lolos sebagai pengecekan regresi upgrade.

## Di luar cakupan

- Autoplay episode berikutnya saat di latar.
- Pola `MediaController` penuh di UI, Cast, Android Auto.
- Pemecahan `PlayerScreen.kt` secara menyeluruh (hanya pembuatan player yang dipindah ke `PlaybackHolder`, dan fungsi murni ke `PlayerPolicy.kt`).
- #8 "Masih menonton?" + Data Saver (dikecualikan user).
- Material 3 Expressive.
