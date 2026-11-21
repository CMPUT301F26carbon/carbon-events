/*
 * Bars.kt (Aditya)
 *
 * The bottom nav bar, the top bar with Back, the bottom action bar and the confirm dialog.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** One destination in the bottom navigation bar. */
data class NavItem(val route: String, val label: String, @param:DrawableRes val icon: Int, val raised: Boolean = false)

private val BarShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

/**
 * The Dusk Violet nav bar from the Figma "Nav Bar" component. A [NavItem.raised] item is
 * the 52dp coral-to-orchid gradient button with a 3dp bar-coloured ring, sitting 1dp
 * below the bar's top edge (it does not stick out above the bar).
 */
@Composable
fun EventsNavBar(items: List<NavItem>, selectedRoute: String?, onSelect: (NavItem) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(24.dp, BarShape, ambientColor = Color(0x471F1459), spotColor = Color(0x471F1459))
            .clip(BarShape)
            .background(EventsColors.Bar)
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 8.dp)) {
            for (item in items) {
                if (item.raised) RaisedItem(item) { onSelect(item) } else TabItem(item, item.route == selectedRoute) { onSelect(item) }
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .height(80.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab, onClickLabel = item.label, onClick = onClick)
            .testTag("nav_${item.label}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(width = 56.dp, height = 30.dp).clip(RoundedCornerShape(15.dp)).background(if (selected) EventsColors.Pill else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) { EventsIcon(item.icon, tint = if (selected) Color.White else EventsColors.OnBar, size = 24.dp) }
        Spacer(Modifier.height(4.dp))
        Text(item.label, style = if (selected) EventsType.LabelSmall else EventsType.Caption, color = if (selected) Color.White else EventsColors.OnBar, maxLines = 1)
    }
}

@Composable
private fun RowScope.RaisedItem(item: NavItem, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .height(80.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = item.label, onClick = onClick)
            .testTag("nav_${item.label}")
            .padding(top = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .shadow(10.dp, CircleShape, ambientColor = Color(0x594D1F66), spotColor = Color(0x594D1F66))
                .size(52.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFFF59A92), Color(0xFFC77DB5))))
                .border(3.dp, EventsColors.Bar, CircleShape),
            contentAlignment = Alignment.Center,
        ) { EventsIcon(item.icon, tint = Color.White, size = 24.dp) }
        Spacer(Modifier.height(3.dp))
        Text(item.label, style = EventsType.Caption, color = EventsColors.OnBar)
    }
}

/** Top bar: 44dp glass back (or close) button and a Fraunces 18/26 title, 8dp apart. */
@Composable
fun BackTopBar(
    title: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.ic_arrow_back,
    actions: @Composable () -> Unit = {},
) {
    Row(modifier.fillMaxWidth().padding(horizontal = PageMargin).padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton(icon, if (icon == R.drawable.ic_close) "Close" else "Back", onBack)
        if (title != null) {
            Spacer(Modifier.width(8.dp))
            Text(title, style = EventsType.Title, color = EventsColors.Text, modifier = Modifier.weight(1f), maxLines = 1)
        } else {
            Spacer(Modifier.weight(1f))
        }
        actions()
    }
}

/** Bottom action bar (Figma "Action Bar"): 88dp Dusk Violet, 24dp top corners, 16dp padding, 12dp gaps. */
@Composable
fun ActionBar(modifier: Modifier = Modifier, header: (@Composable () -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .shadow(24.dp, BarShape, ambientColor = Color(0x471F1459), spotColor = Color(0x471F1459))
            .clip(BarShape)
            .background(EventsColors.Bar)
            .navigationBarsPadding()
            .padding(horizontal = PageMargin, vertical = 18.dp),
    ) {
        header?.invoke()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/**
 * Dialog (Figma): 312dp, Dusk surface, radius 28, 24dp padding, 12dp gaps. The icon tile is
 * a 56dp lavender circle, or for destructive actions a 52dp red-tinted rounded tile. Title
 * Fraunces 18/26, message Regular 12/18, then a tonal cancel and a primary / danger confirm.
 */
@Composable
fun ConfirmDialog(
    @DrawableRes icon: Int,
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismissText: String = "Cancel",
    loading: Boolean = false,
    extra: (@Composable () -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(312.dp)
                .clip(EventsShapes.XLarge)
                .background(EventsColors.Surface)
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 20.dp)
                .testTag("confirm_dialog"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (destructive) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(EventsColors.Danger.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    EventsIcon(icon, tint = EventsColors.Danger, size = 26.dp)
                }
            } else {
                Box(Modifier.size(56.dp).clip(CircleShape).background(EventsColors.PrimaryContainer), contentAlignment = Alignment.Center) {
                    EventsIcon(icon, size = 28.dp)
                }
            }
            Text(title, style = EventsType.Title, color = EventsColors.Text, textAlign = TextAlign.Center)
            Text(message, style = EventsType.BodySmall, color = EventsColors.TextSecondary, textAlign = TextAlign.Center)
            extra?.invoke()
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(dismissText, onDismiss, Modifier.weight(1f))
                if (destructive) DangerButton(confirmText, onConfirm, Modifier.weight(1f), loading = loading)
                else PrimaryButton(confirmText, onConfirm, Modifier.weight(1f), loading = loading)
            }
        }
    }
}
