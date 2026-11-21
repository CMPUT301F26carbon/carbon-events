/*
 * NoticesTest.kt (Vaibhav)
 *
 * Purpose: Unit tests for Notices.kt: the notices for a new lottery, an owner's message and an
 * admin warning.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write tests for every part of Notices.kt that isn't tested yet."
 * Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticesTest {
    private val now = TestData.NOW
    private val day = TestData.DAY

    private val lottery = TestData.lottery(start = now, end = now + 3 * day)

    // ---- Notices ----

    @Test fun openedNotice_tellsFollowersAboutTheNewLottery() {
        val n = openedNotice(lottery, "follower-1", now)
        assertEquals(NotificationType.OPENED, n.type)
        assertEquals("follower-1", n.recipientId)
        assertEquals("New lottery at Two Jack Lakeside", n.title)
        assertTrue(n.body.startsWith("Registration is open "))
        assertEquals(lottery.id, n.lotteryId)
        assertEquals(lottery.campsiteId, n.campsiteId)
    }

    @Test fun announcementNotice_isAMessageFromTheCampsite() {
        val n = announcementNotice(lottery, "entrant-1", "Gates open at 2 pm.", now)
        assertEquals(NotificationType.MESSAGE, n.type)
        assertEquals("Message from Two Jack Lakeside", n.title)
        assertEquals("Gates open at 2 pm.", n.body)
    }

    @Test fun warningNotice_isFromTheEventsTeam_andNotAboutALottery() {
        val n = warningNotice("entrant-1", "Please keep reviews respectful.", now)
        assertEquals(NotificationType.WARNING, n.type)
        assertEquals("Warning from the Events team", n.title)
        assertEquals("", n.lotteryId)
        assertEquals("", n.campsiteId)
    }
}
