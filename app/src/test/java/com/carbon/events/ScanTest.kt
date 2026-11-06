/*
 * ScanTest.kt (Suvir)
 *
 * Purpose: Compose tests for Scan.kt: typing a promo QR code opens that campsite, and a wrong code is refused (US 01.12.01).
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScanTest {
    @get:Rule val compose = createComposeRule()

    private fun enter(code: String): String? {
        var opened: String? = null
        compose.setContent { EventsTheme { ScanScreen(onClose = {}, onCampsite = { opened = it }) } }
        compose.onNodeWithTag("scan_manual").performClick()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("scan_screen"))).performTextInput(code)
        compose.onNodeWithTag("scan_open").performClick()
        compose.waitForIdle()
        return opened
    }

    @Test fun promoCode_opensItsCampsite() = assertEquals("two-jack", enter(QrCodes.payloadFor("two-jack")))

    @Test fun otherCode_isRefused() {
        assertNull(enter("https://example.com/hello"))
        compose.onNodeWithText("That isn't an Events campsite code.").assertExists()
    }
}
