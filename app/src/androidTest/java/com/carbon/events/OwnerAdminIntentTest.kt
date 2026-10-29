/*
 * OwnerAdminIntentTest.kt
 *
 * Purpose: intent tests for the campsite owner and administrator user stories released at
 * the half-way checkpoint: US 02.01.01 / 02.01.02 (register with details), 02.02.01
 * (promo QR), 02.03.01 (my campsites), 02.01.03 / 02.01.04 (view and edit), 02.04.01 /
 * 02.04.03 / 02.04.05 (lottery with registration period and winners), 02.09.01 (waitlist),
 * 02.05.01 (automatic draw), 03.04.01 (browse campsites) and 03.01.01 (remove campsite).
 *
 * Outstanding issues: picking a photo and a map pin use system UI (photo picker, Google
 * Maps) and are covered manually.
 */
package com.carbon.events

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnerAdminIntentTest : AppTestHarness() {

    /** US 02.01.01, 02.01.02, 02.02.01, 02.03.01: register a campsite, see its QR, find it in My Campsites. */
    @Test
    fun owner_registersCampsiteAndGetsQrCode() {
        givenProfile(Role.OWNER); launch()
        compose.onNodeWithTag("owner_campsites").assertIsDisplayed()
        compose.onNodeWithTag("owner_add").performClick()
        field("form_name").performTextInput("Bow River Sites")
        field("form_location").performTextInput("Banff, AB")
        compose.onNodeWithContentDescription("Increase Max occupancy").performClick()
        field("form_price").performScrollTo().performTextInput("55")
        field("form_amenities").performScrollTo().performTextInput("Fire pits, potable water")
        compose.onNodeWithText("Lakeside").performScrollTo().performClick()
        compose.onNodeWithTag("form_save").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("published_title").assertIsDisplayed()
        compose.onNodeWithTag("qr_code").assertIsDisplayed()
        val site = store.snapshot.campsites.values.single { it.name == "Bow River Sites" }
        assertEquals(deviceId, site.ownerId)
        assertEquals(5, site.maxOccupancy)
        assertEquals(55, site.pricePerNight)
        assertEquals(listOf("Lakeside"), site.tags)

        compose.onNodeWithTag("published_done").performClick()
        compose.onNodeWithTag("manage_name").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Bow River Sites").assertIsDisplayed()
    }

    /** US 02.01.02: the form refuses a campsite without a name or location. */
    @Test
    fun owner_formShowsValidationErrors() {
        givenProfile(Role.OWNER); launch()
        compose.onNodeWithTag("owner_add").performClick()
        compose.onNodeWithTag("form_save").performClick()
        compose.onNodeWithText("Give your campsite a name").performScrollTo().assertIsDisplayed()
        assertTrue(store.snapshot.campsites.values.none { it.ownerId == deviceId })
    }

    /** US 02.01.03 + US 02.01.04: view my campsite and edit its details. */
    @Test
    fun owner_viewsAndEditsCampsite() {
        deviceId = SampleData.OWNER_ID
        givenProfile(Role.OWNER, name = "Parks Lodge"); launch()
        compose.onNodeWithTag("campsite_card_two-jack").performClick()
        compose.onNodeWithTag("manage_name").assertIsDisplayed()
        compose.onNodeWithTag("qr_code").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("manage_edit").performClick()
        compose.waitForIdle()
        field("form_name").performTextClearance()
        field("form_name").performTextInput("Two Jack Lakeside North")
        compose.onNodeWithTag("form_save").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Two Jack Lakeside North").assertIsDisplayed()
        assertEquals("Two Jack Lakeside North", store.snapshot.campsites.getValue("two-jack").name)
    }

    /** US 02.04.01, 02.04.03, 02.04.05: list a campsite in a lottery with a registration period and winners. */
    @Test
    fun owner_createsLottery() {
        deviceId = SampleData.OWNER_ID
        givenProfile(Role.OWNER, name = "Parks Lodge"); launch()
        compose.onNodeWithTag("campsite_card_tunnel-mountain").performScrollTo().performClick()
        compose.onNodeWithTag("manage_create_lottery").performScrollTo().performClick()
        compose.onNodeWithTag("preset_start_Now").performClick()
        compose.onNodeWithTag("preset_end_In 1 week").performClick()
        compose.onNodeWithContentDescription("Increase Number of winners").performClick()
        compose.onNodeWithTag("lottery_publish").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("waitlist_summary").assertIsDisplayed()
        compose.onNodeWithText("0 joined").assertIsDisplayed()
        val lot = store.snapshot.lotteries.values.single { it.campsiteId == "tunnel-mountain" }
        assertEquals(5, lot.winnerCount)
        assertEquals(clock.now() + 7L * 24 * 60 * 60_000, lot.registrationEnd)
    }

    /** US 02.09.01: see the entrants on my lottery's waiting list. */
    @Test
    fun owner_viewsWaitlistEntrants() {
        deviceId = SampleData.OWNER_ID
        givenProfile(Role.OWNER, name = "Parks Lodge"); launch()
        compose.onNodeWithTag("campsite_card_two-jack").performClick()
        compose.onNodeWithTag("manage_waitlist").performScrollTo().performClick()
        compose.onNodeWithText("6 joined").assertIsDisplayed()
        compose.onNodeWithText("Maya Chen").assertIsDisplayed()
        compose.onNodeWithText("Liam Okafor").assertIsDisplayed()
    }

    /** US 02.05.01: winners are drawn automatically when registration closes. */
    @Test
    fun owner_seesAutomaticDrawResults() {
        deviceId = SampleData.OWNER_ID
        givenProfile(Role.OWNER, name = "Parks Lodge"); launch()
        compose.onNodeWithTag("campsite_card_lake-louise").performScrollTo().performClick()
        compose.onNodeWithTag("manage_waitlist").performScrollTo().performClick()
        compose.onNodeWithText("Winners · 0").assertIsDisplayed()
        clock.advanceBy(3 * 60_000) // registration closes 2 minutes after "now"
        runBlocking { container.drawScheduler.drawDue(store.observeLotteries().first()) }
        compose.waitForIdle()
        compose.onNodeWithText("Winners · 3").assertIsDisplayed()
        assertEquals(LotteryStatus.DRAWN, store.snapshot.lotteries.getValue("lot-lake-louise").status)
    }

    /** US 03.04.01 + US 03.01.01: an admin browses campsites and removes one after confirming. */
    @Test
    fun admin_browsesAndRemovesCampsite() {
        givenProfile(Role.ADMIN, isAdmin = true); launch()
        compose.onNodeWithTag("admin_campsites").assertIsDisplayed()
        compose.onNodeWithText("6 total", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithTag("campsite_card_two-jack").performClick()
        compose.onNodeWithTag("admin_campsite_name").assertIsDisplayed()
        compose.onNodeWithTag("admin_remove").performClick()
        compose.onNodeWithText("Remove Two Jack Lakeside?").assertIsDisplayed()
        compose.onNodeWithText("Remove").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("removed_banner").assertIsDisplayed()
        assertTrue("two-jack" !in store.snapshot.campsites)
        assertTrue(store.snapshot.lotteries.values.none { it.campsiteId == "two-jack" })
    }
}
