/*
 * ModerationTest.kt (Tausif)
 *
 * Purpose: Unit tests for Moderation.kt: grouping notifications into the admin notification log (US 03.08.01).
 *
 * AI use: drafted with Claude (Claude Opus 5.5, Anthropic), November 6, 2026. Prompt: "Write a
 * test file for each source file that has none." Reviewed and run by Team Carbon; see "AI Use"
 * in the README.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import org.junit.Assert.*
import org.junit.Test

class ModerationTest {
    private val t = 1_792_162_800_000L
    private val site = Campsite("two-jack", "parks", "Parks Lodge", "Two Jack Lakeside", "Banff, AB", maxOccupancy = 6)
    private val names = mapOf("maya" to "Maya Chen", "liam" to "Liam Okafor")
    private fun n(id: String, to: String, type: NotificationType, at: Long, title: String = "t") =
        AppNotification(id, to, type, title, "body", "two-jack/nov-2026", "two-jack", at)

    @Test fun winsAndLossesFromOneDraw_areOneLotteryResultsLine() {
        val log = groupLog(listOf(n("1", "maya", NotificationType.LOTTERY_WON, t), n("2", "liam", NotificationType.LOTTERY_LOST, t + 1_000)), names, mapOf("two-jack" to site))
        assertEquals(1, log.size)
        assertEquals("Lottery results", log[0].title)
        assertEquals("System", log[0].sender)
        assertEquals(listOf("Maya Chen", "Liam Okafor"), log[0].recipients)
    }

    @Test fun ownerMessages_areSentByTheOwner_andRemovedUsersAreNamed() {
        val log = groupLog(listOf(n("1", "ghost", NotificationType.MESSAGE, t, "Road closure")), names, mapOf("two-jack" to site))
        assertEquals("Parks Lodge", log[0].sender)
        assertEquals("parks", log[0].ownerId)
        assertEquals(listOf("a removed user"), log[0].recipients)
    }

    @Test fun differentMinutes_areSeparateLines_newestFirst() {
        val log = groupLog(listOf(n("1", "maya", NotificationType.WARNING, t, "Warning"), n("2", "maya", NotificationType.WARNING, t + 5 * 60_000, "Warning")), names, emptyMap())
        assertEquals(2, log.size)
        assertTrue(log[0].at > log[1].at)
        assertEquals("Admin", log[0].sender)
    }
}
