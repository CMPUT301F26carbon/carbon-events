/*
 * DataTest.kt (Vaibhav)
 *
 * Purpose: Unit tests for the parts of Data.kt that no other test calls directly: a lottery's
 * registration window, a reservation's nights and whether it is still active (US 01.04.01), the
 * record left when an admin removes a profile (US 03.02.01), the notices for a new lottery, an
 * owner's message and an admin warning, the lottery lists the app reads, refilling spots that
 * opened up (US 02.05.03), the admin's list of profiles (US 03.05.01) and acting as a person.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write
 * tests for every part of Data.kt that isn't tested yet." Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DataTest {
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

    // ---- When a profile goes ----

    @Test fun removedRecord_keepsTheNameAndLogin_butDropsContactDetails() {
        val adam = UserProfile("adam-kent", "Adam Kent", "adam@example.com", phone = "+1 780 555 0142", authUid = "uid-1", photoBase64 = "abc")
        val record = adam.removedRecord()
        assertTrue(record.removed)
        assertTrue(record.blocked)
        assertEquals("", record.phone)
        assertNull(record.photoBase64)
        assertEquals("Adam Kent", record.name)
        assertEquals("uid-1", record.authUid)
    }

    @Test fun deletingAProfile_givesBackSpotsNotYetStayedIn() {
        val today = now / day
        assertTrue("won, not booked", WaitlistEntry("l", "e", "E", now, status = EntryStatus.WON).givesSpotBackOnDelete(now))
        assertTrue("stay still to come", booked(today + 2, today + 4).givesSpotBackOnDelete(now))
        assertTrue("checks out today", booked(today - 2, today).givesSpotBackOnDelete(now))
        assertFalse("stay already over", booked(today - 5, today - 2).givesSpotBackOnDelete(now))
        assertFalse("lost", WaitlistEntry("l", "e", "E", now, status = EntryStatus.LOST).givesSpotBackOnDelete(now))
    }

    // ---- Notices ----

    @Test fun openedNotice_tellsFollowersAboutTheNewLottery() {
        val n = openedNotice(lottery, "follower-1", now)
        assertEquals(NotificationType.OPENED, n.type)
        assertEquals("follower-1", n.recipientId)
        assertEquals("New lottery at Two Jack Lakeside", n.title)
        assertTrue(n.body.startsWith("Registration is open "))
        assertEquals(lottery.id, n.lotteryId)
        assertEquals(lottery.campsiteId, n.campsiteId)
    }

    @Test fun announcementNotice_isAMessageFromTheCampsite() {
        val n = announcementNotice(lottery, "entrant-1", "Gates open at 2 pm.", now)
        assertEquals(NotificationType.MESSAGE, n.type)
        assertEquals("Message from Two Jack Lakeside", n.title)
        assertEquals("Gates open at 2 pm.", n.body)
    }

    @Test fun warningNotice_isFromTheEventsTeam_andNotAboutALottery() {
        val n = warningNotice("entrant-1", "Please keep reviews respectful.", now)
        assertEquals(NotificationType.WARNING, n.type)
        assertEquals("Warning from the Events team", n.title)
        assertEquals("", n.lotteryId)
        assertEquals("", n.campsiteId)
    }

    @Test fun everyNotificationType_hasItsOwnStoredWord_andReadsBack() {
        val words = NotificationType.entries.map { it.word }
        assertEquals(words.size, words.toSet().size)
    }

    // ---- The lottery lists the app reads ----

    private fun storeWithLotteries(): InMemoryEventsStore {
        val a = TestData.lottery(id = "a", start = now - 3 * day, end = now - day).copy(status = LotteryStatus.DRAWN)
        val b = TestData.lottery(id = "b", start = now - day, end = now + day)
        val c = TestData.lottery(id = "c", campsiteId = "johnston", start = now, end = now + 2 * day, owner = "owner-2")
        return InMemoryEventsStore(EventsSnapshot(lotteries = listOf(a, b, c).associateBy { it.id }))
    }

    @Test fun openLotteries_areTheOnesNotDrawnYet() = runTest {
        assertEquals(setOf("b", "c"), storeWithLotteries().observeOpenLotteries().first().map { it.id }.toSet())
    }

    @Test fun lotteriesForACampsite_newestFirst() = runTest {
        assertEquals(listOf("b", "a"), storeWithLotteries().observeLotteriesForCampsite("two-jack").first().map { it.id })
    }

    @Test fun lotteriesByOwner_onlyTheirs() = runTest {
        val store = storeWithLotteries()
        assertEquals(setOf("a", "b"), store.observeLotteriesByOwner("owner-1").first().map { it.id }.toSet())
        assertEquals(listOf("c"), store.observeLotteriesByOwner("owner-2").first().map { it.id })
    }

    // ---- Refilling spots ----

    @Test fun fillFreeSpots_drawsTheNextPeople_andTellsThem() = runTest {
        val lot = TestData.lottery(winners = 2).copy(status = LotteryStatus.DRAWN, spotsFree = 1)
        val list = listOf(
            WaitlistEntry(lot.id, "w1", "W1", now, status = EntryStatus.WON),
            WaitlistEntry(lot.id, "l1", "L1", now, status = EntryStatus.LOST),
            WaitlistEntry(lot.id, "l2", "L2", now, status = EntryStatus.LOST),
        )
        val store = InMemoryEventsStore(EventsSnapshot(lotteries = mapOf(lot.id to lot), entries = list))
        assertEquals(1, store.fillFreeSpots(lot.id, now, Random(3)))
        val winners = store.snapshot.entries.filter { it.status == EntryStatus.WON }.map { it.entrantId }
        assertEquals(2, winners.size)
        val promoted = (winners - "w1").single()
        assertEquals(NotificationType.SPOT_OPENED, store.snapshot.notifications.single { it.recipientId == promoted }.type)
        assertEquals("no spot left to fill", 0, store.fillFreeSpots(lot.id, now))
    }

    // ---- Profiles ----

    @Test fun profiles_areListedByName_forTheAdmin() = runTest {
        val store = InMemoryEventsStore()
        store.saveProfile(UserProfile("z", "zoe Park", "z@example.com"))
        store.saveProfile(UserProfile("a", "Adam Kent", "a@example.com"))
        store.saveProfile(UserProfile("m", "Maya Chen", "m@example.com"))
        assertEquals(listOf("Adam Kent", "Maya Chen", "zoe Park"), store.observeProfiles().first().map { it.name })
    }

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

    // ---- Acting as a person ----

    @Test fun forUser_actsAsThatPerson_onTheSameData() {
        val c = AppContainer.inMemory("pixel", FixedClock(now))
        val asAdam = c.forUser("adam-kent")
        assertEquals("adam-kent", asAdam.userId)
        assertEquals("pixel", asAdam.deviceId)
        assertSame(c.profiles, asAdam.profiles)
        assertSame("one draw scheduler per phone", c.drawScheduler, asAdam.drawScheduler)
        assertSame("already that person", c, c.forUser(c.userId))
    }
}
