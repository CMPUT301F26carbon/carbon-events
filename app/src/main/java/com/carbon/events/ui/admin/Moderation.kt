/*
 * Moderation.kt (Tausif)
 *
 * Admin moderation: browse and remove images (campsite and profile photos), the logs of
 * notifications, owner announcements and chats, flagged campsites, and warning, blocking or
 * removing a profile.
 *
 * Outstanding issues: removing a profile can't delete the person's sign-in account (only they
 * or a server can), so a removed person could sign up again; blocking keeps them out.
 */
package com.carbon.events

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val keepAlive = SharingStarted.WhileSubscribed(5_000)

// ---- Images ----

/** An uploaded image an admin can review: a campsite photo or a profile photo. */
data class ModeratedImage(val key: String, val base64: String, val title: String, val subtitle: String, val campsite: Campsite?, val profile: UserProfile?)

/** Every uploaded image, newest owners first. */
class AdminImagesViewModel(private val container: AppContainer) : ViewModel() {
    private val busy = MutableStateFlow(false)

    /** Images (null while loading) and whether a removal is running. */
    val state: StateFlow<Pair<List<ModeratedImage>?, Boolean>> = combine(
        container.campsites.observeCampsites(), container.profiles.observeProfiles(), busy,
    ) { sites, people, b ->
        val images = sites.filter { it.imageBase64 != null }.map { ModeratedImage("c-${it.id}", it.imageBase64!!, it.name, "Campsite · ${it.ownerName}", it, null) } +
            people.filter { it.photoBase64 != null }.map { ModeratedImage("p-${it.id}", it.photoBase64!!, it.name, "Profile photo", null, it) }
        images to b
    }.stateIn(viewModelScope, keepAlive, null to false)

    /** Removes [image] from its campsite or profile (US 03.03.01). */
    fun remove(image: ModeratedImage) {
        busy.value = true
        viewModelScope.launch {
            runCatching {
                image.campsite?.let { container.campsites.updateCampsite(it.copy(imageBase64 = null)) }
                image.profile?.let { container.profiles.saveProfile(it.copy(photoBase64 = null)) }
            }
            busy.value = false
        }
    }
}

