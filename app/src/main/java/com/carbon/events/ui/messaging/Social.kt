/*
 * Social.kt (Tausif)
 *
 * The inbox (guests' and owners' chats), the new-message banner, reporting a campsite and
 * campsite reviews.
 *
 * Outstanding issues: with the app closed, chat notifications arrive through BackgroundSync
 * about every 15 minutes rather than instantly (no push server).
 */
package com.carbon.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

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
                    StarRow(r.stars, null, size = 14)
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
