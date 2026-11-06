/*
 * Social.kt (Tausif)
 *
 * Screens for people talking to each other: the inbox (guests' and owners' chats), a chat,
 * an owner's Notify waitlist with its sent log, reporting a campsite, and campsite reviews.
 *
 * Outstanding issues: with the app closed, chat notifications arrive through BackgroundSync
 * about every 15 minutes rather than instantly (no push server).
 */
package com.carbon.events

import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.border

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val keepAlive = SharingStarted.WhileSubscribed(5_000)

/** Longest chat message or announcement. */
const val MAX_MESSAGE = 500

// ---- Inbox ----

/** The signed-in person's chats, newest first (null while loading). */
class InboxViewModel(container: AppContainer) : ViewModel() {
    /** Chats as guest or owner. */
    val chats: StateFlow<List<Chat>?> = container.social.observeChatsFor(container.userId).stateIn(viewModelScope, keepAlive, null)

    /** Every waitlist notification this person sent as an owner (US 02.10.02). */
    val sent: StateFlow<List<Announcement>?> = container.social.observeAnnouncementsByOwner(container.userId).stateIn(viewModelScope, keepAlive, null)
}

/**
 * Inbox (Figma O-INBOX / E-MESSAGES): every conversation with guests (owner) or hosts (entrant).
 *
 * @param asOwner shows only chats about the person's own campsites; otherwise only their stays.
 */
@Composable
fun InboxScreen(container: AppContainer, asOwner: Boolean, onOpen: (Chat) -> Unit, onBack: (() -> Unit)? = null) {
    val vm = containerViewModel(container, key = "inbox") { InboxViewModel(it) }
    val all by vm.chats.collectAsStateWithLifecycle()
    val me = container.userId
    val chats = all?.filter { if (asOwner) it.ownerId == me else it.entrantId == me }
    val sent by vm.sent.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("inbox")) {
        if (onBack != null) BackTopBar("Messages", onBack)
        else Row(Modifier.height(52.dp).padding(horizontal = PageMargin), verticalAlignment = Alignment.CenterVertically) {
            Text("Inbox", style = EventsType.Headline, color = EventsColors.Text)
        }
        LazyColumn(contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (asOwner) item {
                SegmentedControl(listOf("Chats · ${chats.orEmpty().size}", "Sent · ${sent.orEmpty().size}"), tab, { tab = it }, tagPrefix = "inbox_tab_")
            }
            if (asOwner && tab == 1) {
                if (sent?.isEmpty() == true) item {
                    DuskCard { EmptyState(R.drawable.ic_send, "Nothing sent yet", "Messages you send to a waiting list from Manage → Notify show up here.") }
                }
                items(sent.orEmpty(), key = { it.id }) { a -> SentCard(a, showCampsite = true) }
                return@LazyColumn
            }
            if (chats?.isEmpty() == true) item {
                DuskCard {
                    EmptyState(
                        R.drawable.ic_chat, "No messages yet",
                        if (asOwner) "Guests can message you from your campsite pages and their reservations." else "Message a host from a campsite page or your reservation.",
                    )
                }
            }
            items(chats.orEmpty(), key = { it.id }) { c -> ChatRow(c, me) { onOpen(c) } }
        }
    }
}

@Composable
private fun ChatRow(c: Chat, me: String, onClick: () -> Unit) {
    val unread = c.unreadFor(me)
    DuskCard(Modifier.fillMaxWidth().testTag("chat_${c.id}"), onClick = onClick, padding = PaddingValues(12.dp), borderColor = if (unread) EventsColors.Primary else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(c.otherName(me).split(" ").take(2).joinToString("") { it.take(1).uppercase() }, size = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.otherName(me), style = EventsType.BodyStrong, color = EventsColors.Text, maxLines = 1, modifier = Modifier.weight(1f))
                    if (c.lastAt > 0) Text(formatDay(c.lastAt), style = EventsType.Caption, color = EventsColors.TextTertiary)
                }
                Text(c.campsiteName, style = EventsType.Caption, color = EventsColors.Coral, maxLines = 1)
                Text(
                    (if (c.lastSenderId == me) "You: " else "") + c.lastText, style = if (unread) EventsType.BodySmallStrong else EventsType.BodySmall,
                    color = if (unread) EventsColors.Text else EventsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            // Unread count, WhatsApp-style.
            if (unread) CountBadge(c.unreadCount(me).let { if (it > 99) "99+" else it.toString() })
        }
    }
}

