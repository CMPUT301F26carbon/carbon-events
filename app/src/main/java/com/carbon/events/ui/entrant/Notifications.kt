/*
 * Notifications.kt (Suvir)
 *
 * The notifications list: draw results, owners' messages and updates, newest first.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Keep flows alive for 5s after the screen goes away (survives rotation). */
private val sharing = SharingStarted.WhileSubscribed(5_000)

/**
 * Notifications (Figma E-NOTIFS, E-NOTIFS-CHAT): results, spots, warnings and messages from
 * owners (tap to reply), newest first, then "Manage notification preferences in Profile".
 */
@Composable
fun NotificationsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onChat: (Chat) -> Unit = {},
    onManage: () -> Unit = {},
) {
    val vm = containerViewModel(container) { NotificationsViewModel(it) }
    val stored by vm.notifications.collectAsStateWithLifecycle()
    val chats by vm.chats.collectAsStateWithLifecycle()
    val me = container.userId
    // WhatsApp-style: one row per conversation ("Two Jack Lakeside – from Priya", latest message,
    // unread count) mixed with app updates such as "You won", newest first.
    val rows = stored?.let { notes -> buildInbox(me, chats.filter { it.lastText.isNotBlank() }, notes) }
    val list = stored
    val chatById = chats.associateBy { it.id }
    val highlighted by vm.highlighted.collectAsStateWithLifecycle()
    val now = container.clock.now()
    LaunchedEffect(list) { list?.let { vm.onOpened(it) } }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackTopBar("Notifications", onBack)
        LazyColumn(contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (rows?.isEmpty() == true) {
                item { DuskCard { EmptyState(R.drawable.ic_bell, "You’re all caught up", "Lottery results, messages from owners and new lotteries at campsites you follow show up here.") } }
            }
            val (today, earlier) = rows.orEmpty().partition { isToday(it.at, now) }
            val row: @Composable (InboxRow) -> Unit = { r ->
                val n = r.notification
                if (n != null) NotificationCard(n, n.id in highlighted || !n.read, now) { onOpen(n.campsiteId) }
                else InboxChatRow(r, now) { chatById[r.chatId]?.let(onChat) }
            }
            if (today.isNotEmpty()) item { GroupLabel("Today") }
            items(today, key = { it.key }) { row(it) }
            if (earlier.isNotEmpty()) item { GroupLabel("Earlier") }
            items(earlier, key = { it.key }) { row(it) }
            item {
                Row(
                    Modifier.fillMaxWidth().clip(EventsShapes.Medium).clickable(onClick = onManage).padding(vertical = 8.dp).testTag("notifs_manage"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EventsIcon(R.drawable.ic_info, size = 16.dp, tint = EventsColors.Coral)
                    Text("Manage notification preferences in Profile", style = EventsType.BodySmall, color = EventsColors.Coral)
                }
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) = Text(text, style = EventsType.LabelSmall, color = EventsColors.TextSoft)

/** One conversation in the bell list: who it's with, the latest message, time and unread count. */
@Composable
fun InboxChatRow(r: InboxRow, now: Long, onClick: () -> Unit) {
    DuskCard(
        Modifier.fillMaxWidth().testTag("inbox_${r.chatId}"),
        onClick = onClick,
        padding = PaddingValues(14.dp),
        borderColor = if (r.unread > 0) EventsColors.Primary else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(R.drawable.ic_chat, bg = EventsColors.CoralContainer, tint = EventsColors.Coral)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(r.title, style = EventsType.BodyStrong, color = EventsColors.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(relativeTime(r.at, now), style = EventsType.Caption, color = if (r.unread > 0) EventsColors.Coral else EventsColors.TextTertiary)
                }
                if (r.subtitle.isNotBlank()) Text(r.subtitle, style = EventsType.Caption, color = EventsColors.TextTertiary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(r.preview, style = EventsType.BodySmall, color = EventsColors.TextSecondary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (r.unread > 0) CountBadge(if (r.unread > 99) "99+" else r.unread.toString())
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(n: AppNotification, isNew: Boolean, now: Long, onClick: () -> Unit) {
    val won = n.type == NotificationType.LOTTERY_WON || n.type == NotificationType.SPOT_OPENED
    val icon = when (n.type) {
        NotificationType.LOTTERY_WON, NotificationType.SPOT_OPENED -> R.drawable.ic_trophy
        NotificationType.LOTTERY_LOST -> R.drawable.ic_ticket
        NotificationType.REJECTED, NotificationType.WARNING -> R.drawable.ic_warning
        NotificationType.MESSAGE -> R.drawable.ic_chat
        NotificationType.OPENED -> R.drawable.ic_bell
        NotificationType.CANCELLED -> R.drawable.ic_event_check
        NotificationType.REMOVED -> R.drawable.ic_delete
    }
    DuskCard(
        Modifier.fillMaxWidth().testTag("notification_${n.type.name}"),
        onClick = onClick,
        padding = PaddingValues(14.dp),
        borderColor = if (isNew) EventsColors.Primary else null,
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Figma: icons sit in tinted rounded-square tiles, coloured by kind.
            Box(
                Modifier.size(40.dp).clip(EventsShapes.Medium).background(
                    when {
                        won -> EventsColors.GreenContainer
                        n.type == NotificationType.REJECTED || n.type == NotificationType.WARNING -> EventsColors.DangerContainer
                        n.type == NotificationType.MESSAGE -> EventsColors.CoralContainer
                        else -> EventsColors.SurfaceSoft
                    },
                ),
                contentAlignment = Alignment.Center,
            ) { EventsIcon(icon, size = 20.dp) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(n.title, style = EventsType.BodyStrong, color = EventsColors.Text, modifier = Modifier.weight(1f))
                    Text(relativeTime(n.createdAt, now), style = EventsType.Caption, color = EventsColors.TextTertiary)
                }
                Text(n.body, style = EventsType.BodySmall, color = EventsColors.TextSecondary)
            }
        }
    }
}

/** Notifications for the current user; marks them read when the screen is opened. */
class NotificationsViewModel(private val container: AppContainer) : ViewModel() {
    /** Notifications, newest first (null while loading). */
    val notifications: StateFlow<List<AppNotification>?> = container.notifications.observeNotifications(container.userId)
        .stateIn(viewModelScope, sharing, null)

    /** Conversations, for "Message from …" rows. */
    val chats: StateFlow<List<Chat>> = container.social.observeChatsFor(container.userId).stateIn(viewModelScope, sharing, emptyList())

    private val _snapshotUnread = MutableStateFlow<Set<String>>(emptySet())

    /** IDs that were unread when the screen opened, so they can stay highlighted. */
    val highlighted: StateFlow<Set<String>> = _snapshotUnread.asStateFlow()

    /** Call when the screen opens: remembers what was new, then marks everything read. */
    fun onOpened(current: List<AppNotification>) {
        if (_snapshotUnread.value.isEmpty()) _snapshotUnread.value = current.filter { !it.read }.map { it.id }.toSet()
        if (current.any { !it.read }) viewModelScope.launch { runCatching { container.notifications.markAllRead(container.userId) } }
    }
}
