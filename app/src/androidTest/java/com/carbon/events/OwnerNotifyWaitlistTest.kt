/*
 * OwnerNotifyWaitlistTest.kt (Aditya)
 *
 * Purpose: Compose UI (intent) tests for an owner sending a notification to the entrants on a
 * lottery's waitlist: choosing who gets it, writing it, and it arriving for each of them
 * (US 02.10.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for an owner notifying the entrants on a waitlist."
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

/** Two Jack Lakeside's open lottery in the sample data, with six people waiting for the draw. */
@RunWith(AndroidJUnit4::class)
class OwnerNotifyWaitlistTest {
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

    @Test fun notify_showsHowManyPeopleEachGroupReaches() {
        openNotify()
        compose.onNodeWithText("Notify Entrants").assertExists()
        compose.onNodeWithTag("audience_ALL").assertExists()
        compose.onNodeWithText("Everyone · 6").assertExists()
        compose.onNodeWithText("Winners · 0").assertExists()
        compose.onNodeWithText("Send to 6").assertExists()
    }

    @Test fun typingAMessage_showsAPreviewOfWhatEntrantsGet() {
        openNotify()
        type("The road to the campsite reopens Friday.")
        compose.onNodeWithTag("notify_preview").assertExists()
        compose.onNodeWithText("Two Jack Lakeside – from you").assertExists()
    }

    @Test fun send_deliversTheNoticeToEveryoneOnTheWaitlist() {
        openNotify()
        type("The road to the campsite reopens Friday.")
        tap("notify_send")
        compose.waitUntil(10_000) { sent().isNotEmpty() }
        compose.onNodeWithTag("notify_result").assertTextEquals("Sent to 6 people.")
        val notice = sent().single()
        assertEquals(6, notice.recipientCount)
        assertEquals(Audience.ALL, notice.audience)
        (0..5).forEach { i ->
            val chat = store.snapshot.chats.getValue(Chat.idFor("two-jack", "sample-entrant-$i"))
            assertEquals("The road to the campsite reopens Friday.", chat.lastText)
            assertEquals("the entrant has it unread", 1, chat.unreadForEntrant)
        }
    }

    @Test fun send_onlyReachesTheChosenGroup() {
        openNotify()
        tap("audience_WAITING")
        type("The draw is on Sunday.")
        tap("notify_send")
        compose.waitUntil(10_000) { sent().isNotEmpty() }
        assertEquals(Audience.WAITING, sent().single().audience)
        assertEquals(6, sent().single().recipientCount)
    }

    @Test fun anEmptyMessage_isNotSent() {
        openNotify()
        tap("notify_send")
        compose.onNodeWithText("Write a message first.").assertExists()
        assertTrue(sent().isEmpty())
    }

    @Test fun aGroupWithNobodyInIt_isNotSentTo() {
        openNotify()
        tap("audience_WINNERS")
        type("See you at check-in.")
        tap("notify_send")
        compose.onNodeWithText("Nobody is in that group yet.").assertExists()
        assertTrue(sent().isEmpty())
    }

    @Test fun afterSending_theMessageBoxIsClearedForTheNextOne() {
        openNotify()
        type("The road to the campsite reopens Friday.")
        tap("notify_send")
        compose.waitUntil(10_000) { sent().isNotEmpty() }
        compose.onNodeWithTag("notify_preview").assertDoesNotExist()
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
