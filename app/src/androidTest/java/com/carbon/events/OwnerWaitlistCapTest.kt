/*
 * OwnerWaitlistCapTest.kt (Vaibhav)
 *
 * Purpose: Compose UI (intent) tests for the optional waitlist cap on a lottery (Figma
 * O-LOTTERY-NEW, O-LOTTERY-EDIT, O-LOTTERY-NEW-CAP-KB): the owner taps or types a cap, 0 means
 * no limit, and once the list is full nobody else can join (US 02.04.07).
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

    private fun capField() = compose.onNodeWithTag("lottery_cap_input")
    private fun typeCap(text: String) { capField().performTextReplacement(text); compose.waitForIdle() }
    private fun tapCap(direction: String, times: Int = 1) = repeat(times) {
        compose.onNodeWithContentDescription("$direction Waitlist cap").performSemanticsAction(SemanticsActions.OnClick)
    }
    private fun save() { tap("lottery_publish"); compose.waitForIdle() }

    @Test fun theCapRow_isAlwaysShown_andStartsAtNoLimit() {
        openEditLottery()
        compose.onNodeWithTag("lottery_cap").assertExists()
        capField().assertTextEquals("0")
        compose.onNodeWithText("Optional · no limit").assertExists()
        assertNull(lottery().waitlistCap)
    }

    @Test fun typingACap_savesIt() {
        openEditLottery()
        typeCap("60")
        compose.onNodeWithText("Max entrants allowed to join").assertExists()
        save()
        compose.waitUntil(10_000) { lottery().waitlistCap == 60 }
    }

    @Test fun plusAndMinus_changeTheTypedCap() {
        openEditLottery()
        typeCap("60")
        tapCap("Increase", 2)
        tapCap("Decrease")
        capField().assertTextEquals("61")
        save()
        compose.waitUntil(10_000) { lottery().waitlistCap == 61 }
    }

    @Test fun tappingPlusFromNoLimit_startsTheCapAtOne() {
        openEditLottery()
        tapCap("Increase")
        capField().assertTextEquals("1")
        compose.onNodeWithContentDescription("Decrease Waitlist cap").assertIsEnabled()
    }

    @Test fun typingZero_orClearingTheBox_takesTheCapOff() {
        runBlocking { store.updateLottery(lottery().copy(waitlistCap = 20), NOW) }
        openEditLottery()
        capField().assertTextEquals("20")
        typeCap("")
        compose.onNodeWithText("Optional · no limit").assertExists()
        save()
        compose.waitUntil(10_000) { lottery().waitlistCap == null }
    }

    @Test fun lettersCannotBeTyped() {
        openEditLottery()
        typeCap("6a0")
        capField().assertTextEquals("60")
    }

    @Test fun settingACap_leavesTheRestOfTheLotteryAlone() {
        val before = lottery()
        openEditLottery()
        typeCap("50")
        save()
        compose.waitUntil(10_000) { lottery().waitlistCap == 50 }
        assertEquals(before.copy(waitlistCap = 50), lottery())
    }

    @Test fun onceTheListIsFull_nobodyElseCanJoin() {
        openEditLottery()
        typeCap("6") // the number already waiting
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
