/*
 * ChatRulesTest.kt (Tausif)
 *
 * Purpose: unit tests for who can send in a campsite conversation: the owner and selected
 * entrants can message, other entrants only read notices.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Who can type in a campsite conversation: owners' notices reach everyone who joined, but only a
 * selected (won) entrant and the owner can send messages in that conversation.
 */
class ChatRulesTest {
    private val chat = Chat(Chat.idFor("two-jack", "adam"), "two-jack", "Two Jack Lakeside", "owner-1", "Priya", "adam", "Adam")
    private fun entry(status: EntryStatus) = WaitlistEntry("lot-1", "adam", "Adam", TestData.NOW, status = status)

    @Test fun winner_canReply() = assertTrue(ChatRules.canSend(chat, "adam", entry(EntryStatus.WON)))
    @Test fun notSelected_cannotReply() = assertFalse(ChatRules.canSend(chat, "adam", entry(EntryStatus.LOST)))
    @Test fun stillInTheDraw_cannotReply() = assertFalse(ChatRules.canSend(chat, "adam", entry(EntryStatus.WAITING)))
    @Test fun noEntry_cannotReply() = assertFalse(ChatRules.canSend(chat, "adam", null))
    @Test fun owner_canReplyToAWinner() = assertTrue(ChatRules.canSend(chat, "owner-1", entry(EntryStatus.WON)))
    @Test fun owner_cannotReplyInANoticeOnlyConversation() = assertFalse(ChatRules.canSend(chat, "owner-1", entry(EntryStatus.LOST)))
    @Test fun stranger_cannotSend() = assertFalse(ChatRules.canSend(chat, "someone-else", entry(EntryStatus.WON)))
}
