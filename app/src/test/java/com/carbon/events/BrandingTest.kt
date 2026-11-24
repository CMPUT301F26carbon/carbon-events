/*
 * BrandingTest.kt (Aditya)
 *
 * Purpose: Compose and unit tests for Branding.kt: decoding uploaded photos, the splash screen, the scene art,
 * decoding each sky's frames only once, and the sky's eased phases on the shared clock.
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
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
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
        assertTrue(SkyVariant.values().all { it.frames.isNotEmpty() })
        assertTrue(SceneTheme.values().isNotEmpty())
    }

    @Test fun splash_greetsAReturningUser() {
        compose.setContent { EventsTheme { SplashScreen(usingServer = false, returningName = "Maya") } }
        compose.onNode(hasText("Maya", substring = true)).assertExists()
    }

    @Test fun skyFrames_areDecodedOnce_andReused() = runBlocking {
        val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val frames = SkyFrameCache.load(res, SkyVariant.SCREEN)
        assertEquals(SkyVariant.SCREEN.frames.size, frames.size)
        assertSame("a second sky on screen reuses the decoded frames", frames, SkyFrameCache.load(res, SkyVariant.SCREEN))
        assertSame(frames, SkyFrameCache.firstFrames(res, SkyVariant.SCREEN))
        // A sky not decoded yet starts with its first frame, ready to draw.
        assertEquals(1, SkyFrameCache.firstFrames(res, SkyVariant.SPLASH).size)
    }

    @Test fun twilightSky_drawsWithItsContentOnTop() {
        compose.setContent { EventsTheme { TwilightSky(variant = SkyVariant.MOON) { androidx.compose.material3.Text("On the sky") } } }
        compose.mainClock.advanceTimeBy(5_000)
        compose.onNodeWithText("On the sky").assertExists()
    }

    @Test fun skyPhase_holdsThenEasesIntoTheNextFrame() {
        // The start of a phase holds the frame still.
        assertEquals(Triple(0, 1, 0f), skyPhase(0L, 4))
        assertEquals(0f, skyPhase(600L, 4).third)
        // Halfway through the blend is half of each frame, and it eases (slow, fast, slow).
        assertEquals(0.5f, skyPhase(2_100L, 4).third, 0.001f)
        assertTrue(skyPhase(1_000L, 4).third < (1_000f - 700f) / 2_800f)
        // The end of a phase is (almost) all next frame, and the next phase starts on it.
        assertTrue(skyPhase(3_499L, 4).third > 0.99f)
        assertEquals(Triple(1, 2, 0f), skyPhase(3_500L, 4))
        // The last frame blends back into the first, and the cycle repeats.
        assertEquals(3 to 0, skyPhase(3 * 3_500L + 10, 4).let { it.first to it.second })
        assertEquals(skyPhase(1_234L, 4), skyPhase(4 * 3_500L + 1_234L, 4))
    }
}
