/*
 * ChatReactionViews.kt (Tausif)
 *
 * Emoji reactions in a chat: the row of six that opens when a message is long-pressed, and the
 * small counts under a message ("👍 2"), which add or take off your own reaction when tapped.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The six reactions on offer, shown over a long-pressed message. [mine] is the one this person
 * already chose (ringed); picking it again takes it off.
 */
@Composable
fun ReactionBar(messageId: String, mine: String?, onPick: (String) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(24.dp)).background(EventsColors.Surface).border(1.dp, EventsColors.TintStroke, RoundedCornerShape(24.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp).testTag("reaction_bar_$messageId"),
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatReactions.CHOICES.forEachIndexed { i, emoji ->
            val chosen = emoji == mine
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (chosen) EventsColors.PrimaryContainer else EventsColors.Surface)
                    .clickable { onPick(emoji) }
                    .semantics { contentDescription = "React $emoji"; selected = chosen }.testTag("reaction_pick_$i"),
                contentAlignment = Alignment.Center,
            ) { Text(emoji, fontSize = 22.sp) }
        }
    }
}

/**
 * The reactions on a message, one pill per emoji with how many chose it. The pill with this
 * person's own reaction is highlighted; tapping a pill adds or takes off their reaction.
 */
@Composable
fun ReactionPills(messageId: String, reactions: Map<String, String>, me: String, onToggle: (String) -> Unit, modifier: Modifier = Modifier) {
    val counts = ChatReactions.counts(reactions)
    if (counts.isEmpty()) return
    Row(modifier.testTag("reactions_$messageId"), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        counts.forEach { (emoji, n) ->
            val mine = reactions[me] == emoji
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(if (mine) EventsColors.PrimaryContainer else EventsColors.Surface)
                    .border(1.dp, if (mine) EventsColors.Primary else EventsColors.TintStroke, RoundedCornerShape(12.dp))
                    .clickable { onToggle(emoji) }.padding(horizontal = 8.dp, vertical = 2.dp)
                    .semantics(mergeDescendants = true) { contentDescription = "$emoji $n"; selected = mine }
                    .testTag("reaction_${messageId}_${ChatReactions.CHOICES.indexOf(emoji)}"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(emoji, fontSize = 14.sp)
                Text("$n", style = EventsType.Caption, color = EventsColors.Text)
            }
        }
    }
}