/** Images (Figma A-IMAGES): every campsite and profile photo, tap to review and remove. */
@Composable
fun AdminImagesScreen(container: AppContainer) {
    val vm = containerViewModel(container, key = "admin-images") { AdminImagesViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val (images, busy) = state
    var tab by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<ModeratedImage?>(null) }
    val shown = images.orEmpty().filter { if (tab == 0) it.campsite != null else it.profile != null }
    LazyVerticalGrid(
        GridCells.Fixed(2), Modifier.fillMaxSize().statusBarsPadding().testTag("admin_images"),
        contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(2) }) { AdminHeader(R.drawable.ic_image, "Images", "${images?.size ?: 0} total") }
        item(span = { GridItemSpan(2) }) {
            SegmentedControl(listOf("Campsites · ${images.orEmpty().count { it.campsite != null }}", "Profiles · ${images.orEmpty().count { it.profile != null }}"), tab, { tab = it }, tagPrefix = "images_tab_")
        }
        if (images != null && shown.isEmpty()) item(span = { GridItemSpan(2) }) { DuskCard { EmptyState(R.drawable.ic_image, "No images", "Uploaded photos show up here.") } }
        items(shown, key = { it.key }) { img ->
            val bitmap = remember(img.base64) { decodeBase64Image(img.base64) }
            Column(Modifier.clip(EventsShapes.Medium).background(EventsColors.Surface).clickable { open = img }.testTag("image_${img.key}")) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    if (bitmap != null) Image(bitmap, img.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Column(Modifier.padding(10.dp)) {
                    Text(img.title, style = EventsType.BodySmallStrong, color = EventsColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(img.subtitle, style = EventsType.Caption, color = EventsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    open?.let { img ->
        val bitmap = remember(img.base64) { decodeBase64Image(img.base64) }
        Dialog(onDismissRequest = { open = null }) {
            DuskCard(Modifier.fillMaxWidth().testTag("image_dialog"), padding = PaddingValues(16.dp), spacing = 12.dp) {
                if (bitmap != null) Image(bitmap, img.title, Modifier.fillMaxWidth().height(240.dp).clip(EventsShapes.Medium), contentScale = ContentScale.Crop)
                Text(img.title, style = EventsType.Title, color = EventsColors.Text)
                Text(img.subtitle, style = EventsType.BodySmall, color = EventsColors.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Keep", { open = null }, Modifier.weight(1f), size = ButtonSize.SMALL)
                    DangerButton("Remove image", { vm.remove(img); open = null }, Modifier.weight(1f).testTag("image_remove"), size = ButtonSize.SMALL, loading = busy)
                }
            }
        }
    }
}

// ---- Logs ----

/** State of the admin logs. */
data class LogsState(
    val notifications: List<AppNotification>? = null,
    val chats: List<Chat> = emptyList(),
    val names: Map<String, String> = emptyMap(),
    val campsites: Map<String, Campsite> = emptyMap(),
    val now: Long = 0L,
)

/**
 * One line of the notification log (Figma A-LOGS): a notification sent to one or more people at
 * once, e.g. "Lottery results · System → 58 entrants".
 */
data class LogEntry(val type: NotificationType, val title: String, val sender: String, val ownerId: String?, val recipients: List<String>, val at: Long, val body: String)

/** Groups per-person notifications sent together (same kind, lottery and minute) into log lines. */
fun groupLog(list: List<AppNotification>, names: Map<String, String>, campsites: Map<String, Campsite>): List<LogEntry> =
    list.groupBy {
        // A draw's wins and losses are one "Lottery results" send.
        val kind = if (it.type == NotificationType.LOTTERY_LOST) NotificationType.LOTTERY_WON else it.type
        Triple(kind, it.lotteryId.ifBlank { it.campsiteId }, it.createdAt / 60_000)
    }.values.map { group ->
        val n = group.first()
        val owner = campsites[n.campsiteId]
        val sender = when (n.type) {
            NotificationType.WARNING -> "Admin"
            NotificationType.MESSAGE, NotificationType.REJECTED -> owner?.ownerName ?: "Owner"
            else -> "System"
        }
        val title = when (n.type) {
            NotificationType.LOTTERY_WON, NotificationType.LOTTERY_LOST -> "Lottery results"
            else -> n.title
        }
        LogEntry(n.type, title, sender, owner?.ownerId, group.map { names[it.recipientId] ?: "a removed user" }.distinct(), n.createdAt, n.body)
    }.sortedByDescending { it.at }

/** Notification and message logs for admins (US 03.08.01, US 03.12.01). */
class AdminLogsViewModel(container: AppContainer) : ViewModel() {
    /** Logs state. */
    val state: StateFlow<LogsState> = combine(
        container.social.observeAllNotifications(), container.social.observeAllChats(), container.profiles.observeProfiles(), container.campsites.observeCampsites(),
        nowTicker(container.clock, 60_000),
    ) { n, c, p, sites, now -> LogsState(n, c, p.associate { it.id to it.name }, sites.associateBy { it.id }, now) }.stateIn(viewModelScope, keepAlive, LogsState())
}

/**
 * Logs (Figma A-LOGS, A-LOGS-DATES, A-MESSAGES): Notifications and Messages, filtered by owner
 * and by the last 7 / 14 days.
 */
@Composable
fun AdminLogsScreen(container: AppContainer, onChat: (Chat) -> Unit) {
    val vm = containerViewModel(container, key = "admin-logs") { AdminLogsViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var days by remember { mutableStateOf<Int?>(7) }
    var ownerFilter by remember { mutableStateOf<String?>(null) }
    var pickOwner by remember { mutableStateOf(false) }
    val since = days?.let { s.now - it * 86_400_000L } ?: 0L
    val owners = s.campsites.values.associate { it.ownerId to it.ownerName }
    val log = remember(s) { groupLog(s.notifications.orEmpty(), s.names, s.campsites) }
        .filter { it.at >= since && (ownerFilter == null || it.ownerId == ownerFilter) }
    val chats = s.chats.filter { it.lastAt >= since && (ownerFilter == null || it.ownerId == ownerFilter) }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().testTag("admin_logs"),
        contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { AdminHeader(R.drawable.ic_list, "Logs", days?.let { "Last $it days" } ?: "All time") }
        item { SegmentedControl(listOf("Notifications", "Messages"), tab, { tab = it }, tagPrefix = "logs_tab_") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EventsChip(ownerFilter?.let { owners[it] } ?: "All owners", ChipStyle.CORAL, { pickOwner = true }, Modifier.testTag("logs_owner"), icon = R.drawable.ic_group)
                EventsChip(
                    days?.let { "Last $it days" } ?: "All time", ChipStyle.GLASS,
                    { days = when (days) { 7 -> 14; 14 -> null; else -> 7 } }, Modifier.testTag("logs_days"), icon = R.drawable.ic_calendar,
                )
            }
        }
        if (tab == 0) {
            if (s.notifications != null && log.isEmpty()) item { DuskCard { EmptyState(R.drawable.ic_bell, "Nothing sent", "Lottery results, owner messages and warnings appear here.") } }
            if (log.isNotEmpty()) item {
                Column(Modifier.fillMaxWidth().clip(EventsShapes.Large).background(EventsColors.Surface).padding(6.dp)) {
                    log.take(200).forEach { e -> LogRow(e) }
                }
            }
        } else {
            if (chats.isEmpty()) item { DuskCard { EmptyState(R.drawable.ic_chat, "No messages", "Conversations between owners and guests appear here.") } }
            if (chats.isNotEmpty()) item {
                Column(Modifier.fillMaxWidth().clip(EventsShapes.Large).background(EventsColors.Surface).padding(6.dp)) {
                    chats.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth().clip(EventsShapes.Medium).clickable { onChat(c) }.padding(horizontal = 10.dp, vertical = 10.dp).testTag("log_chat_${c.id}"),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            IconTile(R.drawable.ic_chat)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(c.lastText, style = EventsType.BodyStrong, color = EventsColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${c.ownerName} ↔ ${c.entrantName} · ${formatDayTime(c.lastAt)}", style = EventsType.Caption, color = EventsColors.TextSecondary, maxLines = 1)
                            }
                            EventsIcon(R.drawable.ic_chevron_right, size = 18.dp, tint = EventsColors.TextSecondary)
                        }
                    }
                }
            }
        }
    }
    if (pickOwner) Dialog(onDismissRequest = { pickOwner = false }) {
        DuskCard(Modifier.fillMaxWidth(), padding = PaddingValues(8.dp)) {
            (listOf<Pair<String?, String>>(null to "All owners") + owners.toList().sortedBy { it.second }).forEach { (id, name) ->
                Row(
                    Modifier.fillMaxWidth().clip(EventsShapes.Medium).clickable { ownerFilter = id; pickOwner = false }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, style = EventsType.Body, color = EventsColors.Text, modifier = Modifier.weight(1f))
                    if (ownerFilter == id) EventsIcon(R.drawable.ic_check, size = 18.dp, tint = EventsColors.Coral)
                }
            }
        }
    }
}

