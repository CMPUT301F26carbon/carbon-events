/*
 * Scan.kt (Suvir)
 *
 * QR scanner: camera preview, on-device decoding and the "QR detected" card.
 */
package com.carbon.events

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * QR scanner (Figma E-SCAN).
 *
 * @param campsite looks up a decoded campsite so the "QR detected" card can name it.
 * @param onCampsite called with the campsite ID to open.
 */
@Composable
fun ScanScreen(onClose: () -> Unit, onCampsite: (String) -> Unit, campsite: (String) -> Flow<Campsite?> = { flowOf(null) }) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    var manual by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var detectedId by remember { mutableStateOf<String?>(null) }
    var torch by remember { mutableStateOf(false) }
    val onScanned: (String) -> Unit = { text ->
        val id = QrCodes.parseCampsiteId(text)
        if (id != null) { detectedId = id; error = null } else error = "That QR code isn't an Events campsite code."
    }

    Box(Modifier.fillMaxSize().background(EventsColors.Scrim).testTag("scan_screen")) {
        if (granted && !manual) CameraPreview(torch, onScanned)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = PageMargin, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassIconButton(R.drawable.ic_close, "Close scanner", onClose)
                Text("Scan campsite QR", style = EventsType.TitleSmall, color = EventsColors.Text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                if (granted && !manual) GlassIconButton(R.drawable.ic_flash, if (torch) "Turn torch off" else "Turn torch on", { torch = !torch })
                else Spacer(Modifier.size(44.dp))
            }
            Spacer(Modifier.weight(1f))
            if (!manual) {
                Viewfinder(Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(24.dp))
                Text(
                    if (granted) "Point at a campsite’s promo QR code to open it and join its lottery." else "Allow camera access to scan, or enter the code instead.",
                    style = EventsType.Body, color = EventsColors.Text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
            } else {
                DuskCard(Modifier.fillMaxWidth()) {
                    EventsTextField(code, { code = it; error = null }, "Campsite code", placeholder = "e.g. two-jack", leadingIcon = R.drawable.ic_qr_scan)
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Open campsite", {
                        val id = QrCodes.parseCampsiteId(code)
                        if (id != null) onCampsite(id) else error = "That isn't an Events campsite code."
                    }, Modifier.fillMaxWidth().testTag("scan_open"))
                }
            }
            if (error != null) Text(error!!, style = EventsType.BodySmall, color = EventsColors.Danger, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            Spacer(Modifier.weight(1f))
            val id = detectedId
            if (id != null && !manual) {
                DetectedCard(id, campsite, onOpen = { onCampsite(id) })
                Spacer(Modifier.height(10.dp))
            }
            TextLinkButton(if (manual) "Use camera" else "Enter code instead", { manual = !manual; error = null }, Modifier.align(Alignment.CenterHorizontally).testTag("scan_manual"), color = EventsColors.Text)
        }
    }
}

/** Figma "Detected" card: 76dp, radius 24, scene thumb, "QR detected", Open. */
@Composable
private fun DetectedCard(id: String, campsite: (String) -> Flow<Campsite?>, onOpen: () -> Unit) {
    val site by remember(id) { campsite(id) }.collectAsStateWithLifecycle(null)
    Row(
        Modifier.fillMaxWidth().clip(EventsShapes.Large).background(EventsColors.Surface).padding(10.dp).testTag("scan_detected"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))) { CampsiteImage(id, site?.imageBase64) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("QR detected", style = EventsType.Caption, color = EventsColors.Green)
            Text(site?.name ?: "Campsite $id", style = EventsType.BodyStrong, color = EventsColors.Text, maxLines = 1)
        }
        PrimaryButton("Open", onOpen, size = ButtonSize.SMALL)
    }
}

/** Figma viewfinder: 240dp, radius 28, 6% white, white corner brackets and a glowing lavender scan line. */
@Composable
private fun Viewfinder(modifier: Modifier) {
    val sweep = rememberInfiniteTransition(label = "scan")
    val y by sweep.animateFloat(0.18f, 0.82f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "line")
    Canvas(modifier.size(240.dp)) {
        val w = size.width
        val r = 28.dp.toPx()
        val sw = 6.dp.toPx()
        val inset = sw / 2 + 1.dp.toPx()
        drawRoundRect(Color.White.copy(alpha = 0.06f), cornerRadius = CornerRadius(r, r))
        // Corner brackets: the outline of a rounded square, clipped to its four corners.
        val arm = 52.dp.toPx()
        listOf(Offset(0f, 0f), Offset(w - arm, 0f), Offset(0f, w - arm), Offset(w - arm, w - arm)).forEach { o ->
            clipRect(o.x, o.y, o.x + arm, o.y + arm) {
                drawRoundRect(Color.White, Offset(inset, inset), Size(w - inset * 2, w - inset * 2), CornerRadius(r - inset, r - inset), style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        val lineY = w * y
        drawRoundRect(EventsColors.Primary.copy(alpha = 0.25f), Offset(14.dp.toPx(), lineY - 5.dp.toPx()), Size(w - 28.dp.toPx(), 13.dp.toPx()), CornerRadius(7.dp.toPx()))
        drawRoundRect(EventsColors.Primary, Offset(20.dp.toPx(), lineY), Size(w - 40.dp.toPx(), 3.dp.toPx()), CornerRadius(2.dp.toPx()))
    }
}

/** Live camera preview that reports every decoded QR code's text. */
@Composable
private fun CameraPreview(torch: Boolean, onDecoded: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    LaunchedEffect(camera, torch) { camera?.let { if (it.cameraInfo.hasFlashUnit()) it.cameraControl.enableTorch(torch) } }
    DisposableEffect(Unit) {
        onDispose {
            // Release the camera as soon as the preview leaves the screen.
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            executor.shutdown()
            scanner.close()
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val view = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    if (media == null) { proxy.close(); return@setAnalyzer }
                    scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                        .addOnSuccessListener { codes -> codes.firstNotNullOfOrNull { it.rawValue }?.let { v -> ContextCompat.getMainExecutor(context).execute { onDecoded(v) } } }
                        .addOnCompleteListener { proxy.close() }
                }
                runCatching {
                    provider.unbindAll()
                    camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }
            }, ContextCompat.getMainExecutor(ctx))
            view
        },
    )
}
