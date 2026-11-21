/*
 * ChatReactionViewsTest.kt (Tausif)
 *
 * Purpose: Compose UI (intent) tests for ChatReactionViews.kt: long-pressing a message (or its
 * photo) opens the six reactions, picking one shows it under the message with a count, tapping it
 * again takes it off, and the row and the counts work on their own.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 21, 2026). Prompt: "Write
 * UI (intent) tests for emoji reactions in the chat." Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Wren, a guest Priya selected at Two Jack Lakeside, reacting to her messages. */
@RunWith(AndroidJUnit4::class)
class ChatReactionViewsTest {
    @get:Rule val compose = createComposeRule()
    private val lot = TestData.lottery()
    private val store = InMemoryEventsStore(EventsSnapshot(
        campsites = mapOf("two-jack" to TestData.campsite()),
        lotteries = mapOf(lot.id to lot),
        entries = listOf(WaitlistEntry(lot.id, "wren", "Wren Won", TestData.NOW, status = EntryStatus.WON)),
    ))
    private val chat = Chat(Chat.idFor("two-jack", "wren"), "two-jack", "Two Jack Lakeside", "owner-1", "Priya", "wren", "Wren Won", lotteryKey = lot.id)

    private fun waitFor(tag: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun gone(tag: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
    }

    private fun tap(tag: String) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) }
    private fun longPress(tag: String) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnLongClick) }

    /** Priya's message, and Wren's chat with it open. */
    private fun openWithMessage(photo: String? = null): String {
        runBlocking { store.sendMessage(chat, "owner-1", "Priya", "Firewood is $8 at the shack.", TestData.NOW, photoBase64 = photo) }
        val id = store.snapshot.messages.single().id
        val c = AppContainer.inMemory("wren", FixedClock(TestData.NOW), store)
        compose.setContent { EventsTheme { ChatScreen(c, UserProfile("wren", "Wren Won", "wren@example.com"), chat, onBack = {}) } }
        waitFor("chat_bubble_$id")
        return id
    }

    private fun reactions() = store.snapshot.messages.single().reactions

    // ---- In a chat ----

    @Test fun longPressingAMessage_opensTheSixReactions() {
        val id = openWithMessage()
        longPress("chat_bubble_$id")
        waitFor("reaction_bar_$id")
        ChatReactions.CHOICES.forEach { compose.onNodeWithContentDescription("React $it").assertExists() }
    }

    @Test fun pickingAReaction_showsItUnderTheMessage_andClosesTheRow() {
        val id = openWithMessage()
        longPress("chat_bubble_$id")
        waitFor("reaction_bar_$id")
        tap("reaction_pick_0")
        compose.waitUntil(5_000) { reactions() == mapOf("wren" to "👍") }
        gone("reaction_bar_$id")
        waitFor("reaction_${id}_0")
        compose.onNodeWithContentDescription("👍 1").assertExists()
    }

    @Test fun tappingYourReaction_takesItOff() {
        val id = openWithMessage()
        runBlocking { store.react(chat.id, id, "wren", "🔥") }
        waitFor("reaction_${id}_5")
        tap("reaction_${id}_5")
        compose.waitUntil(5_000) { reactions().isEmpty() }
        gone("reactions_$id")
    }

    @Test fun tappingSomeoneElsesReaction_addsYoursToIt() {
        val id = openWithMessage()
        runBlocking { store.react(chat.id, id, "owner-1", "⛺") }
        waitFor("reaction_${id}_4")
        tap("reaction_${id}_4")
        compose.waitUntil(5_000) { reactions() == mapOf("owner-1" to "⛺", "wren" to "⛺") }
        compose.onNodeWithContentDescription("⛺ 2").assertExists()
    }

    @Test fun longPressingAgain_closesTheRow() {
        val id = openWithMessage()
        longPress("chat_bubble_$id")
        waitFor("reaction_bar_$id")
        longPress("chat_bubble_$id")
        gone("reaction_bar_$id")
    }

    @Test fun longPressingAPhoto_opensTheReactionsToo() {
        val photo = ByteArrayOutputStream().let { out ->
            Bitmap.createBitmap(60, 40, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 80, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
        val id = openWithMessage(photo)
        longPress("chat_photo_$id")
        waitFor("reaction_bar_$id")
    }

    // ---- The pieces on their own ----

    @Test fun theRow_marksYourReaction_andHandsBackThePick() {
        var picked: String? = null
        compose.setContent { EventsTheme { ReactionBar("m1", mine = "❤️") { picked = it } } }
        compose.onNodeWithContentDescription("React ❤️").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        compose.onNodeWithContentDescription("React 👍").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
        tap("reaction_pick_3")
        assertEquals("😮", picked)
    }

    @Test fun thePills_countEachEmoji_andMarkYours() {
        var toggled: String? = null
        compose.setContent { EventsTheme { ReactionPills("m1", mapOf("a" to "🔥", "b" to "🔥", "me" to "👍"), "me", { toggled = it }) } }
        compose.onNodeWithContentDescription("🔥 2").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
        compose.onNodeWithContentDescription("👍 1").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        tap("reaction_m1_5")
        assertEquals("🔥", toggled)
    }

    @Test fun noReactions_showNothing() {
        compose.setContent { EventsTheme { ReactionPills("m1", emptyMap(), "me", {}) } }
        compose.onAllNodesWithTag("reactions_m1").assertCountEquals(0)
    }
}
