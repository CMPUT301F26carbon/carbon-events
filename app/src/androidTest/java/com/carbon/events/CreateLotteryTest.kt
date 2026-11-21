/*
 * CreateLotteryTest.kt (Aditya)
 *
 * Purpose: Compose UI (intent) tests for CreateLottery.kt: ticking a campsite again takes it out of the new lottery (US 02.04.02), a lottery without its dates isn't published (US 02.04.03, US 02.04.04), and the number of winners chosen is the one saved (US 02.04.05). Publishing one lottery over several campsites is tested in OwnerMultiCampsiteLotteryTest.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write UI (intent) tests for creating a lottery."
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

/** The sample owner, Parks Lodge, with six campsites. */
@RunWith(AndroidJUnit4::class)
class CreateLotteryTest {
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

    private fun newLottery() {
        compose.onNodeWithTag("nav_Lotteries").performClick()
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasTestTag("owner_new_lottery"))
        compose.onNodeWithTag("owner_new_lottery").performClick()
        waitFor("lottery_publish")
    }

    private fun choose(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun fillPeriods() {
        choose("preset_start_Now")
        choose("preset_end_In 1 week")
        choose("preset_booking_2 weeks")
    }

    private fun newLotteries(before: Set<String>) = store.snapshot.lotteries.values.filter { it.id !in before }

    @Test fun tickingACampsiteAgain_takesItOut() {
        launchOwner()
        val before = store.snapshot.lotteries.keys.toSet()
        newLottery()
        choose("lottery_site_tunnel-mountain")
        choose("lottery_site_waterfowl-lakes")
        choose("lottery_site_waterfowl-lakes")
        fillPeriods()
        tap("lottery_publish")
        compose.waitUntil(10_000) { newLotteries(before).isNotEmpty() }
        assertEquals(listOf("tunnel-mountain"), newLotteries(before).map { it.campsiteId })
        assertEquals("one campsite: no group", "", newLotteries(before).single().groupId)
    }

    @Test fun withoutItsDates_nothingIsPublished() {
        launchOwner()
        val before = store.snapshot.lotteries.keys.toSet()
        newLottery()
        choose("lottery_site_tunnel-mountain")
        tap("lottery_publish")
        compose.waitForIdle()
        assertTrue(newLotteries(before).isEmpty())
        compose.onNodeWithTag("lottery_publish").assertExists()
    }

    @Test fun theNumberOfWinnersChosen_isSaved() {
        launchOwner()
        val before = store.snapshot.lotteries.keys.toSet()
        newLottery()
        choose("lottery_site_tunnel-mountain")
        fillPeriods()
        compose.onNodeWithContentDescription("Increase Number of winners").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        tap("lottery_publish")
        compose.waitUntil(10_000) { newLotteries(before).isNotEmpty() }
        assertEquals(CreateLotteryState().winners + 1, newLotteries(before).single().winnerCount)
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
