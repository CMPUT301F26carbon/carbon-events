/*
 * LotteryDrawerTest.kt
 *
 * Purpose: unit tests for the automatic draw logic (US 02.05.01) and the win / loss
 * notifications it creates (US 01.03.01, US 01.03.02).
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import com.carbon.events.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LotteryDrawerTest {

    @Test
    fun pickWinners_drawsExactlyWinnerCount() {
        val winners = LotteryDrawer.pickWinners(TestData.entries(count = 10), 3, Random(1))
        assertEquals(3, winners.size)
    }

    @Test
    fun pickWinners_everyoneWinsWhenFewerEntrantsThanSpots() {
        val winners = LotteryDrawer.pickWinners(TestData.entries(count = 2), 8, Random(1))
        assertEquals(setOf("entrant-0", "entrant-1"), winners)
    }

    @Test
    fun pickWinners_emptyWaitlistHasNoWinners() {
        assertTrue(LotteryDrawer.pickWinners(emptyList(), 3).isEmpty())
    }

    @Test
    fun pickWinners_isFairAcrossManyDraws() {
        // Each of 4 entrants should win roughly half of 4,000 draws of 2 winners.
        val counts = IntArray(4)
        val rnd = Random(42)
        repeat(4_000) { LotteryDrawer.pickWinners(TestData.entries(count = 4), 2, rnd).forEach { counts[it.last().digitToInt()]++ } }
        counts.forEach { assertTrue("count $it should be near 2000", it in 1_850..2_150) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun pickWinners_rejectsNegativeCount() {
        LotteryDrawer.pickWinners(TestData.entries(), -1)
    }

    @Test
    fun draw_marksEveryEntryWonOrLost() {
        val result = LotteryDrawer.draw(TestData.lottery(winners = 2), TestData.entries(count = 5), TestData.NOW, Random(3))
        assertEquals(2, result.entries.count { it.status == EntryStatus.WON })
        assertEquals(3, result.entries.count { it.status == EntryStatus.LOST })
        assertTrue(result.entries.none { it.status == EntryStatus.WAITING })
    }

    @Test
    fun draw_sendsOneMatchingNotificationPerEntrant() {
        val lottery = TestData.lottery(winners = 1)
        val result = LotteryDrawer.draw(lottery, TestData.entries(count = 3), TestData.NOW, Random(5))
        assertEquals(3, result.notifications.size)
        for (n in result.notifications) {
            val won = n.recipientId in result.winnerIds
            assertEquals(if (won) NotificationType.LOTTERY_WON else NotificationType.LOTTERY_LOST, n.type)
            assertEquals(lottery.campsiteId, n.campsiteId)
            assertEquals(TestData.NOW, n.createdAt)
        }
        assertTrue(result.notifications.single { it.type == NotificationType.LOTTERY_WON }.title.contains("You won"))
    }
}
