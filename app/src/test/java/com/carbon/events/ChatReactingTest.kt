/*
 * ChatReactingTest.kt (Tausif)
 *
 * Purpose: Unit tests for reacting to a chat message (ChatViewModel in Chat.kt): a reaction is
 * saved, tapping the same one takes it off, a different one replaces it, the other person's
 * reaction is left alone, and a reaction that can't be saved says why.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 21, 2026). Prompt: "Write
 * tests for emoji reactions in the chat." Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Wren, a selected guest, reacting to Priya's messages about Two Jack Lakeside. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatReactingTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = ViewModelStore()
    private val lot = TestData.lottery()
    private val store = InMemoryEventsStore(EventsSnapshot(
        campsites = mapOf("two-jack" to TestData.campsite()),
        lotteries = mapOf(lot.id to lot),
        entries = listOf(WaitlistEntry(lot.id, "wren", "Wren Won", TestData.NOW, status = EntryStatus.WON)),
    ))
    private val chat = Chat(Chat.idFor("two-jack", "wren"), "two-jack", "Two Jack Lakeside", "owner-1", "Priya", "wren", "Wren Won", lotteryKey = lot.id)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }

    private fun TestScope.open(social: SocialRepository = store): ChatViewModel {
        val c = AppContainer("wren", "Pixel 8", store, store, store, store, FixedClock(TestData.NOW), usingServer = false, social = social)
        val vm = ChatViewModel(c, chat).also { models.put("chat", it) }
        backgroundScope.launch { vm.state.collect {} }
        return vm
    }

    private suspend fun message(): ChatMessage {
        if (store.snapshot.messages.isEmpty()) store.sendMessage(chat, "owner-1", "Priya", "Firewood is $8 at the shack.", TestData.NOW)
        return store.observeMessages(chat.id).first().single()
    }

    @Test fun reacting_savesIt() = runTest(dispatcher) {
        val vm = open()
        vm.react(message(), "👍")
        assertEquals(mapOf("wren" to "👍"), message().reactions)
    }

    @Test fun tappingTheSameReaction_takesItOff() = runTest(dispatcher) {
        val vm = open()
        vm.react(message(), "❤️")
        vm.react(message(), "❤️")
        assertEquals(emptyMap<String, String>(), message().reactions)
    }

    @Test fun aDifferentReaction_replacesYourOwn() = runTest(dispatcher) {
        val vm = open()
        vm.react(message(), "😂")
        vm.react(message(), "🔥")
        assertEquals(mapOf("wren" to "🔥"), message().reactions)
    }

    @Test fun theOtherPersonsReaction_isLeftAlone() = runTest(dispatcher) {
        store.react(chat.id, message().id, "owner-1", "⛺")
        val vm = open()
        vm.react(message(), "👍")
        assertEquals(mapOf("owner-1" to "⛺", "wren" to "👍"), message().reactions)
    }

    @Test fun aReactionThatCantBeSaved_saysWhy() = runTest(dispatcher) {
        val offline = object : SocialRepository by store {
            override suspend fun react(chatId: String, messageId: String, userId: String, emoji: String?) = throw AuthException(AuthError.NETWORK)
        }
        val vm = open(offline)
        vm.react(message(), "👍")
        assertEquals(AuthError.NETWORK.message, vm.state.value.message)
        assertEquals(emptyMap<String, String>(), message().reactions)
    }
}
