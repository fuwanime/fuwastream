package com.fuwanime.ui.transfer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fuwanime.R
import com.fuwanime.data.backup.BackupCodec
import com.fuwanime.data.backup.BackupFile
import com.fuwanime.data.backup.BackupRepository
import com.fuwanime.data.backup.BackupResult
import com.fuwanime.data.backup.QrFrames
import com.fuwanime.data.backup.qrBitmap
import com.fuwanime.ui.components.FuwaSnackbarHost
import com.fuwanime.ui.components.MessageState
import com.fuwanime.util.UiText
import com.fuwanime.util.fuwaViewModel
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How long each frame stays on screen while the export QR cycles. */
private const val FRAME_MS = 400L
/** Rendered QR size in pixels; scaled up on screen with nearest-neighbour so modules stay crisp. */
private const val QR_PX = 640

sealed interface QrExportState {
    data object Loading : QrExportState
    data object Empty : QrExportState
    data class Ready(val frames: List<String>, val file: BackupFile) : QrExportState
    data class Failed(val message: UiText) : QrExportState
}

/** Scanner accumulator: frames arrive in any order; [ready] holds the decoded backup once all arrived. */
data class QrScanState(
    val received: Int = 0,
    val total: Int = 0,
    val ready: BackupFile? = null,
    val restored: Boolean = false,
    val error: UiText? = null,
)

class QrTransferViewModel(private val backup: BackupRepository) : ViewModel() {
    private val _export = MutableStateFlow<QrExportState>(QrExportState.Loading)
    val export: StateFlow<QrExportState> = _export.asStateFlow()

    private val _scan = MutableStateFlow(QrScanState())
    val scan: StateFlow<QrScanState> = _scan.asStateFlow()

    private val chunks = HashMap<Int, String>()
    private var lastText: String? = null

    init {
        viewModelScope.launch {
            _export.value = runCatching {
                val file = backup.currentBackup()
                val empty = file.favorites.isEmpty() && file.history.isEmpty() && file.mangaFavorites.isEmpty() &&
                    file.mangaHistory.isEmpty() && file.collectionLists.isEmpty() && file.savedSearchFilters.isEmpty() &&
                    file.playbackBookmarks.isEmpty() && file.contentPreferences.isEmpty()
                if (empty) QrExportState.Empty
                else QrExportState.Ready(QrFrames.encode(BackupCodec.encode(file)), file)
            }.getOrElse { QrExportState.Failed(UiText(R.string.mine_backup_err_other, listOf(it.message ?: ""))) }
        }
    }

    /** Called for every decoded QR text; ignores foreign codes and duplicate frames. */
    fun onQrText(text: String) {
        val s = _scan.value
        if (s.ready != null || s.restored) return
        if (text == lastText) return
        lastText = text
        val (index, total, chunk) = QrFrames.parse(text) ?: return
        // A different frame count means a new export started; old chunks no longer belong to it.
        if (total != s.total) chunks.clear()
        val old = chunks.put(index, chunk)
        if (old == chunk && total == s.total) return
        if (chunks.size < total) {
            _scan.value = QrScanState(received = chunks.size, total = total)
            return
        }
        val json = QrFrames.decode(chunks, total)
        chunks.clear()
        lastText = null
        if (json == null) {
            _scan.value = QrScanState(error = UiText(R.string.mine_qr_scan_corrupt))
            return
        }
        _scan.value = when (val result = BackupCodec.decode(json)) {
            is BackupResult.Ok -> QrScanState(received = total, total = total, ready = result.file)
            BackupResult.TooNew -> QrScanState(error = UiText(R.string.mine_restore_too_new))
            BackupResult.Corrupt -> QrScanState(error = UiText(R.string.mine_qr_scan_corrupt))
        }
    }

    fun resetScan() {
        chunks.clear()
        lastText = null
        _scan.value = QrScanState()
    }

    fun clearScanError() = _scan.update { it.copy(error = null) }

