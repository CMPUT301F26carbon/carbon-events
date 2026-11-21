/*
 * LotteryGroupsTest.kt (Aditya, Suvir)
 *
 * Purpose: Unit tests for LotteryGroups.kt: campsites published together in one lottery, joining
 * and leaving all of them at once, and a winner of several sites keeping only the one they book
 * (US 02.04.02, US 01.04.02).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 17, 2026). Prompt: "Write
 * tests for one lottery over several campsites." Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Two Jack and Tunnel Mountain in one lottery, plus Lake Louise on its own. */
class LotteryGroupsTest {
    private val now = TestData.NOW
    private val day = dayOf(now)
    private fun lot(id: String, site: String, name: String, group: String = "", status: LotteryStatus = LotteryStatus.OPEN) =
        Lottery(id, site, name, "owner", now - 3_600_000, now + 86_400_000, 2, status = status, bookingStartDay = day + 3, bookingEndDay = day + 20, groupId = group)

    private val twoJack = lot("lot-a", "two-jack", "Two Jack Lakeside", "grp-1")
    private val tunnel = lot("lot-b", "tunnel-mountain", "Tunnel Mountain Village", "grp-1")
    private val louise = lot("lot-c", "lake-louise", "Lake Louise Tent Sites")
    private val all = listOf(twoJack, tunnel, louise)

    private fun store(vararg lotteries: Lottery, entries: List<WaitlistEntry> = emptyList()) =
        InMemoryEventsStore(EventsSnapshot(lotteries = lotteries.associateBy { it.id }, entries = entries))

    @Test fun groupId_isNewEachTime_andMarkedAsAGroup() {
        val a = LotteryGroups.newGroupId(now, Random(1))
        val b = LotteryGroups.newGroupId(now, Random(2))
        assertTrue(a.startsWith("grp-"))
        assertNotEquals(a, b)
    }

    @Test fun members_areTheLotteriesPublishedTogether_sortedByName() {
        assertEquals(listOf(tunnel, twoJack), LotteryGroups.members(tunnel, all))
        assertEquals(listOf(tunnel, twoJack), LotteryGroups.members(twoJack, all))
    }

    @Test fun aLotteryOnItsOwn_isItsOnlyMember() {
        assertEquals(listOf(louise), LotteryGroups.members(louise, all))
    }

    @Test fun openWins_listsTheSitesWonButNotBookedYet() {
        val mine = listOf(
            WaitlistEntry("lot-a", "me", "Me", now, EntryStatus.WON),
            WaitlistEntry("lot-b", "me", "Me", now, EntryStatus.WON),
        )
        assertEquals(listOf(tunnel, twoJack), LotteryGroups.openWins(twoJack, all, mine, "me"))
        val oneBooked = listOf(mine[0].copy(checkInDay = day + 4, checkOutDay = day + 6), mine[1])
        assertEquals(listOf(tunnel), LotteryGroups.openWins(twoJack, all, oneBooked, "me"))
        val oneLost = listOf(mine[0], mine[1].copy(status = EntryStatus.LOST))
        assertEquals(listOf(twoJack), LotteryGroups.openWins(twoJack, all, oneLost, "me"))
    }

    @Test fun joinAll_putsTheEntrantOnEveryWaitlistInTheGroup() = runTest {
        val s = store(*all.toTypedArray())
        LotteryGroups.joinAll(s, twoJack, "me", "Maya Chen", now)
        assertEquals(setOf("lot-a", "lot-b"), s.snapshot.entries.filter { it.entrantId == "me" }.map { it.lotteryId }.toSet())
        assertEquals(1, s.snapshot.lotteries.getValue("lot-b").entrantCount)
        assertEquals(0, s.snapshot.lotteries.getValue("lot-c").entrantCount)
    }

    @Test fun joinAll_skipsASiblingThatIsFull_butStillJoinsTheOneAskedFor() = runTest {
        val s = store(twoJack, tunnel.copy(waitlistCap = 1, entrantCount = 1), entries = listOf(WaitlistEntry("lot-b", "other", "Other", now)))
        LotteryGroups.joinAll(s, twoJack, "me", "Maya Chen", now)
        assertTrue(s.snapshot.entries.any { it.lotteryId == "lot-a" && it.entrantId == "me" })
        assertFalse(s.snapshot.entries.any { it.lotteryId == "lot-b" && it.entrantId == "me" })
    }

    @Test fun joinAll_onALotteryOnItsOwn_joinsJustThatOne() = runTest {
        val s = store(*all.toTypedArray())
        LotteryGroups.joinAll(s, louise, "me", "Maya Chen", now)
        assertEquals(listOf("lot-c"), s.snapshot.entries.map { it.lotteryId })
    }

    @Test fun leaveAll_takesTheEntrantOffEveryWaitlistInTheGroup() = runTest {
        val s = store(*all.toTypedArray())
        LotteryGroups.joinAll(s, twoJack, "me", "Maya Chen", now)
        LotteryGroups.leaveAll(s, tunnel, "me", now)
        assertTrue(s.snapshot.entries.none { it.entrantId == "me" })
        assertEquals(0, s.snapshot.lotteries.getValue("lot-a").entrantCount)
    }

    @Test fun giveBackOthers_returnsTheSitesNotBooked() = runTest {
        val drawnA = twoJack.copy(status = LotteryStatus.DRAWN)
        val drawnB = tunnel.copy(status = LotteryStatus.DRAWN)
        val s = store(drawnA, drawnB, louise, entries = listOf(
            WaitlistEntry("lot-a", "me", "Me", now, EntryStatus.WON, day + 4, day + 6),
            WaitlistEntry("lot-b", "me", "Me", now, EntryStatus.WON),
        ))
        assertEquals(1, LotteryGroups.giveBackOthers(s, drawnA, "me", now))
        assertEquals(EntryStatus.DECLINED, s.snapshot.entries.first { it.lotteryId == "lot-b" }.status)
        assertEquals(1, s.snapshot.lotteries.getValue("lot-b").spotsFree)
        assertEquals(EntryStatus.WON, s.snapshot.entries.first { it.lotteryId == "lot-a" }.status)
    }

    @Test fun giveBackOthers_onALotteryOnItsOwn_givesNothingBack() = runTest {
        val s = store(louise, entries = listOf(WaitlistEntry("lot-c", "me", "Me", now, EntryStatus.WON)))
        assertEquals(0, LotteryGroups.giveBackOthers(s, louise, "me", now))
    }
}
