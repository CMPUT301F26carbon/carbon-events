/*
 * ReceiptsTest.kt (Suvir)
 *
 * Purpose: Unit tests for Receipts.kt: reservation codes and the CSV receipts guests and owners
 * download (US 01.04.01, US 02.07.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write tests for reservation codes and CSV receipts."
 * Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptsTest {
    private val site = TestData.campsite().copy(checkInTime = "3:00 PM", checkOutTime = "11:00 AM")
    private fun stay(status: EntryStatus = EntryStatus.WON, note: String = "", endedAt: Long? = null) =
        Reservation("two-jack/lot-two-jack-26", "two-jack", "Two Jack Lakeside", "owner-1", "e1", "Maya Chen", 20_400, 20_403, TestData.NOW, 2, status, note).let {
            if (endedAt == null) it else it.copy(endedAt = endedAt)
        }

    @Test fun reservationCode_isStable_andDiffersByGuest() {
        val a = reservationCode("two-jack/lot-two-jack-26", "e1")
        assertEquals(a, reservationCode("two-jack/lot-two-jack-26", "e1"))
        assertNotEquals(a, reservationCode("two-jack/lot-two-jack-26", "e2"))
        assertTrue(a, Regex("^EV-[A-Z0-9]+-[A-Z2-9]{4}$").matches(a))
    }

    @Test fun reservationReceipt_showsTheTotalDueAtCheckIn() {
        val csv = ReceiptCsv.build(stay(), "EV-TWO26-AB3C", site)
        val rows = csv.trim().split("\n").associate { it.substringBefore(',') to it.substringAfter(',') }
        assertEquals("Reservation confirmation", rows["Receipt"])
        assertEquals(java.time.LocalDate.ofEpochDay(20_400).toString() + " 3:00 PM", rows["Check-in"])
        assertEquals(java.time.LocalDate.ofEpochDay(20_403).toString() + " 11:00 AM", rows["Check-out"])
        assertEquals("3", rows["Nights"])
        assertEquals("195", rows["Subtotal (CAD)"])
        assertEquals("10", rows["GST 5% (CAD)"])
        assertEquals("205", rows["Total due at check-in (CAD)"])
        assertFalse(csv.contains("Reason"))
    }

    @Test fun cancelledAndRejectedReceipts_chargeNothing_andSayWhen() {
        val cancelled = ReceiptCsv.build(stay(EntryStatus.CANCELLED, endedAt = TestData.NOW + 1), "C", site)
        assertTrue(cancelled.contains("Cancellation confirmation"))
        assertTrue(cancelled.contains("Amount charged (CAD),0"))
        assertTrue(cancelled.contains("Cancelled at,"))
        val rejected = ReceiptCsv.build(stay(EntryStatus.REJECTED, note = "", endedAt = TestData.NOW + 1), "R", site)
        assertTrue(rejected.contains("Reservation rejected by owner"))
        assertTrue(rejected.contains("Reason,No reason given"))
    }

    @Test fun cells_withCommasOrQuotes_areQuoted() {
        assertEquals("plain", ReceiptCsv.cell("plain"))
        assertEquals("\"Banff, AB\"", ReceiptCsv.cell("Banff, AB"))
        assertEquals("\"say \"\"hi\"\"\"", ReceiptCsv.cell("say \"hi\""))
    }

    @Test fun fileName_saysWhatKind_andIsSafe() {
        assertEquals("events-reservation-EV-TWO26-AB3C.csv", ReceiptCsv.fileName(stay(), "EV-TWO26-AB3C"))
        assertEquals("events-cancellation-EVX.csv", ReceiptCsv.fileName(stay(EntryStatus.CANCELLED), "EV/X"))
    }
}
