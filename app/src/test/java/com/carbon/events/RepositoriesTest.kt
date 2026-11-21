/*
 * RepositoriesTest.kt (Vaibhav)
 *
 * Purpose: Unit tests for Repositories.kt, through the in-memory store: the lottery lists the app
 * reads, refilling spots that opened up (US 02.05.03), the admin's list of profiles (US 03.05.01),
 * and the repository's built-in trust check and log-out mark.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write tests for every part of Repositories.kt that isn't tested yet."
 * Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoriesTest {
    private val now = TestData.NOW
    private val day = TestData.DAY

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

    // ---- Built into every repository ----

    @Test fun trustCheck_bydefault_readsWhoThePhoneIsTrustedFor() = runTest {
        val store = InMemoryEventsStore(EventsSnapshot(devices = mapOf("pixel" to TrustedDevice("pixel", "adam", "Pixel 8", now))))
        val plain = object : ProfileRepository by store {
            override suspend fun trustedUserIdNow(deviceId: String): String? = observeUserIdForDevice(deviceId).first()
        }
        assertEquals("adam", plain.trustedUserIdNow("pixel"))
        assertNull(plain.trustedUserIdNow("tablet"))
    }

    @Test fun logOutMark_isSavedOnTheProfile() = runTest {
        val store = InMemoryEventsStore()
        store.saveProfile(UserProfile("adam", "Adam Kent", "adam@example.com"))
        store.signOutEverywhere("adam", now)
        assertEquals(now, store.snapshot.profiles.getValue("adam").signedOutAt)
        store.signOutEverywhere("nobody", now)
        assertNull(store.snapshot.profiles["nobody"])
    }

    @Test fun pathsAndIds_areReadable() {
        assertEquals("lot-1", Paths.keyOf("two-jack/lot-1"))
        assertEquals("lot-1", Paths.keyOf("lot-1"))
        assertTrue(Ids.readable("Adam Kent").startsWith("adam-kent-"))
    }
}
