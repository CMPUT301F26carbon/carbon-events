/*
 * QrCodesTest.kt (Suvir, Vaibhav)
 *
 * Purpose: Unit tests for QrCodes.kt: the text in a campsite's promo QR code, and reading it back
 * from a scan or typed in (US 02.02.01, US 01.12.01).
 *
 * AI use: the tests added on November 20, 2026 were written with AI assistance (Claude Opus 5.5, Anthropic). Prompt: "Write tests for the promo QR code text."
 * Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class QrCodesTest {
    @Test fun payload_roundTrips() = assertEquals("two-jack", QrCodes.parseCampsiteId(QrCodes.payloadFor("two-jack")))
    @Test fun acceptsBareCodeForManualEntry() = assertEquals("site1001", QrCodes.parseCampsiteId("  site1001 "))
    @Test fun rejectsOtherLinks() = assertNull(QrCodes.parseCampsiteId("https://example.com/campsite"))
    @Test fun rejectsEmpty() = assertNull(QrCodes.parseCampsiteId("carbon-events://campsite/"))

    @Test fun payload_isTheAppLink() {
        assertEquals("carbon-events://campsite/two-jack", QrCodes.payloadFor("two-jack"))
    }

    @Test fun payload_refusesIdsThatAreNotPlain() {
        assertThrows(IllegalArgumentException::class.java) { QrCodes.payloadFor("../etc") }
        assertThrows(IllegalArgumentException::class.java) { QrCodes.payloadFor("two jack") }
        assertThrows(IllegalArgumentException::class.java) { QrCodes.payloadFor("x".repeat(65)) }
    }

    @Test fun reading_aScannedLink_orATypedId() {
        assertEquals("two-jack", QrCodes.parseCampsiteId("carbon-events://campsite/two-jack"))
        assertEquals("two-jack", QrCodes.parseCampsiteId("  CARBON-EVENTS://campsite/two-jack/ "))
        assertEquals("two-jack", QrCodes.parseCampsiteId("two-jack"))
    }

    @Test fun reading_somethingElse_givesNothing() {
        assertNull(QrCodes.parseCampsiteId("https://example.com/two-jack"))
        assertNull(QrCodes.parseCampsiteId(""))
        assertNull(QrCodes.parseCampsiteId("carbon-events://campsite/"))
    }

    @Test fun everyPayload_readsBackToItsId() {
        listOf("a", "two-jack", "lake_louise-2", "X".repeat(64)).forEach { assertEquals(it, QrCodes.parseCampsiteId(QrCodes.payloadFor(it))) }
    }
}
