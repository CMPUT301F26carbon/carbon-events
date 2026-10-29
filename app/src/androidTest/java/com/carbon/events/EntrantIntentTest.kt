/*
 * EntrantIntentTest.kt
 *
 * Purpose: intent tests for every entrant user story released at the half-way checkpoint:
 * US 01.09.01 (device identification), 01.01.01 (search), 01.01.04 (summary),
 * 01.01.05 (details), 01.01.06 / 01.02.01 (join), 01.02.02 (leave),
 * 01.03.01 / 01.03.02 (win / loss notifications), 01.07.03 (update profile) and
 * 01.12.01 (open details from a promo QR code).
 *
 * Outstanding issues: the live camera scan is exercised manually; the test uses the
 * scanner's code-entry path and the deep link that a scanned code opens.
 */
package com.carbon.events

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntrantIntentTest : AppTestHarness() {

    /** US 01.09.01: first launch creates a profile tied to the device, no password. */
    @Test
    fun firstLaunch_createsDeviceProfile() {
        launch()
        compose.onNodeWithText("No username or password. Your profile is linked to this device.").assertIsDisplayed()
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("Adam Kent")
        fields[1].performTextInput("adam@example.com")
        compose.onNodeWithTag("setup_continue").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("explore").assertIsDisplayed()
        compose.onAllNodesWithText("Adam")[0].assertIsDisplayed()
        assertEquals("Adam Kent", runBlocking { store.observeProfile(deviceId).first() }?.name)
    }

    /** US 01.01.01 + US 01.01.04: keyword search shows matching campsites with their summary. */
    @Test
    fun search_showsMatchingCampsiteSummaries() {
        givenProfile(); launch()
        compose.onNodeWithTag("explore_search").performClick()
        compose.onNodeWithTag("search_field").performTextInput("banff")
        compose.waitForIdle()
        compose.onNodeWithTag("result_count").assertIsDisplayed()
        compose.onNodeWithText("Two Jack Lakeside").assertIsDisplayed()
        compose.onNodeWithText("Tunnel Mountain Village").assertIsDisplayed()
        compose.onAllNodesWithText("Lake Louise Tent Sites").fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
        // Summary: name, location, max occupancy and rating.
        compose.onAllNodesWithText("Max 6")[0].assertIsDisplayed()
        compose.onNodeWithText("4.8").assertIsDisplayed()
    }

    /** US 01.01.05, 01.01.06, 01.02.01, 01.02.02: open details, join, then leave the waitlist. */
    @Test
    fun details_joinAndLeaveWaitlist() {
        givenProfile(); launch()
        compose.onNodeWithTag("explore").performScrollToNode(hasTestTag("campsite_card_two-jack"))
        compose.onNodeWithTag("campsite_card_two-jack").performClick()
        compose.onNodeWithTag("details_name").assertIsDisplayed()
        compose.onNodeWithTag("lottery_card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Join lottery").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Leave waitlist").assertIsDisplayed()
        assertTrue(store.snapshot.entries.any { it.entrantId == deviceId && it.lotteryId == "lot-two-jack" })

        compose.onNodeWithText("Leave waitlist").performClick()
        compose.onNodeWithText("Leave the Waitlist?").assertIsDisplayed()
        compose.onNodeWithText("Leave").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Join lottery").assertIsDisplayed()
        assertTrue(store.snapshot.entries.none { it.entrantId == deviceId })
    }

    /** US 01.03.01 + US 01.03.02: after the automatic draw the entrant gets win and loss notifications. */
    @Test
    fun notifications_showWinAndLoss() {
        givenProfile()
        runBlocking {
            // Two Jack draws 8 winners from 7 entrants: a guaranteed win.
            store.joinWaitlist("lot-two-jack", deviceId, "Adam Kent", clock.now())
            // A second lottery that draws no winners: a guaranteed loss.
            val lose = store.createLottery(
                Lottery("", "tunnel-mountain", "Tunnel Mountain Village", SampleData.OWNER_ID, clock.now() - 60_000, clock.now() + 60_000, 0),
            )
            store.joinWaitlist(lose, deviceId, "Adam Kent", clock.now())
        }
        clock.advanceBy(4L * 24 * 60 * 60_000) // both registration periods close
        launch()
        runBlocking { container.drawScheduler.drawDue(store.observeLotteries().first()) }
        compose.onNodeWithContentDescription("Notifications").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("You won Two Jack Lakeside!").assertIsDisplayed()
        compose.onNodeWithText("Tunnel Mountain Village results").assertIsDisplayed()
        compose.onNodeWithTag("notification_LOTTERY_WON").assertIsDisplayed()
        compose.onNodeWithTag("notification_LOTTERY_LOST").assertIsDisplayed()
    }

    /** US 01.07.03: update phone number on the profile. */
    @Test
    fun profile_updateContactDetails() {
        givenProfile(); launch()
        compose.onNodeWithTag("nav_Profile").performClick()
        compose.onNodeWithTag("profile_edit").performClick()
        compose.onAllNodes(hasSetTextAction())[2].performTextInput("+1 780 555 0142")
        compose.onNodeWithTag("profile_save").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("+1 780 555 0142").assertIsDisplayed()
        assertEquals("+1 780 555 0142", runBlocking { store.observeProfile(deviceId).first() }?.phone)
    }

    /** US 01.12.01: the scanner's code path opens the campsite's details. */
    @Test
    fun scan_opensCampsiteDetails() {
        givenProfile(); launch()
        compose.onNodeWithTag("nav_Scan").performClick()
        compose.onNodeWithTag("scan_manual").performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("two-jack")
        compose.onNodeWithTag("scan_open").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("details_name").assertIsDisplayed()
        compose.onNodeWithText("Two Jack Lakeside").assertIsDisplayed()
    }

    /** US 01.12.01: a scanned promo link (carbon-events://campsite/<id>) opens the listing. */
    @Test
    fun deepLink_opensCampsiteDetails() {
        givenProfile(); launch(deepLinkCampsiteId = "lake-louise")
        compose.onNodeWithTag("details_name").assertIsDisplayed()
        compose.onNodeWithText("Lake Louise Tent Sites").assertIsDisplayed()
        assertTrue(SampleData.campsites(clock.now()).any { it.id == "lake-louise" })
    }
}
