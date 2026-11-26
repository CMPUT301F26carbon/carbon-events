/*
 * WinnerBookingTest.kt (Suvir)
 *
 * Purpose: Compose UI (intent) tests for a lottery winner booking their stay: the calendar shows
 * the nights that can still be reserved, with other winners' nights struck out (US 01.04.01),
 * the rules have to be read and ticked before confirming (US 02.11.01), and the booking is
 * confirmed on screen with a downloadable CSV (US 01.05.01, US 01.05.02); and the winner sees when
 * they have to book by.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for a lottery winner choosing dates and confirming a reservation."
 * Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import android.Manifest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Johnston Canyon in the sample data: its lottery is drawn and its booking period is open. */
@RunWith(AndroidJUnit4::class)
class WinnerBookingTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 },
        ).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val me = UserProfile("test-phone", "Adam Kent", "adam@example.com")
    private val lot = "lot-johnston"
    private val today = dayOf(NOW)

    /** The sample data, plus Adam as a winner at Johnston Canyon who hasn't booked yet. Two other winners already have. */
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW).let { s ->
        s.copy(entries = s.entries + WaitlistEntry(lot, me.id, me.name, NOW - 10 * DAY, EntryStatus.WON, campsiteId = "johnston-canyon", campsiteName = "Johnston Canyon", ownerId = SampleData.OWNER_ID, wonAt = NOW - 2 * HOUR))
    })

    /** Signs [profile] in on this phone and opens the entrant app on Explore. */
    private fun launchEntrant(profile: UserProfile, seed: suspend () -> Unit = {}) {
        runBlocking {
            store.saveProfile(profile)
            store.linkDevice(profile.id, profile.id)
            seed()
        }
        val c = AppContainer.inMemory(profile.id, clock, store)
        compose.setContent {
            EventsTheme {
                EventsApp(c, showSplash = false, splash = {}, setup = {}, alerts = {},
                    entrant = { LocalAppSession.current.let { EntrantShell(it.container, it.requireProfile(), it.profileVm, null, {}) } },
                    owner = {}, admin = {},
                )
            }
        }
        waitFor("explore")
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun tap(tag: String) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) }
    private fun tapText(text: String) { compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick) }

    /** Explore > Lotteries tab > Choose your dates. */
    private fun openBooking() {
        launchEntrant(me)
        compose.onNodeWithTag("nav_Lotteries").performClick()
        compose.onNodeWithText("Results · 1").performClick()
        waitFor("book_$lot")
        tap("book_$lot")
        waitFor("book_title")
    }

    private fun myEntry() = store.snapshot.entries.first { it.lotteryId == lot && it.entrantId == me.id }
    private fun day(d: Long) = compose.onNodeWithTag("day_$d")
    private fun pick(d: Long) { day(d).performSemanticsAction(SemanticsActions.OnClick) }

    @Test fun winner_seesChooseYourDates_andTheBookingScreen() {
        openBooking()
        compose.onNodeWithTag("book_title").assertTextEquals("You Won Johnston Canyon!")
        compose.onNodeWithTag("stay_calendar").assertExists()
        compose.onNodeWithTag("book_confirm").assertIsNotEnabled()
    }

    @Test fun winner_seesWhenTheyHaveToBookBy() {
        openBooking()
        compose.onNodeWithTag("book_deadline", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Book by ${formatDayTime(NOW + 46 * HOUR)}", substring = true).assertExists()
    }

    @Test fun calendar_offersTheNightsStillFree() {
        openBooking()
        (today + 3..today + 9).forEach { day(it).assertHasClickAction().assertIsEnabled() }
    }

    @Test fun calendar_doesNotOfferNightsAnotherWinnerBooked() {
        openBooking()
        // Another winner is staying from yesterday until two days from now.
        listOf(today, today + 1).forEach { day(it).assertIsNotEnabled() }
    }

    @Test fun calendar_doesNotOfferNightsOutsideTheBookingPeriodOrInThePast() {
        openBooking()
        day(today + 10).assertIsNotEnabled() // the booking period ends on today + 9
        day(today - 3).assertIsNotEnabled() // inside the period, but already gone
    }

    @Test fun pickingCheckInAndCheckOut_showsThePrice() {
        openBooking()
        pick(today + 3); pick(today + 5)
        compose.onNodeWithText("$36 × 2 nights", substring = true).assertExists()
        compose.onNodeWithText("pay at check-in", substring = true).assertExists()
    }

    @Test fun confirm_staysDisabledUntilTheRulesAreTicked() {
        openBooking()
        pick(today + 3); pick(today + 5)
        compose.onNodeWithTag("book_confirm").assertIsNotEnabled()
        tap("book_agree")
        compose.onNodeWithTag("book_confirm").assertIsEnabled()
        assertFalse("nothing is booked yet", myEntry().isBooked)
    }

    @Test fun rulesAndPolicies_openTheCampsiteRules_andCanBeAcceptedThere() {
        openBooking()
        pick(today + 3); pick(today + 5)
        tap("book_rules")
        compose.onNodeWithTag("rules_sheet").assertExists()
        compose.onNodeWithText("Campsite rules & cancellation policy").assertExists()
        compose.onNodeWithText("Max 6 guests per site").assertExists()
        compose.onNodeWithText("Cancellation").assertExists()
        tap("rules_tick")
        tap("rules_got_it")
        compose.onNodeWithTag("rules_sheet").assertDoesNotExist()
        compose.onNodeWithTag("book_confirm").assertIsEnabled()
    }

    @Test fun confirming_savesTheNightsGuestsAndThatTheRulesWereRead() {
        openBooking()
        pick(today + 3); pick(today + 5)
        compose.onNodeWithContentDescription("Increase Guests").performSemanticsAction(SemanticsActions.OnClick)
        tap("book_agree")
        tap("book_confirm")
        waitFor("book_done")
        val entry = myEntry()
        assertEquals(today + 3, entry.checkInDay)
        assertEquals(today + 5, entry.checkOutDay)
        assertEquals(2, entry.guests)
        assertTrue(entry.agreedToRules)
    }

    @Test fun confirmation_showsTheStay_theAmountDue_andACode() {
        openBooking()
        pick(today + 3); pick(today + 5)
        tap("book_agree")
        tap("book_confirm")
        waitFor("book_done")
        compose.onNodeWithTag("book_done").assertTextEquals("You're Booked!")
        compose.onNodeWithText("Johnston Canyon").assertExists()
        compose.onNodeWithText("Due at check-in").assertExists()
        compose.onNodeWithText(reservationCode(lot, me.id)).assertExists()
    }

    @Test fun confirmation_offersTheCsvDownload_andTheBookingsList() {
        openBooking()
        pick(today + 3); pick(today + 5)
        tap("book_agree")
        tap("book_confirm")
        waitFor("book_done")
        compose.onNodeWithTag("download_csv").assertExists()
        compose.onNodeWithText("Download confirmation (CSV)").assertExists()
        tap("view_bookings")
        waitFor("entrant_bookings")
        compose.onNodeWithTag("my_booking_$lot").assertExists()
    }

    private companion object { const val NOW = 1_792_162_800_000L; const val DAY = 86_400_000L; const val HOUR = 3_600_000L }
}
