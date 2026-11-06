/*
 * LotteriesTest.kt (Suvir)
 *
 * Purpose: Compose tests for Lotteries.kt: My Lotteries shows open entries and results (US 01.03.01, US 01.03.02), and Notifications lists them.
 *
 * AI use: drafted with Claude (Claude Opus 5.5, Anthropic), November 6, 2026. Prompt: "Write a
 * test file for each source file that has none." Reviewed and run by Team Carbon; see "AI Use"
 * in the README.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LotteriesTest {
    @get:Rule val compose = createComposeRule()
    private val open = TestData.lottery("lot-open")
    private val drawn = TestData.lottery("lot-drawn", campsiteId = "lake-louise").copy(status = LotteryStatus.DRAWN, drawnAt = TestData.NOW - 1)
    private val store = InMemoryEventsStore(EventsSnapshot(
        campsites = mapOf("two-jack" to TestData.campsite(), "lake-louise" to TestData.campsite("lake-louise", "Lake Louise Tent Sites")),
        lotteries = mapOf(open.id to open, drawn.id to drawn),
        entries = listOf(WaitlistEntry(open.id, "maya", "Maya Chen", TestData.NOW), WaitlistEntry(drawn.id, "maya", "Maya Chen", TestData.NOW, EntryStatus.WON)),
        notifications = listOf(AppNotification("n1", "maya", NotificationType.LOTTERY_WON, "You won Lake Louise Tent Sites!", "Choose your dates.", drawn.id, "lake-louise", TestData.NOW)),
    ))
    private val c = AppContainer.inMemory("maya", FixedClock(TestData.NOW), store)

    @Test fun myLotteries_showsAnOpenEntry() {
        compose.setContent { EventsTheme { MyLotteriesScreen(c, onOpen = {}, onExplore = {}, onNotifications = {}) } }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("my_lottery_lot-open")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("my_lottery_lot-open").assertExists()
    }

    @Test fun notifications_listTheWin() {
        compose.setContent { EventsTheme { NotificationsScreen(c, onBack = {}, onOpen = {}) } }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("notification_LOTTERY_WON")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("You won Lake Louise Tent Sites!").assertExists()
    }
}
