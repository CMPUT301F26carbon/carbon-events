/*
 * InMemoryEventsStoreTest.kt
 *
 * Purpose: unit tests for the repository contract using the in-memory store: creating
 * campsites (US 02.01.01), joining / leaving waiting lists (US 01.02.01, US 01.02.02),
 * the exactly-once automatic draw with notifications (US 02.05.01, US 01.03.01,
 * US 01.03.02), the draw scheduler, and admin removal with cascade (US 03.01.01).
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import com.carbon.events.TestData
import com.carbon.events.TestData.DAY
import com.carbon.events.TestData.NOW
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

class InMemoryEventsStoreTest {

    private fun store() = InMemoryEventsStore(EventsSnapshot(
        campsites = mapOf("two-jack" to TestData.campsite()),
        lotteries = mapOf("lot-1" to TestData.lottery(winners = 2)),
    ))

    @Test
    fun createCampsite_assignsIdAndListsUnderOwner() = runTest {
        val s = store()
        val id = s.createCampsite(TestData.campsite(id = "", name = "Bow River Sites", owner = "owner-2"))
        assertEquals("Bow River Sites", s.observeCampsite(id).first()?.name)
        assertEquals(listOf(id), s.observeCampsitesByOwner("owner-2").first().map { it.id })
    }

    @Test
    fun join_addsEntryAndCountsIt() = runTest {
        val s = store()
        s.joinWaitlist("lot-1", "adam", "Adam Kent", NOW)
        assertEquals(listOf("adam"), s.observeEntries("lot-1").first().map { it.entrantId })
        assertEquals(1, s.observeLottery("lot-1").first()?.entrantCount)
        assertEquals(1, s.observeEntriesForEntrant("adam").first().size)
    }

    @Test
    fun join_twiceIsRefused() = runTest {
        val s = store()
        s.joinWaitlist("lot-1", "adam", "Adam Kent", NOW)
        try {
            s.joinWaitlist("lot-1", "adam", "Adam Kent", NOW)
            fail("expected refusal")
        } catch (e: WaitlistException) {
            assertEquals(WaitlistError.ALREADY_JOINED, e.error)
        }
        assertEquals(1, s.observeLottery("lot-1").first()?.entrantCount)
    }

    @Test
    fun leave_removesEntry() = runTest {
        val s = store()
        s.joinWaitlist("lot-1", "adam", "Adam Kent", NOW)
        s.leaveWaitlist("lot-1", "adam", NOW + 1)
        assertTrue(s.observeEntries("lot-1").first().isEmpty())
        assertEquals(0, s.observeLottery("lot-1").first()?.entrantCount)
    }

    @Test
    fun draw_notDueDoesNothing() = runTest {
        val s = store()
        assertFalse(s.runDrawIfDue("lot-1", NOW, Random(1)))
        assertEquals(LotteryStatus.OPEN, s.observeLottery("lot-1").first()?.status)
    }

    @Test
    fun draw_runsExactlyOnceAndNotifiesEveryone() = runTest {
        val s = store()
        listOf("a", "b", "c").forEach { s.joinWaitlist("lot-1", it, it.uppercase(), NOW) }
        val end = TestData.lottery().registrationEnd
        assertTrue(s.runDrawIfDue("lot-1", end, Random(1)))
        assertFalse("second draw must not happen", s.runDrawIfDue("lot-1", end + 1, Random(2)))
        val entries = s.observeEntries("lot-1").first()
        assertEquals(2, entries.count { it.status == EntryStatus.WON })
        assertEquals(1, entries.count { it.status == EntryStatus.LOST })
        assertEquals(LotteryStatus.DRAWN, s.observeLottery("lot-1").first()?.status)
        assertEquals(3, s.snapshot.notifications.size)
        assertEquals(1, s.observeNotifications("a").first().size)
    }

    @Test
    fun markAllRead_onlyAffectsRecipient() = runTest {
        val s = store()
        listOf("a", "b").forEach { s.joinWaitlist("lot-1", it, it, NOW) }
        s.runDrawIfDue("lot-1", NOW + 4 * DAY, Random(1))
        s.markAllRead("a")
        assertTrue(s.observeNotifications("a").first().all { it.read })
        assertTrue(s.observeNotifications("b").first().none { it.read })
    }

    @Test
    fun deleteCampsite_cascadesToLotteriesAndEntries() = runTest {
        val s = store()
        s.joinWaitlist("lot-1", "adam", "Adam", NOW)
        s.deleteCampsite("two-jack")
        assertNull(s.observeCampsite("two-jack").first())
        assertNull(s.observeLottery("lot-1").first())
        assertTrue(s.snapshot.entries.isEmpty())
    }

    @Test
    fun scheduler_drawsOnlyDueLotteries() = runTest {
        val clock = FixedClock(NOW)
        val s = InMemoryEventsStore(SampleData.snapshot(NOW))
        val scheduler = DrawScheduler(s, clock)
        assertEquals(0, scheduler.drawDue(s.observeLotteries().first()))
        clock.advanceBy(3 * TestData.MIN) // the sample Lake Louise lottery closes 2 min after "now"
        assertEquals(1, scheduler.drawDue(s.observeLotteries().first()))
        assertEquals(LotteryStatus.DRAWN, s.observeLottery("lot-lake-louise").first()?.status)
        assertEquals(LotteryStatus.OPEN, s.observeLottery("lot-two-jack").first()?.status)
    }
}
