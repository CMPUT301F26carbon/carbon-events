/*
 * MyReservationsTest.kt (Vaibhav)
 *
 * Purpose: Compose UI (intent) tests for the entrant's Bookings tab: the list of active
 * reservations (US 01.06.01), laid out as in Figma E-BOOKINGS (the next stay as a photo card,
 * later ones as rows), the list of past ones (US 01.06.02), and opening one.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for an entrant viewing their active and past reservations."
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

/** An entrant with one upcoming stay, one in progress, one finished and one cancelled. */
@RunWith(AndroidJUnit4::class)
class MyReservationsTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 },
        ).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val me = UserProfile("test-phone", "Adam Kent", "adam@example.com")
    private val today = dayOf(NOW)

    private fun drawn(id: String, campsiteId: String, name: String) =
        Lottery(id, campsiteId, name, SampleData.OWNER_ID, NOW - 40 * DAY, NOW - 30 * DAY, 3, status = LotteryStatus.DRAWN, drawnAt = NOW - 30 * DAY,
            bookingStartDay = today - 25, bookingEndDay = today + 25)

    private fun booked(lotteryId: String, campsiteId: String, name: String, from: Long, until: Long, guests: Int, status: EntryStatus = EntryStatus.WON) =
        WaitlistEntry(lotteryId, me.id, me.name, NOW - 35 * DAY, status, from, until, NOW - 29 * DAY, campsiteId, name, SampleData.OWNER_ID, guests, true,
            endedAt = if (status == EntryStatus.WON) null else NOW - 2 * DAY)

    /**
     * Adam's stays on top of the sample data: one coming up, one he is on right now, one that has
     * finished and one he cancelled.
     */
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW).let { s ->
        s.copy(
            lotteries = s.lotteries + listOf(
                drawn(UPCOMING, "tunnel-mountain", "Tunnel Mountain Village"), drawn(NOW_STAYING, "two-jack", "Two Jack Lakeside"),
                drawn(FINISHED, "waterfowl-lakes", "Waterfowl Lakes"), drawn(CANCELLED, "moraine-lake", "Moraine Lake Sites"),
            ).associateBy { it.id },
            entries = s.entries + listOf(
                booked(UPCOMING, "tunnel-mountain", "Tunnel Mountain Village", today + 6, today + 8, 2),
                booked(NOW_STAYING, "two-jack", "Two Jack Lakeside", today - 1, today + 2, 4),
                booked(FINISHED, "waterfowl-lakes", "Waterfowl Lakes", today - 20, today - 17, 3),
                booked(CANCELLED, "moraine-lake", "Moraine Lake Sites", today + 12, today + 14, 2, EntryStatus.CANCELLED),
            ),
        )
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

    private fun openBookings() {
        launchEntrant(me)
        compose.onNodeWithTag("nav_Bookings").performClick()
        waitFor("entrant_bookings")
    }

    private fun card(lotteryId: String) = compose.onNodeWithTag("my_booking_$lotteryId")
    private fun inCard(lotteryId: String, text: String) =
        compose.onNode(hasText(text, substring = true, ignoreCase = true) and hasAnyAncestor(hasTestTag("my_booking_$lotteryId")), useUnmergedTree = true)
    private fun scrollTo(lotteryId: String) { compose.onNodeWithTag("entrant_bookings").performScrollToNode(hasTestTag("my_booking_$lotteryId")) }
    private fun entry(lotteryId: String) = store.snapshot.entries.first { it.lotteryId == lotteryId && it.entrantId == me.id }

    @Test fun activeTab_listsUpcomingAndCurrentStays() {
        openBookings()
        compose.onNodeWithText("Active · 2").assertExists()
        scrollTo(UPCOMING); card(UPCOMING).assertExists()
        scrollTo(NOW_STAYING); card(NOW_STAYING).assertExists()
        card(FINISHED).assertDoesNotExist()
        card(CANCELLED).assertDoesNotExist()
    }

    @Test fun theSoonestStay_isThePhotoCard_withDatesNightsAndGuests() {
        openBookings()
        compose.onNodeWithTag("featured_booking_$NOW_STAYING", useUnmergedTree = true).assertExists()
        inCard(NOW_STAYING, "Two Jack Lakeside").assertExists()
        inCard(NOW_STAYING, "Staying now").assertExists()
        inCard(NOW_STAYING, "3 nights").assertExists()
        inCard(NOW_STAYING, "4 guests").assertExists()
    }

    @Test fun laterStays_areRows_withDatesGuestsStatusAndChat() {
        openBookings()
        scrollTo(UPCOMING)
        compose.onNodeWithTag("featured_booking_$UPCOMING", useUnmergedTree = true).assertDoesNotExist()
        inCard(UPCOMING, "Tunnel Mountain Village").assertExists()
        inCard(UPCOMING, "2 guests").assertExists()
        inCard(UPCOMING, "Confirmed").assertExists()
        compose.onNodeWithTag("chat_$UPCOMING").assertExists()
        compose.onNodeWithTag("cancel_$UPCOMING").assertDoesNotExist() // cancelled from its details
    }

    @Test fun pastTab_listsFinishedAndCancelledStays() {
        openBookings()
        compose.onNodeWithText("Past · 2").assertExists()
        tap("my_bookings_tab_1")
        card(FINISHED).assertExists()
        inCard(FINISHED, "Waterfowl Lakes").assertExists()
        inCard(FINISHED, "Completed").assertExists()
        inCard(FINISHED, "3 guests").assertExists()
        card(CANCELLED).assertExists()
        inCard(CANCELLED, "Cancelled").assertExists()
        card(UPCOMING).assertDoesNotExist()
    }

    @Test fun aStayThatHasFinished_movesFromActiveToPast() {
        clock.advanceBy(3 * DAY) // the stay in progress ended yesterday
        openBookings()
        compose.onNodeWithText("Active · 1").assertExists()
        compose.onNodeWithText("Past · 3").assertExists()
    }

    @Test fun tappingAReservation_opensItsDetails() {
        openBookings()
        scrollTo(UPCOMING)
        card(UPCOMING).performSemanticsAction(SemanticsActions.OnClick)
        waitFor("reservation_details")
        compose.onNodeWithText("Tunnel Mountain Village").assertExists()
        compose.onNodeWithText(reservationCode(UPCOMING, me.id)).assertExists()
        compose.onNodeWithText("Show this code at check-in").assertExists()
        compose.onNodeWithText("Parks Lodge").assertExists() // the host
    }

    @Test fun someoneWithNoReservations_isPointedToExplore() {
        launchEntrant(UserProfile("new-phone", "Jordan Lee", "jordan@example.com"))
        compose.onNodeWithTag("nav_Bookings").performClick()
        waitFor("entrant_bookings")
        compose.onNodeWithText("No upcoming stays").assertExists()
        compose.onNodeWithText("Explore campsites").assertExists()
    }

    private companion object {
        const val NOW = 1_792_162_800_000L
        const val DAY = 86_400_000L
        const val UPCOMING = "lot-upcoming"
        const val NOW_STAYING = "lot-staying"
        const val FINISHED = "lot-finished"
        const val CANCELLED = "lot-cancelled"
    }
}