// ---- One chat ----

/** State of one chat. */
data class ChatState(
    val chat: Chat? = null,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val sending: Boolean = false,
    val message: String? = null,
    /** Whether this person can type here: the owner and a selected (won) entrant. Null while loading. */
    val canSend: Boolean? = null,
)

/**
 * One conversation. [start] is the chat to create with the first message if it doesn't exist yet.
 */
class ChatViewModel(private val container: AppContainer, private val start: Chat) : ViewModel() {
    private val draft = MutableStateFlow(ChatState())

    /** Chat state. */
    val state: StateFlow<ChatState> = combine(
        // Start both streams empty so typing shows at once, even before (or without) a reply from the server.
        container.social.observeChat(start.id).onStart { emit(null) },
        container.social.observeMessages(start.id).onStart { emit(emptyList()) },
        draft,
        entrantEntry(),
    ) { chat, messages, d, entry -> d.copy(chat = chat ?: start, messages = messages, canSend = ChatRules.canSend(chat ?: start, container.userId, entry)) }
        .stateIn(viewModelScope, keepAlive, ChatState(start))

    /**
     * The entrant's entry in this conversation's lottery: the entrant reads it from their own list,
     * the owner from the lottery's waitlist (both allowed by the security rules).
     */
    private fun entrantEntry(): Flow<WaitlistEntry?> {
        val key = Paths.keyOf(start.lotteryKey)
        val match: (WaitlistEntry) -> Boolean = { it.entrantId == start.entrantId && Paths.keyOf(it.lotteryId) == key }
        val source = if (container.userId == start.entrantId) container.lotteries.observeEntriesForEntrant(start.entrantId)
            // Server ids are "campsite/key"; the offline demo uses the key alone.
            else container.lotteries.observeEntries(if (container.usingServer) Paths.lotteryId(start.campsiteId, key) else start.lotteryKey)
        return source.map { list -> list.firstOrNull(match) }.onStart { emit(null) }
    }

    /** Marks the chat read (when it's on screen and changes). */
    fun markRead() { viewModelScope.launch { runCatching { container.social.markChatRead(start.id, container.userId) } } }

    /** Updates the message being typed. */
    fun onDraft(v: String) { if (v.length <= MAX_MESSAGE) draft.value = draft.value.copy(draft = v, message = null) }

    /** Sends the draft. */
    fun send(senderName: String) {
        val text = draft.value.draft.trim()
        if (text.isEmpty()) return
        draft.value = draft.value.copy(sending = true, message = null)
        viewModelScope.launch {
            runCatching { container.social.sendMessage(state.value.chat ?: start, container.userId, senderName, text, container.clock.now()) }
                .onSuccess { draft.value = draft.value.copy(draft = "", sending = false) }
                .onFailure { e -> draft.value = draft.value.copy(sending = false, message = e.userMessage()) }
        }
    }
}

