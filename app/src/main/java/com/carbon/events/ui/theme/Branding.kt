/*
 * Branding.kt (Aditya)
 *
 * Branding: the animated twilight sky (drawn in code), the campsite scene art, the logo and
 * the splash screen.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import android.content.Context
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** The three Twilight Sky variants from Figma, drawn in code by [TwilightSky]. */
enum class SkyVariant(val moon: Boolean = false, val mountains: Boolean = false) {
    /** Explore: sky with the moon top-right. */
    MOON(moon = true),

    /** Every other screen. */
    SCREEN,

    /** Splash and welcome: mountains along the bottom. */
    SPLASH(mountains = true),
}

/** How long the moon or the mountains take to fade in or out when the sky changes. */
private const val SKY_FADE_MS = 600

/**
 * Full-screen animated twilight background; [content] is drawn on top.
 *
 * The sky is drawn in code from the Figma Twilight Sky artwork (measured in its 720 x 1600
 * frame): the twilight gradient, three aurora ribbons whose wavy edges slowly flow sideways, and
 * stars that each twinkle on their own rhythm, plus the moon or the mountains for [variant].
 * Drawing it (instead of cross-fading four still pictures) gives real, smooth motion and keeps
 * the stars sharp at any size, landscape tablets included.
 *
 * The time is read only while drawing, so the sky redraws each frame without recomposing the
 * screen on top. Every sky follows the same clock, so moving between screens never restarts the
 * aurora; with animations turned off in Android settings the sky holds still.
 *
 * @param variant which Figma sky to show.
 */
@Composable
fun TwilightSky(modifier: Modifier = Modifier, variant: SkyVariant = SkyVariant.SCREEN, content: @Composable BoxScope.() -> Unit) {
    val clock = rememberSkyClock()
    val moon = animateFloatAsState(if (variant.moon) 1f else 0f, tween(SKY_FADE_MS), label = "moon")
    val mountains = animateFloatAsState(if (variant.mountains) 1f else 0f, tween(SKY_FADE_MS), label = "mountains")
    Box(
        modifier.fillMaxSize().background(EventsColors.SkyTop).clipToBounds().drawWithCache {
            val unit = skyUnit(size.width, size.height)
            val gradient = Brush.verticalGradient(*SKY_GRADIENT.toTypedArray(), startY = 0f, endY = size.height)
            val stars = skyStars(size.width, size.height, unit)
            onDrawBehind {
                val seconds = clock.longValue / 1000.0
                drawRect(gradient)
                drawAurora(seconds, unit)
                drawStars(stars, seconds)
                if (moon.value > 0f) drawMoon(unit, moon.value)
                if (mountains.value > 0f) drawMountains(unit, mountains.value)
            }
        },
        content = content,
    )
}

/**
 * Pixels per pixel of the 720 x 1600 Figma frame. The frame covers a phone screen exactly; on a
 * wider screen (a tablet in landscape) the art keeps its height instead of being blown up.
 */
internal fun skyUnit(width: Float, height: Float): Float = min(width / 720f, height / 1600f).coerceAtLeast(0.01f)

/** The twilight gradient, top to bottom, sampled from the Figma frames. */
private val SKY_GRADIENT = listOf(
    0f to Color(0xFF151A4B), 0.0375f to Color(0xFF252B64), 0.25f to Color(0xFF2C2A6D), 0.375f to Color(0xFF393382),
    0.5f to Color(0xFF534492), 0.625f to Color(0xFF6E57A1), 0.75f to Color(0xFF8D6AB1), 0.8125f to Color(0xFFA477AB),
    0.875f to Color(0xFFBA84A1), 0.9375f to Color(0xFFD18F9A), 1f to Color(0xFFE79B93),
)

// ---- Stars ----

/**
 * One star: where it is, how big, whether it is a four-point sparkle, and its twinkle (how bright
 * at most, how many seconds per twinkle, and where in the twinkle it starts).
 */
internal class SkyStar(val x: Float, val y: Float, val radius: Float, val sparkle: Boolean, val brightness: Float, val period: Float, val phase: Float)

/**
 * The stars for a [width] x [height] sky: always the same ones for the same size, about as many
 * per area as the Figma frames show, thinning out towards the warm horizon.
 */
