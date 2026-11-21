/*
 * OwnerMultiCampsiteLotteryTest.kt (Aditya)
 *
 * Purpose: Compose UI (intent) tests for an owner listing several campsites in one lottery for
 * the same period: each campsite gets its waitlist, all tied together, and entrants who join
 * one join them all (US 02.04.02).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for an owner listing multiple campsites in one lottery."
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

/** The sample owner, Parks Lodge, who runs six campsites. */
@RunWith(AndroidJUnit4::class)
class OwnerMultiCampsiteLotteryTest {
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

    private fun openNewLottery() {
        launchOwner()
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

    private fun newLotteries() = store.snapshot.lotteries.values.filter { it.campsiteId in setOf("tunnel-mountain", "waterfowl-lakes") }

    @Test fun pickingTwoCampsites_saysTheyShareOneLottery() {
        openNewLottery()
        choose("lottery_site_tunnel-mountain")
        compose.onNodeWithTag("lottery_sites_note").assertDoesNotExist()
        choose("lottery_site_waterfowl-lakes")
        compose.onNodeWithTag("lottery_sites_note").assertExists()
        compose.onNodeWithText("2 campsites in one lottery", substring = true).assertExists()
    }

    @Test fun publishing_listsBothCampsites_withTheSamePeriodsAndOneGroup() {
        openNewLottery()
        choose("lottery_site_tunnel-mountain")
        choose("lottery_site_waterfowl-lakes")
        fillPeriods()
        tap("lottery_publish")
        compose.waitUntil(10_000) { newLotteries().size == 2 }
        val (a, b) = newLotteries()
        assertTrue(a.groupId.isNotBlank())
        assertEquals(a.groupId, b.groupId)
        assertEquals(a.registrationStart, b.registrationStart)
        assertEquals(a.registrationEnd, b.registrationEnd)
        assertEquals(a.bookingStartDay, b.bookingStartDay)
        assertEquals(a.bookingEndDay, b.bookingEndDay)
        assertEquals(a.winnerCount, b.winnerCount)
        assertEquals(setOf("Tunnel Mountain Village", "Waterfowl Lakes"), newLotteries().map { it.campsiteName }.toSet())
    }

    @Test fun oneCampsite_publishesALotteryOnItsOwn() {
        openNewLottery()
        choose("lottery_site_tunnel-mountain")
        fillPeriods()
        tap("lottery_publish")
        compose.waitUntil(10_000) { newLotteries().isNotEmpty() }
        assertEquals("", newLotteries().single().groupId)
    }

    @Test fun tappingAPickedCampsiteAgain_takesItOut() {
        openNewLottery()
        choose("lottery_site_tunnel-mountain")
        choose("lottery_site_waterfowl-lakes")
        choose("lottery_site_waterfowl-lakes")
        compose.onNodeWithTag("lottery_sites_note").assertDoesNotExist()
        fillPeriods()
        tap("lottery_publish")
        compose.waitUntil(10_000) { newLotteries().isNotEmpty() }
        assertEquals(listOf("tunnel-mountain"), newLotteries().map { it.campsiteId })
    }

    @Test fun noCampsitePicked_isNotPublished() {
        openNewLottery()
        fillPeriods()
        tap("lottery_publish")
        compose.onNodeWithText("Choose at least one campsite for this lottery.").assertExists()
        assertTrue(newLotteries().isEmpty())
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