/** A chat between a guest and a host (Figma E-CHAT / O-CHAT). */
@Composable
fun ChatScreen(container: AppContainer, profile: UserProfile, start: Chat, onBack: () -> Unit) {
    val vm = containerViewModel(container, key = "chat-${start.id}") { ChatViewModel(it, start) }
    // While this chat is on screen, new messages in it don't pop a banner.
    DisposableEffect(start.id) { OpenChat.id = start.id; onDispose { if (OpenChat.id == start.id) OpenChat.id = null } }
    val s by vm.state.collectAsStateWithLifecycle()
    val chat = s.chat ?: start
    val me = container.userId
    val list = rememberLazyListState()
    LaunchedEffect(s.messages.size, chat.lastAt) {
        if (chat.unreadFor(me)) vm.markRead()
        if (s.messages.isNotEmpty()) list.animateScrollToItem(s.messages.lastIndex)
    }
    Column(Modifier.fillMaxSize().imePadding().testTag("chat_screen")) {
        // Figma E-CHAT / O-CHAT header: back, avatar, name, "campsite" underneath.
        Row(
            Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = PageMargin, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassIconButton(R.drawable.ic_arrow_back, "Back", onBack)
            val other = chat.otherName(me)
            Avatar(other.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }, size = 36.dp)
            Column(Modifier.weight(1f)) {
                Text(other, style = EventsType.BodyStrong, color = EventsColors.Text, maxLines = 1)
                Text(chat.campsiteName, style = EventsType.Caption, color = EventsColors.TextSecondary, maxLines = 1)
            }
        }
        LazyColumn(
            Modifier.weight(1f), state = list,
            contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (s.messages.isEmpty()) item {
                Text(
                    when {
                        s.canSend == false && me == chat.entrantId -> "Notices from ${chat.ownerName} about ${chat.campsiteName} will show up here."
                        s.canSend == false -> "Notices you send to ${chat.entrantName} will show up here."
                        me == chat.entrantId -> "Ask ${chat.ownerName} anything about ${chat.campsiteName}."
                        else -> "Start the conversation with ${chat.entrantName}."
                    },
                    style = EventsType.BodySmall, color = EventsColors.TextSecondary, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            }
            // Figma: a day label ("TODAY") above each day's messages.
            s.messages.groupBy { dayOf(it.sentAt) }.forEach { (day, msgs) ->
                item(key = "day-$day") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        StatusTag(if (day == dayOf(container.clock.now())) "Today" else formatDayNum(day), TagTone.BLUE)
                    }
                }
                items(msgs, key = { it.id }) { m -> Bubble(m, mine = m.senderId == me) }
            }
        }
        s.message?.let { Text(it, style = EventsType.BodySmall, color = EventsColors.Danger, modifier = Modifier.padding(horizontal = PageMargin)) }
        // One-way until selected: entrants who weren't picked (or are still in the draw) read the
        // owner's notices but can't reply.
        if (s.canSend == false) Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = PageMargin, vertical = 10.dp)
                .clip(EventsShapes.Medium).background(EventsColors.Surface).padding(12.dp).testTag("chat_read_only"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EventsIcon(R.drawable.ic_info, size = 16.dp, tint = EventsColors.Text)
            Text(
                if (me == chat.ownerId) "Notices only. You can message this guest once they're selected."
                else "Only the owner can send messages here. You can reply if you're selected.",
                style = EventsType.BodySmall, color = EventsColors.Text,
            )
        } else if (s.canSend == true) Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = PageMargin, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChatInput(s.draft, vm::onDraft, Modifier.weight(1f))
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(if (s.draft.isBlank()) EventsColors.PrimaryContainer else EventsColors.Primary)
                    .clickable(enabled = s.draft.isNotBlank() && !s.sending) { vm.send(profile.name) }.testTag("chat_send"),
                contentAlignment = Alignment.Center,
            ) { EventsIcon(R.drawable.ic_send, tint = if (s.draft.isBlank()) EventsColors.TextSecondary else EventsColors.OnPrimary, size = 22.dp, contentDescription = "Send") }
        }
    }
}

