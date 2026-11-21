/*
 * OwnerWinnersInputTest.kt (Vaibhav)
 *
 * Purpose: Compose UI (intent) tests for typing the number of winners in the lottery form, as
 * well as tapping it up and down (Figma O-LOTTERY-NEW-WINNERS-KB; US 02.04.05).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for typing the number of winners."
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

/** Two Jack Lakeside's open lottery in the sample data. */
@RunWith(AndroidJUnit4::class)
class OwnerWinnersInputTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 },
        ).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW))
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

    private val lot = "lot-two-jack"
    private fun lottery() = store.snapshot.lotteries.getValue(lot)

    private fun openEditLottery() {
        launchOwner()
        compose.onNodeWithTag("owner_campsites").performScrollToNode(hasText("Two Jack Lakeside"))
        compose.onNodeWithText("Two Jack Lakeside").performClick()
        waitFor("manage_edit_lottery")
        tap("manage_edit_lottery")
        waitFor("lottery_publish")
    }

    private fun field() = compose.onNodeWithTag("lottery_winners_input")

    @Test fun theWinnersBox_showsTheCurrentNumber() {
        openEditLottery()
        field().assertTextEquals("${lottery().winnerCount}")
    }

    @Test fun typingTheNumberOfWinners_savesIt() {
        openEditLottery()
        field().performTextReplacement("12")
        tap("lottery_publish")
        compose.waitUntil(10_000) { lottery().winnerCount == 12 }
    }

    @Test fun aNumberAboveTheLimit_isKeptAtTheLimit() {
        openEditLottery()
        field().performTextReplacement("150")
        compose.waitForIdle()
        field().assertTextEquals("99")
    }

    @Test fun clearingTheBox_meansOneWinner() {
        openEditLottery()
        field().performTextReplacement("")
        tap("lottery_publish")
        compose.waitUntil(10_000) { lottery().winnerCount == 1 }
    }

    @Test fun plusAfterTyping_countsUpFromTheTypedNumber() {
        openEditLottery()
        field().performTextReplacement("7")
        compose.onNodeWithContentDescription("Increase Number of winners").performSemanticsAction(SemanticsActions.OnClick)
        field().assertTextEquals("8")
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
