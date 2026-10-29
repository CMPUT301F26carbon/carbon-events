/*
 * TestData.kt
 *
 * Purpose: realistic test fixtures shared by the unit tests: a fixed "now", campsites,
 * lotteries and waiting-list entries modelled on real Banff-area campsites.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events


/** Shared fixtures for unit tests. */
object TestData {
    /** Oct 16, 2026 09:00 MDT. */
    const val NOW = 1_792_162_800_000L
    const val MIN = 60_000L
    const val DAY = 24 * 60 * MIN

    fun campsite(id: String = "two-jack", name: String = "Two Jack Lakeside", location: String = "Banff, AB", tags: List<String> = listOf("Lakeside"), owner: String = "owner-1") =
        Campsite(id, owner, "Parks Lodge", name, location, 51.23, -115.49, 6, "Fire pits, potable water", "Sites under Mount Rundle.", tags, 65)

    fun lottery(
        id: String = "lot-1",
        campsiteId: String = "two-jack",
        start: Long = NOW - DAY,
        end: Long = NOW + 3 * DAY,
        winners: Int = 2,
        cap: Int? = null,
        owner: String = "owner-1",
    ) = Lottery(id, campsiteId, "Two Jack Lakeside", owner, start, end, winners, cap)

    fun entries(lotteryId: String = "lot-1", count: Int = 5) =
        List(count) { WaitlistEntry(lotteryId, "entrant-$it", "Entrant $it", NOW - DAY + it * MIN) }
}
