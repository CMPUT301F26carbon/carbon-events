/*
 * OwnerSentNotificationsTest.kt (Aditya)
 *
 * Purpose: Compose UI (intent) tests for the owner's list of every notification they have
 * sent: under the message box on Notify for that lottery, and all of them together in
 * Inbox > Sent (US 02.10.02).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for an owner viewing the notifications they have sent."
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

/** An owner who has sent notices to two different waitlists. */
@RunWith(AndroidJUnit4::class)
class OwnerSentNotificationsTest {
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

    /** My Campsites > Two Jack Lakeside > Notify. */
    private fun openNotify() {
        launchOwner()
        compose.onNodeWithTag("owner_campsites").performScrollToNode(hasText("Two Jack Lakeside"))
        compose.onNodeWithText("Two Jack Lakeside").performClick()
        waitFor("manage_notify")
        tap("manage_notify")
        waitFor("notify_send")
    }

    private fun type(text: String) =
        compose.onNode(hasSetTextAction() and (hasTestTag("notify_text") or hasAnyAncestor(hasTestTag("notify_text")))).performTextInput(text)
    private fun sent() = store.snapshot.announcements

    private suspend fun sendTwo() {
        store.sendAnnouncement(store.snapshot.lotteries.getValue("lot-two-jack"), Audience.ALL, "Gates open at 2 PM on check-in day.", NOW - 2 * 3_600_000)
        store.sendAnnouncement(store.snapshot.lotteries.getValue("lot-lake-louise"), Audience.WAITING, "The draw is tonight.", NOW - 3_600_000)
    }

    private fun openSent(seed: suspend () -> Unit = {}) {
        launchOwner(seed)
        compose.onNodeWithTag("nav_Inbox").performClick()
        waitFor("inbox")
        tap("inbox_tab_1")
    }

    @Test fun inboxSent_listsEveryNoticeAcrossCampsites() {
        openSent { sendTwo() }
        compose.onNodeWithText("Sent · 2").assertExists()
        sent().forEach { compose.onNodeWithTag("sent_${it.id}").assertExists() }
        compose.onNodeWithText("Gates open at 2 PM on check-in day.").assertExists()
        compose.onNodeWithText("The draw is tonight.").assertExists()
    }

    @Test fun eachSentNotice_showsTheCampsite_theGroup_andHowManyGotIt() {
        openSent { sendTwo() }
        val twoJack = sent().first { it.campsiteId == "two-jack" }
        fun inCard(text: String) = compose.onNode(hasText(text, ignoreCase = true) and hasAnyAncestor(hasTestTag("sent_${twoJack.id}")), useUnmergedTree = true)
        inCard("Two Jack Lakeside").assertExists()
        inCard("Everyone").assertExists()
        inCard("Delivered to 6").assertExists()
    }

    @Test fun inboxSent_withNothingSent_saysSo() {
        openSent()
        compose.onNodeWithText("Sent · 0").assertExists()
        compose.onNodeWithText("Nothing sent yet").assertExists()
    }

    @Test fun notifyScreen_listsWhatWasSentForThatLottery() {
        runBlocking { sendTwo() }
        openNotify()
        compose.onNodeWithText("Gates open at 2 PM on check-in day.").assertExists()
        compose.onNodeWithText("The draw is tonight.").assertDoesNotExist() // that one went to another campsite's list
    }

    @Test fun aNewlySentNotice_appearsInTheSentList() {
        openNotify()
        compose.onNodeWithText("Nothing sent yet.").assertExists()
        type("Firewood is sold at the gate.")
        tap("notify_send")
        compose.waitUntil(10_000) { sent().isNotEmpty() }
        compose.onNodeWithTag("sent_${sent().single().id}").assertExists()
        compose.onNodeWithText("Delivered to 6").assertExists()
    }

    @Test fun anotherOwnersNotices_areNotListed() {
        openSent {
            val id = store.createCampsite(Campsite("", "owner-2", "Rocky Ridge Camping", "Elk Meadow", "Jasper, AB", maxOccupancy = 4, createdAt = NOW))
            val lottery = Lottery("lot-elk", id, "Elk Meadow", "owner-2", NOW - 3_600_000, NOW + 86_400_000, 2)
            store.sendAnnouncement(lottery.copy(id = store.createLottery(lottery)), Audience.ALL, "Hello from Rocky Ridge.", NOW)
        }
        compose.onNodeWithText("Sent · 0").assertExists()
        compose.onNodeWithText("Hello from Rocky Ridge.").assertDoesNotExist()
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
