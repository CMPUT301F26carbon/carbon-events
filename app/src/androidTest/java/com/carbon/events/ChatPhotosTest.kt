/*
 * ChatPhotosTest.kt (Tausif)
 *
 * Purpose: Compose UI (intent) tests for ChatPhotos.kt: the photo button beside the message box,
 * the photo waiting to be sent (and taking it off), a photo inside a message, and opening it full
 * screen, both in a guest's chat and in the admin's read-only log.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 21, 2026). Prompt: "Write
 * UI (intent) tests for sending photos in the chat." Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Priya, owner of Two Jack Lakeside, and Wren, a guest she selected. */
@RunWith(AndroidJUnit4::class)
class ChatPhotosTest {
    @get:Rule val compose = createComposeRule()
    private val lot = TestData.lottery()
    private val store = InMemoryEventsStore(EventsSnapshot(
        campsites = mapOf("two-jack" to TestData.campsite()),
        lotteries = mapOf(lot.id to lot),
        entries = listOf(
            WaitlistEntry(lot.id, "wren", "Wren Won", TestData.NOW, status = EntryStatus.WON),
            WaitlistEntry(lot.id, "lee", "Lee Lost", TestData.NOW, status = EntryStatus.LOST),
        ),
    ))
    private val chat = Chat(Chat.idFor("two-jack", "wren"), "two-jack", "Two Jack Lakeside", "owner-1", "Priya", "wren", "Wren Won", lotteryKey = lot.id)

    /** A small real JPEG, as the photo picker would hand back. */
    private val photo: String = ByteArrayOutputStream().let { out ->
        Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF3366AA.toInt()) }.compress(Bitmap.CompressFormat.JPEG, 80, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun waitFor(tag: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun tap(tag: String) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) }

    private fun openChat(userId: String, name: String, with: Chat = chat) {
        val c = AppContainer.inMemory(userId, FixedClock(TestData.NOW), store)
        compose.setContent { EventsTheme { ChatScreen(c, UserProfile(userId, name, "$userId@example.com"), with, onBack = {}) } }
        waitFor("chat_screen")
    }

    private fun onlyMessageId() = store.snapshot.messages.single().id

    // ---- In a chat ----

    @Test fun aSelectedGuest_seesThePhotoButton() {
        openChat("wren", "Wren Won")
        waitFor("chat_photo_button")
        compose.onNodeWithContentDescription("Add a photo").assertExists()
    }

    @Test fun aGuestWhoCantReply_hasNoPhotoButton() {
        openChat("lee", "Lee Lost", chat.copy(id = Chat.idFor("two-jack", "lee"), entrantId = "lee", entrantName = "Lee Lost"))
        waitFor("chat_read_only")
        compose.onAllNodesWithTag("chat_photo_button").assertCountEquals(0)
    }

    @Test fun aPhotoInAMessage_showsInTheBubble_withItsCaption() {
        runBlocking { store.sendMessage(chat, "owner-1", "Priya", "Your site, number 14", TestData.NOW, photoBase64 = photo) }
        openChat("wren", "Wren Won")
        waitFor("chat_photo_${onlyMessageId()}")
        compose.onNodeWithText("Your site, number 14").assertExists()
    }

    @Test fun aPhotoOnItsOwn_showsNoEmptyText() {
        runBlocking { store.sendMessage(chat, "owner-1", "Priya", "", TestData.NOW, photoBase64 = photo) }
        openChat("wren", "Wren Won")
        waitFor("chat_photo_${onlyMessageId()}")
        compose.onAllNodesWithText("📷 Photo").assertCountEquals(0)
    }

    @Test fun tappingAPhoto_opensItFullScreen_andTheCrossClosesIt() {
        runBlocking { store.sendMessage(chat, "owner-1", "Priya", "", TestData.NOW, photoBase64 = photo) }
        openChat("wren", "Wren Won")
        val tag = "chat_photo_${onlyMessageId()}"
        waitFor(tag)
        tap(tag)
        waitFor("chat_photo_viewer")
        compose.onNodeWithTag("chat_photo_close").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("chat_photo_viewer")).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
    }

    @Test fun theSendButton_staysOff_untilThereIsSomethingToSend() {
        openChat("owner-1", "Priya")
        waitFor("chat_send")
        tap("chat_send")
        compose.waitForIdle()
        assertEquals(0, store.snapshot.messages.size)
    }

    // ---- The pieces on their own ----

    @Test fun aPendingPhoto_showsAboveTheBox_andTheCrossTakesItOff() {
        var removed = 0
        compose.setContent { EventsTheme { PendingPhoto(photo, onRemove = { removed++ }) } }
        compose.onNodeWithTag("chat_pending_photo").assertExists()
        compose.onNodeWithContentDescription("Photo to send").assertExists()
        tap("chat_pending_photo_remove")
        assertEquals(1, removed)
    }

    @Test fun aMessagePhoto_opensWhenTapped() {
        var opened = 0
        compose.setContent { EventsTheme { MessagePhoto(photo, "m1") { opened++ } } }
        tap("chat_photo_m1")
        assertEquals(1, opened)
    }

    @Test fun aPhotoThatCantBeShown_saysSo() {
        compose.setContent { EventsTheme { MessagePhoto("not a photo", "m2") {} } }
        compose.onNodeWithContentDescription("Photo couldn't be shown").assertExists()
    }

    @Test fun theViewer_closesWithTheCross() {
        var closed = 0
        compose.setContent { EventsTheme { PhotoViewer(photo) { closed++ } } }
        compose.onNodeWithTag("chat_photo_viewer").assertExists()
        compose.onNodeWithTag("chat_photo_close").performClick()
        assertEquals(1, closed)
    }

    // ---- The admin's read-only log ----

    @Test fun theAdminLog_showsAPhotoSentInAChat() {
        runBlocking { store.sendMessage(chat, "owner-1", "Priya", "Site map", TestData.NOW, photoBase64 = photo) }
        val c = AppContainer.inMemory("admin-1", FixedClock(TestData.NOW), store)
        compose.setContent { EventsTheme { AdminChatLogScreen(c, chat, onBack = {}) } }
        val tag = "chat_photo_${onlyMessageId()}"
        waitFor(tag)
        compose.onNodeWithText("Site map").assertExists()
        tap(tag)
        waitFor("chat_photo_viewer")
    }
}
