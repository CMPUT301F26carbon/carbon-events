/*
 * AppRoot.kt (Tausif)
 *
 * App root: picks splash, setup or the right role, plus shared helpers, maps and
 * photo / QR image tools.
 */
package com.carbon.events

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** What every area of the app needs: the data, the profile and any waiting promo link. */
data class AppSession(
    val container: AppContainer,
    val profileVm: ProfileViewModel,
    val profile: UserProfile?,
    val deepLinkCampsiteId: String? = null,
    val onDeepLinkConsumed: () -> Unit = {},
) {
    /** The signed-in profile; only null during first-launch setup. */
    fun requireProfile(): UserProfile = checkNotNull(profile) { "No profile yet" }
}

/** The session for the screen being drawn, provided by [EventsApp]. */
val LocalAppSession = staticCompositionLocalOf<AppSession> { error("EventsApp provides the session") }

/**
 * The whole app: splash while the profile loads, first-launch setup, then the area for the
 * active role. MainActivity passes in each area so every teammate's part plugs in here.
 *
 * @param showSplash whether to hold the splash briefly (off in UI tests).
 */
@Composable
fun EventsApp(
    container: AppContainer,
    deepLinkCampsiteId: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    showSplash: Boolean = true,
    splash: @Composable () -> Unit,
    setup: @Composable () -> Unit,
    alerts: @Composable () -> Unit,
    entrant: @Composable () -> Unit,
    owner: @Composable () -> Unit,
    admin: @Composable () -> Unit,
) {
    val profileVm = containerViewModel(container) { ProfileViewModel(it) }
    val state by profileVm.state.collectAsStateWithLifecycle()
    var splashDone by remember { mutableStateOf(!showSplash) }
    LaunchedEffect(Unit) { if (showSplash) { delay(1_100); splashDone = true } }

    val screen = if (!splashDone) ProfileState.Loading else state
    Crossfade(targetState = screen::class, label = "root") { kind ->
        when (kind) {
            ProfileState.Loading::class -> splash()
            ProfileState.Missing::class -> CompositionLocalProvider(LocalAppSession provides AppSession(container, profileVm, null)) { setup() }
            else -> {
                val profile = (state as? ProfileState.Ready)?.profile ?: return@Crossfade
                val session = AppSession(container, profileVm, profile, deepLinkCampsiteId, onDeepLinkConsumed)
                CompositionLocalProvider(LocalAppSession provides session) {
                    alerts()
                    // Only people with the admin flag can stay in admin mode.
                    val role = if (profile.activeRole == Role.ADMIN && !profile.isAdmin) Role.ENTRANT else profile.activeRole
                    when (role) {
                        Role.ENTRANT -> entrant()
                        Role.OWNER -> owner()
                        Role.ADMIN -> admin()
                    }
                }
            }
        }
    }
}

/** Creates (or reuses) a view model of type [VM] built from the [container]. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(container: AppContainer, key: String? = null, crossinline create: (AppContainer) -> VM): VM =
    viewModel(key = key) { create(container) }

/** Emits the current time now and then every [periodMillis], for countdowns. */
fun nowTicker(clock: Clock, periodMillis: Long = 30_000L): Flow<Long> = flow {
    while (true) {
        emit(clock.now())
        delay(periodMillis)
    }
}

/** A message suitable for showing the user when [this] is thrown by a repository call. */
fun Throwable.userMessage(): String {
    var t: Throwable? = this
    while (t != null) {
        if (t is WaitlistException) return t.error.message
        t = t.cause
    }
    return "Something went wrong. Check your connection and try again."
}

/** A titled tab with an empty state card. */
@Composable
fun TabPlaceholder(title: String, @DrawableRes icon: Int, emptyTitle: String, message: String) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = PageMargin).padding(top = 8.dp).testTag("tab_$title"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, style = EventsType.Headline, color = EventsColors.Text)
        DuskCard { EmptyState(icon, emptyTitle, message) }
    }
}

