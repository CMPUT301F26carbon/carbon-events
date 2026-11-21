/*
 * RateStay.kt (Suvir)
 *
 * Rating a campsite after a stay: the stars and the rating card.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** Five tappable stars. */
@Composable
fun StarRow(stars: Int, onChange: (Int) -> Unit, size: Int = 32, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 1..5) {
            Box(Modifier.size(size.dp).clickable { onChange(i) }.testTag("star_$i"), contentAlignment = Alignment.Center) {
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
    var stars by remember { mutableIntStateOf(current?.stars ?: 0) }
    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(EventsColors.SkyTop.copy(alpha = 0.55f)).clickable(onClick = onDismiss), contentAlignment = Alignment.BottomCenter) {
            RateCard(campsiteName, stayed, stars, { stars = it }, { onSubmit(stars, current?.comment.orEmpty()) }, onDismiss)
        }
    }
}

/** The card inside [RateDialog]. */
@Composable
fun RateCard(campsiteName: String, stayed: String?, stars: Int, onStars: (Int) -> Unit, onSubmit: () -> Unit, onNotNow: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(EventsColors.Surface).clickable(enabled = false) {}.navigationBarsPadding().padding(PageMargin).testTag("rate_dialog"),
        verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width = 40.dp, height = 4.dp).clip(EventsShapes.Pill).background(EventsColors.TextTertiary))
        Text(if (campsiteName.isBlank()) "Rate your stay" else "Rate $campsiteName", style = EventsType.Title, color = EventsColors.Text)
        stayed?.let { MetaItem(R.drawable.ic_calendar, "You stayed $it", EventsColors.TextSecondary) }
        StarRow(stars, onStars, size = 40)
        Text(if (stars == 0) "Tap a star to rate" else "$stars of 5 stars · tap a star to change", style = EventsType.BodySmall, color = EventsColors.TextSecondary)
        PrimaryButton("Submit rating", onSubmit, Modifier.fillMaxWidth().testTag("rate_submit"), enabled = stars > 0)
        TextLinkButton("Not now", onNotNow, color = EventsColors.TextSecondary)
    }
}