internal fun skyStars(width: Float, height: Float, unit: Float): List<SkyStar> {
    val random = java.util.Random(301L)
    val frameArea = 720f * 1600f * unit * unit
    val count = (90 * (width * height / frameArea)).roundToInt().coerceIn(20, 400)
    val band = height * 0.72f
    return List(count) {
        val y = band * random.nextFloat().let { it * it * 0.2f + it * 0.8f } // a few more near the top
        val sparkle = random.nextFloat() < 0.16f
        SkyStar(
            x = random.nextFloat() * width,
            y = y,
            radius = unit * (if (sparkle) 1.6f + random.nextFloat() * 0.8f else 1f + random.nextFloat() * 1.1f),
            sparkle = sparkle,
            brightness = (0.55f + random.nextFloat() * 0.45f) * (1f - y / band * 0.6f),
            period = 2.4f + random.nextFloat() * 3.6f,
            phase = random.nextFloat() * 2f * PI.toFloat(),
        )
    }
}

/** How bright [star] is at [seconds]: each star rises and dims smoothly on its own rhythm. */
internal fun twinkle(star: SkyStar, seconds: Double): Float {
    val wave = 0.5 + 0.5 * sin(2 * PI * seconds / star.period + star.phase)
    return star.brightness * (0.3f + 0.7f * wave.toFloat())
}

private val StarColor = Color(0xFFFFFFFF)
private val StarHalo = Color(0xFFDCD3FA)

private fun DrawScope.drawStars(stars: List<SkyStar>, seconds: Double) {
    for (star in stars) {
        val alpha = twinkle(star, seconds)
        if (alpha <= 0.01f) continue
        val center = Offset(star.x, star.y)
        if (star.sparkle) {
            // A four-point sparkle whose arms grow a little as it brightens.
            val arm = star.radius * (2.4f + 1.4f * alpha)
            val width = star.radius * 0.55f
            drawLine(StarColor, Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), width, StrokeCap.Round, alpha = alpha * 0.85f)
            drawLine(StarColor, Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), width, StrokeCap.Round, alpha = alpha * 0.85f)
            drawCircle(StarHalo, star.radius * 2.2f, center, alpha = alpha * 0.18f)
        }
        drawCircle(StarColor, star.radius, center, alpha = alpha)
    }
}

// ---- Aurora ----

/**
 * One aurora ribbon, measured in the Figma frame: its top and bottom edges sit around [top] and
 * [bottom] and sway by [sway]; [colors] run from its top edge to its bottom edge (the bottom is
 * the bright fringe).
 */
private class Ribbon(val top: Float, val bottom: Float, val sway: Float, val phase: Float, val colors: List<Color>)

/** Back to front: the faint lower veil, the bright green ribbon, and the blue veil above it. */
private val RIBBONS = listOf(
    Ribbon(300f, 362f, 24f, 2.1f, listOf(Color(0xD9253966), Color(0xE6334C78), Color(0xF23E557F))),
    Ribbon(192f, 292f, 30f, 0.9f, listOf(Color(0xF22D516E), Color(0xFF386E7C), Color(0xFF569B98), Color(0xFF7CBDB5))),
    Ribbon(88f, 186f, 26f, 0f, listOf(Color(0x003A4A8B), Color(0x8C3E5A90), Color(0xF24C7997))),
)

/** Width of the slow, wide wave and of the quicker ripple on top of it, in Figma pixels. */
private const val WAVE = 540f
private const val RIPPLE = 300f

/**
 * Where the [edge] (0 = top, 1 = bottom) of [ribbon] is at [x] after [seconds], in Figma pixels.
 * Both edges share the wide wave, so the ribbon keeps its shape as it flows; the ripples differ,
 * so it gently thickens and thins.
 */
private fun ribbonEdge(ribbon: Ribbon, edge: Int, x: Float, seconds: Double): Float {
    val base = if (edge == 0) ribbon.top else ribbon.bottom
    val wide = sin(2 * PI * x / WAVE + seconds * 0.21 + ribbon.phase)
    val ripple = sin(2 * PI * x / RIPPLE - seconds * (0.33 + 0.07 * edge) + ribbon.phase * 1.7 + edge * 1.3)
    return base + ribbon.sway * (0.7f * wide.toFloat() + 0.3f * ripple.toFloat())
}