/** Figma's pill "Write a message…" box (grows to a few lines). */
@Composable
private fun ChatInput(text: String, onChange: (String) -> Unit, modifier: Modifier) {
    Box(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(EventsColors.Tint)
            .border(1.dp, EventsColors.TintStroke, RoundedCornerShape(24.dp)).padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (text.isEmpty()) Text("Write a message…", style = EventsType.Body, color = EventsColors.TextSoft)
        androidx.compose.foundation.text.BasicTextField(
            text, { if (it.length <= MAX_MESSAGE) onChange(it) }, Modifier.fillMaxWidth().testTag("chat_input"),
            textStyle = EventsType.Body.copy(color = EventsColors.Text), maxLines = 4,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(EventsColors.Coral),
        )
    }
}

@Composable
private fun Bubble(m: ChatMessage, mine: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 280.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp))
                .background(if (mine) EventsColors.Primary else EventsColors.Surface).padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            if (m.announcement) Text("NOTICE", style = EventsType.Overline, color = if (mine) EventsColors.OnPrimary.copy(alpha = 0.8f) else EventsColors.Coral)
            Text(m.text, style = EventsType.Body, color = if (mine) EventsColors.OnPrimary else EventsColors.Text)
            Text(formatDayTime(m.sentAt), style = EventsType.Caption, color = if (mine) EventsColors.OnPrimary.copy(alpha = 0.7f) else EventsColors.TextTertiary)
        }
    }
}

// ---- Owner: notify waitlist ----

/** State of Notify waitlist. */
data class NotifyState(
    val lottery: Lottery? = null,
    val entries: List<WaitlistEntry> = emptyList(),
    val sent: List<Announcement> = emptyList(),
    val audience: Audience = Audience.ALL,
    val text: String = "",
    val sending: Boolean = false,
    val result: String? = null,
    val error: String? = null,
) {
    /** How many people the message would reach. */
    val reach: Int get() = entries.count(audience::includes)
}

/** An owner messaging (part of) a lottery's waiting list, with the sent log. */
class NotifyViewModel(private val container: AppContainer, lotteryId: String) : ViewModel() {
    private val form = MutableStateFlow(NotifyState())

    /** Screen state. */
    val state: StateFlow<NotifyState> = combine(
        container.lotteries.observeLottery(lotteryId), container.lotteries.observeEntries(lotteryId), container.social.observeAnnouncements(lotteryId), form,
    ) { lot, entries, sent, f -> f.copy(lottery = lot, entries = entries, sent = sent) }.stateIn(viewModelScope, keepAlive, NotifyState())

    /** Picks who it goes to. */
    fun onAudience(a: Audience) { form.value = form.value.copy(audience = a, result = null, error = null) }

    /** Updates the message. */
    fun onText(v: String) { if (v.length <= MAX_MESSAGE) form.value = form.value.copy(text = v, result = null, error = null) }

    /** Sends it. */
    fun send() {
        val s = state.value
        val lot = s.lottery ?: return
        if (s.text.isBlank()) { form.value = form.value.copy(error = "Write a message first."); return }
        if (s.reach == 0) { form.value = form.value.copy(error = "Nobody is in that group yet."); return }
        form.value = form.value.copy(sending = true, error = null)
        viewModelScope.launch {
            runCatching { container.social.sendAnnouncement(lot, s.audience, s.text.trim(), container.clock.now()) }
                .onSuccess { n -> form.value = form.value.copy(sending = false, text = "", result = "Sent to $n ${if (n == 1) "person" else "people"}.") }
                .onFailure { e -> form.value = form.value.copy(sending = false, error = e.userMessage()) }
        }
    }
}

