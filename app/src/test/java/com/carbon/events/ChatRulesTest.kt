/*
 * ChatRulesTest.kt (Tausif)
 *
 * Purpose: unit tests for who can send in a campsite conversation: the owner and selected
 * entrants can message, other entrants only read notices. A guest with a reservation messages
 * the owner about it (US 01.14.01), and the owner messages that guest (US 02.12.01).
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

    /** US 01.14.01: a guest messages the campsite owner about a reservation they have. */
    @Test fun guestWithAReservation_canMessageTheOwner() =
        assertTrue(ChatRules.canSend(chat, "adam", entry(EntryStatus.WON).copy(checkInDay = 20_000, checkOutDay = 20_002)))

    /** US 02.12.01: the owner messages a guest who is reserving their campsite. */
    @Test fun owner_canMessageAGuestWithAReservation() =
        assertTrue(ChatRules.canSend(chat, "owner-1", entry(EntryStatus.WON).copy(checkInDay = 20_000, checkOutDay = 20_002)))

    @Test fun guestWhoCancelled_canNoLongerMessage() =
        assertFalse(ChatRules.canSend(chat, "adam", entry(EntryStatus.CANCELLED).copy(checkInDay = 20_000, checkOutDay = 20_002)))

    @Test fun winner_canReply() = assertTrue(ChatRules.canSend(chat, "adam", entry(EntryStatus.WON)))
    @Test fun notSelected_cannotReply() = assertFalse(ChatRules.canSend(chat, "adam", entry(EntryStatus.LOST)))
    @Test fun stillInTheDraw_cannotReply() = assertFalse(ChatRules.canSend(chat, "adam", entry(EntryStatus.WAITING)))
    @Test fun noEntry_cannotReply() = assertFalse(ChatRules.canSend(chat, "adam", null))
    @Test fun owner_canReplyToAWinner() = assertTrue(ChatRules.canSend(chat, "owner-1", entry(EntryStatus.WON)))
    @Test fun owner_cannotReplyInANoticeOnlyConversation() = assertFalse(ChatRules.canSend(chat, "owner-1", entry(EntryStatus.LOST)))
    @Test fun stranger_cannotSend() = assertFalse(ChatRules.canSend(chat, "someone-else", entry(EntryStatus.WON)))
}
