/*
 * AppRoot.kt (Tausif)
 *
 * App root: picks splash, setup or the right role, plus shared helpers, maps and
 * photo / QR image tools.
 *
 * Maps are provided by Maps.kt using OpenStreetMap/osmdroid. Tile availability depends on
 * network/cache state; this app does not use a Google Maps key.
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
            ProfileState.Missing::class -> CompositionLocalProvider(LocalAppSession provides AppSession(container, profileVm, null)) {
                // Signed out: stop greeting the last person on the splash.
                val context = androidx.compose.ui.platform.LocalContext.current
                LaunchedEffect(Unit) {
                    container.drawScheduler.actAs(null, false)
                    if ((state as? ProfileState.Missing)?.account == null) ReturningUser.remember(context, null)
                }
                setup()
            }
            else -> {
                val profile = (state as? ProfileState.Ready)?.profile ?: return@Crossfade
                val context = androidx.compose.ui.platform.LocalContext.current
                // Greet by name on the next launch only if this phone is trusted (it will stay signed in);
                // an untrusted phone goes to Welcome, so it shouldn't say "Welcome back".
                val trustedHere by container.profiles.observeUserIdForDevice(container.deviceId).collectAsStateWithLifecycle(null)
                LaunchedEffect(profile.firstName, trustedHere) { ReturningUser.remember(context, profile.firstName.takeIf { trustedHere == profile.id }) }
                // This phone runs the draws for the person's own lotteries (or every lottery for an admin).
                LaunchedEffect(profile.id, profile.isAdmin) { container.drawScheduler.actAs(profile.id, profile.isAdmin) }
                if (profile.blocked) {
                    BlockedScreen(profile, onSignOut = profileVm::signOut)
                    return@Crossfade
                }
                // From here on the app acts as the person, whichever of their phones this is.
                val session = AppSession(container.forUser(profile.id), profileVm, profile, deepLinkCampsiteId, onDeepLinkConsumed)
                CompositionLocalProvider(LocalAppSession provides session) {
                    alerts()
                    // Only people with the admin flag can use admin mode; admins can also view the
                    // app as an entrant or owner from Profile (US 03.11.01).
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

/**
 * Shows [content] with a red "Offline demo" strip across the top when [enabled]: the build has no
 * Firebase config, so nothing is saved to the server and everything is lost when the app closes.
 */
@Composable
fun OfflineDemoFrame(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) return content()
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        content()
        Text(
            "Offline demo – no Firebase, nothing is saved",
            style = EventsType.Caption,
            color = EventsColors.OnPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(EventsColors.Danger).statusBarsPadding().padding(vertical = 2.dp)
                .testTag("offline_demo_banner"),
        )
    }
}

/** Remembers, on this phone only, the first name of whoever last signed in, for the splash greeting. */
object ReturningUser {
    private const val PREFS = "returning_user"
    private const val KEY = "first_name"

    /** The remembered first name, or null. */
    fun firstName(context: android.content.Context): String? =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString(KEY, null)

    /** Remembers [firstName] (or forgets it when null, e.g. after signing out). */
    fun remember(context: android.content.Context, firstName: String?) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putString(KEY, firstName).apply()
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
        // Booking and lottery-edit rules can also fail inside a Firestore transaction (wrapped).
        if (t is BookingException) return t.error.message
        if (t is AuthException) return t.error.message
        if (t is LotteryEditException) return t.message ?: break
        if (t is com.google.firebase.firestore.FirebaseFirestoreException) {
            android.util.Log.w("Events", "Firestore error ${t.code}", this)
            return when (t.code) {
                com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "The server didn't allow that. If you're setting up Firebase, publish the latest security rules."
                com.google.firebase.firestore.FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                    "This list needs a Firebase index. Open Logcat for the link that creates it."
                com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE ->
                    "Couldn't reach the server. Check your connection and try again."
                else -> "Something went wrong (${t.code.name.lowercase()}). Try again."
            }
        }
        t = t.cause
    }
    android.util.Log.w("Events", "Unexpected error", this)
    return "Something went wrong. Try again."
}

/** Maps are OpenStreetMap, which needs no key, so they're always available. */
val mapsAvailable: Boolean get() = true

/** Small map with a pin at the campsite (OpenStreetMap). [onClick] makes it tappable. */
@Composable
fun MapPreview(lat: Double, lng: Double, label: String, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 96.dp, onClick: (() -> Unit)? = null) {
    Box(modifier.fillMaxWidth().height(height).clip(EventsShapes.Medium).background(EventsColors.SurfaceSoft)) {
        OsmPreview(lat, lng, label, Modifier.fillMaxSize(), onClick)
    }
}

/**
 * Full-screen picker for a campsite's map pin (Figma O-MAP). Tap the map to move the pin.
 *
 * @param initial starting pin, or null to centre on Banff.
 * @param onConfirm called with the chosen latitude and longitude.
 */
@Composable
fun MapPickerDialog(initial: Pair<Double, Double>?, onConfirm: (Double, Double) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf(initial ?: (51.1784 to -115.5708)) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        TwilightSky {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                BackTopBar("Set Location", onDismiss)
                Box(Modifier.weight(1f).padding(horizontal = 16.dp).clip(EventsShapes.Large)) {
                    val start = remember { pin }
                    OsmMap(
                        start.first, start.second, 10.0, listOf(MapPin("pin", pin.first, pin.second, "Campsite")),
                        Modifier.fillMaxSize(), onTap = { lat, lng -> pin = lat to lng },
                    )
                }
                Text("Tap the map to drop the pin on your campsite.", style = EventsType.BodySmall, color = EventsColors.TextSoft, modifier = Modifier.padding(16.dp))
                PrimaryButton("Confirm location", { onConfirm(pin.first, pin.second) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), icon = R.drawable.ic_check)
            }
        }
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

/** Shown instead of the app when an admin has blocked this profile. */
@Composable
fun BlockedScreen(profile: UserProfile, onSignOut: () -> Unit) {
    TwilightSky {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp).semanticsTag("blocked"),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconTile(R.drawable.ic_shield, bg = EventsColors.CoralContainer)
            Text("Your account is blocked", style = EventsType.Headline, color = EventsColors.Text)
            Text(
                "An administrator blocked ${profile.firstName}'s account, so you can't join lotteries, list campsites or book. " +
                    "If you think this is a mistake, contact support@carbon-events.app.",
                style = EventsType.Body, color = EventsColors.TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            SecondaryButton("Sign out", onSignOut, Modifier.fillMaxWidth(), icon = R.drawable.ic_logout)
        }
    }
}
