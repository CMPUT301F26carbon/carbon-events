/*
 * RateStay.kt (Suvir)
 *
 * Rating and reviewing a campsite after a stay: the stars, the review box and the rating card.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Five stars. With [onChange] each star can be tapped (and is at least 48dp to tap); without it
 * the row only shows a rating, isn't tappable, and reads as "4 of 5 stars" to a screen reader.
 */
@Composable
fun StarRow(stars: Int, onChange: ((Int) -> Unit)?, size: Int = 32, modifier: Modifier = Modifier) {
    if (onChange == null) {
        Row(modifier.clearAndSetSemantics { contentDescription = "$stars of 5 stars" }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 1..5) EventsIcon(R.drawable.ic_star, tint = if (i <= stars) EventsColors.Star else EventsColors.Outline, size = size.dp)
        }
        return
    }
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 1..5) {
            Box(
                Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).size(size.dp)
                    .selectable(selected = i == stars, role = Role.RadioButton) { onChange(i) }
                    .semantics { contentDescription = if (i == 1) "1 star" else "$i stars" }.testTag("star_$i"),
                contentAlignment = Alignment.Center,
            ) {
                EventsIcon(R.drawable.ic_star, tint = if (i <= stars) EventsColors.Star else EventsColors.Outline, size = size.dp)
            }
        }
    }
}

/**
 * Rate a campsite (Figma E-RATE): a sheet with the stay dates, five stars, "n of 5 stars" and
 * Submit rating / Not now.
 *
 * @param stayed the stay being rated, e.g. "Sep 12 – 14, 2026", or null.
 */
@Composable
fun RateDialog(current: Rating?, onDismiss: () -> Unit, campsiteName: String = "", stayed: String? = null, onSubmit: (Int, String) -> Unit) {
    var stars by rememberSaveable { mutableIntStateOf(current?.stars ?: 0) }
    var review by rememberSaveable { mutableStateOf(current?.comment.orEmpty()) }
    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        // Tapping the dimmed sky above the card closes it; a tap handler (not clickable) keeps the
        // card's stars and review box separate for screen readers.
        Box(Modifier.fillMaxSize().background(EventsColors.SkyTop.copy(alpha = 0.55f)).pointerInput(Unit) { detectTapGestures { onDismiss() } }, contentAlignment = Alignment.BottomCenter) {
            RateCard(campsiteName, stayed, stars, { stars = it }, { onSubmit(stars, review.trim()) }, onDismiss, review, { review = it.take(RatingPolicy.MAX_COMMENT) })
        }
    }
}

/** The card inside [RateDialog]: stars, then an optional written review of up to [RatingPolicy.MAX_COMMENT] characters. */
@Composable
fun RateCard(
    campsiteName: String, stayed: String?, stars: Int, onStars: (Int) -> Unit, onSubmit: () -> Unit, onNotNow: () -> Unit,
    review: String = "", onReview: (String) -> Unit = {},
) {
    Column(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(EventsColors.Surface).pointerInput(Unit) { detectTapGestures { } }.imePadding().navigationBarsPadding().padding(PageMargin).testTag("rate_dialog"),
        verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width = 40.dp, height = 4.dp).clip(EventsShapes.Pill).background(EventsColors.TextTertiary))
        Text(if (campsiteName.isBlank()) "Rate your stay" else "Rate $campsiteName", style = EventsType.Title, color = EventsColors.Text)
        stayed?.let { MetaItem(R.drawable.ic_calendar, "You stayed $it", EventsColors.TextSecondary) }
        StarRow(stars, onStars, size = 40)
        Text(if (stars == 0) "Tap a star to rate" else "$stars of 5 stars · tap a star to change", style = EventsType.BodySmall, color = EventsColors.TextSecondary)
        EventsTextField(
            review, onReview, "Your review (optional)", Modifier.fillMaxWidth().testTag("rate_review"),
            placeholder = "What was your stay like?", singleLine = false, minLines = 3,
        )
        Text("${review.length} / ${RatingPolicy.MAX_COMMENT}", style = EventsType.Caption, color = EventsColors.TextTertiary, modifier = Modifier.align(Alignment.End))
        PrimaryButton("Submit rating", onSubmit, Modifier.fillMaxWidth().testTag("rate_submit"), enabled = stars > 0)
        TextLinkButton("Not now", onNotNow, color = EventsColors.TextSecondary)
    }
}