    /** Merges the received backup into this phone; nothing local is wiped. */
    fun restore(file: BackupFile) {
        viewModelScope.launch {
            runCatching { backup.restore(file) }
                .onSuccess { _scan.value = QrScanState(restored = true) }
                .onFailure { _scan.value = QrScanState(error = UiText(R.string.mine_qr_scan_failed, listOf(it.message ?: ""))) }
        }
    }
}

/** Shows the user's data as one static QR, or a cycling sequence of numbered frames for larger payloads. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrExportRoute(onBack: () -> Unit) {
    val vm: QrTransferViewModel = fuwaViewModel { c, _ -> QrTransferViewModel(c.backupRepository) }
    val state by vm.export.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    // Bright and backlit: max screen brightness and no sleep while another phone scans this one.
    DisposableEffect(view) {
        val window = context.findActivity()?.window
        val oldBrightness = window?.attributes?.screenBrightness ?: -1f
        window?.let { it.attributes = it.attributes.apply { screenBrightness = 1f } }
        view.keepScreenOn = true
        onDispose {
            window?.let { it.attributes = it.attributes.apply { screenBrightness = oldBrightness } }
            view.keepScreenOn = false
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mine_qr_export_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.mine_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        when (val s = state) {
            QrExportState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            QrExportState.Empty -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                MessageState(
                    Icons.Rounded.QrCode2,
                    stringResource(R.string.mine_qr_export_empty_title),
                    message = stringResource(R.string.mine_qr_export_empty),
                )
            }
            is QrExportState.Failed -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                MessageState(Icons.Rounded.ErrorOutline, stringResource(R.string.mine_qr_export_empty_title), message = s.message.resolve(context.resources))
            }
            is QrExportState.Ready -> QrExportContent(s, Modifier.fillMaxSize().padding(padding))
        }
    }
}

@Composable
private fun QrExportContent(state: QrExportState.Ready, modifier: Modifier) {
    val frames = state.frames
    var index by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    LaunchedEffect(frames, paused) {
        if (frames.size <= 1 || paused) return@LaunchedEffect
        while (true) {
            delay(FRAME_MS)
            index = (index + 1) % frames.size
        }
    }
    // One bitmap at a time: a long transfer may have hundreds of frames.
    val bitmap = remember(frames, index) { qrBitmap(frames[index], QR_PX) }
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(
                R.string.mine_qr_export_summary,
                state.file.favorites.size, state.file.history.size, state.file.collectionLists.size, state.file.savedSearchFilters.size,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        // White card: QR needs a light quiet zone regardless of the app theme. Tap pauses the cycle.
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.clickable(enabled = frames.size > 1) { paused = !paused },
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.mine_qr_export_qr_cd, index + 1, frames.size),
                modifier = Modifier.padding(12.dp).widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (frames.size > 1) {
            Text(stringResource(R.string.mine_qr_export_frame, index + 1, frames.size), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (index + 1).toFloat() / frames.size },
                modifier = Modifier.width(240.dp).height(4.dp).clip(CircleShape),
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
            if (paused) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.mine_qr_export_paused), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text(stringResource(R.string.mine_qr_export_single), style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.mine_qr_export_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** Camera scanner that collects the export frames, then merges the received backup after confirmation. */
