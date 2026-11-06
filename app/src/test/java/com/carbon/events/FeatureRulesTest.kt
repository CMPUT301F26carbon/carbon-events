/*
 * FeatureRulesTest.kt (Suvir)
 */
package com.carbon.events

import org.junit.Assert.*
import org.junit.Test

/** Regression tests for search, preferences, prices, social models and promo codes. */
class FeatureRulesTest {
    private val site = TestData.campsite()

    @Test fun optOut_suppressesOrdinaryAlertsButKeepsModerationWarnings() {
        val prefs = NotificationPrefs(optOut = true)
        NotificationType.entries.forEach { type -> assertEquals(type == NotificationType.WARNING, prefs.allows(type)) }
    }

    @Test fun preferences_roundTripEveryStoredSwitch() {
        val prefs = NotificationPrefs(false, false, false, true, false, false, false, false, true)
        assertEquals(prefs, NotificationPrefs.from(prefs.toMap()::get))
    }

    @Test fun oldProfiles_receiveDefaultsForMissingPreferenceKeys() =
        assertEquals(NotificationPrefs(), NotificationPrefs.from { null })

    @Test fun reservationAlerts_areIndependentOfLotteryResults() {
        val prefs = NotificationPrefs(results = false, reservations = true)
        assertFalse(prefs.allows(NotificationType.LOTTERY_WON))
        assertTrue(prefs.allows(NotificationType.REJECTED))
    }

    @Test fun cancellationDeadline_usesTheSelectedPolicy() {
        assertEquals(99L, CancellationPolicy.FLEXIBLE.freeUntil(100))
        assertEquals(95L, CancellationPolicy.MODERATE.freeUntil(100))
        assertEquals(86L, CancellationPolicy.STRICT.freeUntil(100))
    }

    @Test fun storedCancellationPolicy_isCaseInsensitiveAndHasLegacyDefault() {
        assertEquals(CancellationPolicy.STRICT, CancellationPolicy.of("strict"))
        assertEquals(CancellationPolicy.FLEXIBLE, CancellationPolicy.of(null))
        assertEquals(CancellationPolicy.FLEXIBLE, CancellationPolicy.of("unknown"))
    }

    @Test fun combinedFilters_requireLocationGuestsAndAllAmenities() {
        val other = site.copy(id = "jasper", location = "Jasper, AB", maxOccupancy = 2)
        val filters = SearchFilters(location = "BANFF", guests = 4, amenities = setOf("Water", "Fire pit"))
        assertEquals(listOf(site), CampsiteFilter.apply(listOf(other, site), filters, { null }, null, TestData.NOW))
    }

    @Test fun wifiFilter_acceptsAlternativeSpelling() {
        val wifi = site.copy(amenities = "WiFi, parking")
        assertEquals(listOf(wifi), CampsiteFilter.apply(listOf(wifi), SearchFilters(amenities = setOf("Wi-Fi")), { null }, null, TestData.NOW))
    }

    @Test fun bookingDateFilter_excludesMissingAndInsufficientBookingPeriods() {
        val filters = SearchFilters(stayFrom = 100, stayUntil = 105)
        assertTrue(CampsiteFilter.apply(listOf(site), filters, { null }, null, TestData.NOW).isEmpty())
        val lot = TestData.lottery().copy(bookingStartDay = 100, bookingEndDay = 104)
        assertTrue(CampsiteFilter.apply(listOf(site), filters, { lot }, null, TestData.NOW).isEmpty())
        assertEquals(listOf(site), CampsiteFilter.apply(listOf(site), filters, { lot.copy(bookingEndDay = 105) }, null, TestData.NOW))
    }

    @Test fun priceSort_ordersCheaperSitesFirst() {
        val cheap = site.copy(id = "cheap", pricePerNight = 10)
        assertEquals(listOf("cheap", site.id), CampsiteFilter.apply(listOf(site, cheap), SearchFilters(sort = SearchSort.PRICE), { null }, null, TestData.NOW).map { it.id })
    }

    @Test fun distanceFilter_excludesUnmappedSitesWhenLocationIsKnown() {
        val unmapped = site.copy(id = "unknown", latitude = null, longitude = null)
        assertEquals(listOf(site), CampsiteFilter.apply(listOf(unmapped, site), SearchFilters(withinKm = 1), { null }, GeoPoint(site.latitude!!, site.longitude!!), TestData.NOW))
    }

    @Test fun distance_samePointIsZeroAndOrderIsSymmetric() {
        val a = GeoPoint(53.5461, -113.4938)
        val b = GeoPoint(51.1784, -115.5708)
        assertEquals(0.0, distanceKm(a, a), 0.000001)
        assertEquals(distanceKm(a, b), distanceKm(b, a), 0.000001)
        assertTrue(distanceKm(a, b) in 295.0..305.0)
    }

    @Test fun nearestSort_keepsInputOrderWithoutDeviceLocation() {
        val sites = listOf(site.copy(id = "b"), site.copy(id = "a"))
        assertEquals(sites, nearestFirst(sites, null) { it })
    }

    @Test fun stayPrice_includesRoundedTaxAndZeroCostStays() {
        val price = StayPrice(3, 65)
        assertEquals(195L, price.subtotal)
        assertEquals(10L, price.tax)
        assertEquals(205L, price.total)
        assertEquals(0L, StayPrice(4, 0).total)
    }

    @Test fun audienceSelection_doesNotTreatDeclinedEntrantsAsWinners() {
        val entry = TestData.entries(count = 1).single()
        assertTrue(Audience.WAITING.includes(entry))
        assertFalse(Audience.WINNERS.includes(entry.copy(status = EntryStatus.DECLINED)))
        assertTrue(Audience.NOT_SELECTED.includes(entry.copy(status = EntryStatus.LOST)))
    }

    @Test fun chatUnreadState_isParticipantSpecific() {
        // Unread is now a count per person (stage 5); 1 unread for the owner, none for the entrant.
        val chat = Chat("s--e", "s", "Site", "o", "Owner", "e", "Entrant", unreadForOwner = 1)
        assertTrue(chat.unreadFor("o"))
        assertFalse(chat.unreadFor("e"))
        assertFalse(chat.unreadFor("outsider"))
        assertEquals("Entrant", chat.otherName("o"))
    }

    @Test fun qrParser_rejectsForeignHostsAndNestedPaths() {
        listOf("https://example.com/site", "carbon-events://other/site", "carbon-events://campsite/a/b").forEach { assertNull(QrCodes.parseCampsiteId(it)) }
    }

    /** Red/green regression: generated QR payloads must be accepted by the scanner parser. */
    @Test fun qrGenerator_rejectsIdsThatTheScannerCannotOpen() {
        listOf("", "../site", "a/b", "has space", "x".repeat(65)).forEach { id ->
            assertThrows(IllegalArgumentException::class.java) { QrCodes.payloadFor(id) }
        }
    }
}
