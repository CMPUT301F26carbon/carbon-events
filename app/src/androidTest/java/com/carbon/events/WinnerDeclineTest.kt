/*
 * WinnerDeclineTest.kt (Suvir)
 *
 * Purpose: Compose UI (intent) tests for a lottery winner turning their spot down: Decline asks
 * first, then gives the spot back so it can go to the next person on the waitlist
 * (US 01.04.03).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for a lottery winner declining a reservation."
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
class WinnerDeclineTest {
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
        s.copy(entries = s.entries + WaitlistEntry(lot, me.id, me.name, NOW - 10 * DAY, EntryStatus.WON, campsiteId = "johnston-canyon", campsiteName = "Johnston Canyon", ownerId = SampleData.OWNER_ID))
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

    @Test fun decline_asksFirst() {
        openBooking()
        tap("book_decline")
        compose.onNodeWithTag("confirm_dialog").assertExists()
        compose.onNodeWithText("Decline Reservation?").assertExists()
        compose.onNodeWithText("Your spot at Johnston Canyon will go to the next person on the waitlist. You can't undo this.").assertExists()
        assertEquals(EntryStatus.WON, myEntry().status)
    }

    @Test fun keepMySpot_changesNothing() {
        openBooking()
        tap("book_decline")
        tapText("Keep my spot")
        compose.onNodeWithTag("confirm_dialog").assertDoesNotExist()
        assertEquals(EntryStatus.WON, myEntry().status)
        compose.onNodeWithTag("book_title").assertExists()
    }

    @Test fun declining_givesTheSpotBackToTheLottery() {
        val free = store.snapshot.lotteries.getValue(lot).spotsFree
        openBooking()
        tap("book_decline")
        compose.onNode(hasText("Decline") and hasAnyAncestor(hasTestTag("confirm_dialog")) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { myEntry().status == EntryStatus.DECLINED }
        assertEquals(free + 1, store.snapshot.lotteries.getValue(lot).spotsFree)
        assertNull("no nights were booked", myEntry().checkInDay)
    }

    @Test fun afterDeclining_thereIsNothingLeftToBook() {
        openBooking()
        tap("book_decline")
        compose.onNode(hasText("Decline") and hasAnyAncestor(hasTestTag("confirm_dialog")) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { myEntry().status == EntryStatus.DECLINED }
        compose.onNodeWithTag("book_confirm").assertDoesNotExist()
    }

    @Test fun someoneWhoDidNotWin_cannotBook() {
        launchEntrant(UserProfile("sample-entrant-3", "Noah Singh", "noah@example.com")) // not selected in this draw
        compose.onNodeWithTag("nav_Lotteries").performClick()
        compose.onNodeWithText("Results · 1").performClick()
        waitFor("my_lottery_$lot")
        compose.onNodeWithTag("book_$lot").assertDoesNotExist()
    }

    private companion object { const val NOW = 1_792_162_800_000L; const val DAY = 86_400_000L }
}
