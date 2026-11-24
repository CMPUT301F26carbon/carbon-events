/*
 * Branding.kt (Aditya)
 *
 * Branding: the animated twilight sky, the campsite scene art, the logo and
 * the splash screen.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import android.content.Context
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The three Twilight Sky variants from Figma. */
enum class SkyVariant(@param:DrawableRes val frames: List<Int>) {
    /** Explore: sky with the moon top-right. */
    MOON(listOf(R.drawable.sky_moon_1, R.drawable.sky_moon_2, R.drawable.sky_moon_3, R.drawable.sky_moon_4)),

    /** Every other screen. */
    SCREEN(listOf(R.drawable.sky_screen_1, R.drawable.sky_screen_2, R.drawable.sky_screen_3, R.drawable.sky_screen_4)),

    /** Splash and welcome. */
    SPLASH(listOf(R.drawable.sky_splash_1, R.drawable.sky_splash_2, R.drawable.sky_splash_3, R.drawable.sky_splash_4)),
}

/** How long each Figma phase lasts: a short hold, then a slow blend into the next. */
private const val PHASE_MS = 3_500L

/** Part of each phase spent blending into the next; the rest holds still. */
private const val BLEND_PART = 0.8f

/** How long a newly shown sky (Explore's moon, or the first decoded frames) takes to fade in. */
private const val SKY_FADE_MS = 600

/**
 * Full-screen animated twilight background; [content] is drawn on top.
 *
 * The phase is read only while drawing, so the animation redraws the sky each frame without
 * recomposing the screen on top of it, and the frames are decoded once (see [SkyFrameCache]).
 * Every sky follows the same clock, so moving between screens never restarts the aurora, and a
 * change of [variant] fades in. With animations turned off in Android settings, the sky holds still.
 *
 * @param variant which Figma sky to show.
 */
@Composable
fun TwilightSky(modifier: Modifier = Modifier, variant: SkyVariant = SkyVariant.SCREEN, content: @Composable BoxScope.() -> Unit) {
    val layers = rememberSkyLayers(variant)
    val clock = rememberSkyClock()
    Box(
        modifier.fillMaxSize().background(EventsColors.SkyTop).clipToBounds()
            .drawBehind {
                val millis = clock.longValue
                val fade = layers.fade.value
                layers.previous?.let { if (fade < 1f) drawSky(it, millis, 1f) }
                drawSky(layers.current, millis, fade)
            },
        content = content,
    )
}

/**
 * Draws the sky at [millis] on the shared clock: each frame holds briefly, then eases into the
 * next. A list of just one frame (the rest still decoding) is drawn still.
 */
private fun DrawScope.drawSky(frames: List<ImageBitmap>, millis: Long, alpha: Float) {
    if (frames.isEmpty() || alpha <= 0f) return
    if (frames.size == 1) return drawSkyFrame(frames[0], alpha)
    val (current, next, blend) = skyPhase(millis, frames.size)
    drawSkyFrame(frames[current], alpha)
    if (blend > 0f) drawSkyFrame(frames[next], alpha * blend)
}

/**
 * The frame shown at [millis] on the shared clock, the frame it is blending into, and how far
 * (0 to 1, eased so the blend starts and ends gently instead of with a jolt).
 */
internal fun skyPhase(millis: Long, count: Int): Triple<Int, Int, Float> {
    val slot = Math.floorDiv(millis, PHASE_MS)
    val current = Math.floorMod(slot, count.toLong()).toInt()
    val within = Math.floorMod(millis, PHASE_MS) / PHASE_MS.toFloat()
    val x = ((within - (1f - BLEND_PART)) / BLEND_PART).coerceIn(0f, 1f)
    return Triple(current, (current + 1) % count, x * x * (3f - 2f * x))
}

/** Draws [image] scaled to cover the whole area, keeping its top centre in view (like ContentScale.Crop + TopCenter). */
private fun DrawScope.drawSkyFrame(image: ImageBitmap, alpha: Float) {
    val scale = max(size.width / image.width, size.height / image.height)
    val w = (image.width * scale).roundToInt()
    val h = (image.height * scale).roundToInt()
    drawImage(
        image, dstOffset = IntOffset(((size.width - w) / 2f).roundToInt(), 0), dstSize = IntSize(w, h),
        alpha = alpha, filterQuality = FilterQuality.Low,
    )
}

/**
 * The time every sky is drawn at, in milliseconds on the display's frame clock. All skies read the
 * same frame time, so two screens (or a screen and the next one fading in) show the same phase.
 * It stays at 0, the first frame, when animations are off in Android settings.
 */
@Composable
private fun rememberSkyClock(): LongState {
    val context = LocalContext.current
    val still = remember { animationsOff(context) }
    val millis = remember { mutableLongStateOf(if (still) 0L else SystemClock.uptimeMillis()) }
    if (!still) LaunchedEffect(Unit) { while (true) withInfiniteAnimationFrameMillis { millis.longValue = it } }
    return millis
}