/** The top and bottom of each ribbon at [x] after [seconds], in Figma pixels; the bottom is always below the top. */
internal fun auroraEdges(x: Float, seconds: Double): List<Pair<Float, Float>> = RIBBONS.map { ribbon ->
    val top = ribbonEdge(ribbon, 0, x, seconds)
    top to max(ribbonEdge(ribbon, 1, x, seconds), top + 12f)
}

private fun DrawScope.drawAurora(seconds: Double, unit: Float) {
    val steps = (size.width / (12f * unit)).roundToInt().coerceIn(24, 160)
    val shimmer = (0.9 + 0.1 * sin(seconds * 0.6)).toFloat()
    for (ribbon in RIBBONS) {
        val path = Path()
        for (i in 0..steps) {
            val x = size.width * i / steps
            val y = ribbonEdge(ribbon, 0, x / unit, seconds) * unit
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        for (i in steps downTo 0) {
            val x = size.width * i / steps
            val top = ribbonEdge(ribbon, 0, x / unit, seconds)
            path.lineTo(x, max(ribbonEdge(ribbon, 1, x / unit, seconds), top + 12f) * unit)
        }
        path.close()
        val from = (ribbon.top - ribbon.sway) * unit
        val to = (ribbon.bottom + ribbon.sway) * unit
        drawPath(path, Brush.verticalGradient(ribbon.colors, from, to), alpha = shimmer)
    }
}

// ---- Moon (Explore) and mountains (splash and welcome) ----

private fun DrawScope.drawMoon(unit: Float, alpha: Float) {
    val r = 42f * unit
    val c = Offset(size.width - 121f * unit, 235f * unit)
    drawCircle(Brush.radialGradient(listOf(Color(0x33FFFFFF), Color(0x00FFFFFF)), c, r * 1.7f), r * 1.7f, c, alpha = alpha)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFF0F1F5), Color(0xFFE3E5EC), Color(0xFFCAD0D9)), c - Offset(r * 0.3f, r * 0.3f), r * 1.4f), r, c, alpha = alpha)
    // A few soft craters.
    for ((dx, dy, crater) in listOf(Triple(-0.32f, -0.12f, 0.17f), Triple(0.28f, 0.22f, 0.13f), Triple(0.02f, 0.5f, 0.09f), Triple(0.35f, -0.38f, 0.07f))) {
        drawCircle(Color(0xFFC7CBD8), r * crater, c + Offset(r * dx, r * dy), alpha = alpha * 0.85f)
    }
}

/** Mountain ridge and the ground in front of it, as (x across the frame, height above the bottom). */
private val RIDGE = listOf(0f to 178f, 120f to 270f, 192f to 225f, 312f to 321f, 432f to 228f, 528f to 290f, 720f to 200f)
private val GROUND = listOf(0f to 116f, 96f to 137f, 192f to 145f, 288f to 140f, 408f to 119f, 504f to 111f, 600f to 117f, 720f to 145f)

private fun DrawScope.drawMountains(unit: Float, alpha: Float) {
    drawPath(outline(RIDGE, unit, smooth = false), Color(0xFF392E66), alpha = alpha)
    drawPath(outline(GROUND, unit, smooth = true), Color(0xFF221E52), alpha = alpha)
}

/** A filled shape from the bottom of the screen up to [points] (stretched across the width), sharp or smooth. */
private fun DrawScope.outline(points: List<Pair<Float, Float>>, unit: Float, smooth: Boolean): Path {
    fun at(p: Pair<Float, Float>) = Offset(p.first / 720f * size.width, size.height - p.second * unit)
    val path = Path()
    path.moveTo(0f, size.height)
    path.lineTo(at(points.first()).x, at(points.first()).y)
    for (i in 1 until points.size) {
        val to = at(points[i])
        if (smooth) {
            val from = at(points[i - 1])
            val mid = (from.x + to.x) / 2f
            path.cubicTo(mid, from.y, mid, to.y, to.x, to.y)
        } else {
            path.lineTo(to.x, to.y)
        }
    }
    path.lineTo(size.width, size.height)
    path.close()
    return path
}

/**
 * The time every sky is drawn at, in milliseconds on the display's frame clock. All skies read the
 * same frame time, so two screens (or a screen and the next one fading in) show the same moment.
 * It stays at 0 when animations are off in Android settings.
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