/** Notify waitlist (Figma O-NOTIFY): pick a group, write, send; sent messages below. */
@Composable
fun NotifyWaitlistScreen(container: AppContainer, lotteryId: String, onBack: () -> Unit) {
    val vm = containerViewModel(container, key = "notify-$lotteryId") { NotifyViewModel(it, lotteryId) }
    val s by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().imePadding()) {
        BackTopBar("Notify Entrants", onBack, Modifier.statusBarsPadding())
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PageMargin).padding(top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(s.lottery?.campsiteName.orEmpty(), style = EventsType.Title, color = EventsColors.Text)
            // Figma O-NOTIFY: "Send to" chips with how many people each reaches.
            Text("Send to", style = EventsType.Label, color = EventsColors.TextSecondary)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Audience.entries.forEach { a ->
                    val n = s.entries.count(a::includes)
                    EventsChip("${a.label} · $n", if (s.audience == a) ChipStyle.CORAL else ChipStyle.DUSK, { vm.onAudience(a) }, Modifier.testTag("audience_${a.name}"))
                }
            }
            EventsTextField(s.text, vm::onText, "Message", placeholder = "e.g. The road to the campsite reopens Friday.", singleLine = false, minLines = 4, modifier = Modifier.testTag("notify_text"), supporting = "${s.text.length}/$MAX_MESSAGE")
            // Preview: how it lands in each person's conversation under the bell.
            if (s.text.isNotBlank()) {
                Text("Preview", style = EventsType.Label, color = EventsColors.TextSecondary)
                DuskCard(Modifier.fillMaxWidth().testTag("notify_preview"), padding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconTile(R.drawable.ic_chat, bg = EventsColors.CoralContainer, tint = EventsColors.Coral)
                        Column(Modifier.weight(1f)) {
                            Text("${s.lottery?.campsiteName.orEmpty()} – from you", style = EventsType.BodyStrong, color = EventsColors.Text)
                            Text(s.text.trim(), style = EventsType.BodySmall, color = EventsColors.TextSecondary)
                        }
                        Text("now", style = EventsType.Caption, color = EventsColors.TextTertiary)
                    }
                }
            }
            s.error?.let { Text(it, style = EventsType.BodySmall, color = EventsColors.Danger) }
            s.result?.let { Text(it, style = EventsType.BodyStrong, color = EventsColors.Green, modifier = Modifier.testTag("notify_result")) }
            SectionTitle("Sent")
            if (s.sent.isEmpty()) Text("Nothing sent yet.", style = EventsType.BodySmall, color = EventsColors.TextSecondary)
            s.sent.forEach { a -> SentCard(a, showCampsite = false) }
        }
        ActionBar { PrimaryButton("Send to ${s.reach}", vm::send, Modifier.weight(1f).testTag("notify_send"), icon = R.drawable.ic_send, loading = s.sending) }
    }
}

/** One sent waitlist notification: audience, time, text, campsite and how many got it. */
@Composable
private fun SentCard(a: Announcement, showCampsite: Boolean) {
    DuskCard(Modifier.fillMaxWidth().testTag("sent_${a.id}"), spacing = 4.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusTag(a.audience.label, TagTone.BLUE)
            Box(Modifier.weight(1f))
            Text(formatDayTime(a.sentAt), style = EventsType.Caption, color = EventsColors.TextTertiary)
        }
        if (showCampsite) Text(a.campsiteName, style = EventsType.BodyStrong, color = EventsColors.Text)
        Text(a.text, style = EventsType.BodySmall, color = EventsColors.Text)
        Text("Delivered to ${a.recipientCount}", style = EventsType.Caption, color = EventsColors.TextSecondary)
    }
}

// ---- Reporting a campsite ----

