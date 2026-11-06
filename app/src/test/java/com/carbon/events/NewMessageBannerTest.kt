/*
 * NewMessageBannerTest.kt (Ansh)
 *
 * Purpose: unit tests for when the new-message banner appears at the top of the app.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The pop-up banner at the top of the app when a new message arrives (not shown inside that chat). */
class NewMessageBannerTest {
    private fun chat(entrant: String, unread: Int, at: Long, sender: String = "owner-1") =
        Chat(Chat.idFor("two-jack", entrant), "two-jack", "Two Jack Lakeside", "owner-1", "Priya", entrant, "Adam",
            lastText = "See you Friday", lastAt = at, lastSenderId = sender, unreadForEntrant = unread)

    @Test fun newUnreadMessage_showsBanner() {
        val shown = newMessageBanner(listOf(chat("adam", 0, 1)), listOf(chat("adam", 1, 2)), "adam", openChatId = null)
        assertEquals(Chat.idFor("two-jack", "adam"), shown?.id)
    }

    @Test fun noChange_noBanner() = assertNull(newMessageBanner(listOf(chat("adam", 1, 2)), listOf(chat("adam", 1, 2)), "adam", null))

    @Test fun insideThatChat_noBanner() =
        assertNull(newMessageBanner(listOf(chat("adam", 0, 1)), listOf(chat("adam", 1, 2)), "adam", openChatId = Chat.idFor("two-jack", "adam")))

    @Test fun ownMessage_noBanner() = assertNull(newMessageBanner(listOf(chat("adam", 0, 1)), listOf(chat("adam", 0, 2, sender = "adam")), "adam", null))

    @Test fun brandNewConversation_showsBanner() =
        assertEquals("See you Friday", newMessageBanner(emptyList(), listOf(chat("adam", 1, 2)), "adam", null)?.lastText)

    @Test fun firstLoad_doesNotShowOldMessages() = assertNull(newMessageBanner(null, listOf(chat("adam", 3, 2)), "adam", null))
}
