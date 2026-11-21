/*
 * Cards.kt (Aditya)
 *
 * Cards, tags, chips, tiles and the campsite and lottery cards from our Figma.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Dusk surface card: #2D2A6E, radius 24, no outline. */
@Composable
fun DuskCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    color: Color = EventsColors.Surface,
    shape: RoundedCornerShape = EventsShapes.Large,
    borderColor: Color? = null,
    spacing: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(shape)
            .background(color)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** Tag tones from the Figma Tag component. */
enum class TagTone(val bg: Color, val fg: Color) {
    BLUE(EventsColors.PrimaryContainer, EventsColors.OnPrimaryContainer),
    GREEN(EventsColors.GreenContainer, EventsColors.Green),
    AMBER(EventsColors.AmberContainer, EventsColors.Amber),
    CORAL(EventsColors.CoralContainer, Color(0xFFFFC2B6)),
    RED(EventsColors.DangerContainer, EventsColors.Danger),
    GREY(EventsColors.SurfaceSoft, EventsColors.TextSecondary),
    SOLID(EventsColors.Primary, EventsColors.OnPrimary),
}

/** Tag: 24dp pill, Plus Jakarta Bold 11 uppercase with 0.8 letter spacing. */
@Composable
fun StatusTag(text: String, tone: TagTone, modifier: Modifier = Modifier) {
    Box(modifier.height(24.dp).clip(EventsShapes.Pill).background(tone.bg).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(text.uppercase(), style = EventsType.Overline, color = tone.fg, maxLines = 1)
    }
}

/** Chip styles used across the Figma screens. */
enum class ChipStyle {
    /** Frosted chip on the sky (Explore vibes, search filters). */
    GLASS,
    /** Coral chip (selected vibe / selected tag). */
    CORAL,
    /** Dusk chip on a card (Chip component, default state). */
    DUSK,
    /** Lavender chip (Chip component, selected state). */
    SELECTED,
}

/** Chip: 36dp pill, 16dp icon, Plus Jakarta SemiBold 12. */
@Composable
fun EventsChip(text: String, style: ChipStyle, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    val (bg, fg, stroke) = when (style) {
        ChipStyle.GLASS -> Triple(EventsColors.Tint, EventsColors.Text, EventsColors.TintStroke)
        ChipStyle.CORAL -> Triple(EventsColors.Coral, EventsColors.OnCoral, null)
        ChipStyle.DUSK -> Triple(EventsColors.Surface, EventsColors.Text, EventsColors.Outline)
        ChipStyle.SELECTED -> Triple(EventsColors.Primary, EventsColors.OnPrimary, null)
    }
    Row(
        modifier
            .height(36.dp)
            .clip(EventsShapes.Pill)
            .background(bg)
            .then(if (stroke != null) Modifier.border(1.dp, stroke, EventsShapes.Pill) else Modifier)
            .clickable(onClick = onClick)
            .padding(start = if (icon != null) 14.dp else 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) EventsIcon(icon, tint = fg, size = 16.dp)
        Text(text, style = EventsType.LabelSmall, color = fg, maxLines = 1)
    }
}

