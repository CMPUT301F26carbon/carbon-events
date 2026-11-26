/*
 * CampsiteInfo.kt (Ansh)
 *
 * The parts of a campsite's details page under its name: what it offers and who hosts it, when
 * guests can stay, its rules and cancellation policy, the lottery card, "Notify me", and the
 * link to report it.
 *
 * Stories in this file:
 *   Ansh - US 01.01.05 campsite details (rules, cancellation policy, availability, owner)
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** The description, then amenities, vibe tags and the host, each shown when the owner filled it in. */
@Composable
fun CampsiteAbout(site: Campsite) {
    Column(Modifier.fillMaxWidth().testTag("details_about"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (site.description.isNotBlank()) Text(site.description, style = EventsType.BodySmall, color = EventsColors.TextSecondary)
        if (site.amenities.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("details_amenities")) {
            Text("Amenities", style = EventsType.BodySmallStrong, color = EventsColors.Text)
            Text(site.amenities, style = EventsType.BodySmall, color = EventsColors.TextSecondary)
        }
        if (site.tags.isNotEmpty()) FlowRow(
            Modifier.testTag("details_tags"), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { site.tags.forEach { StatusTag(it, TagTone.BLUE) } }
        if (site.ownerName.isNotBlank()) MetaItem(R.drawable.ic_person, "Hosted by ${site.ownerName}", modifier = Modifier.testTag("details_host"))
    }
}

/**
 * When guests can stay: the lottery's booking period, the nights a winner may book, check-in and
 * check-out times and the price. Shown to everyone, before joining or winning.
 */
@Composable
fun StayInfoCard(site: Campsite, lottery: Lottery?) {
    DuskCard(Modifier.fillMaxWidth().testTag("details_stay"), color = EventsColors.SurfaceSoft, spacing = 8.dp) {
        Text("Availability", style = EventsType.BodyStrong, color = EventsColors.Text)
        DetailRow(
            R.drawable.ic_calendar, "Stays",
            lottery?.takeIf { it.hasBookingPeriod }?.let { formatStay(it.bookingStartDay!!, it.bookingEndDay!!).substringBefore(" ·") } ?: "Set with the next lottery",
            labelColor = EventsColors.TextSecondary,
        )
        DetailRow(R.drawable.ic_clock, "Nights per stay", if (site.minNights == site.maxNights) "${site.minNights}" else "${site.minNights} – ${site.maxNights}", labelColor = EventsColors.TextSecondary)
        DetailRow(R.drawable.ic_event_check, "Check-in · out", "${site.checkInTime} · ${site.checkOutTime}", labelColor = EventsColors.TextSecondary)
        DetailRow(R.drawable.ic_money, "Price", "$${site.pricePerNight} per night", labelColor = EventsColors.TextSecondary)
    }
}

/** The campsite's rules and cancellation policy, as on the rules sheet before booking (Figma E-RULES). */
@Composable
fun RulesAndPolicy(site: Campsite) {
    DuskCard(Modifier.fillMaxWidth().testTag("details_rules"), color = EventsColors.SurfaceSoft, spacing = 8.dp) {
        Text("Rules", style = EventsType.BodyStrong, color = EventsColors.Text)
        rulesList(site.rules.ifBlank { DEFAULT_RULES }, site).forEach { rule ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(EventsColors.GreenContainer), contentAlignment = Alignment.Center) {
                    EventsIcon(R.drawable.ic_check, size = 12.dp, tint = EventsColors.Green)
                }
                Text(rule, style = EventsType.BodySmall, color = EventsColors.Text)
            }
        }
    }
    DuskCard(Modifier.fillMaxWidth().testTag("details_policy"), color = EventsColors.SurfaceSoft, spacing = 6.dp) {
        Text("Cancellation · ${site.cancellation.label}", style = EventsType.BodyStrong, color = EventsColors.Text)
        Text("${site.cancellation.summary} $CANCELLATION_POLICY", style = EventsType.BodySmall, color = EventsColors.TextSecondary)
    }
}

