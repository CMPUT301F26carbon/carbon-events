/*
 * Branding.kt (Aditya)
 *
 * Branding: the animated twilight sky, the campsite scene art, the logo and
 * the splash screen.
 */
package com.carbon.events

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs
import kotlin.math.floor

/** The three Twilight Sky variants from Figma. */
enum class SkyVariant(@param:DrawableRes val frames: List<Int>) {
    /** Explore: sky with the moon top-right. */
    MOON(listOf(R.drawable.sky_moon_1, R.drawable.sky_moon_2, R.drawable.sky_moon_3, R.drawable.sky_moon_4)),

    /** Every other screen. */
    SCREEN(listOf(R.drawable.sky_screen_1, R.drawable.sky_screen_2, R.drawable.sky_screen_3, R.drawable.sky_screen_4)),

    /** Splash and welcome. */
    SPLASH(listOf(R.drawable.sky_splash_1, R.drawable.sky_splash_2, R.drawable.sky_splash_3, R.drawable.sky_splash_4)),
}

/** Seconds each Figma phase is shown before blending into the next. */
private const val PHASE_MS = 3_500

/**
 * Full-screen animated twilight background; [content] is drawn on top.
 *
 * @param variant which Figma sky to show.
 */
@Composable
fun TwilightSky(modifier: Modifier = Modifier, variant: SkyVariant = SkyVariant.SCREEN, content: @Composable BoxScope.() -> Unit) {
    val frames = variant.frames
    val transition = rememberInfiniteTransition(label = "sky")
    val t by transition.animateFloat(
        0f, frames.size.toFloat(), infiniteRepeatable(tween(PHASE_MS * frames.size, easing = LinearEasing)), label = "phase",
    )
    val current = floor(t).toInt() % frames.size
    val next = (current + 1) % frames.size
    // Hold each phase, then blend into the next over the last 40% of its slot (like Smart Animate).
    val frac = t - floor(t)
    val blend = ((frac - 0.6f) / 0.4f).coerceIn(0f, 1f)
    Box(modifier.fillMaxSize().background(EventsColors.SkyTop)) {
        SkyFrame(frames[current], 1f)
        if (blend > 0f) SkyFrame(frames[next], blend)
        content()
    }
}

@Composable
private fun BoxScope.SkyFrame(@DrawableRes res: Int, alpha: Float) {
    Image(
        painterResource(res), contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
        modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
    )
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
fun SplashScreen(usingServer: Boolean) {
    TwilightSky(variant = SkyVariant.SPLASH) {
        Column(Modifier.fillMaxSize().testTag("splash"), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(0.364f))
            Image(painterResource(R.drawable.splash_mark), contentDescription = "Events", modifier = Modifier.size(width = 176.dp, height = 138.dp))
            Spacer(Modifier.height(14.dp))
            Text("Events", style = EventsType.Wordmark, color = EventsColors.Text)
            Spacer(Modifier.height(14.dp))
            Text("Fair camping, by lottery", style = EventsType.Body, color = EventsColors.TextSoft)
            Spacer(Modifier.height(75.dp))
            Spinner()
            Spacer(Modifier.weight(0.5f))
            if (!usingServer) Text("Demo mode · sample data", style = EventsType.Caption, color = EventsColors.OnPrimary, modifier = Modifier.padding(bottom = 6.dp))
            Text("v1.0", style = EventsType.Caption, color = EventsColors.OnPrimary, modifier = Modifier.navigationBarsPadding().padding(bottom = 26.dp))
        }
    }
}
