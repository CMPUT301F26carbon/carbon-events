/*
 * CancelErrorTest.kt (Vaibhav)
 *
 * Purpose: Compose UI (intent) tests for a cancel that doesn't go through: the guest is told why
 * instead of nothing happening, and the message goes away on the next try (US 01.07.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for showing an error when cancelling a reservation fails."
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

/** Adam's one booked stay, with the owner rejecting it just before he cancels. */
@RunWith(AndroidJUnit4::class)
class CancelErrorTest {
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

    /** Adam's one booked stay, at Tunnel Mountain next week. */
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW).let { s ->
        val lot = Lottery(LOT, "tunnel-mountain", "Tunnel Mountain Village", SampleData.OWNER_ID, NOW - 40 * DAY, NOW - 30 * DAY, 3,
            status = LotteryStatus.DRAWN, drawnAt = NOW - 30 * DAY, bookingStartDay = today - 25, bookingEndDay = today + 25)
        val stay = WaitlistEntry(LOT, me.id, me.name, NOW - 35 * DAY, EntryStatus.WON, today + 6, today + 8, NOW - 29 * DAY,
            "tunnel-mountain", "Tunnel Mountain Village", SampleData.OWNER_ID, 2, true)
        s.copy(lotteries = s.lotteries + (LOT to lot), entries = s.entries + stay)
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

    private fun entry() = store.snapshot.entries.first { it.lotteryId == LOT && it.entrantId == me.id }

    private fun openBookings() {
        launchEntrant(me)
        compose.onNodeWithTag("nav_Bookings").performClick()
        waitFor("cancel_$LOT")
    }

    private fun confirmCancel() {
        compose.onNode(hasText("Cancel booking") and hasAnyAncestor(hasTestTag("confirm_dialog")) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
    }

    /** The owner rejects the stay while Adam has the cancel dialog open. */
    private fun ownerRejectsMeanwhile() = runBlocking { store.rejectReservation(LOT, me.id, "Site closed for repairs", NOW) }

    @Test fun aCancelThatFails_saysWhy() {
        openBookings()
        tap("cancel_$LOT")
        ownerRejectsMeanwhile()
        confirmCancel()
        waitFor("cancel_error")
        compose.onNodeWithText(CANCEL_CHANGED_MESSAGE).assertExists()
        assertEquals("nothing was cancelled", EntryStatus.REJECTED, entry().status)
    }

    @Test fun aCancelThatFails_staysOnBookings() {
        openBookings()
        tap("cancel_$LOT")
        ownerRejectsMeanwhile()
        confirmCancel()
        waitFor("cancel_error")
        compose.onNodeWithTag("booking_cancelled").assertDoesNotExist()
        compose.onNodeWithTag("entrant_bookings").assertExists()
    }

    @Test fun theMessage_canBeDismissed() {
        openBookings()
        tap("cancel_$LOT")
        ownerRejectsMeanwhile()
        confirmCancel()
        waitFor("cancel_error")
        tap("cancel_error_dismiss")
        compose.onNodeWithTag("cancel_error").assertDoesNotExist()
    }

    @Test fun aCancelThatWorks_showsNoError() {
        openBookings()
        tap("cancel_$LOT")
        confirmCancel()
        waitFor("booking_cancelled")
        compose.onNodeWithTag("cancel_error").assertDoesNotExist()
        assertEquals(EntryStatus.CANCELLED, entry().status)
    }

    @Test fun onTheReservationScreen_aCancelThatFails_alsoSaysWhy() {
        launchEntrant(me)
        compose.onNodeWithTag("nav_Bookings").performClick()
        waitFor("my_booking_$LOT")
        compose.onNodeWithTag("my_booking_$LOT").performSemanticsAction(SemanticsActions.OnClick)
        waitFor("reservation_cancel")
        tap("reservation_cancel")
        ownerRejectsMeanwhile()
        compose.onNode(hasText("Cancel reservation") and hasAnyAncestor(hasTestTag("confirm_dialog")) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(CANCEL_CHANGED_MESSAGE)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private companion object { const val NOW = 1_792_162_800_000L; const val DAY = 86_400_000L; const val LOT = "lot-upcoming" }
}