/** Whether a Google Maps API key was configured for this build. */
val mapsAvailable: Boolean get() = BuildConfig.HAS_MAPS_KEY

/** Read-only map preview with a pin at the campsite, or a location card without a key. */
@Composable
fun MapPreview(lat: Double, lng: Double, label: String, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 96.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(EventsShapes.Medium).background(EventsColors.SurfaceSoft)) {
        if (mapsAvailable) {
            val pos = LatLng(lat, lng)
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(pos, 11f) },
                uiSettings = MapUiSettings(zoomControlsEnabled = false, scrollGesturesEnabled = false, zoomGesturesEnabled = false, mapToolbarEnabled = false),
            ) { Marker(state = remember { MarkerState(pos) }, title = label) }
        } else {
            IllustratedMap(Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.Center).size(36.dp).clip(CircleShape).background(EventsColors.Primary)
                    .border(2.dp, EventsColors.SkyTop, CircleShape),
                contentAlignment = Alignment.Center,
            ) { EventsIcon(R.drawable.ic_tent, tint = EventsColors.OnPrimary, size = 18.dp, contentDescription = label) }
        }
    }
}

/** Stylised map from the Figma details screen (river, coral roads), used when no Maps key is set. */
@Composable
private fun IllustratedMap(modifier: Modifier) {
    Canvas(modifier.background(Color(0xFF34307A))) {
        val w = size.width; val h = size.height
        // Faint terrain blobs.
        drawCircle(Color(0xFF3E3A8A), radius = h * 0.16f, center = Offset(w * 0.45f, h * 0.18f))
        drawCircle(Color(0xFF3E3A8A), radius = h * 0.13f, center = Offset(w * 0.88f, h * 0.22f))
        drawCircle(Color(0xFF3E3A8A), radius = h * 0.1f, center = Offset(w * 0.12f, h * 0.85f))
        // River.
        val river = Path().apply {
            moveTo(0f, h * 0.62f)
            cubicTo(w * 0.25f, h * 0.45f, w * 0.45f, h * 0.9f, w * 0.7f, h * 0.68f)
            cubicTo(w * 0.82f, h * 0.58f, w * 0.92f, h * 0.62f, w, h * 0.58f)
        }
        drawPath(river, Color(0xFF5560C8), style = Stroke(width = h * 0.16f, cap = StrokeCap.Round))
        // Roads.
        val road = Color(0xFFE89A8C)
        val roadWidth = 2.dp.toPx()
        drawLine(road, Offset(0f, h * 0.3f), Offset(w, h * 0.18f), roadWidth)
        drawLine(road, Offset(w * 0.18f, 0f), Offset(w * 0.3f, h), roadWidth)
        drawLine(road, Offset(w * 0.64f, 0f), Offset(w * 0.74f, h), roadWidth)
    }
}

/**
 * Full-screen picker for a campsite's map pin. Tap the map to move the pin.
 *
 * @param initial starting pin, or null to centre on Banff.
 * @param onConfirm called with the chosen latitude and longitude.
 */
@Composable
fun MapPickerDialog(initial: Pair<Double, Double>?, onConfirm: (Double, Double) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf(initial?.let { LatLng(it.first, it.second) } ?: LatLng(51.1784, -115.5708)) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        TwilightSky {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                BackTopBar("Set Location", onDismiss)
                Box(Modifier.weight(1f).padding(horizontal = 16.dp).clip(EventsShapes.Large)) {
                    if (mapsAvailable) {
                        val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(pin, 10f) }
                        GoogleMap(Modifier.fillMaxSize(), cameraPositionState = camera, onMapClick = { pin = it }) {
                            Marker(state = MarkerState(pin), title = "Campsite")
                        }
                    } else {
                        CoordinateEntry(pin) { pin = it }
                    }
                }
                Text(
                    if (mapsAvailable) "Tap the map to drop the pin on your campsite." else "Maps key not set up yet, so enter the coordinates.",
                    style = EventsType.BodySmall, color = EventsColors.TextSoft, modifier = Modifier.padding(16.dp),
                )
                PrimaryButton("Confirm location", { onConfirm(pin.latitude, pin.longitude) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), icon = R.drawable.ic_check)
            }
        }
    }
}