/** True when "Remove animations" (animator duration scale 0) is on. */
private fun animationsOff(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** The sky being shown, and the one it is fading in over. */
private class SkyLayers(first: List<ImageBitmap>) {
    var current by mutableStateOf(first)
    var previous by mutableStateOf<List<ImageBitmap>?>(null)
    val fade = Animatable(1f)
}

/**
 * The decoded frames of [variant]: the first one at once, all of them once they are decoded off
 * the main thread. Whenever new frames arrive (another variant, or the rest of this one) they
 * fade in over the old ones instead of popping in.
 */
@Composable
private fun rememberSkyLayers(variant: SkyVariant): SkyLayers {
    val res = LocalContext.current.resources
    val layers = remember { SkyLayers(SkyFrameCache.firstFrames(res, variant)) }
    LaunchedEffect(variant) {
        val loaded = SkyFrameCache.load(res, variant)
        if (loaded == layers.current) return@LaunchedEffect
        layers.previous = layers.current
        layers.current = loaded
        layers.fade.snapTo(0f)
        layers.fade.animateTo(1f, tween(SKY_FADE_MS, easing = LinearEasing))
        layers.previous = null
    }
    return layers
}

/**
 * Keeps the decoded frames of the sky shown last. Each frame is a full-screen image, so it is
 * decoded once, off the main thread, rather than on the main thread every few seconds.
 */
internal object SkyFrameCache {
    private var variant: SkyVariant? = null
    private var frames: List<ImageBitmap> = emptyList()

    /** The frames of [v] if they are already decoded, else null. */
    @Synchronized fun peek(v: SkyVariant): List<ImageBitmap>? = frames.takeIf { variant == v }

    /** All frames of [v] if ready; otherwise just the first, decoded now so the sky shows from the first frame drawn. */
    fun firstFrames(res: Resources, v: SkyVariant): List<ImageBitmap> = peek(v) ?: listOf(ImageBitmap.imageResource(res, v.frames.first()))

    /** All frames of [v], decoding them on a background thread the first time. */
    suspend fun load(res: Resources, v: SkyVariant): List<ImageBitmap> = peek(v) ?: withContext(Dispatchers.Default) {
        val decoded = v.frames.map { id -> ImageBitmap.imageResource(res, id).also { it.prepareToDraw() } }
        synchronized(this@SkyFrameCache) { variant = v; frames = decoded }
        decoded
    }
}

/** The four Scene themes from the Figma component set. */
enum class SceneTheme(@param:DrawableRes val image: Int) {
    SUNSET(R.drawable.scene_sunset),
    DAY(R.drawable.scene_day),
    GOLDEN(R.drawable.scene_golden),
    NIGHT(R.drawable.scene_night);

    companion object {
        /** The theme used for the campsite with [id]. */
        fun forId(id: String): SceneTheme = entries[abs(id.hashCode()) % entries.size]
    }
}

/**
 * Draws a Figma scene filling the available space.
 *
 * @param alignment which part of the scene stays visible when cropped (tent and moon sit low and right).
 */
@Composable
fun SceneArt(theme: SceneTheme, modifier: Modifier = Modifier, alignment: Alignment = Alignment.BottomCenter) {
    Image(painterResource(theme.image), null, modifier.fillMaxSize(), alignment = alignment, contentScale = ContentScale.Crop)
}

/** Decodes a base64 JPEG/PNG into an [ImageBitmap], or null if the data is invalid. */
fun decodeBase64Image(data: String?): ImageBitmap? = data?.let {
    runCatching {
        val bytes = Base64.decode(it, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

/**
 * Shows the campsite's uploaded photo if it has one, otherwise its Figma scene.
 *
 * @param campsiteId used to pick a stable scene theme.
 * @param imageBase64 the uploaded photo, if any.
 */
@Composable
fun CampsiteImage(campsiteId: String, imageBase64: String?, modifier: Modifier = Modifier, alignment: Alignment = Alignment.BottomCenter) {
    val bitmap = remember(imageBase64) { decodeBase64Image(imageBase64) }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.fillMaxSize())
    } else {
        SceneArt(SceneTheme.forId(campsiteId), modifier, alignment)
    }
}

/** The Figma "Logo/Mark" (rounded-square app mark). */
@Composable
fun LogoMark(size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.logo_mark), contentDescription = "Events logo", modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.28f)))
}

/** The Figma "Logo/Lockup": 36dp mark + "Events" in Plus Jakarta ExtraBold 22. */
@Composable
fun LogoLockup(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LogoMark(36.dp)
        Text("Events", style = EventsType.Wordmark.copy(fontSize = 22.sp, lineHeight = 28.sp), color = EventsColors.Text)
    }
}

/** Splash spinner: 24dp, 2.5dp white 30% track with a coral arc. */
@Composable
private fun Spinner() {
    val t = rememberInfiniteTransition(label = "spin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1_000, easing = LinearEasing)), label = "angle")
    Canvas(Modifier.size(24.dp).graphicsLayer { rotationZ = angle }) {
        val stroke = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)
        drawArc(EventsColors.Text.copy(alpha = 0.3f), 0f, 360f, false, style = stroke)
        drawArc(EventsColors.Coral, -90f, 90f, false, style = stroke)
    }
}

/** Splash screen (Figma S-SPLASH) shown while the profile loads. */
@Composable
fun SplashScreen(usingServer: Boolean, returningName: String? = null) {
    TwilightSky(variant = SkyVariant.SPLASH) {
        Column(Modifier.fillMaxSize().testTag("splash"), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(0.364f))
            Image(painterResource(R.drawable.splash_mark), contentDescription = "Events", modifier = Modifier.size(width = 176.dp, height = 138.dp))
            Spacer(Modifier.height(14.dp))
            Text("Events", style = EventsType.Wordmark, color = EventsColors.Text)
            Spacer(Modifier.height(14.dp))
            // Figma S-SPLASH-RETURNING greets someone who has signed in on this phone before.
            Text(returningName?.let { "Welcome back, $it" } ?: "Fair camping, by lottery", style = EventsType.Body, color = EventsColors.TextSoft, modifier = Modifier.testTag("splash_greeting"))
            Spacer(Modifier.height(75.dp))
            Spinner()
            Spacer(Modifier.weight(0.5f))
            Text("v${BuildConfig.VERSION_NAME}", style = EventsType.Caption, color = EventsColors.OnPrimary, modifier = Modifier.navigationBarsPadding().padding(bottom = 26.dp))
        }
    }
}
