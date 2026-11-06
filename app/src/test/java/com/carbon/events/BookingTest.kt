/*
 * BookingTest.kt (Suvir)
 *
 * Purpose: Unit tests for Booking.kt: stay price with GST, reservation codes, the campsite rules list and the CSV receipts (US 01.05.02, US 01.07.02).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 6, 2026). Prompt: "Write
 * a test file for each source file that has none." Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import org.junit.Assert.*
import org.junit.Test

class BookingTest {
    private val site = Campsite("two-jack", "owner", "Parks Lodge", "Two Jack Lakeside", "Banff, AB", maxOccupancy = 6, pricePerNight = 65)
    private fun reservation(status: EntryStatus = EntryStatus.WON, note: String = "", endedAt: Long? = null) =
        Reservation("two-jack/nov-2026", "two-jack", "Two Jack Lakeside", "owner", "maya-chen-x9k2", "Maya Chen",
            checkInDay = 20_400, checkOutDay = 20_403, bookedAt = 1_792_162_800_000L, guests = 4, status = status, note = note, endedAt = endedAt)

    @Test fun stayPrice_addsFivePercentGst() {
        val p = StayPrice(nights = 3, perNight = 65)
        assertEquals(195, p.subtotal)
        assertEquals(10, p.tax)
        assertEquals(205, p.total)
    }

    @Test fun reservationCode_isStableAndReadable() {
        val code = reservationCode("two-jack/nov-2026", "maya-chen-x9k2")
        assertTrue(code.matches(Regex("EV-NOV26-[A-Z2-9]{4}")))
        assertEquals(code, reservationCode("two-jack/nov-2026", "maya-chen-x9k2"))
        assertNotEquals(code, reservationCode("two-jack/nov-2026", "liam-okafor-a2b3"))
    }

    @Test fun rulesList_splitsSentencesAndAddsCampsiteLimits() {
        val rules = rulesList("Quiet hours 10 PM. Fires only in the pit!", site)
        assertEquals(listOf("Quiet hours 10 PM", "Fires only in the pit!", "Max 6 guests per site", "Check-in 3:00 PM, check-out 11:00 AM"), rules)
        assertEquals(listOf("Pets on a leash"), rulesList("Pets on a leash.", null))
    }

    @Test fun receipt_kindFollowsTheReservationStatus() {
        assertEquals(ReceiptCsv.Kind.RESERVATION, ReceiptCsv.kindOf(reservation()))
        assertEquals(ReceiptCsv.Kind.CANCELLATION, ReceiptCsv.kindOf(reservation(EntryStatus.CANCELLED)))
        assertEquals(ReceiptCsv.Kind.REJECTION, ReceiptCsv.kindOf(reservation(EntryStatus.REJECTED)))
    }

    @Test fun receiptCsv_listsTheStayAndTotalDue() {
        val csv = ReceiptCsv.build(reservation(), "EV-NOV26-AB3C", site).lines()
        assertEquals("Field,Value", csv.first())
        assertTrue("Reservation code,EV-NOV26-AB3C" in csv)
        assertTrue("Location,\"Banff, AB\"" in csv)
        assertTrue("Nights,3" in csv)
        assertTrue("Total due at check-in (CAD),205" in csv)
    }

    @Test fun rejectedReceipt_chargesNothingAndGivesTheReason() {
        val csv = ReceiptCsv.build(reservation(EntryStatus.REJECTED, "Fire warning", 1_792_200_000_000L), "EV-X", site).lines()
        assertTrue("Receipt,Reservation rejected by owner" in csv)
        assertTrue("Amount charged (CAD),0" in csv)
        assertTrue("Reason,Fire warning" in csv)
    }

    @Test fun csvCells_andFileNamesAreSafe() {
        assertEquals("plain", ReceiptCsv.cell("plain"))
        assertEquals("\"a, \"\"b\"\"\"", ReceiptCsv.cell("a, \"b\""))
        assertEquals("events-cancellation-EV-NOV26-AB3C.csv", ReceiptCsv.fileName(reservation(EntryStatus.CANCELLED), "EV-NOV26-AB3C"))
    }
}
