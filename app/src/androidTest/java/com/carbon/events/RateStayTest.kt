/*
 * RateStayTest.kt (Suvir)
 *
 * Purpose: Compose UI tests for RateStay.kt: tapping stars sets the rating, and a rating can only be
 * sent once a star is picked (US 01.06.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write UI tests for rating a stay."
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
class RateStayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tappingAStar_setsThatManyStars() {
        var stars by mutableIntStateOf(0)
        compose.setContent { EventsTheme { StarRow(stars, { stars = it }) } }
        compose.onNodeWithTag("star_4").performClick()
        assertEquals(4, stars)
    }

    @Test fun ratingCard_canOnlyBeSent_afterPickingAStar() {
        var stars by mutableIntStateOf(0)
        var sent = 0
        var later = 0
        compose.setContent { EventsTheme { RateCard("Two Jack Lakeside", "Oct 11 – 14", stars, { stars = it }, { sent++ }, { later++ }) } }
        compose.onNodeWithText("Rate Two Jack Lakeside").assertExists()
        compose.onNodeWithText("Tap a star to rate").assertExists()
        compose.onNodeWithTag("rate_submit").assertIsNotEnabled()
        compose.onNodeWithTag("star_5").performClick()
        compose.onNodeWithText("5 of 5 stars · tap a star to change").assertExists()
        compose.onNodeWithTag("rate_submit").performClick()
        assertEquals(1, sent)
    }
}