/** Report a campsite to the admins (Figma E-REPORT). */
@Composable
fun ReportDialog(campsiteName: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var reason by remember { mutableStateOf(REPORT_REASONS.first()) }
    var details by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        DuskCard(Modifier.fillMaxWidth().testTag("report_dialog"), padding = PaddingValues(20.dp), spacing = 10.dp) {
            Text("Report $campsiteName", style = EventsType.Title, color = EventsColors.Text)
            Text("An admin will review it. The owner won't see who reported.", style = EventsType.BodySmall, color = EventsColors.TextSecondary)
            REPORT_REASONS.forEach { r ->
                Row(Modifier.fillMaxWidth().clickable { reason = r }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(reason == r, { reason = r }, colors = RadioButtonDefaults.colors(selectedColor = EventsColors.Primary, unselectedColor = EventsColors.TextSecondary))
                    Text(r, style = EventsType.Body, color = EventsColors.Text)
                }
            }
            EventsTextField(details, { if (it.length <= 280) details = it }, "Details (optional)", singleLine = false, minLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Cancel", onDismiss, Modifier.weight(1f), size = ButtonSize.SMALL)
                DangerButton("Report", { onSubmit(if (details.isBlank()) reason else "$reason: ${details.trim()}") }, Modifier.weight(1f).testTag("report_submit"), size = ButtonSize.SMALL)
            }
        }
    }
}

// ---- Reviews on a campsite ----

/** The latest reviews on a campsite, under its rating. */
@Composable
fun ReviewsSection(ratings: List<Rating>, average: Double, count: Int) {
    Column(Modifier.fillMaxWidth().testTag("reviews"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Reviews")
        if (count == 0) {
            Text("No ratings yet. Guests can rate after they book a stay.", style = EventsType.BodySmall, color = EventsColors.TextSecondary)
            return
        }
        RatingLine(average, count, "($count rating${if (count == 1) "" else "s"})")
        ratings.filter { it.comment.isNotBlank() }.take(5).forEach { r ->
            DuskCard(Modifier.fillMaxWidth(), spacing = 4.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(r.userName.substringBefore(' '), style = EventsType.BodyStrong, color = EventsColors.Text, modifier = Modifier.weight(1f))
                    StarRow(r.stars, {}, size = 14)
                }
                Text(r.comment, style = EventsType.BodySmall, color = EventsColors.TextSecondary)
                Text(formatDay(r.createdAt), style = EventsType.Caption, color = EventsColors.TextTertiary)
            }
        }
    }
}

/** The conversation open on screen right now, if any (so it doesn't banner itself). */
object OpenChat { @Volatile var id: String? = null }

/**
 * The banner that drops in at the top of the app when a new message or notice arrives
 * ("Priya · Two Jack Lakeside: Draw moved to Friday"). Tapping it opens that conversation; it
 * hides itself after a few seconds.
 */
@Composable
fun NewMessageBannerHost(container: AppContainer, modifier: Modifier = Modifier, onOpen: (Chat) -> Unit) {
    var shown by remember { mutableStateOf<Chat?>(null) }
    LaunchedEffect(container) {
        var before: List<Chat>? = null
        container.social.observeChatsFor(container.userId).collect { now ->
            newMessageBanner(before, now, container.userId, OpenChat.id)?.let { shown = it }
            before = now
        }
    }
    LaunchedEffect(shown) { if (shown != null) { kotlinx.coroutines.delay(4_000); shown = null } }
    androidx.compose.animation.AnimatedVisibility(
        visible = shown != null, modifier = modifier.statusBarsPadding().padding(horizontal = PageMargin, vertical = 8.dp),
        enter = androidx.compose.animation.slideInVertically { -it } + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically { -it } + androidx.compose.animation.fadeOut(),
    ) {
        val c = shown ?: return@AnimatedVisibility
        val me = container.userId
        DuskCard(
            Modifier.fillMaxWidth().testTag("message_banner"),
            onClick = { shown = null; onOpen(c) },
            padding = PaddingValues(12.dp), color = EventsColors.SurfaceSoft, borderColor = EventsColors.Primary,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(R.drawable.ic_chat, bg = EventsColors.CoralContainer, tint = EventsColors.Coral)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (me == c.ownerId) "${c.entrantName} · ${c.campsiteName}" else "${c.ownerName} · ${c.campsiteName}",
                        style = EventsType.BodyStrong, color = EventsColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(c.lastText, style = EventsType.BodySmall, color = EventsColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
