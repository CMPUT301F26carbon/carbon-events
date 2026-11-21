/*
 * ButtonsTest.kt (Aditya)
 *
 * Purpose: Compose UI tests for Buttons.kt: each button runs its action once, and a disabled or
 * loading button does nothing.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write UI tests for the buttons in Buttons.kt."
 * Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ButtonsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun primary_secondary_danger_andLink_eachRunTheirAction() {
        var taps = mutableListOf<String>()
        compose.setContent {
            EventsTheme {
                androidx.compose.foundation.layout.Column {
                    PrimaryButton("Join", { taps += "primary" }, Modifier.testTag("p"))
                    SecondaryButton("Leave", { taps += "secondary" }, Modifier.testTag("s"))
                    DangerButton("Delete", { taps += "danger" }, Modifier.testTag("d"))
                    TextLinkButton("Forgot?", { taps += "link" }, Modifier.testTag("l"))
                }
            }
        }
        listOf("p", "s", "d", "l").forEach { compose.onNodeWithTag(it).performClick() }
        assertEquals(listOf("primary", "secondary", "danger", "link"), taps)
    }

    @Test fun aDisabledButton_doesNothing() {
        var taps = 0
        compose.setContent { EventsTheme { PrimaryButton("Join", { taps++ }, Modifier.testTag("p"), enabled = false) } }
        compose.onNodeWithTag("p").assertIsNotEnabled().performClick()
        assertEquals(0, taps)
    }

    @Test fun aLoadingButton_doesNothing_whileItWorks() {
        var taps = 0
        compose.setContent { EventsTheme { PrimaryButton("Join", { taps++ }, Modifier.testTag("p"), loading = true) } }
        compose.onNodeWithTag("p").performClick()
        assertEquals(0, taps)
    }

    @Test fun glassIconButton_isNamedForScreenReaders_andShowsItsCount() {
        var taps = 0
        compose.setContent { EventsTheme { GlassIconButton(R.drawable.ic_bell, "Notifications", { taps++ }, badgeText = "3") } }
        compose.onNodeWithContentDescription("Notifications").performClick()
        assertEquals(1, taps)
        compose.onNodeWithText("3").assertExists()
    }
}
