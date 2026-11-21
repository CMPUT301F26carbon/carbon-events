/*
 * FormatsTest.kt (Aditya)
 *
 * Purpose: Unit tests for Formats.kt: how dates, times, stays and countdowns are written across
 * the app.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write tests for the date and stay formats in Formats.kt."
 * Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatsTest {
    private val zone = ZoneId.systemDefault()
    private fun at(y: Int, m: Int, d: Int, h: Int = 9, min: Int = 0) = LocalDate.of(y, m, d).atTime(h, min).atZone(zone).toInstant().toEpochMilli()
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()
    private val now = at(2026, 10, 16, 9)

    @Test fun day_andDayTime() {
        assertEquals("Oct 16", formatDay(now))
        assertEquals("Oct 16, 9:30 p.m.".lowercase().replace(".", ""), formatDayTime(at(2026, 10, 16, 21, 30)).lowercase().replace(".", ""))
        assertEquals("Oct 1 – Oct 15", formatRange(at(2026, 10, 1), at(2026, 10, 15)))
    }

    @Test fun relativeTime_countsUpFromNow() {
        assertEquals("now", relativeTime(now - 30_000, now))
        assertEquals("5m", relativeTime(now - 5 * 60_000, now))
        assertEquals("2h", relativeTime(now - 2 * 3_600_000, now))
        assertEquals("Yesterday", relativeTime(at(2026, 10, 15, 8), now))
        assertEquals("Oct 4", relativeTime(at(2026, 10, 4), now))
    }

    @Test fun isToday_isTheSameCalendarDay() {
        assertTrue(isToday(at(2026, 10, 16, 0, 1), now))
        assertFalse(isToday(at(2026, 10, 15, 23, 59), now))
    }

    @Test fun closesIn_countsDown_thenSaysClosed() {
        val lottery = TestData.lottery(start = now - TestData.DAY, end = now + 3 * TestData.DAY)
        assertEquals("Closes in 3 days", closesIn(lottery, now))
        assertEquals("Closes in 2 h", closesIn(lottery, lottery.registrationEnd - 90 * 60_000))
        assertEquals("Closes in 2 min", closesIn(lottery, lottery.registrationEnd - 90_000))
        assertEquals("Registration closed", closesIn(lottery, lottery.registrationEnd))
    }

    @Test fun stays_inFullShortAndCompact() {
        assertEquals("Nov 1 – Nov 15, 2026 · 14 nights", formatStay(day(2026, 11, 1), day(2026, 11, 15)))
        assertEquals("Oct 11 – Oct 12 · 1 night", formatStayShort(day(2026, 10, 11), day(2026, 10, 12)))
        assertEquals("Nov 1 – 15", formatStayCompact(day(2026, 11, 1), day(2026, 11, 15)))
        assertEquals("Oct 26 – Nov 9", formatStayCompact(day(2026, 10, 26), day(2026, 11, 9)))
        assertEquals("Sun, Nov 1", formatDayNum(day(2026, 11, 1)))
    }

    @Test fun dayOf_isTheLocalCalendarDay() {
        assertEquals(day(2026, 10, 16), dayOf(now))
        assertEquals(dayOf(now) * DAY_MILLIS, LocalDate.ofEpochDay(dayOf(now)).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli())
    }
}
