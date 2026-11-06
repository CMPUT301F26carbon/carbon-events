/*
 * BackgroundSyncTest.kt (Rayner)
 *
 * Purpose: Unit tests for BackgroundSync.kt: the background pass draws an owner's due lotteries (US 02.05.01) and does nothing for entrants or blocked accounts.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 6, 2026). Prompt: "Write
 * a test file for each source file that has none." Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BackgroundSyncTest {
    private val due = TestData.lottery(start = TestData.NOW - 3 * TestData.DAY, end = TestData.NOW - 1)
    private fun store(profile: UserProfile) = InMemoryEventsStore(EventsSnapshot(
        campsites = mapOf("two-jack" to TestData.campsite()), lotteries = mapOf(due.id to due),
        entries = TestData.entries(count = 4), profiles = mapOf(profile.id to profile),
    ))
    private fun pass(s: InMemoryEventsStore, authUid: String) = runBlocking { BackgroundSync.run(null, s, s, s, s, authUid, TestData.NOW) }

    @Test fun owner_drawsTheirDueLottery() {
        val s = store(UserProfile("owner-1", "Priya", "p@example.com", activeRole = Role.OWNER, authUid = "uid-owner"))
        assertEquals(1, pass(s, "uid-owner"))
        assertEquals(LotteryStatus.DRAWN, s.snapshot.lotteries.getValue(due.id).status)
        assertEquals(2, s.snapshot.entries.count { it.status == EntryStatus.WON })
    }

    @Test fun entrant_neverDraws() {
        val s = store(UserProfile("entrant-0", "Entrant 0", "e@example.com", authUid = "uid-e"))
        assertEquals(0, pass(s, "uid-e"))
        assertEquals(LotteryStatus.OPEN, s.snapshot.lotteries.getValue(due.id).status)
    }

    @Test fun blockedOwner_andUnknownAccount_doNothing() {
        val s = store(UserProfile("owner-1", "Priya", "p@example.com", activeRole = Role.OWNER, authUid = "uid-owner", blocked = true))
        assertEquals(0, pass(s, "uid-owner"))
        assertEquals(0, pass(s, "nobody"))
    }
}
