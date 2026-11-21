/*
 * OwnerPhoneDrawTest.kt (Rayner)
 *
 * Purpose: Unit tests for who runs the automatic draw (US 02.05.01): only phones signed in to
 * the lottery's owner draw it, both in the background pass and while the app is open. An
 * admin's phone draws only the lotteries of campsites the admin owns.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 18, 2026). Prompt: "Write
 * tests that the automatic draw only runs on the owner's phones." Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** One owner's lottery that is due, and an admin who owns a campsite of their own. */
class OwnerPhoneDrawTest {
    private val now = TestData.NOW
    private val ownersLottery = TestData.lottery(start = now - 3 * TestData.DAY, end = now - 1)
    private val adminsLottery = ownersLottery.copy(id = "lot-admin", campsiteId = "admin-site", campsiteName = "Admin Meadow", ownerId = "admin-1")
    private val owner = UserProfile(ownersLottery.ownerId, "Priya", "p@example.com", activeRole = Role.OWNER, authUid = "uid-owner")
    private val admin = UserProfile("admin-1", "Alex Admin", "a@example.com", activeRole = Role.ADMIN, isAdmin = true, authUid = "uid-admin")

    private fun store() = InMemoryEventsStore(EventsSnapshot(
        lotteries = mapOf(ownersLottery.id to ownersLottery, adminsLottery.id to adminsLottery),
        entries = TestData.entries(count = 4) + TestData.entries(count = 4).map { it.copy(lotteryId = adminsLottery.id) },
        profiles = mapOf(owner.id to owner, admin.id to admin),
    ))

    private fun pass(s: InMemoryEventsStore, authUid: String) = runBlocking { BackgroundSync.run(null, s, s, s, s, authUid, now) }
    private fun status(s: InMemoryEventsStore, id: String) = s.snapshot.lotteries.getValue(id).status

    @Test fun ownersPhone_drawsTheirOwnLottery_inTheBackground() {
        val s = store()
        assertEquals(1, pass(s, "uid-owner"))
        assertEquals(LotteryStatus.DRAWN, status(s, ownersLottery.id))
        assertEquals(LotteryStatus.OPEN, status(s, adminsLottery.id))
    }

    @Test fun adminsPhone_doesNotDrawAnotherOwnersLottery_inTheBackground() {
        val s = store()
        pass(s, "uid-admin")
        assertEquals(LotteryStatus.OPEN, status(s, ownersLottery.id))
        assertTrue(s.snapshot.entries.filter { it.lotteryId == ownersLottery.id }.all { it.status == EntryStatus.WAITING })
    }

    @Test fun adminsPhone_drawsALotteryForACampsiteTheAdminOwns() {
        val s = store()
        assertEquals(1, pass(s, "uid-admin"))
        assertEquals(LotteryStatus.DRAWN, status(s, adminsLottery.id))
    }

    @Test fun adminViewingTheAppAsAnEntrant_stillDrawsOnlyTheirOwn() {
        val s = store()
        s.snapshot.profiles.getValue(admin.id).let { runBlocking { s.saveProfile(it.copy(activeRole = Role.ENTRANT)) } }
        assertEquals(1, pass(s, "uid-admin"))
        assertEquals(LotteryStatus.OPEN, status(s, ownersLottery.id))
    }

    @Test fun openApp_onTheAdminsPhone_doesNotDrawAnotherOwnersLottery() = runTest {
        val s = store()
        val scheduler = DrawScheduler(s, FixedClock(now), tickMillis = 100)
        scheduler.actAs(admin.id)
        val job = scheduler.start(backgroundScope)
        runCurrent(); advanceTimeBy(300); runCurrent()
        assertEquals(LotteryStatus.OPEN, s.observeLottery(ownersLottery.id).first()!!.status)
        assertEquals(LotteryStatus.DRAWN, s.observeLottery(adminsLottery.id).first()!!.status)
        job.cancel()
    }

    @Test fun openApp_onTheOwnersPhone_drawsTheirLottery() = runTest {
        val s = store()
        val scheduler = DrawScheduler(s, FixedClock(now), tickMillis = 100)
        scheduler.actAs(owner.id)
        val job = scheduler.start(backgroundScope)
        runCurrent(); advanceTimeBy(300); runCurrent()
        assertEquals(LotteryStatus.DRAWN, s.observeLottery(ownersLottery.id).first()!!.status)
        assertEquals(1, scheduler.drawsPerformed.value)
        job.cancel()
    }

    @Test fun signedOutPhone_drawsNothing() = runTest {
        val s = store()
        val scheduler = DrawScheduler(s, FixedClock(now), tickMillis = 100)
        scheduler.actAs(null)
        val job = scheduler.start(backgroundScope)
        runCurrent(); advanceTimeBy(300); runCurrent()
        assertEquals(0, scheduler.drawsPerformed.value)
        job.cancel()
    }
}
