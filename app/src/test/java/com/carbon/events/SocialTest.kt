/*
 * SocialTest.kt (Tausif)
 *
 * Purpose: Compose tests for Social.kt, the wow-factor chat: an owner messages a selected entrant (US 02.12.01), and an entrant who was not selected can only read the owner's notices.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 6, 2026). Prompt: "Write
 * a test file for each source file that has none." Reviewed, edited and run by Team Carbon.
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
class SocialTest {
    @get:Rule val compose = createComposeRule()
    private val lot = TestData.lottery()
    private val store = InMemoryEventsStore(EventsSnapshot(
        campsites = mapOf("two-jack" to TestData.campsite()), lotteries = mapOf(lot.id to lot),
        entries = TestData.entries(count = 2).mapIndexed { i, e -> e.copy(status = if (i == 0) EntryStatus.WON else EntryStatus.LOST) },
    ))
    private fun chatWith(entrant: String) = Chat(Chat.idFor("two-jack", entrant), "two-jack", "Two Jack Lakeside", "owner-1", "Priya", entrant, "Entrant", lotteryKey = lot.id)

    private fun open(userId: String, name: String, chat: Chat) {
        val c = AppContainer.inMemory(userId, FixedClock(TestData.NOW), store)
        compose.setContent { EventsTheme { ChatScreen(c, UserProfile(userId, name, "$userId@example.com"), chat, onBack = {}) } }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("chat_screen")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun owner_messagesASelectedEntrant() {
        val chat = chatWith("entrant-0")
        open("owner-1", "Priya", chat)
        compose.onNodeWithTag("chat_input").performTextInput("Firewood is sold at the gate.")
        compose.onNodeWithTag("chat_send").performClick()
        compose.waitUntil(5_000) { runBlocking { store.observeMessages(chat.id).first() }.isNotEmpty() }
        val msg = runBlocking { store.observeMessages(chat.id).first() }.single()
        assertEquals("Firewood is sold at the gate.", msg.text)
        assertEquals("owner-1", msg.senderId)
        compose.onNodeWithText("Firewood is sold at the gate.").assertExists()
    }

    @Test fun entrantNotSelected_canOnlyReadNotices() {
        open("entrant-1", "Entrant 1", chatWith("entrant-1"))
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("chat_read_only")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("chat_read_only").assertExists()
        compose.onAllNodesWithTag("chat_input").assertCountEquals(0)
    }
}
