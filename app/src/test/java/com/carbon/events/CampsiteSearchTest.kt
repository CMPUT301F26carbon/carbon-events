/*
 * CampsiteSearchTest.kt (Suvir, Vaibhav)
 *
 * Purpose: Unit tests for CampsiteSearch.kt: keyword search and what a campsite is searched by
 * (US 01.01.01).
 *
 * AI use: the tests added on November 20, 2026 were written with AI assistance (Claude Opus 5.5, Anthropic). Prompt: "Write tests for every part of CampsiteSearch.kt that isn't tested yet."
 * Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampsiteSearchTest {
    private val sites = listOf(
        TestData.campsite("two-jack", "Two Jack Lakeside", "Banff, AB", listOf("Lakeside")),
        TestData.campsite("tunnel", "Tunnel Mountain Village", "Banff, AB", listOf("Mountain")),
        TestData.campsite("louise", "Lake Louise Tent Sites", "Lake Louise, AB", listOf("Riverside")),
    )

    @Test fun blankQuery_returnsAllAlphabetically() =
        assertEquals(listOf("louise", "tunnel", "two-jack"), CampsiteSearch.search(sites, "  ").map { it.id })

    @Test fun matchesNameIgnoringCase() = assertEquals(listOf("tunnel"), CampsiteSearch.search(sites, "TUNNEL").map { it.id })

    @Test fun matchesLocationAndTags() {
        assertEquals(setOf("two-jack", "tunnel"), CampsiteSearch.search(sites, "banff").map { it.id }.toSet())
        assertEquals(listOf("tunnel"), CampsiteSearch.search(sites, "mountain").map { it.id })
    }

    @Test fun everyWordMustMatch() = assertEquals(listOf("two-jack"), CampsiteSearch.search(sites, "banff lakeside").map { it.id })

    @Test fun nameMatchesRankFirst() {
        // "lake" is in Lake Louise's name but only in Two Jack's tag/name word "Lakeside".
        val ids = CampsiteSearch.search(sites, "lake").map { it.id }
        assertEquals("louise", ids.first())
    }

    @Test fun noMatchReturnsEmpty() = assertTrue(CampsiteSearch.search(sites, "jasper").isEmpty())

    // ---- Search ----

    @Test fun searchableText_coversNameLocationAmenitiesDescriptionAndTags_inLowerCase() {
        val text = CampsiteSearch.searchableText(TestData.campsite(tags = listOf("Lakeside", "Quiet")))
        listOf("two jack lakeside", "banff, ab", "fire pits", "mount rundle", "quiet").forEach { assertTrue(it, text.contains(it)) }
        assertEquals(text.lowercase(), text)
    }
}
