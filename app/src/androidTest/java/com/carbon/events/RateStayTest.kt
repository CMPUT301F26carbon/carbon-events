/*
 * RateStayTest.kt (Suvir)
 *
 * Purpose: Compose UI tests for RateStay.kt: tapping stars sets the rating, and a rating can only be
 * sent once a star is picked (US 01.13.01); a written review goes with it (US 01.13.02); stars that
 * only show a rating can't be tapped and read as "n of 5 stars"; each star is big enough to tap.
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
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RateStayTest {
    @get:Rule val compose = createComposeRule()
    private fun review() = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("rate_review")))

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

    @Test fun theReviewBox_takesText_upToTheLimit() {
        var review by mutableStateOf("")
        compose.setContent { EventsTheme { RateCard("Two Jack Lakeside", null, 4, {}, {}, {}, review, { review = it.take(RatingPolicy.MAX_COMMENT) }) } }
        review().performTextInput("Great views of Mount Rundle.")
        assertEquals("Great views of Mount Rundle.", review)
        compose.onNodeWithText("28 / ${RatingPolicy.MAX_COMMENT}").assertExists()
        review().performTextReplacement("x".repeat(RatingPolicy.MAX_COMMENT + 20))
        assertEquals(RatingPolicy.MAX_COMMENT, review.length)
    }

    @Test fun theDialog_sendsTheStarsAndTheReview() {
        var sent: Pair<Int, String>? = null
        compose.setContent { EventsTheme { RateDialog(null, {}, "Two Jack Lakeside") { s, c -> sent = s to c } } }
        compose.onNodeWithTag("star_3").performClick()
        review().performTextInput("  Windy but lovely.  ")
        compose.onNodeWithTag("rate_submit").performClick()
        assertEquals(3 to "Windy but lovely.", sent)
    }

    @Test fun theDialog_startsWithTheReviewAlreadyWritten() {
        val mine = Rating("two-jack", "maya", "Maya Chen", 4, "Busy weekend.", "lot-two-jack", 0L)
        compose.setContent { EventsTheme { RateDialog(mine, {}, "Two Jack Lakeside") { _, _ -> } } }
        review().assert(hasText("Busy weekend."))
        compose.onNodeWithText("4 of 5 stars · tap a star to change").assertExists()
    }

    @Test fun starsThatOnlyShowARating_cantBeTapped_andReadAsText() {
        compose.setContent { EventsTheme { StarRow(4, null, size = 14) } }
        compose.onNodeWithContentDescription("4 of 5 stars").assertExists().assertHasNoClickAction()
        compose.onAllNodesWithTag("star_1").assertCountEquals(0)
    }

    @Test fun tappableStars_areAtLeast48dp_andSayWhichIsPicked() {
        compose.setContent { EventsTheme { StarRow(2, {}, size = 14) } }
        compose.onNodeWithTag("star_1").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("star_2").assertIsSelected()
        compose.onNodeWithContentDescription("2 stars").assertIsSelected()
        compose.onNodeWithTag("star_3").assertIsNotSelected()
    }
}
