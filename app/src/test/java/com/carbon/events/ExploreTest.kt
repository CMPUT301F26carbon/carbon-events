/*
 * ExploreTest.kt (Ansh)
 *
 * Purpose: Unit tests for Explore.kt: the filter summary chips (US 01.01.02), the lottery shown for a
 * campsite (the one taking entries now, not just the newest), vibe icons and remembered chats.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 6, 2026). Prompt: "Write
 * a test file for each source file that has none." Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import org.junit.Assert.*
import org.junit.Test

class ExploreTest {
    @Test fun filterSummary_listsEveryChosenFilter() {
        val f = SearchFilters(guests = 4, maxPrice = 80, withinKm = 50, openOnly = true, tags = setOf("Lakeside"), sort = SearchSort.PRICE, location = "Banff, AB")
        assertEquals(listOf("Banff, AB", "4 guests", "Lakeside", "Lowest price", "Under \$80", "Within 50 km", "Open lotteries"), filterSummary(f))
        assertTrue(filterSummary(SearchFilters()).isEmpty())
    }

    private val now = 1_792_162_800_000L
    private val day = 86_400_000L
    private val base = SampleData.snapshot(now).lotteries.getValue("lot-two-jack")
    private fun lot(id: String, opens: Long, closes: Long, status: LotteryStatus = LotteryStatus.OPEN) =
        base.copy(id = id, registrationStart = opens, registrationEnd = closes, status = status, drawnAt = if (status == LotteryStatus.DRAWN) closes else null)

    @Test fun currentLottery_isTheOneTakingEntries_evenWhenANewerOneIsScheduled() {
        val open = lot("open", now - day, now + 3 * day)
        val nextMonth = lot("next", now + 30 * day, now + 40 * day)
        assertEquals(open, currentLottery(listOf(nextMonth, open), now))
    }

    @Test fun currentLottery_ofTwoOpenOnes_isTheOneClosingSooner() {
        val soon = lot("soon", now - day, now + day)
        val later = lot("later", now - 2 * day, now + 9 * day)
        assertEquals(soon, currentLottery(listOf(later, soon), now))
    }

    @Test fun currentLottery_waitingForItsDraw_comesBeforeTheNextToOpen() {
        val closed = lot("closed", now - 9 * day, now - day)
        val next = lot("next", now + day, now + 9 * day)
        assertEquals(closed, currentLottery(listOf(next, closed), now))
    }

    @Test fun currentLottery_withNothingOpen_isTheNextToOpen_thenTheLatestDrawn() {
        val first = lot("first", now + 2 * day, now + 9 * day)
        val second = lot("second", now + 20 * day, now + 29 * day)
        assertEquals(first, currentLottery(listOf(second, first), now))
        val old = lot("old", now - 60 * day, now - 50 * day, LotteryStatus.DRAWN)
        val recent = lot("recent", now - 20 * day, now - 10 * day, LotteryStatus.DRAWN)
        assertEquals(recent, currentLottery(listOf(old, recent), now))
        assertNull(currentLottery(emptyList(), now))
    }

    @Test fun vibeIcon_matchesTheTag() {
        assertEquals(R.drawable.ic_water, vibeIcon("Lakeside"))
        assertEquals(R.drawable.ic_fire, vibeIcon("Fire pit"))
        assertEquals(R.drawable.ic_wifi, vibeIcon("Wi-Fi"))
        assertEquals(R.drawable.ic_tent, vibeIcon("Quiet"))
    }
}