@Composable
fun QrScanRoute(onBack: () -> Unit) {
    val vm: QrTransferViewModel = fuwaViewModel { c, _ -> QrTransferViewModel(c.backupRepository) }
    val state by vm.scan.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val res = context.resources
    val snackbar = remember { SnackbarHostState() }
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var askedOnce by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        askedOnce = true
    }
    LaunchedEffect(Unit) { if (!cameraGranted) permission.launch(Manifest.permission.CAMERA) }
    LaunchedEffect(state.error) {
        state.error?.let {
            vm.clearScanError()
            snackbar.showSnackbar(it.resolve(res))
        }
    }
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            state.restored -> MessageState(
                Icons.Rounded.CheckCircle,
                stringResource(R.string.mine_qr_scan_done_title),
                modifier = Modifier.align(Alignment.Center),
                message = stringResource(R.string.mine_qr_scan_done_body),
                actionLabel = stringResource(R.string.mine_qr_scan_done_action),
                onAction = onBack,
            )
            !cameraGranted -> MessageState(
                Icons.Rounded.PhotoCamera,
                stringResource(R.string.mine_qr_scan_camera_title),
                modifier = Modifier.align(Alignment.Center),
                message = stringResource(if (askedOnce) R.string.mine_qr_scan_denied else R.string.mine_qr_scan_camera_body),
                actionLabel = stringResource(if (askedOnce) R.string.mine_qr_scan_open_settings else R.string.mine_qr_scan_grant),
                onAction = {
                    if (askedOnce) {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    } else {
                        permission.launch(Manifest.permission.CAMERA)
                    }
                },
            )
            else -> {
                QrCameraPreview(active = state.ready == null, onFrame = vm::onQrText)
                // Viewfinder: a simple frame so the user knows where to aim.
                Box(
                    Modifier.align(Alignment.Center).size(260.dp)
                        .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(24.dp)),
                )
                Row(
                    Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.mine_back), tint = Color.White) }
                    Text(
                        stringResource(R.string.mine_qr_scan_title),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = vm::resetScan) { Text(stringResource(R.string.mine_qr_scan_restart), color = Color.White) }
                }
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (state.total > 0) {
                            Text(
                                stringResource(R.string.mine_qr_scan_progress, state.received, state.total),
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { state.received.toFloat() / state.total },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.25f),
                                drawStopIndicator = {},
                                gapSize = 0.dp,
                            )
                        } else {
                            Text(stringResource(R.string.mine_qr_scan_waiting), color = Color.White, style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.mine_qr_scan_hint),
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        FuwaSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars))
    }

    // All frames in: show what arrived and merge only after the user confirms (same flow as file restore).
    state.ready?.let { file ->
        val date = remember(file) { SimpleDateFormat("d MMM yyyy", res.configuration.locales[0]).format(Date(file.createdAt)) }
        AlertDialog(
            onDismissRequest = vm::resetScan,
            icon = { Icon(Icons.Rounded.Restore, null) },
            title = { Text(stringResource(R.string.mine_restore_title)) },
            text = { Text(stringResource(R.string.mine_restore_body, file.favorites.size, file.history.size, date)) },
            confirmButton = { TextButton(onClick = { vm.restore(file) }) { Text(stringResource(R.string.mine_restore_confirm)) } },
            dismissButton = { TextButton(onClick = vm::resetScan) { Text(stringResource(R.string.mine_cancel)) } },
        )
    }
}

/** CameraX preview + frame analysis; ZXing reads the QR from the Y plane (no native/Play-services deps). */
@Composable
private fun QrCameraPreview(active: Boolean, onFrame: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentActive by rememberUpdatedState(active)
    val currentOnFrame by rememberUpdatedState(onFrame)
    val previewView = remember { PreviewView(context) }
    DisposableEffect(lifecycleOwner, previewView) {
        val executor = ContextCompat.getMainExecutor(context)
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(executor) { image ->
            try {
                if (currentActive) decodeQr(image)?.let(currentOnFrame)
            } finally {
                image.close()
            }
        }
        future.addListener({
            val p = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            runCatching {
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, executor)
        onDispose {
            analysis.clearAnalyzer()
            provider?.unbindAll()
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/** Decodes one camera frame; null when it holds no (readable) QR. YUV_420_888's Y plane is the luminance. */
private fun decodeQr(image: ImageProxy): String? {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val data = ByteArray(buffer.remaining())
    buffer.get(data)
    // rowStride can exceed width (padding); the crop keeps only the real pixels.
    val source = PlanarYUVLuminanceSource(data, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
    return runCatching {
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), mapOf(DecodeHintType.TRY_HARDER to true)).text
    }.getOrNull()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
