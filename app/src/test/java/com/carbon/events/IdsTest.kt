/*
 * IdsTest.kt (Vaibhav)
 *
 * Purpose: unit tests for the generated Firestore document IDs (naming convention in
 * docs/firestore-structure.md): readable user and campsite IDs, lottery month keys and
 * the app-side lottery ID.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdsTest {
    @Test fun slug_lowercaseWordsJoinedByDashes() = assertEquals("two-jack-lakeside", Ids.slug("  Two Jack Lakeside! "))

    @Test fun slug_dropsAccents() = assertEquals("cafe-la-riviere", Ids.slug("Café la Rivière"))

    @Test fun slug_neverBlank() = assertEquals("item", Ids.slug("!!!"))

    @Test fun readable_isSlugPlusFourCharTail() =
        assertTrue(Ids.readable("Maya Chen", Random(1)).matches(Regex("maya-chen-[a-km-np-z2-9]{4}")))

    @Test fun readable_sameNameGivesDifferentIds() =
        assertNotEquals(Ids.readable("Maya Chen", Random(1)), Ids.readable("Maya Chen", Random(2)))

    @Test fun lotteryKey_isStayMonth() =
        assertEquals("nov-2026", Ids.lotteryKey(java.time.LocalDate.of(2026, 11, 3).toEpochDay(), 0L))

    @Test fun lotteryKey_addsNumberWhenTaken() =
        assertEquals("nov-2026-2", Ids.lotteryKey(java.time.LocalDate.of(2026, 11, 3).toEpochDay(), 0L, attempt = 2))

    @Test fun lotteryId_roundTrips() {
        val id = Paths.lotteryId("two-jack-lakeside-7f3k", "nov-2026")
        assertEquals("two-jack-lakeside-7f3k", Paths.campsiteOf(id))
        assertEquals("nov-2026", Paths.keyOf(id))
    }
}