/** Figma A-LOGS row: icon, title, "sender → recipients · time". */
@Composable
private fun LogRow(e: LogEntry) {
    val (icon, bg) = when (e.type) {
        NotificationType.LOTTERY_WON, NotificationType.LOTTERY_LOST -> R.drawable.ic_trophy to EventsColors.GreenContainer
        NotificationType.MESSAGE -> R.drawable.ic_send to EventsColors.PrimaryContainer
        NotificationType.WARNING -> R.drawable.ic_bell to EventsColors.AmberContainer
        NotificationType.REJECTED -> R.drawable.ic_close to EventsColors.DangerContainer
        else -> R.drawable.ic_bell to EventsColors.PrimaryContainer
    }
    val to = if (e.recipients.size == 1) e.recipients.first() else "${e.recipients.size} people"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp).testTag("log_row"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(icon, bg = bg)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(e.title, style = EventsType.BodyStrong, color = EventsColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${e.sender} → $to · ${formatDayTime(e.at)}", style = EventsType.Caption, color = EventsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A chat read by an admin (read-only). */
@Composable
fun AdminChatLogScreen(container: AppContainer, chat: Chat, onBack: () -> Unit) {
    val messages by container.social.observeMessages(chat.id).collectAsStateWithLifecycle(emptyList())
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackTopBar("${chat.entrantName} ↔ ${chat.ownerName}", onBack)
        LazyColumn(contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("${chat.campsiteName} · read-only", style = EventsType.Caption, color = EventsColors.Coral) }
            items(messages, key = { it.id }) { m ->
                DuskCard(Modifier.fillMaxWidth(), spacing = 2.dp, color = if (m.senderId == chat.ownerId) EventsColors.PrimaryContainer else EventsColors.Surface) {
                    Text("${m.senderName} · ${formatDayTime(m.sentAt)}", style = EventsType.Caption, color = EventsColors.TextTertiary)
                    Text(m.text, style = EventsType.Body, color = EventsColors.Text)
                }
            }
        }
    }
}

