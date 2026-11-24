/*
 * Chat.kt (Tausif)
 *
 * One conversation between a guest and a campsite's owner: text messages, photos, and emoji
 * reactions (long-press a message).
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val keepAlive = SharingStarted.WhileSubscribed(5_000)

// ---- One chat ----

/** State of one chat. */
data class ChatState(
    val chat: Chat? = null,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    /** The photo picked to send with the next message (base64 JPEG), if any. */
    val photo: String? = null,
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

    /**
     * Attaches a picked photo to the next message. [base64] is null when the phone couldn't read
     * the photo; a photo too big for one message is refused.
     */
    fun attachPhoto(base64: String?) {
        draft.value = when {
            base64 == null -> draft.value.copy(message = "Couldn't open that photo. Try another one.")
            base64.length > ChatPhotos.MAX_CHARS -> draft.value.copy(message = "That photo is too large to send.")
            else -> draft.value.copy(photo = base64, message = null)
        }
    }

    /** Takes the picked photo off the next message. */
    fun removePhoto() { draft.value = draft.value.copy(photo = null) }

    /**
     * Reacts to [m] with [emoji], or takes this person's reaction off if [emoji] is the one they
     * already chose.
     */
    fun react(m: ChatMessage, emoji: String) {
        val next = ChatReactions.toggle(m.reactions, container.userId, emoji)
        viewModelScope.launch {
            runCatching { container.social.react(start.id, m.id, container.userId, next) }
                .onFailure { e -> draft.value = draft.value.copy(message = e.userMessage()) }
        }
    }

    /** Sends the draft: text, a photo, or a photo with the text as its caption. */
    fun send(senderName: String) {
        val text = draft.value.draft.trim()
        val photo = draft.value.photo
        if (text.isEmpty() && photo == null) return
        draft.value = draft.value.copy(sending = true, message = null)
        viewModelScope.launch {
            runCatching { container.social.sendMessage(state.value.chat ?: start, container.userId, senderName, text, container.clock.now(), photo) }
                .onSuccess { draft.value = draft.value.copy(draft = "", photo = null, sending = false) }
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
    var viewing by rememberSaveable { mutableStateOf<String?>(null) }
    // The message whose reaction row is open (long-pressed), if any.
    var reactingTo by rememberSaveable { mutableStateOf<String?>(null) }
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
                items(msgs, key = { it.id }) { m ->
                    Bubble(
                        m, mine = m.senderId == me, me = me, onOpenPhoto = { viewing = m.photoBase64 },
                        reacting = reactingTo == m.id, onLongPress = { reactingTo = if (reactingTo == m.id) null else m.id },
                        onReact = { emoji -> vm.react(m, emoji); reactingTo = null },
                    )
                }
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
        } else if (s.canSend == true) Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = PageMargin, vertical = 10.dp)) {
            s.photo?.let { PendingPhoto(it, vm::removePhoto, Modifier.padding(bottom = 8.dp)) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChatPhotoButton(vm::attachPhoto, enabled = !s.sending)
                ChatInput(s.draft, vm::onDraft, Modifier.weight(1f), hint = if (s.photo != null) "Add a caption…" else "Write a message…")
                val ready = (s.draft.isNotBlank() || s.photo != null) && !s.sending
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(if (ready) EventsColors.Primary else EventsColors.PrimaryContainer)
                        .clickable(enabled = ready) { vm.send(profile.name) }.testTag("chat_send"),
                    contentAlignment = Alignment.Center,
                ) { EventsIcon(R.drawable.ic_send, tint = if (ready) EventsColors.OnPrimary else EventsColors.TextSecondary, size = 22.dp, contentDescription = "Send") }
            }
        }
    }
    viewing?.let { PhotoViewer(it) { viewing = null } }
}

/** Figma's pill "Write a message…" box (grows to a few lines). [hint] shows while it's empty. */
@Composable
private fun ChatInput(text: String, onChange: (String) -> Unit, modifier: Modifier, hint: String = "Write a message…") {
    Box(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(EventsColors.Tint)
            .border(1.dp, EventsColors.TintStroke, RoundedCornerShape(24.dp)).padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (text.isEmpty()) Text(hint, style = EventsType.Body, color = EventsColors.TextSoft)
        androidx.compose.foundation.text.BasicTextField(
            text, { if (it.length <= MAX_MESSAGE) onChange(it) }, Modifier.fillMaxWidth().testTag("chat_input"),
            textStyle = EventsType.Body.copy(color = EventsColors.Text), maxLines = 4,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(EventsColors.Coral),
        )
    }
}

/**
 * One message: a coloured bubble, with its photo (tap to open) above any text, and its reactions
 * underneath. Long-pressing it ([onLongPress]) opens the reaction row when [reacting].
 */
@Composable
private fun Bubble(
    m: ChatMessage, mine: Boolean, me: String, onOpenPhoto: () -> Unit,
    reacting: Boolean, onLongPress: () -> Unit, onReact: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (reacting) ReactionBar(m.id, m.reactions[me], onReact)
        Column(
            Modifier.widthIn(max = 280.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp))
                .combinedClickable(onClickLabel = null, onLongClickLabel = "React", onLongClick = onLongPress, onClick = { if (reacting) onLongPress() })
                .testTag("chat_bubble_${m.id}")
                .background(if (mine) EventsColors.Primary else EventsColors.Surface)
                .padding(horizontal = if (m.photoBase64 != null) 4.dp else 14.dp, vertical = if (m.photoBase64 != null) 4.dp else 9.dp),
        ) {
            if (m.announcement) Text("NOTICE", style = EventsType.Overline, color = if (mine) EventsColors.OnPrimary.copy(alpha = 0.8f) else EventsColors.Coral)
            m.photoBase64?.let { MessagePhoto(it, m.id, onLongClick = onLongPress, onOpen = onOpenPhoto) }
            Column(Modifier.padding(horizontal = if (m.photoBase64 != null) 10.dp else 0.dp, vertical = if (m.photoBase64 != null) 4.dp else 0.dp)) {
                if (m.text.isNotBlank()) Text(m.text, style = EventsType.Body, color = if (mine) EventsColors.OnPrimary else EventsColors.Text)
                Text(formatDayTime(m.sentAt), style = EventsType.Caption, color = if (mine) EventsColors.OnPrimary.copy(alpha = 0.7f) else EventsColors.TextTertiary)
            }
        }
        ReactionPills(m.id, m.reactions, me, onReact)
    }
}
