/*
 * ChatPhotoSendingTest.kt (Tausif)
 *
 * Purpose: Unit tests for sending a photo from a chat (ChatViewModel in Chat.kt): a picked photo
 * waits above the message box, can be taken off, is sent on its own or with a caption, and a
 * photo the phone couldn't read or that is too large is refused with a message.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 21, 2026). Prompt: "Write
 * tests for sending photos in the chat." Reviewed, edited and run by Team Carbon.
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Priya, owner of Two Jack Lakeside, chatting with Wren, a selected guest. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatPhotoSendingTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = ViewModelStore()
    private val lot = TestData.lottery()
    private val store = InMemoryEventsStore(EventsSnapshot(
        campsites = mapOf("two-jack" to TestData.campsite()),
        lotteries = mapOf(lot.id to lot),
        entries = listOf(WaitlistEntry(lot.id, "wren", "Wren Won", TestData.NOW, status = EntryStatus.WON)),
    ))
    private val chat = Chat(Chat.idFor("two-jack", "wren"), "two-jack", "Two Jack Lakeside", "owner-1", "Priya", "wren", "Wren Won", lotteryKey = lot.id)
    private val photo = "/9j/" + "A".repeat(400)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }

    private fun TestScope.open(): ChatViewModel {
        val vm = ChatViewModel(AppContainer.inMemory("owner-1", FixedClock(TestData.NOW), store), chat).also { models.put("chat", it) }
        backgroundScope.launch { vm.state.collect {} }
        return vm
    }

    private suspend fun messages() = store.observeMessages(chat.id).first()

    @Test fun aPickedPhoto_waitsToBeSent() = runTest(dispatcher) {
        val vm = open()
        vm.attachPhoto(photo)
        assertEquals(photo, vm.state.value.photo)
        assertNull(vm.state.value.message)
        assertTrue(messages().isEmpty())
    }

    @Test fun aPickedPhoto_canBeTakenOff() = runTest(dispatcher) {
        val vm = open()
        vm.attachPhoto(photo)
        vm.removePhoto()
        assertNull(vm.state.value.photo)
    }

    @Test fun aPhotoOnItsOwn_isSent_andTheBoxClears() = runTest(dispatcher) {
        val vm = open()
        vm.attachPhoto(photo)
        vm.send("Priya")
        val sent = messages().single()
        assertEquals(photo, sent.photoBase64)
        assertEquals("", sent.text)
        assertNull(vm.state.value.photo)
        assertEquals("📷 Photo", store.observeChat(chat.id).first()!!.lastText)
    }

    @Test fun aPhotoWithACaption_sendsBoth() = runTest(dispatcher) {
        val vm = open()
        vm.attachPhoto(photo)
        vm.onDraft("  Your site, number 14  ")
        vm.send("Priya")
        val sent = messages().single()
        assertEquals(photo, sent.photoBase64)
        assertEquals("Your site, number 14", sent.text)
        assertEquals("", vm.state.value.draft)
    }

    @Test fun nothingTyped_andNoPhoto_sendsNothing() = runTest(dispatcher) {
        val vm = open()
        vm.onDraft("   ")
        vm.send("Priya")
        assertTrue(messages().isEmpty())
    }

    @Test fun aPhotoThePhoneCouldntRead_isRefused() = runTest(dispatcher) {
        val vm = open()
        vm.attachPhoto(null)
        assertNull(vm.state.value.photo)
        assertEquals("Couldn't open that photo. Try another one.", vm.state.value.message)
    }

    @Test fun aPhotoTooLargeForOneMessage_isRefused() = runTest(dispatcher) {
        val vm = open()
        vm.attachPhoto("A".repeat(ChatPhotos.MAX_CHARS + 1))
        assertNull(vm.state.value.photo)
        assertEquals("That photo is too large to send.", vm.state.value.message)
    }

    @Test fun aRefusedPhoto_keepsTheOneAlreadyPicked() = runTest(dispatcher) {
        val vm = open()
        vm.attachPhoto(photo)
        vm.attachPhoto(null)
        assertEquals(photo, vm.state.value.photo)
    }
}
