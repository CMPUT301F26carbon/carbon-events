/*
 * OwnerRejectReservationTest.kt (Rayner)
 *
 * Purpose: Compose UI (intent) tests for an owner rejecting a reservation: the reason goes to
 * the guest, the stay is marked Rejected and its spot goes back to the lottery (US 02.08.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for an owner rejecting a reservation."
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

/** The owner's Bookings tab with an active, an upcoming, a finished and a cancelled stay. */
@RunWith(AndroidJUnit4::class)
class OwnerRejectReservationTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 },
        ).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val today = dayOf(NOW)
    private val lot = "lot-johnston"

    /**
     * Johnston Canyon in the sample data has two booked winners (Maya's stay is over, Liam is
     * staying now). Added here: Sofia with a stay next week, and Noah who cancelled his.
     */
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW).let { s ->
        fun guest(i: Int) = s.entries.first { it.lotteryId == lot && it.entrantId == "sample-entrant-$i" }
        s.copy(entries = s.entries.map { e ->
            when {
                e.lotteryId != lot -> e
                e.entrantId == "sample-entrant-2" -> guest(2).copy(status = EntryStatus.WON, checkInDay = today + 5, checkOutDay = today + 7, bookedAt = NOW - 4 * DAY, guests = 3, agreedToRules = true)
                e.entrantId == "sample-entrant-3" -> guest(3).copy(status = EntryStatus.CANCELLED, checkInDay = today + 3, checkOutDay = today + 4, bookedAt = NOW - 4 * DAY, guests = 2, endedAt = NOW - DAY)
                else -> e
            }
        })
    })
    private val owner = SampleData.OWNER_ID

    /** Signs the sample owner (Parks Lodge) in on this phone and opens My Campsites. */
    private fun launchOwner(seed: suspend () -> Unit = {}) {
        runBlocking {
            store.saveProfile(SampleData.owner(NOW).copy(activeRole = Role.OWNER))
            store.linkDevice(owner, owner)
            seed()
        }
        val c = AppContainer.inMemory(owner, clock, store)
        compose.setContent {
            EventsTheme {
                EventsApp(c, showSplash = false, splash = {}, setup = {}, alerts = {},
                    entrant = {},
                    owner = { LocalAppSession.current.let { OwnerShell(it.container, it.requireProfile(), it.profileVm) } },
                    admin = {},
                )
            }
        }
        waitFor("owner_campsites")
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun tap(tag: String) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) }
    private fun tapText(text: String) { compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick) }

    private fun openBookings() {
        launchOwner()
        compose.onNodeWithTag("nav_Bookings").performClick()
        waitFor("owner_bookings")
    }

    private fun row(i: Int) = compose.onNodeWithTag("reservation_${lot}_sample-entrant-$i")
    private fun inRow(i: Int, text: String) =
        compose.onNode(hasText(text, substring = true, ignoreCase = true) and hasAnyAncestor(hasTestTag("reservation_${lot}_sample-entrant-$i")), useUnmergedTree = true)
    private fun entry(i: Int) = store.snapshot.entries.first { it.lotteryId == lot && it.entrantId == "sample-entrant-$i" }

    private fun openReject(i: Int) {
        openBookings()
        row(i).performSemanticsAction(SemanticsActions.OnClick)
        waitFor("guest_dialog")
        tap("guest_reject")
        waitFor("reject_reason")
    }

    private fun typeReason(text: String) =
        compose.onNode(hasSetTextAction() and (hasTestTag("reject_reason") or hasAnyAncestor(hasTestTag("reject_reason")))).performTextInput(text)

    @Test fun aReservation_showsTheGuestAndTheirStay() {
        openBookings()
        row(2).performSemanticsAction(SemanticsActions.OnClick)
        waitFor("guest_dialog")
        compose.onNode(hasText("Sofia Rossi") and hasAnyAncestor(hasTestTag("guest_dialog")), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("guest_reject").assertExists()
        compose.onNodeWithTag("guest_message").assertExists()
    }

    @Test fun reject_asksForAReason_beforeAnythingChanges() {
        openReject(2)
        compose.onNodeWithText("Nothing has been paid, so there's nothing to refund. The spot is offered to the next entrant.").assertExists()
        assertEquals(EntryStatus.WON, entry(2).status)
    }

    @Test fun back_keepsTheReservation() {
        openReject(2)
        tapText("Back")
        compose.onNodeWithTag("reject_reason").assertDoesNotExist()
        assertEquals(EntryStatus.WON, entry(2).status)
    }

    @Test fun rejecting_marksTheStayRejected_withTheReason() {
        openReject(2)
        typeReason("The site is closed for repairs that week")
        tap("reject_confirm")
        compose.waitUntil(10_000) { entry(2).status == EntryStatus.REJECTED }
        assertEquals("The site is closed for repairs that week", entry(2).note)
    }

    @Test fun rejecting_givesTheSpotBack_andTellsTheGuestWhy() {
        val free = store.snapshot.lotteries.getValue(lot).spotsFree
        openReject(2)
        typeReason("Fire ban in the area")
        tap("reject_confirm")
        compose.waitUntil(10_000) { entry(2).status == EntryStatus.REJECTED }
        assertEquals(free + 1, store.snapshot.lotteries.getValue(lot).spotsFree)
        val notice = store.snapshot.notifications.single { it.recipientId == "sample-entrant-2" && it.type == NotificationType.REJECTED }
        assertTrue(notice.body.contains("Fire ban in the area"))
        assertEquals("Reservation at Johnston Canyon rejected", notice.title)
    }

    @Test fun aRejectedStay_movesToTheCancelledTab_markedRejected() {
        openReject(2)
        typeReason("Fire ban in the area")
        tap("reject_confirm")
        compose.waitUntil(10_000) { entry(2).status == EntryStatus.REJECTED }
        compose.onNodeWithText("Cancelled · 2").assertExists()
        tap("bookings_tab_2")
        inRow(2, "Rejected").assertExists()
    }

    @Test fun aFinishedStay_cannotBeRejected() {
        openBookings()
        tap("bookings_tab_1")
        row(0).performSemanticsAction(SemanticsActions.OnClick)
        waitFor("guest_dialog")
        compose.onNodeWithTag("guest_reject").assertDoesNotExist()
    }

    @Test fun theRejectButtonOnTheRow_goesStraightToTheReason() {
        openBookings()
        tap("guest_reject_sample-entrant-2")
        waitFor("reject_reason")
    }

    private companion object { const val NOW = 1_792_162_800_000L; const val DAY = 86_400_000L }
}
