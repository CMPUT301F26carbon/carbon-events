/*
 * InboxTest.kt (Suvir)
 *
 * Stage 4 test: written before the stage 5 code. It fails (red) until stage 5 is merged.
 */
package com.carbon.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The bell's WhatsApp-style list: one row per conversation plus app updates, newest first. */
class InboxTest {
    private val n = TestData.NOW
    private fun chat(site: String, siteName: String, entrant: String, lastAt: Long, unreadEntrant: Int = 0, unreadOwner: Int = 0, last: String = "Hi") =
        Chat(Chat.idFor(site, entrant), site, siteName, "owner-1", "Priya", entrant, entrant.replaceFirstChar { it.uppercase() },
            lastText = last, lastAt = lastAt, unreadForOwner = unreadOwner, unreadForEntrant = unreadEntrant)
    private fun note(id: String, at: Long, read: Boolean) =
        AppNotification(id, "adam", NotificationType.LOTTERY_WON, "You won Two Jack Lakeside!", "Choose your dates.", "lot-1", "two-jack", at, read)

    @Test fun entrantRow_namesCampsiteAndOwner_withLatestMessageAndUnread() {
        val row = buildInbox("adam", listOf(chat("two-jack", "Two Jack Lakeside", "adam", n, unreadEntrant = 3, last = "Draw is Friday")), emptyList()).single()
        assertEquals("Two Jack Lakeside – from Priya", row.title)
        assertEquals("Draw is Friday", row.preview)
        assertEquals(3, row.unread)
        assertEquals(Chat.idFor("two-jack", "adam"), row.chatId)
    }

    @Test fun ownerRow_namesTheEntrant_andCountsOwnersUnread() {
        val row = buildInbox("owner-1", listOf(chat("two-jack", "Two Jack Lakeside", "adam", n, unreadEntrant = 5, unreadOwner = 2)), emptyList()).single()
        assertEquals("Adam", row.title)
        assertEquals("Two Jack Lakeside", row.subtitle)
        assertEquals(2, row.unread)
    }

    @Test fun conversationsAndAppUpdates_mixed_newestFirst() {
        val rows = buildInbox(
            "adam",
            listOf(chat("two-jack", "Two Jack Lakeside", "adam", n - 10 * TestData.MIN), chat("moraine", "Moraine Lake", "adam", n)),
            listOf(note("w1", n - 5 * TestData.MIN, read = false)),
        )
        assertEquals(listOf("Moraine Lake – from Priya", "You won Two Jack Lakeside!", "Two Jack Lakeside – from Priya"), rows.map { it.title })
    }

    @Test fun appUpdate_countsOneUnreadUntilRead_andHasNoChat() {
        val unread = buildInbox("adam", emptyList(), listOf(note("w1", n, read = false))).single()
        val read = buildInbox("adam", emptyList(), listOf(note("w1", n, read = true))).single()
        assertEquals(1, unread.unread); assertEquals(0, read.unread); assertNull(unread.chatId)
    }

    @Test fun bellBadge_hiddenAtZero_countUpToNine_thenNinePlus() {
        fun rows(vararg u: Int) = u.mapIndexed { i, c -> chat("s$i", "Site $i", "adam", n - i, unreadEntrant = c) }.let { buildInbox("adam", it, emptyList()) }
        assertNull(bellBadge(rows(0, 0)))
        assertEquals("4", bellBadge(rows(1, 3)))
        assertEquals("9", bellBadge(rows(9)))
        assertEquals("9+", bellBadge(rows(6, 6)))
    }
}
