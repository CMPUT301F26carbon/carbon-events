/*
 * LotteryRulesTest.kt (Vaibhav)
 *
 * Purpose: Unit tests for LotteryRules.kt that no other test calls directly: filling spots a
 * winner gave back (US 01.05.01) and the notice for a rejected reservation.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write tests for every part of LotteryRules.kt that isn't tested yet."
 * Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LotteryRulesTest {
    private val now = TestData.NOW

    // ---- Filling spots that open up ----

    private fun drawn(winners: Int, free: Int) = TestData.lottery(winners = winners).copy(status = LotteryStatus.DRAWN, spotsFree = free)
    private fun entry(i: Int, status: EntryStatus) = WaitlistEntry("lot-1", "entrant-$i", "Entrant $i", now, status = status)

    @Test fun fillSpots_promotesOnlyAsManyAsHaveOpened() {
        val list = listOf(entry(1, EntryStatus.WON), entry(2, EntryStatus.LOST), entry(3, EntryStatus.LOST), entry(4, EntryStatus.LOST))
        val result = LotteryDrawer.fillSpots(drawn(winners = 2, free = 1), list, now, Random(7))
        assertEquals(1, result.entries.size)
        assertTrue(result.entries.all { it.status == EntryStatus.WON })
        assertTrue(result.entries.single().entrantId in setOf("entrant-2", "entrant-3", "entrant-4"))
    }

    @Test fun fillSpots_tellsEachPromotedEntrant() {
        val list = listOf(entry(2, EntryStatus.LOST), entry(3, EntryStatus.LOST))
        val result = LotteryDrawer.fillSpots(drawn(winners = 2, free = 2), list, now, Random(1))
        assertEquals(result.winnerIds, result.notifications.map { it.recipientId }.toSet())
        result.notifications.forEach {
            assertEquals(NotificationType.SPOT_OPENED, it.type)
            assertEquals("A spot opened at Two Jack Lakeside!", it.title)
            assertEquals(now, it.createdAt)
        }
    }

    @Test fun fillSpots_withEveryWinnerStillIn_promotesNobody() {
        val list = listOf(entry(1, EntryStatus.WON), entry(2, EntryStatus.WON), entry(3, EntryStatus.LOST))
        assertTrue(LotteryDrawer.fillSpots(drawn(winners = 2, free = 1), list, now).entries.isEmpty())
    }

    @Test fun fillSpots_withNobodyLeftToDraw_promotesNobody() {
        val list = listOf(entry(1, EntryStatus.WON), entry(2, EntryStatus.DECLINED))
        assertTrue(LotteryDrawer.fillSpots(drawn(winners = 2, free = 1), list, now).entries.isEmpty())
    }

    @Test fun fillSpots_neverPromotesPeopleStillWaitingOrWhoDeclined() {
        val list = listOf(entry(1, EntryStatus.WAITING), entry(2, EntryStatus.DECLINED), entry(3, EntryStatus.LOST))
        val result = LotteryDrawer.fillSpots(drawn(winners = 3, free = 3), list, now)
        assertEquals(setOf("entrant-3"), result.winnerIds)
    }

    // ---- Notices ----

    @Test fun rejectedNotice_givesTheReason() {
        val n = LotteryDrawer.rejectedNotice(TestData.lottery(), "entrant-1", "Site closed for repairs", now)
        assertEquals(NotificationType.REJECTED, n.type)
        assertEquals("entrant-1", n.recipientId)
        assertEquals("Reservation at Two Jack Lakeside rejected", n.title)
        assertTrue(n.body.contains("Site closed for repairs"))
        assertEquals("lot-1", n.lotteryId)
    }

    @Test fun rejectedNotice_withoutAReason_saysSo() {
        assertTrue(LotteryDrawer.rejectedNotice(TestData.lottery(), "entrant-1", "  ", now).body.contains("no reason given"))
    }
}
