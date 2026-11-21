/*
 * Buttons.kt (Aditya)
 *
 * Buttons from our Figma: primary, secondary, danger, text links and round glass icons.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Button sizes from the Figma Button component. */
enum class ButtonSize(val height: Dp, val padding: Dp, val icon: Dp) { LARGE(52.dp, 28.dp, 20.dp), SMALL(40.dp, 18.dp, 18.dp) }

@Composable
private fun EventsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    container: Color,
    content: Color,
    @DrawableRes icon: Int?,
    enabled: Boolean,
    loading: Boolean,
    size: ButtonSize,
    border: BorderStroke? = null,
    glow: Color? = null,
) {
    val shadowMod = if (glow != null && enabled && size == ButtonSize.LARGE) {
        Modifier.shadow(12.dp, EventsShapes.Pill, ambientColor = glow, spotColor = glow)
    } else Modifier
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.then(shadowMod).height(size.height),
        shape = EventsShapes.Pill,
        border = border,
        colors = ButtonDefaults.buttonColors(
            containerColor = container, contentColor = content,
            disabledContainerColor = container.copy(alpha = container.alpha * 0.45f), disabledContentColor = content.copy(alpha = 0.6f),
        ),
        contentPadding = PaddingValues(horizontal = size.padding),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = content, strokeWidth = 2.dp)
        } else {
            if (icon != null) {
                EventsIcon(icon, tint = content, size = size.icon)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = if (size == ButtonSize.LARGE) EventsType.Label else EventsType.LabelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Primary button: coral #FFB4A6 with plum text and a soft coral glow. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null, enabled: Boolean = true, loading: Boolean = false, size: ButtonSize = ButtonSize.LARGE) =
    EventsButton(text, onClick, modifier, EventsColors.Coral, EventsColors.OnCoral, icon, enabled, loading, size, glow = Color(0x59E57373))

/** Tonal (frosted) button: white 20% with a 14% white outline. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null, enabled: Boolean = true, size: ButtonSize = ButtonSize.LARGE) =
    EventsButton(text, onClick, modifier, EventsColors.Tint, EventsColors.Text, icon, enabled, false, size, BorderStroke(1.dp, Color(0x24FFFFFF)))

/** Destructive button: solid red with white text. */
@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null, loading: Boolean = false, size: ButtonSize = ButtonSize.LARGE, enabled: Boolean = true) =
    EventsButton(text, onClick, modifier, EventsColors.Danger, Color.White, icon, enabled, loading, size, glow = Color(0x4DF2667A))

/** Text-only button (e.g. "I already have an account", lottery "Edit"). */
@Composable
fun TextLinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = EventsColors.Primary) {
    Text(
        text, style = EventsType.LabelSmall, color = color,
        modifier = modifier.clip(EventsShapes.Pill).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
    )
}

/** Round glass icon button: 44dp, #121846 at 38% with a 16% white outline, 22dp icon. */
@Composable
fun GlassIconButton(@DrawableRes icon: Int, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, badge: Boolean = false, badgeText: String? = null) {
    // The circle is drawn by an inner box so the count badge can sit over its edge without being cut off.
    Box(modifier.size(44.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.matchParentSize().clip(CircleShape).background(Color(0x61121846)).border(1.dp, Color(0x29FFFFFF), CircleShape)
                .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        )
        EventsIcon(icon, contentDescription = contentDescription)
        if (badgeText != null) CountBadge(badgeText, Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp))
        else if (badge) Box(Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 11.dp).size(8.dp).clip(CircleShape).background(EventsColors.Coral))
    }
}

/** A small red pill with a count, for unread messages. */
@Composable
fun CountBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.defaultMinSize(minWidth = 18.dp, minHeight = 18.dp).clip(CircleShape).background(EventsColors.Danger).padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = EventsType.Caption, color = Color.White, maxLines = 1) }
}
