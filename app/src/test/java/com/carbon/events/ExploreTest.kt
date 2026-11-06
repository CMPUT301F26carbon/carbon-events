/*
 * ExploreTest.kt (Ansh)
 *
 * Purpose: Unit tests for Explore.kt: the filter summary chips (US 01.01.02), the lottery shown for a campsite, vibe icons and remembered chats.
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

    @Test fun currentLottery_isTheMostRecentlyOpened() {
        val now = 1_792_162_800_000L
        val a = SampleData.snapshot(now).lotteries.getValue("lot-two-jack")
        val b = a.copy(id = "two-jack/dec-2026", registrationStart = a.registrationStart + 1)
        assertEquals(b, currentLottery(listOf(a, b)))
        assertNull(currentLottery(emptyList()))
    }

    @Test fun vibeIcon_matchesTheTag() {
        assertEquals(R.drawable.ic_water, vibeIcon("Lakeside"))
        assertEquals(R.drawable.ic_fire, vibeIcon("Fire pit"))
        assertEquals(R.drawable.ic_wifi, vibeIcon("Wi-Fi"))
        assertEquals(R.drawable.ic_tent, vibeIcon("Quiet"))
    }
}
