/*
 * OwnerMessageGuestTest.kt (Rayner)
 *
 * Purpose: Compose UI (intent) tests for an owner messaging a guest who is reserving their
 * campsite: from the guest's row in Bookings into a conversation, sending a message, and the
 * conversation showing up in the Inbox (US 02.12.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for an owner messaging a user who is reserving their campsite."
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

/** Johnston Canyon in the sample data: Liam Okafor has a reservation there and is staying now. */
@RunWith(AndroidJUnit4::class)
class OwnerMessageGuestTest {
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

    private val guest = "sample-entrant-1" // Liam Okafor
    private val chatId = Chat.idFor("johnston-canyon", guest)

    private fun openChatFromBookings() {
        launchOwner()
        compose.onNodeWithTag("nav_Bookings").performClick()
        waitFor("owner_bookings")
        tap("guest_chat_$guest")
        waitFor("chat_screen")
    }

    private fun type(text: String) =
        compose.onNode(hasSetTextAction() and (hasTestTag("chat_input") or hasAnyAncestor(hasTestTag("chat_input")))).performTextInput(text)
    private fun messages() = store.snapshot.messages.filter { it.chatId == chatId }

    @Test fun bookings_messageButton_opensAConversationWithThatGuest() {
        openChatFromBookings()
        compose.onAllNodesWithText("Liam Okafor", substring = true).onFirst().assertExists()
        compose.onNodeWithTag("chat_input").assertExists()
        compose.onNodeWithTag("chat_read_only").assertDoesNotExist()
    }

    @Test fun sending_deliversTheMessageToTheGuest() {
        openChatFromBookings()
        type("Your site is number 14, by the creek.")
        tap("chat_send")
        compose.waitUntil(10_000) { messages().isNotEmpty() }
        val m = messages().single()
        assertEquals("Your site is number 14, by the creek.", m.text)
        assertEquals(owner, m.senderId)
        assertEquals("Parks Lodge", m.senderName)
        assertEquals("the guest has it unread", 1, store.snapshot.chats.getValue(chatId).unreadForEntrant)
    }

    @Test fun theSentMessage_showsInTheConversation() {
        openChatFromBookings()
        type("Your site is number 14, by the creek.")
        tap("chat_send")
        compose.waitUntil(10_000) { messages().isNotEmpty() }
        compose.onNodeWithText("Your site is number 14, by the creek.").assertExists()
    }

    @Test fun anEmptyMessage_cannotBeSent() {
        openChatFromBookings()
        compose.onNodeWithTag("chat_send").assertIsNotEnabled()
        assertTrue(messages().isEmpty())
    }

    @Test fun theGuestsReply_showsUpForTheOwner() {
        openChatFromBookings()
        type("Your site is number 14, by the creek.")
        tap("chat_send")
        compose.waitUntil(10_000) { messages().isNotEmpty() }
        runBlocking { store.sendMessage(store.snapshot.chats.getValue(chatId), guest, "Liam Okafor", "Thanks, see you Friday.", NOW + 60_000) }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Thanks, see you Friday.")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    @Test fun theConversation_isListedInTheOwnersInbox() {
        launchOwner {
            val chat = Chat(chatId, "johnston-canyon", "Johnston Canyon", owner, "Parks Lodge", guest, "Liam Okafor", lotteryKey = "lot-johnston")
            store.sendMessage(chat, owner, "Parks Lodge", "Your site is number 14, by the creek.", NOW)
        }
        compose.onNodeWithTag("nav_Inbox").performClick()
        waitFor("inbox")
        compose.onNodeWithText("Chats · 1").assertExists()
        compose.onNodeWithTag("chat_$chatId").assertExists()
    }

    @Test fun theGuestDialog_alsoOffersMessage() {
        launchOwner()
        compose.onNodeWithTag("nav_Bookings").performClick()
        waitFor("owner_bookings")
        compose.onNodeWithTag("reservation_lot-johnston_$guest").performSemanticsAction(SemanticsActions.OnClick)
        waitFor("guest_dialog")
        tap("guest_message")
        waitFor("chat_screen")
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
