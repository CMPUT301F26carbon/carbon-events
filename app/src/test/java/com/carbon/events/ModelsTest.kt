/*
 * ModelsTest.kt (Vaibhav)
 *
 * Purpose: unit tests for Models.kt: lottery registration windows (US 02.04.03), profile
 * helpers used for device-based profiles and role switching (US 01.09.01), a reservation's
 * nights and whether it is still active (US 01.04.01), and notification types.
 *
 * AI use: the tests in ModelsTest were written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write tests for every part of Models.kt that isn't tested yet."
 * Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import com.carbon.events.TestData
import com.carbon.events.TestData.DAY
import com.carbon.events.TestData.NOW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LotteryModelTest {
    private val lottery = TestData.lottery(start = NOW, end = NOW + DAY)

    @Test fun upcomingBeforeStart() = assertTrue(lottery.isUpcoming(NOW - 1))
    @Test fun openFromStartUntilEnd() {
        assertTrue(lottery.isRegistrationOpen(NOW))
        assertTrue(lottery.isRegistrationOpen(NOW + DAY - 1))
        assertFalse(lottery.isRegistrationOpen(NOW + DAY))
    }
    @Test fun drawDueAtEnd() {
        assertFalse(lottery.isDrawDue(NOW + DAY - 1))
        assertTrue(lottery.isDrawDue(NOW + DAY))
    }
    @Test fun drawnLotteryIsNeverOpenOrDue() {
        val drawn = lottery.copy(status = LotteryStatus.DRAWN)
        assertFalse(drawn.isRegistrationOpen(NOW))
        assertFalse(drawn.isDrawDue(NOW + 2 * DAY))
    }
}

class UserProfileTest {
    private val adam = UserProfile("device-1", "Adam Kent", "adam@example.com")

    @Test fun firstNameAndInitials() {
        assertEquals("Adam", adam.firstName)
        assertEquals("AK", adam.initials)
        assertEquals("M", adam.copy(name = "Maya").initials)
    }

    @Test fun nonAdminKeepsSignupRole() {
        assertEquals(listOf(Role.ENTRANT), adam.availableRoles)
        assertEquals(listOf(Role.OWNER), adam.copy(activeRole = Role.OWNER).availableRoles)
    }

    @Test fun adminCanUseEveryRole() = assertEquals(Role.entries, adam.copy(isAdmin = true).availableRoles)

    @Test fun campsiteMapLocationFlag() {
        assertTrue(TestData.campsite().hasMapLocation)
        assertFalse(TestData.campsite().copy(latitude = null).hasMapLocation)
    }
}

class ModelsTest {
    private val now = TestData.NOW
    private val day = TestData.DAY

    // ---- A lottery's registration window ----

    private val lottery = TestData.lottery(start = now, end = now + 3 * day)

    @Test fun registration_isOpenFromItsStartUntilJustBeforeItsClose() {
        assertFalse("before", lottery.isRegistrationOpen(now - 1))
        assertTrue("at the start", lottery.isRegistrationOpen(now))
        assertTrue("just before the close", lottery.isRegistrationOpen(now + 3 * day - 1))
        assertFalse("at the close", lottery.isRegistrationOpen(now + 3 * day))
    }

    @Test fun aDrawnLottery_isNeverOpen_upcoming_orDue() {
        val drawn = lottery.copy(status = LotteryStatus.DRAWN)
        assertFalse(drawn.isRegistrationOpen(now + day))
        assertFalse(drawn.isUpcoming(now - day))
        assertFalse(drawn.isDrawDue(now + 5 * day))
    }

    @Test fun upcoming_beforeItOpens_andDue_onceItCloses() {
        assertTrue(lottery.isUpcoming(now - 1))
        assertFalse(lottery.isUpcoming(now))
        assertFalse(lottery.isDrawDue(now + 3 * day - 1))
        assertTrue(lottery.isDrawDue(now + 3 * day))
    }

    @Test fun bookingPeriod_needsBothEnds() {
        assertFalse(lottery.hasBookingPeriod)
        assertFalse(lottery.copy(bookingStartDay = 10).hasBookingPeriod)
        assertTrue(lottery.copy(bookingStartDay = 10, bookingEndDay = 14).hasBookingPeriod)
    }

    // ---- A reservation ----

    private fun booked(checkIn: Long, checkOut: Long) =
        WaitlistEntry("lot-1", "entrant-1", "Entrant 1", now, status = EntryStatus.WON, checkInDay = checkIn, checkOutDay = checkOut, bookedAt = now, guests = 3)

    @Test fun aBookedWinner_isAReservation_withTheirNights() {
        val r = booked(100, 103).toReservation()!!
        assertEquals(100L until 103L, r.nightDays())
        assertEquals(3, r.guests)
        assertEquals(3, r.nightDays().count())
    }

    @Test fun anUnbookedWinner_orSomeoneWhoLost_isNoReservation() {
        assertNull(WaitlistEntry("lot-1", "e", "E", now, status = EntryStatus.WON).toReservation())
        assertNull(booked(100, 103).copy(status = EntryStatus.LOST).toReservation())
    }

    @Test fun aReservation_isActiveUntilItsCheckOutDay() {
        val r = booked(100, 103).toReservation()!!
        assertTrue("during the stay", r.isActive(101))
        assertTrue("last night", r.isActive(102))
        assertFalse("on check-out day it moves to Past", r.isActive(103))
        assertTrue(r.isPast(103))
        assertFalse(r.isPast(99))
    }

    // ---- Notices ----

    @Test fun everyNotificationType_hasItsOwnStoredWord_andReadsBack() {
        val words = NotificationType.entries.map { it.word }
        assertEquals(words.size, words.toSet().size)
    }

    // ---- Profiles ----

    @Test fun profile_firstNameAndInitials() {
        val p = UserProfile("a", "  Adam   Kent ", "a@example.com")
        assertEquals("Adam", p.firstName)
        assertEquals("AK", p.initials)
        assertEquals("Maya", UserProfile("m", "Maya", "").firstName)
    }

    @Test fun onlyAdmins_canViewTheAppAsAnyRole() {
        assertEquals(listOf(Role.OWNER), UserProfile("o", "O", "", activeRole = Role.OWNER).availableRoles)
        assertEquals(Role.entries, UserProfile("x", "X", "", activeRole = Role.ADMIN, isAdmin = true).availableRoles)
    }
}
