/*
 * ThemeTest.kt (Aditya)
 *
 * Purpose: Compose and unit tests for Theme.kt: status tags, buttons, the date labels used across the app,
 * and screens keeping a readable width on tablets.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 6, 2026). Prompt: "Write
 * a test file for each source file that has none." Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.flow.first
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

    // ---- Tablets ----

    @Test fun readableWidth_onATablet_keepsScreensPhoneWidth_inTheMiddle() {
        compose.setContent {
            EventsTheme {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.requiredSize(1280.dp, 800.dp).testTag("device")) {
                    ReadableWidth { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().testTag("screen")) }
                }
            }
        }
        val device = compose.onNodeWithTag("device").getBoundsInRoot()
        val screen = compose.onNodeWithTag("screen").getBoundsInRoot()
        assertEquals(MaxContentWidth, screen.right - screen.left)
        assertEquals((1280.dp - MaxContentWidth) / 2, screen.left - device.left)
        assertEquals(800.dp, screen.bottom - screen.top)
    }

    @Test fun readableWidth_onAPhone_changesNothing() {
        compose.setContent {
            EventsTheme {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.requiredSize(360.dp, 780.dp).testTag("device")) {
                    ReadableWidth { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().testTag("screen")) }
                }
            }
        }
        val device = compose.onNodeWithTag("device").getBoundsInRoot()
        val screen = compose.onNodeWithTag("screen").getBoundsInRoot()
        assertEquals(360.dp, screen.right - screen.left)
        assertEquals(device.left, screen.left)
    }
}
