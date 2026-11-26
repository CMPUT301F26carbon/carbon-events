/*
 * ChatReturningGuestTest.kt (Suvir)
 *
 * Purpose: Unit tests for a returning guest messaging the owner about their reservation
 * (US 01.14.01): a guest who wasn't selected in one of a campsite's lotteries, then won and booked
 * in a later one, can still message the owner, even after an owner's notice tied the conversation
 * to the earlier lottery; the message goes out tied to the lottery they won. Someone who never won
 * there still only reads notices.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatReturningGuestTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = ViewModelStore()
    private val now = TestData.NOW
    private val today = now / TestData.DAY
    private val site = TestData.campsite()
    private val may = TestData.lottery("two-jack/may-2026").copy(status = LotteryStatus.DRAWN)
    private val october = TestData.lottery("two-jack/oct-2026").copy(status = LotteryStatus.DRAWN)

    /** Maya wasn't selected in May, then won October and booked two nights. */
    private val lostMay = WaitlistEntry(may.id, "maya", "Maya Chen", now, EntryStatus.LOST, campsiteId = site.id, ownerId = site.ownerId)
    private val wonOctober = WaitlistEntry(october.id, "maya", "Maya Chen", now, EntryStatus.WON, today + 3, today + 5, campsiteId = site.id, ownerId = site.ownerId)

    /** The conversation, last tied to May by the owner's notice to May's waitlist. */
    private val chat = Chat(Chat.idFor(site.id, "maya"), site.id, site.name, site.ownerId, "Parks Lodge", "maya", "Maya Chen", lotteryKey = Paths.keyOf(may.id))

    private lateinit var store: InMemoryEventsStore

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = InMemoryEventsStore(EventsSnapshot(
            campsites = mapOf(site.id to site), lotteries = mapOf(may.id to may, october.id to october),
            entries = listOf(lostMay, wonOctober), chats = mapOf(chat.id to chat),
        ))
    }

    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }

    private fun <T : ViewModel> track(vm: T): T = vm.also { models.put(System.identityHashCode(vm).toString(), vm) }
    private fun TestScope.observe(flow: Flow<*>) { backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} } }

    @Test fun entryFor_findsTheirWinAtThisCampsite_inAnyLottery() {
        assertEquals(wonOctober, ChatRules.entryFor(chat, listOf(lostMay, wonOctober)))
        val elsewhere = wonOctober.copy(lotteryId = "lake-louise/oct-2026", campsiteId = "lake-louise")
        assertEquals("a win at another campsite doesn't count", lostMay, ChatRules.entryFor(chat, listOf(lostMay, elsewhere)))
        assertNull(ChatRules.entryFor(chat, emptyList()))
    }

    @Test fun tiedTo_pointsTheConversationAtTheirWin() {
        assertEquals(Paths.keyOf(october.id), ChatRules.tiedTo(chat, wonOctober).lotteryKey)
        assertEquals("not at an entry they didn't win", chat, ChatRules.tiedTo(chat, lostMay))
    }

    @Test fun aReturningGuest_canMessageTheOwner_aboutTheirNewBooking() = runTest(dispatcher) {
        val vm = track(ChatViewModel(AppContainer.inMemory("maya", FixedClock(now), store), chat))
        observe(vm.state)
        assertEquals(true, vm.state.value.canSend)
        vm.onDraft("Can we arrive after 9pm?")
        vm.send("Maya Chen")
        assertEquals("Can we arrive after 9pm?", store.observeMessages(chat.id).first().single().text)
        assertEquals("the conversation is now about the lottery she won", Paths.keyOf(october.id), Paths.keyOf(store.observeChat(chat.id).first()!!.lotteryKey))
    }

    @Test fun theOwner_canReplyToThem_too() = runTest(dispatcher) {
        val vm = track(ChatViewModel(AppContainer.inMemory(site.ownerId, FixedClock(now), store), chat))
        observe(vm.state)
        assertEquals(true, vm.state.value.canSend)
    }

    @Test fun someoneWhoNeverWonThere_stillOnlyReadsNotices() = runTest(dispatcher) {
        store = InMemoryEventsStore(EventsSnapshot(campsites = mapOf(site.id to site), lotteries = mapOf(may.id to may), entries = listOf(lostMay), chats = mapOf(chat.id to chat)))
        val vm = track(ChatViewModel(AppContainer.inMemory("maya", FixedClock(now), store), chat))
        observe(vm.state)
        assertFalse(vm.state.value.canSend!!)
        assertTrue(store.observeMessages(chat.id).first().isEmpty())
    }
}