/** Stat tile (details): #37347F, radius 16, value SemiBold 14 with 16dp icon, label Medium 11. */
@Composable
fun InfoTile(@DrawableRes icon: Int, value: String, label: String, modifier: Modifier = Modifier) {
    // Tight padding and SemiBold 13 so Figma's "6 guests" / "Nov 1 – 15" fit three tiles on a 360dp phone.
    Column(modifier.clip(EventsShapes.Medium).background(EventsColors.SurfaceSoft).padding(horizontal = 10.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            EventsIcon(icon, tint = EventsColors.TextSecondary, size = 15.dp)
            Text(value, style = EventsType.BodySmallStrong, color = EventsColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(label, style = EventsType.Caption, color = EventsColors.TextSecondary, maxLines = 1)
    }
}

/** Label / value row: 16dp icon, Regular 12 label, SemiBold 12 value (lottery card rows). */
@Composable
fun DetailRow(@DrawableRes icon: Int, label: String, value: String, labelColor: Color = EventsColors.OnPrimaryContainer) {
    Row(Modifier.fillMaxWidth().height(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EventsIcon(icon, tint = labelColor, size = 16.dp)
        Text(label, style = EventsType.BodySmall, color = labelColor, modifier = Modifier.weight(1f), maxLines = 1)
        Text(value, style = EventsType.BodySmallStrong, color = EventsColors.Text, textAlign = TextAlign.End, maxLines = 1)
    }
}

/** Icon + text meta line (location, guests, email): 16dp icon, Regular 12. */
@Composable
fun MetaItem(@DrawableRes icon: Int, text: String, color: Color = EventsColors.TextSecondary, modifier: Modifier = Modifier, iconTint: Color = color) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        EventsIcon(icon, tint = iconTint, size = 16.dp)
        Text(text, style = EventsType.BodySmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Section header: Fraunces SemiBold 18/26, with an optional trailing action. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = EventsType.Title, color = EventsColors.Text, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Friendly empty state with an icon tile, title and message. */
@Composable
fun EmptyState(@DrawableRes icon: Int, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(EventsColors.PrimaryContainer), contentAlignment = Alignment.Center) {
            EventsIcon(icon, size = 28.dp)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = EventsType.Title, color = EventsColors.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = EventsType.BodySmall, color = EventsColors.TextSecondary, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/** Avatar: #4E2F5C circle with a Dusk outline and white SemiBold initials. */
@Composable
fun Avatar(initials: String, modifier: Modifier = Modifier, size: Dp = 36.dp, bg: Color = EventsColors.CoralContainer, fg: Color? = null) {
    Box(
        modifier.size(size).clip(CircleShape).background(bg).border(if (size > 40.dp) 3.dp else 2.dp, EventsColors.Surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials.ifBlank { "?" }, style = if (size > 40.dp) EventsType.TitleSmall else EventsType.LabelSmall, color = fg ?: if (size > 40.dp) EventsColors.Coral else EventsColors.Text)
    }
}

/** Icon tile used in settings rows: 36dp, radius 12, #3E3A8A. */
@Composable
fun IconTile(@DrawableRes icon: Int, modifier: Modifier = Modifier, bg: Color = EventsColors.PrimaryContainer, tint: Color = EventsColors.Text) {
    Box(modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) { EventsIcon(icon, tint = tint, size = 20.dp) }
}

/** A row reserved for content that hugs the bottom of a column. */
@Composable
fun RowScope.Fill() = Spacer(Modifier.weight(1f))

/** The status tag for a lottery at time [now]. */
@Composable
fun LotteryStatusTag(lottery: Lottery?, now: Long, modifier: Modifier = Modifier, openTone: TagTone = TagTone.GREEN) {
    when {
        lottery == null -> StatusTag("No lottery", TagTone.GREY, modifier)
        lottery.status == LotteryStatus.DRAWN -> StatusTag("Winners drawn", TagTone.CORAL, modifier)
        lottery.isUpcoming(now) -> StatusTag("Opens ${formatDay(lottery.registrationStart)}", TagTone.AMBER, modifier)
        lottery.isRegistrationOpen(now) -> StatusTag(closesIn(lottery, now), openTone, modifier)
        else -> StatusTag("Drawing winners", TagTone.AMBER, modifier)
    }
}

/** Rating line: star, SemiBold 12 score, Regular 12 count in tertiary. */
@Composable
fun RatingLine(rating: Double, count: Int, countLabel: String = "($count)") {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        EventsIcon(R.drawable.ic_star, size = 16.dp, tint = EventsColors.Star)
        Text("%.1f".format(rating), style = EventsType.BodySmallStrong, color = EventsColors.Text)
        Text(countLabel, style = EventsType.BodySmall, color = EventsColors.TextTertiary)
    }
}

/**
 * Campsite list card (Figma search result): 104dp tall, 84dp thumbnail (radius 16),
 * name SemiBold 14, location and "Max N" with rating, chevron. Summary for US 01.01.04.
 */
@Composable
fun CampsiteListCard(campsite: Campsite, onClick: () -> Unit, modifier: Modifier = Modifier, thumb: Dp = 84.dp, distance: String? = null, status: (@Composable () -> Unit)? = null) {
    DuskCard(
        modifier.fillMaxWidth().testTag("campsite_card_${campsite.id}"),
        onClick = onClick,
        padding = PaddingValues(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (thumb < 80.dp) 12.dp else 14.dp)) {
            Box(Modifier.size(thumb).clip(EventsShapes.Medium)) { CampsiteImage(campsite.id, campsite.imageBase64) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(campsite.name, style = EventsType.BodyStrong, color = EventsColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                MetaItem(R.drawable.ic_location, campsite.location)
                if (status != null) {
                    status()
                } else {
                    // Figma E-SEARCH: "Max 6  ★ 4.8 (126)" on one row.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetaItem(R.drawable.ic_group, "Max ${campsite.maxOccupancy}")
                        if (campsite.ratingCount > 0) RatingLine(campsite.rating, campsite.ratingCount)
                        else Text("New", style = EventsType.Caption, color = EventsColors.TextTertiary)
                    }
                    if (distance != null) MetaItem(R.drawable.ic_my_location, distance, EventsColors.TextTertiary)
                }
            }
            EventsIcon(R.drawable.ic_chevron_right, tint = EventsColors.TextSecondary, size = 20.dp)
        }
    }
}

/** Featured lottery card from the Explore carousel: 290x282, radius 24, scene art, info panel. */
@Composable
fun FeaturedLotteryCard(campsite: Campsite, lottery: Lottery, now: Long, onClick: () -> Unit, modifier: Modifier = Modifier, distance: String? = null) {
    Box(modifier.size(width = 290.dp, height = 282.dp).clip(EventsShapes.Large).background(EventsColors.Surface).testTag("featured_${campsite.id}")) {
        CampsiteImage(campsite.id, campsite.imageBase64, Modifier.fillMaxSize())
        LotteryStatusTag(lottery, now, Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 14.dp), openTone = TagTone.SOLID)
        if (distance != null) Row(
            Modifier.align(Alignment.TopStart).padding(top = 16.dp, start = 14.dp).height(24.dp).clip(EventsShapes.Pill)
                .background(EventsColors.SkyTop.copy(alpha = 0.7f)).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EventsIcon(R.drawable.ic_my_location, tint = EventsColors.Text, size = 14.dp)
            Text(distance, style = EventsType.Caption, color = EventsColors.Text)
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
                .fillMaxWidth()
                .clip(EventsShapes.Medium)
                .background(EventsColors.Bar)
                .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(campsite.name, style = EventsType.TitleSmall, color = EventsColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Figma: coral meta icons; the guest count never truncates, the location gives way.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetaItem(R.drawable.ic_location, campsite.location, EventsColors.TextSoft, Modifier.weight(1f, fill = false), iconTint = EventsColors.Coral)
                    MetaItem(R.drawable.ic_group, "Up to ${campsite.maxOccupancy}", EventsColors.TextSoft, iconTint = EventsColors.Coral)
                }
            }
            PrimaryButton("View", onClick, size = ButtonSize.SMALL)
        }
    }
}

/** Carousel dots: active 18x6, inactive 6x6 at 35% white, 6dp apart. */
@Composable
fun CarouselDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(Modifier.size(width = if (i == current) 18.dp else 6.dp, height = 6.dp).clip(RoundedCornerShape(3.dp)).background(if (i == current) Color.White else Color(0x59FFFFFF)))
        }
    }
}

/** Thin divider (#4A4790) used inside cards. */
@Composable
fun CardDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(EventsColors.Outline))
}
