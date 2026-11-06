/*
 * OwnerLotteryTest.kt (Aditya)
 *
 * Purpose: Compose UI (intent) tests for the owner's promo QR code (US 02.02.01), listing a
 * campsite in a lottery (US 02.04.01), updating a lottery listing (US 02.04.06) and viewing the
 * entrants on a lottery's waitlist (US 02.09.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 6, 2026). Prompt: "Write
 * UI (intent) tests for the 16 half-way user stories that have no UI test yet." Reviewed, edited
 * and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The sample owner, Parks Lodge, running lotteries on an in-memory store. */
@RunWith(AndroidJUnit4::class)
class OwnerLotteryTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 }).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW))
    private val owner = SampleData.OWNER_ID

    private fun launch() {
        runBlocking {
            store.saveProfile(SampleData.owner(NOW).copy(activeRole = Role.OWNER))
            store.linkDevice(owner, owner)
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

    /** Taps a chip; these sit in sideways-scrolling rows, so tap it by its click action. */
    private fun choose(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun openCampsite(name: String) {
        compose.onNodeWithTag("owner_campsites").performScrollToNode(hasText(name))
        compose.onNodeWithText(name).performClick()
        waitFor("manage_name")
        compose.waitForIdle()
    }

    /** US 02.02.01: each campsite shows a unique promo QR code that opens that campsite. */
    @Test fun campsite_showsAPromoQrCodeThatLinksToItsDetails() {
        launch()
        openCampsite("Two Jack Lakeside")
        compose.onNodeWithTag("qr_code").assertIsDisplayed()
        compose.onNodeWithText("Promo QR code").assertIsDisplayed()
        // The code's text leads back to this campsite, and each campsite gets a different one.
        assertEquals("two-jack", QrCodes.parseCampsiteId(QrCodes.payloadFor("two-jack")))
        val codes = SampleData.campsites(NOW).map { QrCodes.payloadFor(it.id) }
        assertEquals(codes.size, codes.toSet().size)
    }

    /** US 02.04.01: the owner lists a campsite in a lottery for a chosen period. */
    @Test fun owner_listsACampsiteInALottery() {
        launch()
        compose.onNodeWithTag("nav_Lotteries").performClick()
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasTestTag("owner_new_lottery"))
        compose.onNodeWithTag("owner_new_lottery").performClick()
        choose("lottery_site_tunnel-mountain")
        choose("preset_start_Now")
        choose("preset_end_In 1 week")
        choose("preset_booking_2 weeks")
        compose.onNodeWithTag("lottery_publish").performClick()
        compose.waitUntil(10_000) { store.snapshot.lotteries.values.any { it.campsiteId == "tunnel-mountain" } }
        val lot = store.snapshot.lotteries.values.single { it.campsiteId == "tunnel-mountain" }
        assertEquals(owner, lot.ownerId)
        assertEquals(NOW, lot.registrationStart)
        assertEquals(NOW + 7 * DAY, lot.registrationEnd)
        assertTrue(lot.isRegistrationOpen(NOW))
    }

    /** US 02.04.06: the owner updates a published lottery and the change is saved. */
    @Test fun owner_updatesALotteryListing() {
        launch()
        openCampsite("Two Jack Lakeside")
        compose.onNodeWithTag("manage_edit_lottery").performScrollTo().performClick()
        compose.onNodeWithText("Edit Lottery").assertIsDisplayed()
        compose.onNodeWithContentDescription("Increase Number of winners").performScrollTo().performClick()
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(10_000) { store.snapshot.lotteries.getValue("lot-two-jack").winnerCount == 9 }
        val before = SampleData.snapshot(NOW).lotteries.getValue("lot-two-jack")
        val lot = store.snapshot.lotteries.getValue("lot-two-jack")
        assertEquals(9, lot.winnerCount)
        // Everything the owner did not touch stays the same.
        assertEquals(before.registrationStart, lot.registrationStart)
        assertEquals(before.registrationEnd, lot.registrationEnd)
        assertEquals(6, lot.entrantCount)
    }

    /** US 02.09.01: the owner sees every entrant who joined the lottery's waitlist. */
    @Test fun owner_viewsTheEntrantsOnTheWaitlist() {
        launch()
        openCampsite("Two Jack Lakeside")
        compose.onNodeWithTag("manage_waitlist").performScrollTo().performClick()
        waitFor("waitlist_summary")
        val joined = store.snapshot.entries.filter { it.lotteryId == "lot-two-jack" }
        assertEquals(6, joined.size)
        joined.forEach { e ->
            compose.onNodeWithTag("entry_${e.entrantId}").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(e.entrantName).assertExists()
        }
    }

    private companion object {
        /** Fixed time shared with the realistic fixture. */
        const val NOW = 1_792_162_800_000L
        const val DAY = 24 * 60 * 60_000L
    }
}
