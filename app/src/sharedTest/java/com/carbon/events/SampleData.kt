/*
 * SampleData.kt (Vaibhav)
 *
 * Purpose: realistic Banff-area campsites, lotteries, waiting lists and reservations used to
 * fill the in-memory store in tests and the screenshot tour. It is not part of the app.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

/** Builds the sample data set. */
object SampleData {
    /** Device ID of the sample owner, "Parks Lodge". */
    const val OWNER_ID = "owner-parks-lodge"

    private const val MINUTE = 60_000L
    private const val DAY = 24 * 60 * MINUTE

    private val entrantNames = listOf(
        "Maya Chen", "Liam Okafor", "Sofia Rossi", "Noah Singh", "Ethan Moreau", "Priya Patel", "Akshat Kumar", "Jordan Lee",
    )

    /** The sample owner profile. */
    fun owner(now: Long) = UserProfile(OWNER_ID, "Parks Lodge", "hello@parkslodge.ca", "+1 403 555 0199", createdAt = now - 90 * DAY)

    /** Sample campsites from the Figma mockups. */
    fun campsites(now: Long): List<Campsite> = listOf(
        Campsite("two-jack", OWNER_ID, "Parks Lodge", "Two Jack Lakeside", "Banff, AB", 51.2316, -115.4983, 6,
            "Fire pits, potable water, flush toilets, bear lockers, canoe launch",
            "Lakeside sites under Mount Rundle with fire pits, potable water and flush toilets. Canoe launch 2 min away.",
            listOf("Lakeside", "Fire pits", "Pet friendly"), 65, rating = 4.8, ratingCount = 126, createdAt = now - 60 * DAY),
        Campsite("tunnel-mountain", OWNER_ID, "Parks Lodge", "Tunnel Mountain Village", "Banff, AB", 51.1891, -115.5376, 8,
            "Showers, power hookups, Wi-Fi at the hub", "Forested sites five minutes from downtown Banff, with showers and hookups.",
            listOf("Mountain", "Wi-Fi", "Family friendly"), 42, rating = 4.6, ratingCount = 342, createdAt = now - 55 * DAY),
        Campsite("lake-louise", OWNER_ID, "Parks Lodge", "Lake Louise Tent Sites", "Lake Louise, AB", 51.4194, -116.1795, 4,
            "Electric-fenced tent area, food lockers, flush toilets", "Tent-only sites inside an electric fence along the Bow River.",
            listOf("Riverside", "Quiet hours"), 38, rating = 4.9, ratingCount = 211, createdAt = now - 50 * DAY),
        Campsite("johnston-canyon", OWNER_ID, "Parks Lodge", "Johnston Canyon", "Banff, AB", 51.2453, -115.8389, 6,
            "Firewood, potable water", "Steps from the canyon trail and its waterfalls.",
            listOf("Forest", "Fire pits"), 36, rating = 4.7, ratingCount = 98, createdAt = now - 40 * DAY),
        Campsite("moraine-lake", OWNER_ID, "Parks Lodge", "Moraine Lake Sites", "Lake Louise, AB", 51.3217, -116.1860, 4,
            "Vault toilets, bear lockers", "Small walk-in sites near the Valley of the Ten Peaks.",
            listOf("Lakeside", "Mountain"), 48, rating = 4.9, ratingCount = 64, createdAt = now - 30 * DAY),
        Campsite("waterfowl-lakes", OWNER_ID, "Parks Lodge", "Waterfowl Lakes", "Icefields Pkwy, AB", 51.8380, -116.6170, 6,
            "Kitchen shelter, potable water", "Glacier-fed lakes along the Icefields Parkway.",
            listOf("Lakeside", "Quiet hours"), 30, rating = 4.5, ratingCount = 51, createdAt = now - 20 * DAY),
    )

    /** Builds the complete starting snapshot for [now]. */
    fun snapshot(now: Long): EventsSnapshot {
        val sites = campsites(now).associateBy { it.id }
        val today = dayOf(now)
        val lotteries = listOf(
            Lottery("lot-two-jack", "two-jack", "Two Jack Lakeside", OWNER_ID, now - 2 * DAY, now + 3 * DAY, 8,
                bookingStartDay = today + 5, bookingEndDay = today + 19),
            Lottery("lot-lake-louise", "lake-louise", "Lake Louise Tent Sites", OWNER_ID, now - 4 * DAY, now + 2 * MINUTE, 3,
                bookingStartDay = today + 1, bookingEndDay = today + 15),
            Lottery("lot-moraine", "moraine-lake", "Moraine Lake Sites", OWNER_ID, now + 2 * DAY, now + 9 * DAY, 4,
                bookingStartDay = today + 10, bookingEndDay = today + 24),
            Lottery("lot-johnston", "johnston-canyon", "Johnston Canyon", OWNER_ID, now - 20 * DAY, now - 6 * DAY, 2,
                status = LotteryStatus.DRAWN, drawnAt = now - 6 * DAY, bookingStartDay = today - 5, bookingEndDay = today + 9),
        )
        val entries = buildList {
            entrantNames.take(6).forEachIndexed { i, n -> add(WaitlistEntry("lot-two-jack", "sample-entrant-$i", n, now - 2 * DAY + i * 3_600_000L)) }
            entrantNames.take(5).forEachIndexed { i, n -> add(WaitlistEntry("lot-lake-louise", "sample-entrant-$i", n, now - 3 * DAY + i * 3_600_000L)) }
            entrantNames.take(4).forEachIndexed { i, n ->
                val e = WaitlistEntry("lot-johnston", "sample-entrant-$i", n, now - 15 * DAY, if (i < 2) EntryStatus.WON else EntryStatus.LOST)
                // Both winners booked: one stay has ended (past), one is in progress (active).
                add(
                    when (i) {
                        0 -> e.copy(checkInDay = today - 5, checkOutDay = today - 2, bookedAt = now - 6 * DAY)
                        1 -> e.copy(checkInDay = today - 1, checkOutDay = today + 2, bookedAt = now - 5 * DAY)
                        else -> e
                    }.copy(campsiteId = "johnston-canyon", campsiteName = "Johnston Canyon", ownerId = OWNER_ID),
                )
            }
        }
        val counted = lotteries.associate { lot -> lot.id to lot.copy(entrantCount = entries.count { it.lotteryId == lot.id }) }
        return EventsSnapshot(
            profiles = mapOf(OWNER_ID to owner(now)),
            campsites = sites,
            lotteries = counted,
            entries = entries,
        )
    }
}