@Composable
private fun CoordinateEntry(pin: LatLng, onChange: (LatLng) -> Unit) {
    var lat by remember { mutableStateOf(pin.latitude.toString()) }
    var lng by remember { mutableStateOf(pin.longitude.toString()) }
    Column(Modifier.fillMaxSize().background(EventsColors.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        EventsTextField(lat, { lat = it; it.toDoubleOrNull()?.let { v -> onChange(LatLng(v, pin.longitude)) } }, "Latitude", keyboardType = KeyboardType.Decimal)
        EventsTextField(lng, { lng = it; it.toDoubleOrNull()?.let { v -> onChange(LatLng(pin.latitude, v)) } }, "Longitude", keyboardType = KeyboardType.Decimal)
    }
}

/** Encodes [text] as a QR code bitmap of [sizePx] pixels (dark plum on white). */
fun qrBitmap(text: String, sizePx: Int = 512): ImageBitmap = qrAndroidBitmap(text, sizePx).asImageBitmap()

/**
 * Encodes [text] with high error correction, so the logo drawn over the centre (Figma
 * promo QR) still scans.
 */
private fun qrAndroidBitmap(text: String, sizePx: Int): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H)
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val pixels = IntArray(sizePx * sizePx) { i -> if (matrix[i % sizePx, i / sizePx]) 0xFF2A2160.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
}

/**
 * Shows the QR code for [payload] on a white rounded tile (Figma: radius 9).
 *
 * @param withLogo draws the Events mark in the centre, as on the Figma promo QR.
 */
@Composable
fun QrCodeImage(payload: String, size: Dp = 180.dp, modifier: Modifier = Modifier, withLogo: Boolean = false) {
    val bitmap = remember(payload) { qrBitmap(payload) }
    Box(modifier.size(size).clip(RoundedCornerShape(9.dp)).background(Color.White).padding(6.dp), contentAlignment = Alignment.Center) {
        Image(bitmap, contentDescription = "Promo QR code", filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize())
        if (withLogo) {
            Image(
                painterResource(R.drawable.logo_mark), null,
                Modifier.size(size * 0.22f).clip(RoundedCornerShape(5.dp)).background(Color.White).padding(2.dp).clip(RoundedCornerShape(4.dp)),
            )
        }
    }
}

/**
 * Saves the promo QR for [payload] as a PNG in Pictures/Events so owners can print it
 * (US 02.02.01). Uses MediaStore, which needs no storage permission on Android 10+.
 *
 * @return true if the image was written.
 */
fun saveQrToPhotos(context: Context, payload: String, title: String): Boolean = runCatching {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, title.replace(Regex("[^A-Za-z0-9 _-]"), "") + ".png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Events")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    context.contentResolver.openOutputStream(uri)?.use { qrAndroidBitmap(payload, 1024).compress(Bitmap.CompressFormat.PNG, 100, it) } != null
}.getOrDefault(false)

/**
 * Loads the image at [uri], scales its longest side down to [maxSide] pixels, and
 * returns it as a base64 JPEG string (roughly 60–150 KB).
 *
 * @return the encoded image, or null if it could not be read.
 */
fun compressImageToBase64(context: Context, uri: Uri, maxSide: Int = 900, quality: Int = 72): String? = runCatching {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val w = info.size.width
        val h = info.size.height
        val scale = maxSide.toFloat() / maxOf(w, h)
        if (scale < 1f) decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
    Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}.getOrNull()

/** True if [base64] decodes to an image (used by tests and validation). */
fun isValidImage(base64: String): Boolean = runCatching {
    val bytes = Base64.decode(base64, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null
}.getOrDefault(false)
