/*
 * CampsiteSearch.kt (Vaibhav)
 *
 * Searching, filtering and sorting campsites.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

/** Searches campsites by keyword. */
object CampsiteSearch {

    /**
     * Returns the campsites matching [query].
     *
     * A campsite matches when every word in [query] appears somewhere in its searchable
     * text. A blank query returns all campsites. Results are ordered with name matches
     * first, then alphabetically.
     *
     * @param campsites campsites to search.
     * @param query what the user typed.
     * @return matching campsites, best matches first.
     */
    fun search(campsites: List<Campsite>, query: String): List<Campsite> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return campsites.sortedBy { it.name.lowercase() }
        return campsites
            .filter { site -> val text = searchableText(site); words.all { it in text } }
            .sortedWith(compareByDescending<Campsite> { site -> words.all { it in site.name.lowercase() } }
                .thenBy { it.name.lowercase() })
    }

    /** Everything about a campsite that search looks at, in lower case. */
    fun searchableText(site: Campsite): String = listOf(
        site.name, site.location, site.amenities, site.description, site.tags.joinToString(" "),
    ).joinToString(" ").lowercase()
}

/** How search results are ordered. */
enum class SearchSort(val label: String) { BEST("Best match"), NEAREST("Nearest"), PRICE("Lowest price"), RATING("Top rated") }

/**
 * Search filters (Figma E-FILTERS). Every null / empty value means "any".
 *
 * @property guests at least this many guests must fit.
 * @property maxPrice highest price per night.
 * @property stayFrom with [stayUntil], the nights the camper wants; matches lotteries whose booking period covers them.
 * @property withinKm only campsites within this distance of the phone (needs its location).
 * @property openOnly only campsites whose lottery is open (or opening soon).
 * @property tags the campsite must have every one of these tags (Figma "Vibe").
 * @property location the campsite's place must contain this (Figma "Location"), or null.
 * @property sort ordering applied after every enabled filter is satisfied.
 * @property amenities each must appear in the campsite's amenities or tags (Figma "Amenities").
 */
data class SearchFilters(
    val guests: Int? = null,
    val maxPrice: Int? = null,
    val stayFrom: Long? = null,
    val stayUntil: Long? = null,
    val withinKm: Int? = null,
    val openOnly: Boolean = false,
    val tags: Set<String> = emptySet(),
    val sort: SearchSort = SearchSort.BEST,
    val location: String? = null,
    val amenities: Set<String> = emptySet(),
) {
    /** How many filters are switched on (for the Filters button badge). */
    val count: Int get() = listOfNotNull(guests, maxPrice, stayFrom, withinKm, location).size + (if (openOnly) 1 else 0) + tags.size + amenities.size
}

/** The Figma E-FILTERS amenity chips and the words that match them in a campsite's amenities. */
val AMENITY_FILTERS: Map<String, List<String>> = linkedMapOf(
    "Fire pit" to listOf("fire"),
    "Water" to listOf("water"),
    "Wi-Fi" to listOf("wi-fi", "wifi"),
    "Parking" to listOf("parking"),
)

/** Applies [SearchFilters] to keyword results. */
object CampsiteFilter {
    /**
     * @param results keyword matches, best first.
     * @param lotteryOf each campsite's current lottery, if any.
     * @param here the phone's location, or null.
     * @param now current time (epoch ms).
     */
    fun apply(results: List<Campsite>, filters: SearchFilters, lotteryOf: (String) -> Lottery?, here: GeoPoint?, now: Long): List<Campsite> {
        val kept = results.filter { site ->
            val lot = lotteryOf(site.id)
            (filters.guests == null || site.maxOccupancy >= filters.guests) &&
                (filters.maxPrice == null || site.pricePerNight <= filters.maxPrice) &&
                (filters.tags.isEmpty() || filters.tags.all { t -> site.tags.any { it.equals(t, true) } }) &&
                (!filters.openOnly || (lot != null && (lot.isRegistrationOpen(now) || lot.isUpcoming(now)))) &&
                (filters.stayFrom == null || filters.stayUntil == null || (lot != null && lot.hasBookingPeriod &&
                    lot.bookingStartDay!! <= filters.stayFrom && lot.bookingEndDay!! >= filters.stayUntil)) &&
                (filters.withinKm == null || here == null || (distanceKm(here, site)?.let { it <= filters.withinKm } ?: false)) &&
                (filters.location == null || site.location.contains(filters.location, ignoreCase = true)) &&
                filters.amenities.all { a ->
                    val text = (site.amenities + " " + site.tags.joinToString(" ")).lowercase()
                    (AMENITY_FILTERS[a] ?: listOf(a.lowercase())).any { it in text }
                }
        }
        return when (filters.sort) {
            SearchSort.BEST -> kept
            SearchSort.NEAREST -> nearestFirst(kept, here) { it }
            SearchSort.PRICE -> kept.sortedBy { it.pricePerNight }
            SearchSort.RATING -> kept.sortedWith(compareByDescending<Campsite> { it.rating }.thenByDescending { it.ratingCount })
        }
    }
}