/** "Report this campsite" (Figma E-REPORT), at the foot of the details page. */
@Composable
fun ReportLink(onReport: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        EventsIcon(R.drawable.ic_warning, tint = EventsColors.TextSecondary, size = 16.dp)
        TextLinkButton("Report this campsite", onReport, Modifier.testTag("details_report"), color = EventsColors.TextSecondary)
    }
}

/** "Nov 1 – 15", or "Oct 28 – Nov 3" across months (Figma tile). */
internal fun shortRange(from: Long, until: Long): String {
    val a = java.time.LocalDate.ofEpochDay(from); val b = java.time.LocalDate.ofEpochDay(until)
    val m = java.time.format.DateTimeFormatter.ofPattern("MMM", java.util.Locale.CANADA)
    // Across months only the first night fits the tile ("Oct 21").
    return if (a.month == b.month) "${a.format(m)} ${a.dayOfMonth} – ${b.dayOfMonth}" else "${a.format(m)} ${a.dayOfMonth}"
}

/** Figma E-NO-LOTTERY-NOTIFY: "Notify me" when this campsite opens a lottery. */
@Composable
fun NotifyMeCard(on: Boolean, onToggle: () -> Unit) {
    DuskCard(Modifier.fillMaxWidth().testTag("details_follow"), padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), color = EventsColors.PrimaryContainer, shape = RoundedCornerShape(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(R.drawable.ic_bell)
            Column(Modifier.weight(1f)) {
                Text("Notify me", style = EventsType.BodyStrong, color = EventsColors.Text)
                Text(if (on) "We'll tell you when a lottery opens here." else "Get a notification when a lottery opens.", style = EventsType.BodySmall, color = EventsColors.OnPrimaryContainer)
            }
            androidx.compose.material3.Switch(
                on, { onToggle() }, Modifier.testTag("details_follow_switch"),
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = EventsColors.OnPrimary, checkedTrackColor = EventsColors.Primary),
            )
        }
    }
}

/** Lottery card (Figma): radius 16, #3E3A8A, or green once the entrant has joined. */
@Composable
fun LotteryCard(lottery: Lottery?, action: LotteryAction, now: Long, alsoIn: List<String> = emptyList()) {
    val joined = action == LotteryAction.JOINED
    DuskCard(
        Modifier.fillMaxWidth().testTag("lottery_card"),
        padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        color = if (joined) EventsColors.GreenContainer else EventsColors.PrimaryContainer,
        shape = RoundedCornerShape(16.dp),
        spacing = 8.dp,
    ) {
        Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    joined -> "You’re in · ${spots(lottery?.winnerCount ?: 0)}"
                    lottery == null -> "Lottery"
                    else -> "Lottery · ${spots(lottery.winnerCount)}"
                },
                style = EventsType.BodyStrong, color = EventsColors.Text, modifier = Modifier.weight(1f),
            )
            if (joined) StatusTag("Joined", TagTone.GREEN) else LotteryStatusTag(lottery, now)
        }
        if (lottery == null) {
            Text("The owner hasn’t opened a lottery for this campsite yet.", style = EventsType.BodySmall, color = EventsColors.OnPrimaryContainer)
        } else {
            DetailRow(R.drawable.ic_calendar, "Registration", formatRange(lottery.registrationStart, lottery.registrationEnd))
            DetailRow(R.drawable.ic_trophy, "Draw · winners", "${formatDay(lottery.registrationEnd)} · ${spots(lottery.winnerCount)}")
            DetailRow(R.drawable.ic_group, "On waitlist", lottery.waitlistCap?.let { "${lottery.entrantCount} / $it" } ?: "${lottery.entrantCount}")
            // One lottery over several campsites: joining here enters you for all of them.
            if (alsoIn.isNotEmpty()) Text(
                "This lottery also covers ${alsoIn.joinToString(" and ")}. Joining enters you for every campsite in it; if you win more than one, you choose.",
                style = EventsType.Caption, color = EventsColors.OnPrimaryContainer, modifier = Modifier.testTag("lottery_also_in"),
            )
        }
    }
}
