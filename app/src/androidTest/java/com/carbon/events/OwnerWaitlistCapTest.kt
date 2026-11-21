/*
 * OwnerWaitlistCapTest.kt (Vaibhav)
 *
 * Purpose: Compose UI (intent) tests for the optional waitlist cap on a lottery: an owner can
 * set one, change it or go back to no limit, and once the list is full nobody else can join
 * (US 02.04.07).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for an owner setting a capacity on a lottery waitlist."
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

/** Two Jack Lakeside's open lottery in the sample data, which has six people on its waitlist. */
@RunWith(AndroidJUnit4::class)
class OwnerWaitlistCapTest {
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

    private fun lowerCap(times: Int) = repeat(times) {
        compose.onNodeWithContentDescription("Decrease Maximum on the waitlist").performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun save() { tap("lottery_publish"); compose.waitForIdle() }

    @Test fun aLottery_startsWithNoLimit() {
        openEditLottery()
        compose.onNodeWithText("No limit · anyone can join").assertExists()
        compose.onNodeWithTag("lottery_cap_on").assertExists()
        assertNull(lottery().waitlistCap)
    }

    @Test fun setCap_showsTheLimit_andSavesIt() {
        openEditLottery()
        tap("lottery_cap_on")
        compose.onNodeWithText("Max entrants allowed to join").assertExists()
        compose.onNodeWithTag("lottery_cap").assertExists()
        save()
        compose.waitUntil(10_000) { lottery().waitlistCap == 50 }
    }

    @Test fun theCap_canBeChangedBeforeSaving() {
        openEditLottery()
        tap("lottery_cap_on")
        lowerCap(10)
        save()
        compose.waitUntil(10_000) { lottery().waitlistCap == 40 }
    }

    @Test fun noLimit_takesTheCapOffAgain() {
        runBlocking { store.updateLottery(lottery().copy(waitlistCap = 20), NOW) }
        openEditLottery()
        compose.onNodeWithTag("lottery_cap").assertExists()
        tap("lottery_cap_off")
        compose.onNodeWithTag("lottery_cap").assertDoesNotExist()
        save()
        compose.waitUntil(10_000) { lottery().waitlistCap == null }
    }

    @Test fun settingACap_leavesTheRestOfTheLotteryAlone() {
        val before = lottery()
        openEditLottery()
        tap("lottery_cap_on")
        save()
        compose.waitUntil(10_000) { lottery().waitlistCap == 50 }
        assertEquals(before.copy(waitlistCap = 50), lottery())
    }

    @Test fun onceTheListIsFull_nobodyElseCanJoin() {
        openEditLottery()
        tap("lottery_cap_on")
        lowerCap(44) // down to 6, the number already waiting
        save()
        compose.waitUntil(10_000) { lottery().waitlistCap == 6 }
        val refused = runCatching { runBlocking { store.joinWaitlist(lot, "late-entrant", "Jordan Lee", NOW) } }.exceptionOrNull()
        assertEquals(WaitlistError.FULL, (refused as WaitlistException).error)
        assertEquals(6, lottery().entrantCount)
    }

    @Test fun whenSomeoneLeavesAFullList_aSpotOpensUp() {
        runBlocking {
            store.updateLottery(lottery().copy(waitlistCap = 6), NOW)
            store.leaveWaitlist(lot, "sample-entrant-5", NOW)
            store.joinWaitlist(lot, "late-entrant", "Jordan Lee", NOW)
        }
        assertEquals(6, lottery().entrantCount)
        assertTrue(store.snapshot.entries.any { it.lotteryId == lot && it.entrantId == "late-entrant" })
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
