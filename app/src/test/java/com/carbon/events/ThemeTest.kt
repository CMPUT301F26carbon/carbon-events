/*
 * ThemeTest.kt (Aditya)
 *
 * Purpose: Compose and unit tests for Theme.kt: status tags, buttons and the date labels used across the app.
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
class ThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun statusTag_isUppercase() {
        compose.setContent { EventsTheme { StatusTag("Lottery live", TagTone.values().first()) } }
        compose.onNodeWithText("LOTTERY LIVE").assertExists()
    }

    @Test fun primaryButton_clicks() {
        var clicks = 0
        compose.setContent { EventsTheme { PrimaryButton("Join lottery", { clicks++ }) } }
        compose.onNodeWithText("Join lottery").performClick()
        assertEquals(1, clicks)
    }

    @Test fun formatStay_countsNights() {
        val start = java.time.LocalDate.of(2026, 11, 20).toEpochDay()
        assertTrue(formatStay(start, start + 3).endsWith("3 nights"))
        assertTrue(formatStay(start, start + 1).endsWith("1 night"))
    }
}
