/*
 * ReplyDeadlineTest.kt (Vaibhav)
 *
 * Purpose: Unit tests for the time a winner has to book (LotteryRules.kt and both stores): a
 * winner who doesn't book within 48 hours of being selected gives the spot back and is told why,
 * the owner's phone then offers it to the next person, and a winner who booked in time keeps it.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 25, 2026). Prompt: "Write
 * tests for the deadline a lottery winner has to book by." Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Two Jack Lakeside drawn with one winner, Maya, and Sam and Ella not selected. */
class ReplyDeadlineTest {
    private val now = TestData.NOW
    private val hour = 3_600_000L
    private val drawnAt = now - 50 * hour
    private val lot = TestData.lottery(winners = 1).copy(status = LotteryStatus.DRAWN, drawnAt = drawnAt, entrantCount = 3)

    private fun entry(id: String, status: EntryStatus, wonAt: Long? = null) = WaitlistEntry(lot.id, id, id.replaceFirstChar { it.uppercase() }, drawnAt - hour, status, wonAt = wonAt)

    private fun store(maya: WaitlistEntry = entry("maya", EntryStatus.WON, drawnAt)) = InMemoryEventsStore(EventsSnapshot(
        campsites = mapOf(lot.campsiteId to TestData.campsite()),
        lotteries = mapOf(lot.id to lot),
        entries = listOf(maya, entry("sam", EntryStatus.LOST), entry("ella", EntryStatus.LOST)),
    ))

    // ---- The rule ----

    @Test fun aWinnerHas48HoursFromBeingSelected() {
        assertEquals(drawnAt + 48 * hour, LotteryDrawer.replyDeadline(lot, entry("maya", EntryStatus.WON, drawnAt)))
        assertEquals("a spot offered later counts from then", now + 48 * hour, LotteryDrawer.replyDeadline(lot, entry("maya", EntryStatus.WON, now)))
    }

    @Test fun nobodyElseHasADeadline() {
        val booked = entry("maya", EntryStatus.WON, drawnAt).copy(checkInDay = dayOf(now) + 3, checkOutDay = dayOf(now) + 5)
        assertNull("booked in time", LotteryDrawer.replyDeadline(lot, booked))
        assertNull("not selected", LotteryDrawer.replyDeadline(lot, entry("sam", EntryStatus.LOST)))
        assertNull("still waiting", LotteryDrawer.replyDeadline(lot, entry("sam", EntryStatus.WAITING)))
        assertNull("drawn before deadlines were saved", LotteryDrawer.replyDeadline(lot, entry("maya", EntryStatus.WON)))
    }

    @Test fun aWinnerWhoDidntBookInTime_givesTheSpotBack_andIsToldWhy() {
        val result = LotteryDrawer.expireUnbooked(lot, listOf(entry("maya", EntryStatus.WON, drawnAt), entry("sam", EntryStatus.LOST)), now)
        assertEquals(listOf("maya" to EntryStatus.DECLINED), result.entries.map { it.entrantId to it.status })
        assertEquals(now, result.entries.single().endedAt)
        val notice = result.notifications.single()
        assertEquals("maya", notice.recipientId)
        assertEquals("Your spot at Two Jack Lakeside went to someone else", notice.title)
    }

    @Test fun aWinnerStillInsideTheWindow_keepsTheSpot() {
        assertTrue(LotteryDrawer.expireUnbooked(lot, listOf(entry("maya", EntryStatus.WON, now - 47 * hour)), now).entries.isEmpty())
    }

    @Test fun theWinnersNotice_saysHowLongTheyHave() {
        val result = LotteryDrawer.draw(lot.copy(status = LotteryStatus.OPEN), listOf(entry("maya", EntryStatus.WAITING)), now)
        assertTrue(result.notifications.single().body.contains("within 48 hours"))
        assertEquals(now, result.entries.single().wonAt)
    }

    // ---- The stores and the owner's phone ----

    @Test fun expiring_marksTheWinnerDeclined_andFreesTheSpot() = runTest {
        val s = store()
        assertEquals(1, s.expireUnbookedWinners(lot.id, now))
        assertEquals(EntryStatus.DECLINED, s.snapshot.entries.single { it.entrantId == "maya" }.status)
        assertEquals(1, s.snapshot.lotteries.getValue(lot.id).spotsFree)
        assertEquals(1, s.snapshot.notifications.count { it.recipientId == "maya" })
    }

    @Test fun theOwnersPhone_offersAnExpiredSpotToTheNextPerson() = runTest {
        val s = store()
        DrawScheduler(s, FixedClock(now)).drawDue(listOf(lot))
        val won = s.snapshot.entries.filter { it.status == EntryStatus.WON }
        assertEquals(1, won.size)
        assertTrue(won.single().entrantId in setOf("sam", "ella"))
        assertEquals(now, won.single().wonAt)
    }

    @Test fun aWinnerWhoBooked_keepsTheirSpot() = runTest {
        val s = store(entry("maya", EntryStatus.WON, drawnAt).copy(checkInDay = dayOf(now) + 3, checkOutDay = dayOf(now) + 5))
        DrawScheduler(s, FixedClock(now)).drawDue(listOf(lot))
        assertEquals(EntryStatus.WON, s.snapshot.entries.single { it.entrantId == "maya" }.status)
        assertTrue(s.snapshot.entries.filter { it.entrantId != "maya" }.all { it.status == EntryStatus.LOST })
    }
}