/** Shield-tile header used across the admin console. */
@Composable
fun AdminHeader(icon: Int, title: String, count: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(icon, bg = EventsColors.SkyTop.copy(alpha = 0.6f))
        Column(Modifier.weight(1f)) {
            Text("Admin console", style = EventsType.Caption, color = EventsColors.TextSoft)
            Text(title, style = EventsType.Headline, color = EventsColors.Text)
        }
        StatusTag(count, TagTone.BLUE)
    }
}

// ---- Profiles: warn, block, remove ----

/** Admin actions on one profile. */
class ProfileModerationViewModel(private val container: AppContainer) : ViewModel() {
    private val _busy = MutableStateFlow(false)

    /** Whether an action is running. */
    val busy: StateFlow<Boolean> = _busy

    private val _message = MutableStateFlow<String?>(null)

    /** The last result or error. */
    val message: StateFlow<String?> = _message

    private fun act(done: String, block: suspend () -> Unit) {
        _busy.value = true
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { _message.value = done }
                .onFailure { _message.value = it.userMessage() }
            _busy.value = false
        }
    }

    /** Sends [profile] a warning (US 03.x). */
    fun warn(profile: UserProfile, text: String) = act("Warning sent to ${profile.firstName}.") { container.social.warn(profile, text, container.clock.now()) }

    /** Blocks or unblocks [profile]. */
    fun setBlocked(profile: UserProfile, blocked: Boolean) =
        act(if (blocked) "${profile.firstName} is blocked and signed out of every phone." else "${profile.firstName} is unblocked.") {
            container.profiles.saveProfile(profile.copy(blocked = blocked))
            // Blocking also signs them out everywhere: none of their phones stay trusted.
            if (blocked) container.profiles.removeAllDevices(profile.id)
        }

    /** Removes [profile] and their campsites (US 03.02.01, US 03.07.01). */
    fun remove(profile: UserProfile, onDone: () -> Unit) {
        _busy.value = true
        viewModelScope.launch {
            runCatching { container.profiles.deleteProfile(profile) }
                .onSuccess { onDone() }
                .onFailure { _message.value = it.userMessage() }
            _busy.value = false
        }
    }
}

/**
 * Figma A-PROFILE-DETAIL / A-ENTRANT-DETAIL bottom bar: Send warning, and Remove owner / entrant
 * (A-REMOVE-PROFILE), with "Also block this person" (A-REMOVE-PROFILE-BLOCK) which keeps a
 * blocked record instead of deleting, so they can't use the app again with that account.
 */
