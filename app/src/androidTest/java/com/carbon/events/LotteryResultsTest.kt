/*
 * LotteryResultsTest.kt (Suvir)
 *
 * Purpose: Compose UI (intent) tests for the entrant's lottery results and the QR scanner:
 * a notification when they win (US 01.03.01), a notification when they lose (US 01.03.02),
 * and opening a campsite's details from its promo QR code (US 01.12.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 6, 2026). Prompt: "Write
 * UI (intent) tests for the 16 half-way user stories that have no UI test yet." Reviewed, edited
 * and run by Team Carbon.
 *
 * Outstanding issues: the camera itself is not driven here; the scanner's "Enter code instead"
 * path reads the same QR text the camera would.
 */
package com.carbon.events

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Sample entrants on the Lake Louise waitlist, after its draw, on an in-memory store. */
@RunWith(AndroidJUnit4::class)
class LotteryResultsTest {
    // The scanner test types the code, so the camera permission is not needed here.
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 }).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW))

    /** Closes the Lake Louise lottery (it ends two minutes after NOW) and draws its 3 winners. */
    private fun drawLakeLouise(): List<WaitlistEntry> {
        clock.advanceBy(3 * MINUTE)
        runBlocking { assertTrue(store.runDrawIfDue("lot-lake-louise", clock.now(), Random(301))) }
        return store.snapshot.entries.filter { it.lotteryId == "lot-lake-louise" }
    }

    private fun launch(id: String, name: String) {
        runBlocking {
            store.saveProfile(UserProfile(id, name, "${id}@example.com", activeRole = Role.ENTRANT))
            store.linkDevice(id, id)
        }
        val c = AppContainer.inMemory(id, clock, store)
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

    private fun openNotifications() {
        compose.onNodeWithTag("nav_Lotteries").performClick()
        compose.onNodeWithContentDescription("Notifications").performClick()
        waitFor("notifs_manage")
    }

    /** US 01.03.01: a winner is told they won, in the app's notifications. */
    @Test fun entrant_isNotifiedWhenTheyWinTheLottery() {
        val winner = drawLakeLouise().first { it.status == EntryStatus.WON }
        launch(winner.entrantId, winner.entrantName)
        openNotifications()
        compose.onNodeWithTag("notification_LOTTERY_WON").assertIsDisplayed()
        compose.onNodeWithText("You won Lake Louise Tent Sites!").assertIsDisplayed()
        compose.onAllNodesWithTag("notification_LOTTERY_LOST").assertCountEquals(0)
        assertTrue(store.snapshot.notifications.any { it.recipientId == winner.entrantId && it.type == NotificationType.LOTTERY_WON })
    }

    /** US 01.03.02: an entrant who was not drawn is told they were not selected. */
    @Test fun entrant_isNotifiedWhenTheyLoseTheLottery() {
        val loser = drawLakeLouise().first { it.status == EntryStatus.LOST }
        launch(loser.entrantId, loser.entrantName)
        openNotifications()
        compose.onNodeWithTag("notification_LOTTERY_LOST").assertIsDisplayed()
        compose.onNodeWithText("Lake Louise Tent Sites results").assertIsDisplayed()
        compose.onNode(hasText("Not selected this time", substring = true)).assertIsDisplayed()
        compose.onAllNodesWithTag("notification_LOTTERY_WON").assertCountEquals(0)
    }

    /** US 01.12.01: scanning a campsite's promo QR code opens that campsite's details. */
    @Test fun entrant_opensCampsiteDetailsFromItsQrCode() {
        launch("test-phone", "Adam Kent")
        compose.onNodeWithTag("nav_Scan").performClick()
        waitFor("scan_screen")
        compose.onNodeWithTag("scan_manual").performClick()
        // The exact text printed in the Two Jack Lakeside promo QR code.
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("scan_screen"))).performTextInput(QrCodes.payloadFor("two-jack"))
        compose.onNodeWithTag("scan_open").performClick()
        waitFor("details_name")
        compose.onNodeWithTag("details_name").assertTextContains("Two Jack Lakeside")
        compose.onNodeWithText("Join lottery").assertExists()
    }

    private companion object {
        /** Fixed time shared with the realistic fixture. */
        const val NOW = 1_792_162_800_000L
        const val MINUTE = 60_000L
    }
}
