/*
 * NotifyWaitlist.kt (Tausif)
 *
 * An owner's Notify waitlist, with the log of what they sent.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
internal fun SentCard(a: Announcement, showCampsite: Boolean) {
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
