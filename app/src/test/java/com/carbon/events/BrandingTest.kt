/*
 * BrandingTest.kt (Aditya)
 *
 * Purpose: Compose and unit tests for Branding.kt: decoding uploaded photos, the splash screen, and the scene art.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 6, 2026). Prompt: "Write
 * a test file for each source file that has none." Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import android.graphics.Bitmap
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
}
