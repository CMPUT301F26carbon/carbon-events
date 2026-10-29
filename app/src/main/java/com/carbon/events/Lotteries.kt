/*
 * Lotteries.kt (Suvir)
 *
 * My Lotteries and notifications: waitlists I joined, draw results, and the
 * phone notifications for wins and losses.
 */
package com.carbon.events

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
fun MyLotteriesScreen(container: AppContainer, onOpen: (String) -> Unit, onExplore: () -> Unit, onNotifications: () -> Unit) {
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
                GlassIconButton(R.drawable.ic_bell, "Notifications", onNotifications)
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
                            if (item.entry.status == EntryStatus.WAITING) "Draw ${formatDay(lot.registrationEnd)} · ${lot.entrantCount} entered"
                            else "Drawn ${formatDay(lot.drawnAt ?: lot.registrationEnd)}",
                        )
                        when (item.entry.status) {
                            EntryStatus.WAITING -> LotteryStatusTag(lot, state.now, openTone = TagTone.BLUE)
                            EntryStatus.WON -> StatusTag("You won!", TagTone.GREEN)
                            EntryStatus.LOST -> StatusTag("Not selected", TagTone.GREY)
                        }
                    }
                    EventsIcon(R.drawable.ic_chevron_right, tint = EventsColors.TextSecondary, size = 20.dp)
                }
            }
        }
    }
}

/** Notifications (Figma E-NOTIFS): lottery wins and losses, newest first. */
@Composable
fun NotificationsScreen(container: AppContainer, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val vm = containerViewModel(container) { NotificationsViewModel(it) }
    val list by vm.notifications.collectAsStateWithLifecycle()
    val highlighted by vm.highlighted.collectAsStateWithLifecycle()
    val now = container.clock.now()
    LaunchedEffect(list) { list?.let { vm.onOpened(it) } }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackTopBar("Notifications", onBack)
        LazyColumn(contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (list?.isEmpty() == true) {
                item { DuskCard { EmptyState(R.drawable.ic_bell, "You’re all caught up", "Lottery results will show up here as soon as winners are drawn.") } }
            }
            val (today, earlier) = list.orEmpty().partition { isToday(it.createdAt, now) }
            if (today.isNotEmpty()) item { GroupLabel("Today") }
            items(today, key = { it.id }) { NotificationCard(it, it.id in highlighted, now) { onOpen(it.campsiteId) } }
            if (earlier.isNotEmpty()) item { GroupLabel("Earlier") }
            items(earlier, key = { it.id }) { NotificationCard(it, it.id in highlighted, now) { onOpen(it.campsiteId) } }
        }
    }
}

@Composable
private fun GroupLabel(text: String) = Text(text, style = EventsType.LabelSmall, color = EventsColors.TextSoft)

@Composable
private fun NotificationCard(n: AppNotification, isNew: Boolean, now: Long, onClick: () -> Unit) {
    val won = n.type == NotificationType.LOTTERY_WON
    DuskCard(
        Modifier.fillMaxWidth().testTag("notification_${n.type.name}"),
        onClick = onClick,
        padding = PaddingValues(14.dp),
        borderColor = if (isNew) EventsColors.Primary else null,
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (won) EventsColors.GreenContainer else EventsColors.SurfaceSoft),
                contentAlignment = Alignment.Center,
            ) { EventsIcon(if (won) R.drawable.ic_trophy else R.drawable.ic_ticket, size = 20.dp) }
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

data class MyLotteryItem(val entry: WaitlistEntry, val lottery: Lottery?, val campsite: Campsite?)

/** State of the My Lotteries screen. */
data class MyLotteriesState(val loading: Boolean = true, val active: List<MyLotteryItem> = emptyList(), val results: List<MyLotteryItem> = emptyList(), val now: Long = 0L)

/** Lotteries the entrant has entered, split into active and results. */
class MyLotteriesViewModel(container: AppContainer) : ViewModel() {
    /** My Lotteries state. */
    val state: StateFlow<MyLotteriesState> = combine(
        container.lotteries.observeEntriesForEntrant(container.deviceId),
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
    val notifications: StateFlow<List<AppNotification>?> = container.notifications.observeNotifications(container.deviceId)
        .stateIn(viewModelScope, sharing, null)

    private val _snapshotUnread = MutableStateFlow<Set<String>>(emptySet())

    /** IDs that were unread when the screen opened, so they can stay highlighted. */
    val highlighted: StateFlow<Set<String>> = _snapshotUnread.asStateFlow()

    /** Call when the screen opens: remembers what was new, then marks everything read. */
    fun onOpened(current: List<AppNotification>) {
        if (_snapshotUnread.value.isEmpty()) _snapshotUnread.value = current.filter { !it.read }.map { it.id }.toSet()
        if (current.any { !it.read }) viewModelScope.launch { runCatching { container.notifications.markAllRead(container.deviceId) } }
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
    fun showNew(context: Context, items: List<AppNotification>) {
        if (!hasPermission(context)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val shown = prefs.getStringSet(KEY_SHOWN, emptySet()).orEmpty()
        val fresh = items.filter { !it.read && it.id !in shown }
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
        prefs.edit().putStringSet(KEY_SHOWN, shown + fresh.map { it.id }).apply()
    }
}

@Composable
fun NotificationEffect(container: AppContainer) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !LocalNotifier.hasPermission(context)) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(container) {
        container.notifications.observeNotifications(container.deviceId).collect { LocalNotifier.showNew(context, it) }
    }
}

/** Keep flows alive for 5s after the screen goes away (survives rotation). */
private val sharing = SharingStarted.WhileSubscribed(5_000)