@Composable
fun ProfileModerationBar(container: AppContainer, profile: UserProfile, owner: Boolean, campsites: Int, onRemoved: () -> Unit, modifier: Modifier = Modifier) {
    val vm = containerViewModel(container, key = "moderate-${profile.id}") { ProfileModerationViewModel(it) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var warning by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    ActionBar(modifier, header = {
        message?.let { Text(it, style = EventsType.BodySmall, color = EventsColors.Green, modifier = Modifier.padding(bottom = 8.dp).testTag("moderation_message")) }
    }) {
        SecondaryButton("Send warning", { warning = true }, Modifier.weight(1f).testTag("moderate_warn"))
        if (profile.blocked) PrimaryButton("Unblock", { vm.setBlocked(profile, false) }, Modifier.weight(1f).testTag("moderate_block"), loading = busy)
        else DangerButton("Remove", { removing = true }, Modifier.weight(1f).testTag("moderate_remove"), icon = R.drawable.ic_delete, loading = busy)
    }
    if (warning) {
        var text by remember { mutableStateOf("Please follow the Events community guidelines.") }
        Dialog(onDismissRequest = { warning = false }) {
            DuskCard(Modifier.fillMaxWidth(), padding = PaddingValues(20.dp), spacing = 12.dp) {
                Text("Warn ${profile.firstName}", style = EventsType.Title, color = EventsColors.Text)
                EventsTextField(text, { if (it.length <= 300) text = it }, "Message", Modifier.testTag("warn_text"), singleLine = false, minLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Cancel", { warning = false }, Modifier.weight(1f), size = ButtonSize.SMALL)
                    PrimaryButton("Send warning", { warning = false; vm.warn(profile, text.trim()) }, Modifier.weight(1f).testTag("warn_send"), size = ButtonSize.SMALL, enabled = text.isNotBlank())
                }
            }
        }
    }
    if (removing) {
        var block by remember { mutableStateOf(false) }
        Dialog(onDismissRequest = { removing = false }) {
            DuskCard(Modifier.fillMaxWidth().testTag("remove_dialog"), padding = PaddingValues(20.dp), spacing = 12.dp) {
                IconTile(R.drawable.ic_delete, bg = EventsColors.DangerContainer, tint = EventsColors.Danger)
                Text("Remove ${profile.name}?", style = EventsType.Title, color = EventsColors.Text)
                Text(
                    "This deletes their profile, inbox and trusted devices" +
                        (if (campsites > 0) ", and their $campsites campsite${if (campsites == 1) "" else "s"} with every lottery and waiting list" else "") + ".",
                    style = EventsType.BodySmall, color = EventsColors.TextSecondary,
                )
                Row(Modifier.fillMaxWidth().clickable { block = !block }.testTag("remove_block"), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(block, { block = it })
                    Column {
                        Text("Also block this person", style = EventsType.BodyStrong, color = EventsColors.Text)
                        Text("Keeps a blocked record so they can't use the app with this account.", style = EventsType.Caption, color = EventsColors.TextSecondary)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Cancel", { removing = false }, Modifier.weight(1f), size = ButtonSize.SMALL)
                    DangerButton(
                        if (block) "Block" else "Remove", {
                            removing = false
                            if (block) vm.setBlocked(profile, true) else vm.remove(profile, onRemoved)
                        }, Modifier.weight(1f).testTag("remove_confirm"), size = ButtonSize.SMALL,
                    )
                }
            }
        }
    }
}

/** The reports about one campsite, with Dismiss, shown on the admin's campsite review. */
@Composable
fun CampsiteReportsCard(reports: List<Report>, onDismiss: () -> Unit) {
    if (reports.isEmpty()) return
    DuskCard(Modifier.fillMaxWidth().testTag("campsite_reports"), color = EventsColors.DangerContainer, spacing = 8.dp) {
        Text("Flagged ${reports.size} time${if (reports.size == 1) "" else "s"}", style = EventsType.BodyStrong, color = EventsColors.Text)
        reports.take(5).forEach { r ->
            Column {
                Text(r.reason, style = EventsType.BodySmall, color = EventsColors.Text)
                Text("${r.reporterName} · ${formatDayTime(r.createdAt)}", style = EventsType.Caption, color = EventsColors.TextSoft)
            }
        }
        SecondaryButton("Dismiss reports", onDismiss, Modifier.fillMaxWidth().testTag("reports_dismiss"), size = ButtonSize.SMALL)
    }
}
