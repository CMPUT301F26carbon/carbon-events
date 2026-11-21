/*
 * Formats.kt (Aditya)
 *
 * How dates, times and stays are written across the app ("Oct 16", "in 3 days",
 * "Oct 16 – 19 · 3 nights").
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.ui.text.TextStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

private val dayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.CANADA)

private val dayTimeFormat = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.CANADA)

/** "Oct 16" style date for epoch [millis]. */
fun formatDay(millis: Long): String = dayFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** "Oct 16, 9:30 PM" style date and time for epoch [millis]. */
fun formatDayTime(millis: Long): String = dayTimeFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** "Oct 1 – Oct 15" style range. */
fun formatRange(start: Long, end: Long): String = "${formatDay(start)} – ${formatDay(end)}"

/** Short relative time like the Figma notifications: "2m", "1h", "Yesterday", "Oct 4". */
fun relativeTime(millis: Long, now: Long): String {
    val diff = now - millis
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when {
        diff < 60_000 -> "now"
        diff < 3_600_000 -> "${diff / 60_000}m"
        day == today -> "${diff / 3_600_000}h"
        day == today.minusDays(1) -> "Yesterday"
        day.isAfter(today.minusDays(7)) -> day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.CANADA)
        else -> formatDay(millis)
    }
}

/**
 * True when [millis] falls on the same calendar day as [now]. Works on every Android the app
 * supports (LocalDate.ofInstant needs Android 14, so it isn't used).
 */
fun isToday(millis: Long, now: Long): Boolean {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate() == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
}

/** Short countdown text, e.g. "Closes in 3 days", "Closes in 2 min". */
fun closesIn(lottery: Lottery, now: Long): String {
    val left = lottery.registrationEnd - now
    return when {
        left <= 0 -> "Registration closed"
        left < 3_600_000 -> "Closes in ${ceil(left / 60_000.0).toInt()} min"
        left < 86_400_000 -> "Closes in ${ceil(left / 3_600_000.0).toInt()} h"
        else -> "Closes in ${ceil(left / 86_400_000.0).toInt()} days"
    }
}

/** Milliseconds in one day; Material date pickers use UTC midnights, so day number x this = picker millis. */
const val DAY_MILLIS = 86_400_000L

private val dayNumFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.CANADA)

private val dayNumYearFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.CANADA)

/** "Sun, Nov 1" style label for a day number (see [LocalDate.toEpochDay]). */
fun formatDayNum(day: Long): String = dayNumFormat.format(LocalDate.ofEpochDay(day))

/**
 * "Nov 1 – Nov 15, 2026 · 14 nights" style label for a stay or booking period.
 *
 * @param startDay first night (day number).
 * @param endDay check-out day (day number).
 */
fun formatStay(startDay: Long, endDay: Long): String {
    val nights = endDay - startDay
    return "${dayFormat.format(LocalDate.ofEpochDay(startDay))} – ${dayNumYearFormat.format(LocalDate.ofEpochDay(endDay))} · $nights night${if (nights == 1L) "" else "s"}"
}

/** "Nov 1 – 15" (same month) or "Oct 26 – Nov 9": the shortest stay label, for tiles. */
fun formatStayCompact(startDay: Long, endDay: Long): String {
    val a = LocalDate.ofEpochDay(startDay)
    val b = LocalDate.ofEpochDay(endDay)
    return if (a.month == b.month) "${dayFormat.format(a)} – ${b.dayOfMonth}" else "${dayFormat.format(a)} – ${dayFormat.format(b)}"
}

/** "Oct 11 – Oct 14 · 3 nights": a stay without the year, for tight rows. */
fun formatStayShort(startDay: Long, endDay: Long): String {
    val nights = endDay - startDay
    return "${dayFormat.format(LocalDate.ofEpochDay(startDay))} – ${dayFormat.format(LocalDate.ofEpochDay(endDay))} · $nights night${if (nights == 1L) "" else "s"}"
}

/** The day number (local calendar date) that epoch [millis] falls on. */
fun dayOf(millis: Long): Long = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
