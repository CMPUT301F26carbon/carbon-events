/*
 * CancelReservationTest.kt (Vaibhav)
 *
 * Purpose: Compose UI (intent) tests for an entrant cancelling a reservation from Bookings
 * (US 01.07.01) and the confirmation they get: the stay moves to Past as Cancelled and its
 * cancellation can be downloaded as a CSV (US 01.07.02).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for an entrant cancelling a reservation and getting the cancellation confirmation."
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
class CancelReservationTest {
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

    private fun confirmCancel() {
        compose.onNode(hasText("Cancel booking") and hasAnyAncestor(hasTestTag("confirm_dialog")) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
    }

    @Test fun cancelBooking_asksFirst() {
        openBookings()
        scrollTo(UPCOMING)
        tap("cancel_$UPCOMING")
        compose.onNodeWithTag("confirm_dialog").assertExists()
        compose.onNodeWithText("Cancel This Booking?").assertExists()
        assertEquals(EntryStatus.WON, entry(UPCOMING).status)
    }

    @Test fun keepIt_leavesTheReservation() {
        openBookings()
        scrollTo(UPCOMING)
        tap("cancel_$UPCOMING")
        tapText("Keep it")
        compose.onNodeWithTag("confirm_dialog").assertDoesNotExist()
        assertEquals(EntryStatus.WON, entry(UPCOMING).status)
    }

    @Test fun cancelling_releasesTheNights_andGivesTheSpotBack() {
        val free = store.snapshot.lotteries.getValue(UPCOMING).spotsFree
        openBookings()
        scrollTo(UPCOMING)
        tap("cancel_$UPCOMING")
        confirmCancel()
        compose.waitUntil(10_000) { entry(UPCOMING).status == EntryStatus.CANCELLED }
        assertEquals(free + 1, store.snapshot.lotteries.getValue(UPCOMING).spotsFree)
    }

    @Test fun cancelledStay_movesToPast_markedCancelled() {
        openBookings()
        scrollTo(UPCOMING)
        tap("cancel_$UPCOMING")
        confirmCancel()
        compose.waitUntil(10_000) { entry(UPCOMING).status == EntryStatus.CANCELLED }
        compose.onNodeWithText("Active · 1").assertExists()
        compose.onNodeWithText("Past · 3").assertExists()
        tap("my_bookings_tab_1")
        inCard(UPCOMING, "Cancelled").assertExists()
    }

    @Test fun cancelledStay_offersTheCancellationAsCsv() {
        openBookings()
        tap("my_bookings_tab_1")
        card(CANCELLED).performSemanticsAction(SemanticsActions.OnClick)
        waitFor("reservation_details")
        compose.onNodeWithText("Download cancellation (CSV)").assertExists()
        compose.onNodeWithTag("reservation_cancel").assertDoesNotExist()
    }

    @Test fun aStayInProgress_cannotBeCancelled() {
        openBookings()
        compose.onNodeWithTag("cancel_$NOW_STAYING").assertDoesNotExist()
    }

    @Test fun cancelFromTheReservationScreen_alsoWorks() {
        openBookings()
        scrollTo(UPCOMING)
        card(UPCOMING).performSemanticsAction(SemanticsActions.OnClick)
        waitFor("reservation_cancel")
        tap("reservation_cancel")
        compose.onNode(hasText("Cancel reservation") and hasAnyAncestor(hasTestTag("confirm_dialog")) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { entry(UPCOMING).status == EntryStatus.CANCELLED }
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
