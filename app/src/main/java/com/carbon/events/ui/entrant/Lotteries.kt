/*
 * Lotteries.kt (Suvir)
 *
 * My Lotteries and notifications: waitlists I joined, draw results, the phone notifications
 * for wins and losses, and for winners: booking their nights and the Bookings tab.
 *
 * Outstanding issues: with the app closed, notifications arrive through BackgroundSync about
 * every 15 minutes rather than instantly (no push server).
 */
package com.carbon.events

import androidx.compose.foundation.clickable

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.carbon.events.MainActivity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The entrant's lotteries: waiting lists they are on, and draw results (Figma E-LOTTERIES). */
@Composable
fun MyLotteriesScreen(container: AppContainer, onOpen: (String) -> Unit, onExplore: () -> Unit, onNotifications: () -> Unit, onBook: (String) -> Unit = {}, onChat: (Chat) -> Unit = {}) {
    val vm = containerViewModel(container) { MyLotteriesViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    val items = if (tab == 0) state.active else state.results
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("My Lotteries", style = EventsType.Headline, color = EventsColors.Text, modifier = Modifier.weight(1f))
                BellButton(container, onNotifications)
            }
        }
        item { SegmentedControl(listOf("Active · ${state.active.size}", "Results · ${state.results.size}"), tab, { tab = it }) }
        if (!state.loading && items.isEmpty()) {
            item {
                DuskCard {
                    EmptyState(
                        R.drawable.ic_ticket,
                        if (tab == 0) "No waiting lists yet" else "No results yet",
                        if (tab == 0) "Join a lottery from any campsite’s details page." else "Results appear here after each draw.",
                    ) { if (tab == 0) PrimaryButton("Explore campsites", onExplore) }
                }
            }
        }
        items(items, key = { it.entry.lotteryId }) { item ->
            val lot = item.lottery!!
            DuskCard(Modifier.fillMaxWidth().testTag("my_lottery_${lot.id}"), onClick = { onOpen(lot.campsiteId) }, padding = PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(60.dp).clip(EventsShapes.Medium)) { CampsiteImage(lot.campsiteId, item.campsite?.imageBase64) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(lot.campsiteName, style = EventsType.BodyStrong, color = EventsColors.Text, maxLines = 1)
                        MetaItem(
                            R.drawable.ic_clock,
                            when {
                                item.entry.status != EntryStatus.WAITING -> "Drawn ${formatDay(lot.drawnAt ?: lot.registrationEnd)}"
                                lot.isDrawDue(state.now) -> "Closed · waiting for the owner to draw"
                                else -> "${closesIn(lot, state.now).replace("Closes", "Draw")} · ${lot.entrantCount} entered"
                            },
                        )
                        when (item.entry.status) {
                            // Figma E-LOTTERIES: "IN THE DRAW" while waiting (or "Opens …" before registration).
                            EntryStatus.WAITING -> if (lot.isUpcoming(state.now)) LotteryStatusTag(lot, state.now, openTone = TagTone.BLUE) else StatusTag("In the draw", TagTone.BLUE)
                            EntryStatus.WON -> StatusTag("You won!", TagTone.GREEN)
                            EntryStatus.LOST -> StatusTag("Not selected", TagTone.GREY)
                            EntryStatus.DECLINED -> StatusTag("Declined", TagTone.AMBER)
                            EntryStatus.CANCELLED -> StatusTag("Cancelled", TagTone.AMBER)
                            EntryStatus.REJECTED -> StatusTag("Rejected", TagTone.RED)
                        }
                    }
                    // The conversation with this campsite's owner: notices for everyone, replies once you win.
                    item.campsite?.let { site ->
                        Box(
                            Modifier.size(40.dp).clip(EventsShapes.Medium).background(EventsColors.CoralContainer)
                                .clickable {
                                    onChat(Chat(Chat.idFor(site.id, container.userId), site.id, site.name, site.ownerId, site.ownerName,
                                        container.userId, item.entry.entrantName, lotteryKey = Paths.keyOf(lot.id)))
                                }.testTag("lottery_chat_${lot.id}"),
                            contentAlignment = Alignment.Center,
                        ) { EventsIcon(R.drawable.ic_chat, size = 20.dp, tint = EventsColors.Coral, contentDescription = "Messages from ${site.ownerName}") }
                    }
                }
                if (item.entry.status == EntryStatus.LOST && lot.status == LotteryStatus.DRAWN) {
                    Text("If a winner gives their spot back, you could still be drawn.", style = EventsType.Caption, color = EventsColors.TextTertiary)
                }
                if (item.entry.status == EntryStatus.WON) {
                    if (item.entry.isBooked) {
                        MetaItem(R.drawable.ic_event_check, "Booked · ${formatStay(item.entry.checkInDay!!, item.entry.checkOutDay!!)}", color = EventsColors.Green)
                    } else {
                        PrimaryButton("Choose your dates", { onBook(lot.id) }, Modifier.fillMaxWidth().testTag("book_${lot.id}"), icon = R.drawable.ic_calendar, size = ButtonSize.SMALL)
                    }
                }
            }
        }
    }
}

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

/**
 * One row of My Lotteries.
 *
 * @property entry the entrant's waiting-list entry.
 * @property lottery the lottery it belongs to (null if it was removed).
 * @property campsite the lottery's campsite, for the photo.
 */
data class MyLotteryItem(val entry: WaitlistEntry, val lottery: Lottery?, val campsite: Campsite?)

