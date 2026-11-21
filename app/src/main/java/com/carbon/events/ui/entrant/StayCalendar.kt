/*
 * StayCalendar.kt (Suvir)
 *
 * The month calendar winners pick their nights on, with nights already taken greyed out.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * The inline month calendar from Figma E-WON: nights in the booking period can be picked,
 * nights other winners booked are struck out, and the chosen stay is highlighted.
 */
@Composable
fun StayCalendar(start: Long, end: Long, checkIn: Long?, checkOut: Long?, taken: Set<Long>, onTap: (Long) -> Unit) {
    val first = java.time.LocalDate.ofEpochDay(start).withDayOfMonth(1)
    val last = java.time.LocalDate.ofEpochDay(end).withDayOfMonth(1)
    var month by remember(start) { mutableStateOf(java.time.LocalDate.ofEpochDay(checkIn ?: start).withDayOfMonth(1)) }
    val title = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.CANADA)
    DuskCard(Modifier.fillMaxWidth().testTag("stay_calendar"), padding = PaddingValues(12.dp), spacing = 6.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(month.format(title), style = EventsType.BodyStrong, color = EventsColors.Text, modifier = Modifier.weight(1f))
            val canBack = month > first
            val canNext = month < last
            Box(Modifier.size(32.dp).clip(CircleShape).clickable(enabled = canBack) { month = month.minusMonths(1) }, contentAlignment = Alignment.Center) {
                EventsIcon(R.drawable.ic_arrow_back, size = 16.dp, tint = if (canBack) EventsColors.Text else EventsColors.TextTertiary)
            }
            Box(Modifier.size(32.dp).clip(CircleShape).clickable(enabled = canNext) { month = month.plusMonths(1) }.testTag("calendar_next"), contentAlignment = Alignment.Center) {
                EventsIcon(R.drawable.ic_chevron_right, size = 18.dp, tint = if (canNext) EventsColors.Text else EventsColors.TextTertiary)
            }
        }
        Row {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                Text(d, style = EventsType.Caption, color = EventsColors.TextTertiary, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        val lead = month.dayOfWeek.value % 7 // Sunday first
        val days = month.lengthOfMonth()
        val cells = ((lead + days + 6) / 7) * 7
        (0 until cells).chunked(7).forEach { week ->
            Row {
                week.forEach { cell ->
                    val dom = cell - lead + 1
                    Box(Modifier.weight(1f).height(36.dp), contentAlignment = Alignment.Center) {
                        if (dom in 1..days) {
                            val day = month.withDayOfMonth(dom).toEpochDay()
                            val inPeriod = day in start..end
                            val booked = day in taken
                            val isEnd = day == checkIn || day == checkOut
                            val between = checkIn != null && checkOut != null && day > checkIn && day < checkOut
                            val tappable = inPeriod && !(booked && (checkIn == null || checkOut != null))
                            Box(
                                Modifier.size(34.dp).clip(CircleShape)
                                    .background(
                                        when {
                                            isEnd -> EventsColors.Coral
                                            between -> EventsColors.Coral.copy(alpha = 0.28f)
                                            else -> androidx.compose.ui.graphics.Color.Transparent
                                        },
                                    )
                                    .clickable(enabled = tappable) { onTap(day) }.testTag("day_$day"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$dom",
                                    style = EventsType.BodySmall.copy(textDecoration = if (booked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null),
                                    color = when {
                                        isEnd -> EventsColors.OnPrimary
                                        !inPeriod || booked -> EventsColors.TextTertiary.copy(alpha = 0.6f)
                                        else -> EventsColors.Text
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        CardDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            MetaItem(
                R.drawable.ic_calendar,
                when {
                    checkIn != null && checkOut != null -> "${formatDayNum(checkIn).substringAfter(", ")} – ${formatDayNum(checkOut).substringAfter(", ")} · ${checkOut - checkIn} night${if (checkOut - checkIn == 1L) "" else "s"}"
                    checkIn != null -> "Now pick check-out"
                    else -> "Select your dates"
                },
                modifier = Modifier.weight(1f),
            )
            Text("Struck-out = booked", style = EventsType.Caption, color = EventsColors.TextTertiary)
        }
    }
}
