/*
 * StayCalendarTest.kt (Suvir)
 *
 * Purpose: Compose UI tests for StayCalendar.kt: winners pick nights inside the booking period, nights
 * already taken can't be tapped, and the summary counts the nights (US 01.04.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write UI tests for the stay calendar."
 * Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StayCalendarTest {
    @get:Rule val compose = createComposeRule()
    private val start = java.time.LocalDate.of(2026, 11, 10).toEpochDay()
    private val end = start + 10

    @Test fun tappingADayInThePeriod_picksIt() {
        val tapped = mutableListOf<Long>()
        compose.setContent { EventsTheme { StayCalendar(start, end, null, null, emptySet()) { tapped += it } } }
        compose.onNodeWithTag("day_${start + 2}").performClick()
        assertEquals(listOf(start + 2), tapped)
        compose.onNodeWithText("Select your dates").assertExists()
    }

    @Test fun daysOutsideThePeriod_orAlreadyTaken_cantBeTapped() {
        val tapped = mutableListOf<Long>()
        compose.setContent { EventsTheme { StayCalendar(start, end, null, null, setOf(start + 3)) { tapped += it } } }
        compose.onNodeWithTag("day_${start - 1}").performClick()
        compose.onNodeWithTag("day_${start + 3}").performClick()
        assertTrue(tapped.isEmpty())
        compose.onNodeWithTag("day_${start - 1}").assertIsNotEnabled()
    }

    @Test fun aPickedStay_showsHowManyNights() {
        compose.setContent { EventsTheme { StayCalendar(start, end, start + 1, start + 4, emptySet()) {} } }
        compose.onNodeWithText("3 nights", substring = true).assertExists()
    }

    @Test fun afterCheckIn_itAsksForCheckOut() {
        compose.setContent { EventsTheme { StayCalendar(start, end, start + 1, null, emptySet()) {} } }
        compose.onNodeWithText("Now pick check-out").assertExists()
    }

    @Test fun theMonthArrows_areNamed_andAtLeast48dp() {
        compose.setContent { EventsTheme { StayCalendar(start, start + 45, null, null, emptySet()) {} } }
        compose.onNodeWithTag("calendar_next").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("calendar_back").assertWidthIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Next month", useUnmergedTree = true).assertExists()
    }
}