/** State of the My Lotteries screen. */
data class MyLotteriesState(val loading: Boolean = true, val active: List<MyLotteryItem> = emptyList(), val results: List<MyLotteryItem> = emptyList(), val now: Long = 0L)

/** Lotteries the entrant has entered, split into active and results. */
class MyLotteriesViewModel(container: AppContainer) : ViewModel() {
    /** My Lotteries state. */
    val state: StateFlow<MyLotteriesState> = combine(
        container.lotteries.observeEntriesForEntrant(container.userId),
        container.lotteries.observeLotteries(),
        container.campsites.observeCampsites(),
        nowTicker(container.clock),
    ) { entries, lotteries, sites, now ->
        val lotById = lotteries.associateBy { it.id }
        val siteById = sites.associateBy { it.id }
        val items = entries.map { e -> MyLotteryItem(e, lotById[e.lotteryId], lotById[e.lotteryId]?.let { siteById[it.campsiteId] }) }
            .filter { it.lottery != null }
        MyLotteriesState(
            loading = false,
            active = items.filter { it.entry.status == EntryStatus.WAITING }.sortedBy { it.lottery!!.registrationEnd },
            results = items.filter { it.entry.status != EntryStatus.WAITING }.sortedByDescending { it.lottery!!.drawnAt ?: 0L },
            now = now,
        )
    }.stateIn(viewModelScope, sharing, MyLotteriesState())
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

/** Posts system notifications for new in-app notifications. */
object LocalNotifier {
    private const val CHANNEL_ID = "lottery_results"
    private const val PREFS = "local_notifier"
    private const val KEY_SHOWN = "shown_ids"

    /** Creates the "Lottery results" channel (safe to call repeatedly). */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, context.getString(R.string.notification_channel_lottery), NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Whether the app may post notifications (Android 13+ asks the user). */
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * Shows a system notification for every unread item in [items] not shown before.
     * Tapping one opens the app.
     */
    fun showNew(context: Context, items: List<AppNotification>, allowed: (AppNotification) -> Boolean = { true }) {
        if (!hasPermission(context)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val shown = prefs.getStringSet(KEY_SHOWN, emptySet()).orEmpty()
        val unseen = items.filter { !it.read && it.id !in shown }
        if (unseen.isEmpty()) return
        // Everything unseen is marked shown, so switching a type back on doesn't replay old ones.
        prefs.edit().putStringSet(KEY_SHOWN, shown + unseen.map { it.id }).apply()
        val fresh = unseen.filter(allowed)
        if (fresh.isEmpty()) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val manager = NotificationManagerCompat.from(context)
        for (item in fresh) {
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tent)
                .setContentTitle(item.title)
                .setContentText(item.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(item.body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                manager.notify(item.id.hashCode(), n)
            } catch (_: SecurityException) {
                return
            }
        }
    }
}

/**
 * Turns new notifications and chat messages into phone notifications while the app is open
 * (US 01.03.01, US 01.03.02), honouring Profile > Notifications. Asks for permission on Android 13+.
 *
 * @param container the signed-in person's container, so only their notifications are shown.
 */
@Composable
fun NotificationEffect(container: AppContainer) {
    val context = LocalContext.current
    // The notification pop-up is not shown on launch: it's asked when you join a lottery or turn
    // Notifications on in Profile. Until then, alerts still show inside the app.
    LaunchedEffect(container) {
        // Only the kinds the person switched on in Profile > Notifications pop up.
        combine(container.notifications.observeNotifications(container.userId), container.profiles.observeProfile(container.userId)) { list, p -> list to (p?.prefs ?: NotificationPrefs()) }
            .collect { (list, prefs) -> LocalNotifier.showNew(context, list) { prefs.allows(it.type) } }
    }
    LaunchedEffect(container) {
        // New chat messages, shown as "Message from …" while the app is open.
        combine(container.social.observeChatsFor(container.userId), container.profiles.observeProfile(container.userId)) { chats, p -> chats to (p?.prefs ?: NotificationPrefs()) }
            .collect { (chats, prefs) ->
                if (!prefs.allows(NotificationType.MESSAGE)) return@collect
                val unread = chats.filter { it.unreadFor(container.userId) && it.lastSenderId != container.userId }.map { c ->
                    AppNotification("chat-${c.id}-${c.lastAt}", container.userId, NotificationType.MESSAGE, "Message from ${if (c.lastSenderId == c.ownerId) c.ownerName else c.entrantName}", c.lastText, "", c.campsiteId, c.lastAt)
                }
                LocalNotifier.showNew(context, unread)
            }
    }
}

/** Keep flows alive for 5s after the screen goes away (survives rotation). */
private val sharing = SharingStarted.WhileSubscribed(5_000)

/**
 * The bell with its red unread count (conversations plus app updates). Used on every screen that
 * has a bell, for entrants and owners.
 */
@Composable
fun BellButton(container: AppContainer, onClick: () -> Unit, modifier: Modifier = Modifier, contentDescription: String = "Notifications") {
    val chats by remember(container) { container.social.observeChatsFor(container.userId) }.collectAsStateWithLifecycle(emptyList())
    val notes by remember(container) { container.notifications.observeNotifications(container.userId) }.collectAsStateWithLifecycle(emptyList())
    val badge = bellBadge(buildInbox(container.userId, chats.filter { it.lastText.isNotBlank() }, notes))
    GlassIconButton(R.drawable.ic_bell, contentDescription, onClick, modifier, badgeText = badge)
}
