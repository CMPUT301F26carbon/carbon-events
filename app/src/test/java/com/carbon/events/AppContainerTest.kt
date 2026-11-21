/*
 * AppContainerTest.kt (Vaibhav)
 *
 * Purpose: Unit tests for AppContainer.kt: acting as the signed-in person on the same data.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write tests for every part of AppContainer.kt that isn't tested yet."
 * Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class AppContainerTest {
    private val now = TestData.NOW

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

    @Test fun theOfflineContainer_usesOneInMemoryStore_andNoServer() {
        val c = AppContainer.inMemory("pixel", FixedClock(now))
        assertFalse(c.usingServer)
        assertSame(c.profiles, c.lotteries)
        assertSame(c.profiles, c.social)
        assertEquals("pixel", c.userId)
        assertEquals(now, c.clock.now())
    }
}
