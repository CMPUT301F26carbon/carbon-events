/*
 * NoticeThreadsTest.kt (Vaibhav)
 *
 * Stage 4 test: written before the stage 5 code. It fails (red) until stage 5 is merged.
 */
package com.carbon.events

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner "Notify" lands in each chosen entrant's campsite conversation; unread counts go up and reset when read. */
class NoticeThreadsTest {
    private val lot = TestData.lottery()
    private fun store(): InMemoryEventsStore {
        val e = TestData.entries(count = 3).mapIndexed { i, it ->
            it.copy(campsiteId = "two-jack", campsiteName = "Two Jack Lakeside", ownerId = "owner-1", status = if (i == 0) EntryStatus.WON else EntryStatus.LOST)
        }
        return InMemoryEventsStore(EventsSnapshot(campsites = mapOf("two-jack" to TestData.campsite()), lotteries = mapOf(lot.id to lot), entries = e))
    }

    @Test fun notifyEveryone_putsTheNoticeInEachEntrantsConversation() = runTest {
        val s = store()
        assertEquals(3, s.sendAnnouncement(lot, Audience.ALL, "Draw moved to Friday", TestData.NOW))
        for (i in 0..2) {
            val id = Chat.idFor("two-jack", "entrant-$i")
            val chat = s.observeChat(id).first()!!
            assertEquals(1, chat.unreadCount("entrant-$i"))
            val msg = s.observeMessages(id).first().single()
            assertEquals("Draw moved to Friday", msg.text)
            assertTrue(msg.announcement)
        }
    }

    @Test fun notifyWinners_reachesOnlyWinners() = runTest {
        val s = store()
        assertEquals(1, s.sendAnnouncement(lot, Audience.WINNERS, "Check-in is at 2 pm", TestData.NOW))
        assertTrue(s.observeMessages(Chat.idFor("two-jack", "entrant-0")).first().isNotEmpty())
        assertTrue(s.observeMessages(Chat.idFor("two-jack", "entrant-1")).first().isEmpty())
    }

    @Test fun eachMessage_addsOneUnreadForTheOtherPerson_andReadingResetsIt() = runTest {
        val s = store()
        val chat = Chat(Chat.idFor("two-jack", "entrant-0"), "two-jack", "Two Jack Lakeside", "owner-1", "Priya", "entrant-0", "Entrant 0", lotteryKey = lot.id)
        s.sendMessage(chat, "entrant-0", "Entrant 0", "Is firewood sold?", TestData.NOW)
        s.sendMessage(s.observeChat(chat.id).first()!!, "entrant-0", "Entrant 0", "And ice?", TestData.NOW + 1)
        val now = s.observeChat(chat.id).first()!!
        assertEquals(2, now.unreadCount("owner-1"))
        assertEquals(0, now.unreadCount("entrant-0"))
        s.markChatRead(chat.id, "owner-1")
        assertEquals(0, s.observeChat(chat.id).first()!!.unreadCount("owner-1"))
    }

    @Test fun ownersInbox_listsTheConversationsTheNoticeCreated() = runTest {
        val s = store()
        s.sendAnnouncement(lot, Audience.ALL, "Draw moved to Friday", TestData.NOW)
        assertEquals(3, s.observeChatsFor("owner-1").first().size)
        assertEquals(1, s.observeAnnouncementsByOwner("owner-1").first().size)
    }
}
