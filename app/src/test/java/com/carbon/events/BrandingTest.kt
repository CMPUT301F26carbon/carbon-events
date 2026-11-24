/*
 * BrandingTest.kt (Aditya)
 *
 * Purpose: Compose and unit tests for Branding.kt: decoding uploaded photos, the splash screen, the scene art,
 * and the drawn twilight sky: its size on phones and tablets, the twinkling stars and the flowing aurora.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 6, 2026). Prompt: "Write
 * a test file for each source file that has none." Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import android.graphics.Bitmap
import kotlin.math.abs
import android.util.Base64
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrandingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun decodeBase64Image_readsAPhoto_andSkipsMissing() {
        val bmp = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)
        val png = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val img = decodeBase64Image(Base64.encodeToString(png, Base64.DEFAULT))
        assertNotNull(img)
        assertEquals(4, img!!.width)
        assertNull(decodeBase64Image(null))
    }

    @Test fun everySkyAndScene_hasArt() {
        assertTrue(SkyVariant.MOON.moon && SkyVariant.SPLASH.mountains && !SkyVariant.SCREEN.moon)
        assertTrue(SceneTheme.values().isNotEmpty())
    }

    @Test fun splash_greetsAReturningUser() {
        compose.setContent { EventsTheme { SplashScreen(usingServer = false, returningName = "Maya") } }
        compose.onNode(hasText("Maya", substring = true)).assertExists()
    }

    @Test fun twilightSky_drawsWithItsContentOnTop() {
        compose.setContent { EventsTheme { TwilightSky(variant = SkyVariant.MOON) { androidx.compose.material3.Text("On the sky") } } }
        compose.mainClock.advanceTimeBy(5_000)
        compose.onNodeWithText("On the sky").assertExists()
    }

    @Test fun skyUnit_coversAPhone_andKeepsTheArtsHeightOnALandscapeTablet() {
        assertEquals(1.5f, skyUnit(1080f, 2400f), 0.001f)
        // A landscape tablet is scaled by its height, not blown up by its width.
        assertEquals(1f, skyUnit(2560f, 1600f), 0.001f)
    }

    @Test fun stars_areTheSameEachTime_andALargerSkyHasMore() {
        val phone = skyStars(1080f, 2400f, 1.5f)
        assertEquals(phone.map { it.x to it.y }, skyStars(1080f, 2400f, 1.5f).map { it.x to it.y })
        assertTrue(phone.size in 60..120)
        assertTrue(skyStars(2560f, 1600f, 1f).size > phone.size)
        // Stars stay in the sky above the warm horizon, and some of them are sparkles.
        assertTrue(phone.all { it.x in 0f..1080f && it.y in 0f..(2400f * 0.72f) })
        assertTrue(phone.any { it.sparkle } && phone.any { !it.sparkle })
    }

    @Test fun stars_twinkleSmoothly_eachOnItsOwnRhythm() {
        val stars = skyStars(1080f, 2400f, 1.5f)
        for (star in stars) {
            val levels = (0..600).map { twinkle(star, it / 60.0) } // ten seconds at 60 frames a second
            assertTrue("never fully off, never over its brightness", levels.all { it > 0f && it <= star.brightness + 1e-4f })
            assertTrue("changes a little from frame to frame", levels.zipWithNext().all { (a, b) -> abs(a - b) < 0.05f })
            assertTrue("really twinkles", levels.max() - levels.min() > star.brightness * 0.5f)
        }
        assertTrue("not all in step", stars.map { twinkle(it, 1.0) }.distinct().size > stars.size / 2)
    }

    @Test fun aurora_flowsSmoothly_andItsRibbonsNeverTurnInsideOut() {
        for (x in listOf(0f, 180f, 360f, 540f, 720f, 1800f)) {
            for (frame in 0..1200) {
                val now = auroraEdges(x, frame / 60.0)
                assertTrue(now.all { (top, bottom) -> bottom > top && top > 60f && bottom < 390f })
                val next = auroraEdges(x, (frame + 1) / 60.0)
                assertTrue("moves less than a pixel a frame", now.zip(next).all { (a, b) -> abs(a.first - b.first) < 1f && abs(a.second - b.second) < 1f })
            }
        }
        assertNotEquals("it moves", auroraEdges(300f, 0.0), auroraEdges(300f, 5.0))
    }
}
