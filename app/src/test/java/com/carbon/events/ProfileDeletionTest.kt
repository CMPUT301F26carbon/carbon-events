/*
 * ProfileDeletionTest.kt (Rayner)
 *
 * Purpose: Unit tests for what deleting a profile does to the person's lottery entries: open
 * waiting lists are left, a won spot that hasn't been stayed in goes back to the lottery, and
 * finished stays and old results stay in the owner's records (US 01.08.01, US 03.02.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * tests for what deleting a profile does to waiting lists and reservations." Reviewed, edited and
 * run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** The sample data: an open list (Two Jack) and a drawn lottery with two booked winners (Johnston Canyon). */
class ProfileDeletionTest {
    private val now = TestData.NOW
    private val store = InMemoryEventsStore(SampleData.snapshot(now))

    private fun person(i: Int) = UserProfile("sample-entrant-$i", "Entrant $i", "entrant$i@example.com")
    private fun entry(lotteryId: String, i: Int) = store.snapshot.entries.firstOrNull { it.lotteryId == lotteryId && it.entrantId == "sample-entrant-$i" }
    private fun lottery(id: String) = store.snapshot.lotteries.getValue(id)

    @Test fun deleting_leavesOpenWaitingLists_andLowersTheCount() = runTest {
        val before = lottery("lot-two-jack").entrantCount
        store.deleteProfile(person(2), now)
        assertNull(entry("lot-two-jack", 2))
        assertEquals(before - 1, lottery("lot-two-jack").entrantCount)
    }

    @Test fun deleting_givesBackABookedStayThatHasNotFinished() = runTest {
        val free = lottery("lot-johnston").spotsFree
        store.deleteProfile(person(1), now) // their stay is in progress
        assertEquals(EntryStatus.CANCELLED, entry("lot-johnston", 1)!!.status)
        assertEquals(free + 1, lottery("lot-johnston").spotsFree)
    }

    @Test fun deleting_keepsAFinishedStayInTheOwnersRecords() = runTest {
        val free = lottery("lot-johnston").spotsFree
        store.deleteProfile(person(0), now) // their stay ended two days ago
        assertEquals(EntryStatus.WON, entry("lot-johnston", 0)!!.status)
        assertEquals(free, lottery("lot-johnston").spotsFree)
    }

    @Test fun deleting_keepsOldLosingResults() = runTest {
        store.deleteProfile(person(3), now)
        assertEquals(EntryStatus.LOST, entry("lot-johnston", 3)!!.status)
    }

    @Test fun deleting_leavesOtherPeoplesEntriesAlone() = runTest {
        val others = store.snapshot.entries.filter { it.entrantId != "sample-entrant-2" }
        store.deleteProfile(person(2), now)
        assertEquals(others, store.snapshot.entries.filter { it.entrantId != "sample-entrant-2" })
    }

    @Test fun wonSpot_goesBackOnlyWhileTheStayIsAhead() {
        val today = dayOf(now)
        val won = WaitlistEntry("lot", "me", "Me", now, EntryStatus.WON)
        assertTrue("won, not booked yet", won.givesSpotBackOnDelete(now))
        assertTrue("stay ends today", won.copy(checkInDay = today - 2, checkOutDay = today).givesSpotBackOnDelete(now))
        assertFalse("stay ended yesterday", won.copy(checkInDay = today - 3, checkOutDay = today - 1).givesSpotBackOnDelete(now))
        assertFalse("never won", won.copy(status = EntryStatus.WAITING).givesSpotBackOnDelete(now))
    }
}
