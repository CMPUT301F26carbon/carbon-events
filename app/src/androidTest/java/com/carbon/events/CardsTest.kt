/*
 * CardsTest.kt (Aditya)
 *
 * Purpose: Compose UI tests for Cards.kt: tags, chips, empty states, the lottery status tag and the
 * campsite card.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write UI tests for the cards and tags in Cards.kt."
 * Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.runtime.getValue
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
class CardsTest {
    @get:Rule val compose = createComposeRule()
    private val now = TestData.NOW

    @Test fun statusTag_isWrittenInCapitals() {
        compose.setContent { EventsTheme { StatusTag("Owner", TagTone.BLUE) } }
        compose.onNodeWithText("OWNER").assertExists()
    }

    @Test fun chip_runsItsAction() {
        var taps = 0
        compose.setContent { EventsTheme { EventsChip("Lakeside", ChipStyle.GLASS, { taps++ }, Modifier.testTag("chip")) } }
        compose.onNodeWithTag("chip").performClick()
        assertEquals(1, taps)
    }

    @Test fun emptyState_showsItsTitleMessageAndAction() {
        compose.setContent { EventsTheme { EmptyState(R.drawable.ic_tent, "No campsites", "Check back soon.") { TextLinkButton("Refresh", {}) } } }
        compose.onNodeWithText("No campsites").assertExists()
        compose.onNodeWithText("Check back soon.").assertExists()
        compose.onNodeWithText("Refresh").assertExists()
    }

    @Test fun lotteryStatusTag_saysWhereTheLotteryIs() {
        val open = TestData.lottery(start = now - TestData.DAY, end = now + 3 * TestData.DAY)
        var shown by mutableStateOf<Lottery?>(open)
        compose.setContent { EventsTheme { LotteryStatusTag(shown, now) } }
        compose.onNodeWithText("CLOSES IN 3 DAYS").assertExists()
        shown = open.copy(registrationStart = now + TestData.DAY, registrationEnd = now + 5 * TestData.DAY)
        compose.onNodeWithText("OPENS ${formatDay(now + TestData.DAY).uppercase()}").assertExists()
        shown = open.copy(registrationEnd = now - 1)
        compose.onNodeWithText("DRAWING WINNERS").assertExists()
        shown = open.copy(status = LotteryStatus.DRAWN)
        compose.onNodeWithText("WINNERS DRAWN").assertExists()
        shown = null
        compose.onNodeWithText("NO LOTTERY").assertExists()
    }

    @Test fun campsiteCard_showsTheCampsite_andOpensIt() {
        var opened = 0
        compose.setContent { EventsTheme { CampsiteListCard(TestData.campsite(), { opened++ }, Modifier.testTag("card"), distance = "12 km") } }
        compose.onNodeWithText("Two Jack Lakeside", substring = true).assertExists()
        compose.onNodeWithText("12 km", substring = true).assertExists()
        compose.onNodeWithTag("card").performClick()
        assertEquals(1, opened)
    }

    @Test fun ratingLine_showsTheAverageAndCount() {
        compose.setContent { EventsTheme { RatingLine(4.8, 126) } }
        compose.onNodeWithText("4.8", substring = true).assertExists()
        compose.onNodeWithText("(126)", substring = true).assertExists()
    }
}
