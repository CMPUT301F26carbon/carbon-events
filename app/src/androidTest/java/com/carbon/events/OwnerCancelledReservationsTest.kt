/*
 * OwnerCancelledReservationsTest.kt (Rayner)
 *
 * Purpose: Compose UI (intent) tests for the owner's list of cancelled reservations: the
 * Cancelled tab in Bookings shows the stays guests cancelled, apart from active and past ones
 * (US 02.07.03).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for an owner viewing cancelled reservations."
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
class OwnerCancelledReservationsTest {
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

    @Test fun tabs_countActivePastAndCancelledSeparately() {
        openBookings()
        compose.onNodeWithText("Active · 2").assertExists()
        compose.onNodeWithText("Past · 1").assertExists()
        compose.onNodeWithText("Cancelled · 1").assertExists()
    }

    @Test fun cancelledTab_listsTheStayTheGuestCancelled() {
        openBookings()
        tap("bookings_tab_2")
        row(3).assertExists()
        inRow(3, "Noah Singh").assertExists()
        inRow(3, "Cancelled").assertExists()
        inRow(3, "2 guests").assertExists()
    }

    @Test fun cancelledTab_leavesOutActiveAndPastStays() {
        openBookings()
        tap("bookings_tab_2")
        row(0).assertDoesNotExist()
        row(1).assertDoesNotExist()
        row(2).assertDoesNotExist()
    }

    @Test fun activeTab_doesNotShowTheCancelledStay() {
        openBookings()
        row(3).assertDoesNotExist()
        row(1).assertExists()
        row(2).assertExists()
    }

    @Test fun whenAGuestCancels_theStayMovesToCancelled() {
        openBookings()
        runBlocking { store.giveUpSpot(lot, "sample-entrant-2", NOW) }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Cancelled · 2")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithText("Active · 1").assertExists()
        tap("bookings_tab_2")
        inRow(2, "Sofia Rossi").assertExists()
    }

    @Test fun tappingACancelledStay_showsItsDetails_withNothingToReject() {
        openBookings()
        tap("bookings_tab_2")
        row(3).performSemanticsAction(SemanticsActions.OnClick)
        waitFor("guest_dialog")
        compose.onNode(hasText("Johnston Canyon") and hasAnyAncestor(hasTestTag("guest_dialog")), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("guest_reject").assertDoesNotExist()
    }

    @Test fun withNothingCancelled_theTabSaysSo() {
        runBlocking { store.deleteCampsite("johnston-canyon") }
        openBookings()
        tap("bookings_tab_2")
        compose.onNodeWithText("Nothing cancelled").assertExists()
    }

    private companion object { const val NOW = 1_792_162_800_000L; const val DAY = 86_400_000L }
}
