/*
 * RepositoryLifecycleTest.kt (Tausif)
 */
package com.carbon.events

import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** CRUD, booking transitions and timer-driven draws using isolated repository snapshots. */
@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryLifecycleTest {
    private val now = TestData.NOW
    private val lot = TestData.lottery().copy(bookingStartDay = 100, bookingEndDay = 114)
    private fun store() = InMemoryEventsStore(EventsSnapshot(campsites = mapOf("two-jack" to TestData.campsite()), lotteries = mapOf(lot.id to lot)))
    private suspend fun winner(s: InMemoryEventsStore): String {
        s.joinWaitlist(lot.id, "maya", "Maya Chen", now)
        s.runDrawIfDue(lot.id, lot.registrationEnd, Random(1))
        return "maya"
    }

    @Test fun editListing_preservesIdentityAndOtherOwnersData() = runTest {
        val s = store()
        val other = s.createCampsite(TestData.campsite(id = "", owner = "another-owner"))
        s.updateCampsite(TestData.campsite().copy(name = "Two Jack North", maxOccupancy = 8))
        assertEquals("Two Jack North", s.observeCampsite("two-jack").first()!!.name)
        assertEquals("another-owner", s.observeCampsite(other).first()!!.ownerId)
        assertEquals(2, s.observeCampsites().first().size)
    }

    @Test fun deletingOneListing_keepsUnrelatedListingsAndLotteries() = runTest {
        val s = store()
        val other = s.createCampsite(TestData.campsite(id = "", owner = "other"))
        val otherLot = s.createLottery(lot.copy(id = "", campsiteId = other, ownerId = "other"))
        s.deleteCampsite("two-jack")
        assertNull(s.observeCampsite("two-jack").first())
        assertNotNull(s.observeCampsite(other).first())
        assertNotNull(s.observeLottery(otherLot).first())
    }

    @Test fun fullWaitlist_rejectionDoesNotChangeEntryCount() = runTest {
        val s = InMemoryEventsStore(EventsSnapshot(lotteries = mapOf(lot.id to lot.copy(waitlistCap = 1))))
        s.joinWaitlist(lot.id, "maya", "Maya", now)
        try { s.joinWaitlist(lot.id, "liam", "Liam", now); fail("Capacity must be enforced") }
        catch (e: WaitlistException) { assertEquals(WaitlistError.FULL, e.error) }
        assertEquals(1, s.observeLottery(lot.id).first()!!.entrantCount)
        assertEquals(listOf("maya"), s.observeEntries(lot.id).first().map { it.entrantId })
    }

    @Test fun winnerBooking_appearsInOwnerAndEntrantQueries() = runTest {
        val s = store(); val id = winner(s)
        s.bookDates(lot.id, id, 101, 104, now, 3, true)
        val owner = s.observeReservationsForOwner(lot.ownerId).first().single()
        assertEquals(3, owner.guests)
        assertEquals(3L, owner.nights)
        assertEquals(owner, s.observeReservationsForEntrant(id).first().single())
        assertEquals(owner, s.observeReservationsForCampsite("two-jack").first().single())
    }

    @Test fun cancelledStay_leavesActiveQueriesButKeepsHistory() = runTest {
        val s = store(); val id = winner(s)
        s.bookDates(lot.id, id, 101, 104, now)
        s.giveUpSpot(lot.id, id, now + 1)
        assertTrue(s.observeReservationsForOwner(lot.ownerId).first().isEmpty())
        val history = s.observeEndedReservationsForOwner(lot.ownerId).first().single()
        assertEquals(EntryStatus.CANCELLED, history.status)
        assertEquals(101L, history.checkInDay)
    }

    @Test fun adjacentBookings_doNotOverlapAndDuplicateConfirmationIsRejected() = runTest {
        val s = store()
        s.joinWaitlist(lot.id, "a", "Maya", now); s.joinWaitlist(lot.id, "b", "Liam", now)
        s.runDrawIfDue(lot.id, lot.registrationEnd, Random(2))
        s.bookDates(lot.id, "a", 101, 104, now)
        s.bookDates(lot.id, "b", 104, 106, now)
        try { s.bookDates(lot.id, "a", 107, 109, now); fail("Cannot book twice") }
        catch (e: BookingException) { assertEquals(BookingError.ALREADY_BOOKED, e.error) }
        assertEquals(2, s.observeReservationsForCampsite("two-jack").first().size)
    }

    @Test fun lotteryEdits_preserveEntriesAndRejectChangesAfterDraw() = runTest {
        val s = store()
        s.joinWaitlist(lot.id, "maya", "Maya", now)
        s.updateLottery(lot.copy(winnerCount = 1), now)
        assertEquals(1, s.observeLottery(lot.id).first()!!.entrantCount)
        assertEquals(1, s.observeEntries(lot.id).first().size)
        s.runDrawIfDue(lot.id, lot.registrationEnd, Random(1))
        try { s.updateLottery(lot.copy(winnerCount = 3), now); fail("Drawn lottery must be immutable") }
        catch (_: LotteryEditException) { }
        assertEquals(1, s.observeLottery(lot.id).first()!!.winnerCount)
    }

    @Test fun schedulerTimer_drawsOwnDueLotteryOnceAndStopsWhenCancelled() = runTest {
        val clock = FixedClock(now)
        val own = lot.copy(registrationEnd = now + 500)
        val other = own.copy(id = "other", ownerId = "other-owner")
        val s = InMemoryEventsStore(EventsSnapshot(lotteries = mapOf(own.id to own, other.id to other)))
        val scheduler = DrawScheduler(s, clock, tickMillis = 100)
        scheduler.actAs(lot.ownerId, false)
        val job = scheduler.start(backgroundScope)
        runCurrent()
        assertEquals(0, scheduler.drawsPerformed.value)
        clock.advanceBy(500); advanceTimeBy(200); runCurrent()
        assertEquals(1, scheduler.drawsPerformed.value)
        assertEquals(LotteryStatus.OPEN, s.observeLottery(other.id).first()!!.status)
        advanceTimeBy(200); runCurrent()
        assertEquals(1, scheduler.drawsPerformed.value)
        job.cancel(); scheduler.actAs("admin", true)
        advanceTimeBy(200); runCurrent()
        assertEquals(LotteryStatus.OPEN, s.observeLottery(other.id).first()!!.status)
    }
}
