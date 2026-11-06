/*
 * LocationTest.kt (Aditya)
 *
 * Purpose: Unit tests for Location.kt: distances, distance labels and nearest-first sorting.
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

class LocationTest {
    private val banff = GeoPoint(51.1784, -115.5708)
    private val canmore = GeoPoint(51.0884, -115.3479)
    private fun site(id: String, p: GeoPoint?) = Campsite(id, "o", "Owner", id, "AB", maxOccupancy = 4, latitude = p?.latitude, longitude = p?.longitude)

    @Test fun distance_banffToCanmoreIsAbout18Km() {
        assertEquals(18.0, distanceKm(banff, canmore), 1.5)
        assertEquals(0.0, distanceKm(banff, banff), 1e-9)
    }

    @Test fun distance_toCampsiteNeedsBothLocations() {
        assertNull(distanceKm(null, site("a", canmore)))
        assertNull(distanceKm(banff, site("a", null)))
        assertNotNull(distanceKm(banff, site("a", canmore)))
    }

    @Test fun formatKm_usesMetresThenKilometres() {
        assertEquals("850 m", formatKm(0.86))
        assertEquals("4.2 km", formatKm(4.24))
        assertEquals("1,240 km", formatKm(1240.4))
    }

    @Test fun nearestFirst_putsUnpinnedLast_andKeepsOrderWithoutLocation() {
        val far = site("far", GeoPoint(53.5461, -113.4938))
        val near = site("near", canmore)
        val none = site("none", null)
        assertEquals(listOf(near, far, none), nearestFirst(listOf(none, far, near), banff) { it })
        assertEquals(listOf(none, far, near), nearestFirst(listOf(none, far, near), null) { it })
    }
}
