/*
 * BookingRulesTest.kt (Vaibhav)
 *
 * Purpose: Unit tests for BookingRules.kt: overlapping stays and booked nights, and editing a
 * published lottery (US 02.04.06).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write tests for every part of BookingRules.kt that isn't tested yet."
 * Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingRulesTest {
    private val now = TestData.NOW

    // ---- Filling spots that open up ----

    private fun drawn(winners: Int, free: Int) = TestData.lottery(winners = winners).copy(status = LotteryStatus.DRAWN, spotsFree = free)
    private fun entry(i: Int, status: EntryStatus) = WaitlistEntry("lot-1", "entrant-$i", "Entrant $i", now, status = status)

    // ---- Booked nights ----

    @Test fun stays_thatShareANight_overlap() {
        assertTrue(BookingPolicy.overlaps(10, 13, 12, 15))
        assertTrue("one inside the other", BookingPolicy.overlaps(10, 20, 12, 14))
    }

    @Test fun checkingOut_onTheDaySomeoneChecksIn_isNotAnOverlap() {
        assertFalse(BookingPolicy.overlaps(10, 13, 13, 15))
        assertFalse(BookingPolicy.overlaps(13, 15, 10, 13))
    }

    @Test fun takenNights_areEveryBookedNight_butNotCheckOutDays() {
        val a = entry(1, EntryStatus.WON).copy(checkInDay = 100, checkOutDay = 102)
        val b = entry(2, EntryStatus.WON).copy(checkInDay = 105, checkOutDay = 106)
        val notBooked = entry(3, EntryStatus.WON)
        val lost = entry(4, EntryStatus.LOST).copy(checkInDay = 110, checkOutDay = 112)
        assertEquals(setOf(100L, 101L, 105L), BookingPolicy.takenNights(listOf(a, b, notBooked, lost)))
    }

    @Test fun takenNights_withNoBookings_isEmpty() {
        assertTrue(BookingPolicy.takenNights(emptyList()).isEmpty())
    }

    // ---- Editing a published lottery ----

    private val open = TestData.lottery(start = now - TestData.DAY, end = now + 3 * TestData.DAY).copy(entrantCount = 4)

    @Test fun editing_aDrawnLottery_isRefused() {
        val drawn = open.copy(status = LotteryStatus.DRAWN)
        assertEquals("Winners have been drawn, so this lottery can no longer be changed.", LotteryEditPolicy.check(drawn, drawn.copy(winnerCount = 3), now))
    }

    @Test fun editing_canMoveTheCloseLater_andChangeWinners() {
        assertNull(LotteryEditPolicy.check(open, open.copy(registrationEnd = open.registrationEnd + TestData.DAY, winnerCount = 5), now))
    }

    @Test fun editing_cantMoveToAnotherCampsite() {
        assertEquals("A lottery can't be moved to another campsite.", LotteryEditPolicy.check(open, open.copy(campsiteId = "elsewhere"), now))
    }

    @Test fun editing_cantChangeTheStartOnceOpen() {
        assertEquals("Registration has already opened, so its start can't change.", LotteryEditPolicy.check(open, open.copy(registrationStart = now), now))
    }

    @Test fun editing_beforeItOpens_canMoveTheStart() {
        val later = TestData.lottery(start = now + TestData.DAY, end = now + 4 * TestData.DAY)
        assertNull(LotteryEditPolicy.check(later, later.copy(registrationStart = now + 2 * TestData.DAY), now))
    }

    @Test fun editing_theCloseMustStayInTheFuture_andAfterTheStart() {
        assertEquals("The closing time must be in the future.", LotteryEditPolicy.check(open, open.copy(registrationEnd = now), now))
        val later = TestData.lottery(start = now + 2 * TestData.DAY, end = now + 4 * TestData.DAY)
        assertEquals("Closing must be after opening.", LotteryEditPolicy.check(later, later.copy(registrationEnd = now + TestData.DAY), now))
    }

    @Test fun editing_needsAtLeastOneWinner() {
        assertEquals("Draw at least 1 winner.", LotteryEditPolicy.check(open, open.copy(winnerCount = 0), now))
    }

    @Test fun editing_cantCapBelowThePeopleAlreadyWaiting() {
        assertEquals("The cap can't be below the 4 people already on the waitlist.", LotteryEditPolicy.check(open, open.copy(waitlistCap = 3), now))
        assertNull(LotteryEditPolicy.check(open, open.copy(waitlistCap = 4), now))
    }
}
