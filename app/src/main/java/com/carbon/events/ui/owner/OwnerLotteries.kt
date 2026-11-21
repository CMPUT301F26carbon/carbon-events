/*
 * OwnerLotteries.kt (Aditya)
 *
 * The owner's lotteries and a lottery's waitlist, with its entrants (US 02.09.01).
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Avatar fills and initials colours used on the Figma waitlist rows. */
private val avatarTones = listOf(
    EventsColors.CoralContainer to EventsColors.Coral,
    EventsColors.PrimaryContainer to EventsColors.Primary,
    EventsColors.GreenContainer to EventsColors.Green,
    EventsColors.AmberContainer to EventsColors.Amber,
)

private val sharing = SharingStarted.WhileSubscribed(5_000)


/** All lotteries the owner runs (Figma O-LOTTERIES). */
@Composable
fun OwnerLotteriesScreen(container: AppContainer, onOpen: (String) -> Unit, onNew: () -> Unit) {
    val vm = containerViewModel(container) { OwnerLotteriesViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val (drawn, live) = s.lotteries.partition { it.status == LotteryStatus.DRAWN }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Lotteries", style = EventsType.DisplaySmall, color = EventsColors.Coral, modifier = Modifier.weight(1f))
            }
        }
        if (!s.loading && s.lotteries.isEmpty()) item { DuskCard { EmptyState(R.drawable.ic_ticket, "No lotteries yet", "Create a lottery for one of your campsites.") } }
        if (live.isNotEmpty()) item { SectionTitle("Live Now · ${live.size}") }
        items(live, key = { it.id }) { lot -> OwnerLotteryCard(lot, s.campsites[lot.campsiteId], s.now, onOpen) }
        if (drawn.isNotEmpty()) item { SectionTitle("Recently Drawn", Modifier.padding(top = 4.dp)) }
        items(drawn, key = { it.id }) { lot -> OwnerLotteryCard(lot, s.campsites[lot.campsiteId], s.now, onOpen) }
        item { PrimaryButton("New lottery", onNew, Modifier.fillMaxWidth().padding(top = 4.dp).testTag("owner_new_lottery"), icon = R.drawable.ic_add) }
    }
}

/** Figma lottery row: campsite card with a LIVE / DRAWN tag and the close date and count. */
@Composable
private fun OwnerLotteryCard(lot: Lottery, site: Campsite?, now: Long, onOpen: (String) -> Unit) {
    val campsite = site ?: Campsite(id = lot.campsiteId, name = lot.campsiteName, location = "", ownerId = lot.ownerId, ownerName = "", maxOccupancy = 0)
    CampsiteListCard(campsite, { onOpen(lot.id) }, Modifier.testTag("owner_lottery_${lot.id}"), thumb = 72.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                lot.status == LotteryStatus.DRAWN -> StatusTag("Drawn", TagTone.BLUE)
                lot.isRegistrationOpen(now) -> StatusTag("Live", TagTone.GREEN)
                else -> LotteryStatusTag(lot, now)
            }
            Text(
                if (lot.status == LotteryStatus.DRAWN) "${formatDay(lot.drawnAt ?: lot.registrationEnd)} · ${lot.winnerCount} winners notified"
                else "Closes ${formatDay(lot.registrationEnd)} · ${lot.waitlistCap?.let { "${lot.entrantCount} / $it" } ?: "${lot.entrantCount}"} joined",
                style = EventsType.Caption, color = EventsColors.TextSecondary, maxLines = 1,
            )
        }
    }
}

/**
 * A lottery's waiting list and winners with their booked nights (Figma O-WAITLIST,
 * US 02.09.01, US 02.05.01, US 02.06.01).
 *
 * @param onEdit opens the lottery form in edit mode (US 02.04.06); hidden after the draw.
 */
@Composable
fun WaitlistScreen(
    container: AppContainer,
    lotteryId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit = {},
    onNotify: (String) -> Unit = {},
    onChat: (Chat) -> Unit = {},
    ownerName: String = "",
) {
    val vm = containerViewModel(container, key = "waitlist-$lotteryId") { WaitlistViewModel(it, lotteryId) }
    val s by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    val lot = s.lottery
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackTopBar(lot?.let { "Waitlist · ${it.campsiteName}" } ?: "Waitlist", onBack) {
            if (lot != null && lot.status != LotteryStatus.DRAWN) GlassIconButton(R.drawable.ic_edit, "Edit lottery", { onEdit(lot.id) }, Modifier.testTag("waitlist_edit"))
        }
        if (s.loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = EventsColors.Coral) }; return@Column }
        if (lot == null) { EmptyState(R.drawable.ic_info, "Lottery not found", "It may have been removed."); return@Column }
        val winners = s.entries.filter { it.status == EntryStatus.WON }
        // Figma O-WAITLIST: two tabs, Waitlist (everyone else) and Winners.
        val waitlist = s.entries.filter { it.status != EntryStatus.WON }
        val shown = if (tab == 0) waitlist else winners
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                DuskCard(Modifier.fillMaxWidth().testTag("waitlist_summary"), padding = PaddingValues(14.dp), spacing = 8.dp) {
                    Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        val cap = lot.waitlistCap
                        Text(if (cap != null) "${s.entries.size} of $cap spots filled" else "${s.entries.size} joined", style = EventsType.BodyStrong, color = EventsColors.Text, modifier = Modifier.weight(1f))
                        if (lot.status == LotteryStatus.DRAWN) StatusTag("Drawn", TagTone.GREY)
                        else if (lot.isRegistrationOpen(s.now)) StatusTag("Open", TagTone.GREEN)
                        else LotteryStatusTag(lot, s.now)
                    }
                    lot.waitlistCap?.takeIf { it > 0 }?.let { cap ->
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(EventsColors.PrimaryContainer)) {
                            Box(Modifier.fillMaxWidth((s.entries.size.toFloat() / cap).coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(EventsColors.Primary))
                        }
                    }
                    MetaItem(R.drawable.ic_calendar, "Open ${formatRange(lot.registrationStart, lot.registrationEnd)}")
                    MetaItem(R.drawable.ic_trophy, "${if (lot.status == LotteryStatus.DRAWN) "Drawn" else "Auto-draw"} ${formatDayTime(lot.drawnAt ?: lot.registrationEnd)} · ${lot.winnerCount} winners")
                    if (lot.hasBookingPeriod) MetaItem(R.drawable.ic_event_check, "Stays ${formatStay(lot.bookingStartDay!!, lot.bookingEndDay!!)}")
                    if (lot.isDrawDue(s.now)) DrawNowButton(s.drawing) { vm.drawNow() }
                    if (lot.spotsFree > 0) Text("${lot.spotsFree} spot${if (lot.spotsFree == 1) "" else "s"} given back; offering ${if (lot.spotsFree == 1) "it" else "them"} to the next entrants…", style = EventsType.BodySmall, color = EventsColors.Amber)
                }
            }
            item {
                SegmentedControl(listOf("Waitlist · ${waitlist.size}", "Winners · ${winners.size}"), tab, { tab = it })
            }
            if (shown.isEmpty()) item {
                DuskCard {
                    EmptyState(
                        R.drawable.ic_group,
                        if (tab == 0) (if (s.entries.isEmpty()) "No one has joined yet" else "Everyone was drawn") else if (lot.status != LotteryStatus.DRAWN) "Not drawn yet" else "No winners",
                        if (tab == 0) "Share your promo QR code so campers can find this lottery." else "Winners are drawn automatically on ${formatDayTime(lot.registrationEnd)}.",
                    )
                }
            }
            if (shown.isNotEmpty()) item {
                Column(Modifier.fillMaxWidth().clip(EventsShapes.Large).background(EventsColors.Surface).padding(6.dp)) {
                    shown.forEach { e ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp).testTag("entry_${e.entrantId}"),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val tone = avatarTones[(e.entrantId.hashCode() and 0x7fffffff) % avatarTones.size]
                            Avatar(e.entrantName.split(" ").take(2).joinToString("") { it.take(1).uppercase() }, size = 40.dp, bg = tone.first, fg = tone.second)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(e.entrantName, style = EventsType.BodyStrong, color = EventsColors.Text, maxLines = 1)
                                Text(
                                    // Figma: "Party of 4 · joined Oct 2" (party size is known once they book).
                                    listOfNotNull(
                                        e.guests?.let { "Party of $it" },
                                        if (e.isBooked) "booked ${formatStayShort(e.checkInDay!!, e.checkOutDay!!).substringBefore(" ·")}"
                                        else if (e.status == EntryStatus.WON) "hasn’t picked dates yet" else "joined ${formatDay(e.joinedAt)}",
                                    ).joinToString(" · ").replaceFirstChar { it.uppercase() },
                                    style = EventsType.BodySmall, color = EventsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            // Individual messages are only for entrants selected in this lottery.
                            if (e.status == EntryStatus.WON) RoundIconButton(
                                R.drawable.ic_chat, "Message ${e.entrantName}", EventsColors.PrimaryContainer, EventsColors.Text, Modifier.testTag("waitlist_chat_${e.entrantId}"),
                            ) { onChat(Chat(Chat.idFor(lot.campsiteId, e.entrantId), lot.campsiteId, lot.campsiteName, lot.ownerId, ownerName, e.entrantId, e.entrantName, lotteryKey = Paths.keyOf(lot.id))) }
                            when (e.status) {
                                EntryStatus.WAITING -> StatusTag("Waiting", TagTone.BLUE)
                                EntryStatus.WON -> StatusTag("Winner", TagTone.GREEN)
                                EntryStatus.LOST -> StatusTag("Not selected", TagTone.GREY)
                                EntryStatus.DECLINED -> StatusTag("Declined", TagTone.AMBER)
                                EntryStatus.CANCELLED -> StatusTag("Cancelled", TagTone.AMBER)
                                EntryStatus.REJECTED -> StatusTag("Rejected", TagTone.RED)
                            }
                        }
                    }
                }
            }
        }
        // Figma O-WAITLIST: "Notify waitlist" across the bottom.
        if (s.entries.isNotEmpty()) ActionBar {
            PrimaryButton("Notify waitlist", { onNotify(lot.id) }, Modifier.weight(1f).testTag("waitlist_notify"), icon = R.drawable.ic_send)
        }
    }
}

/** State of the owner's lotteries list. */
data class OwnerLotteriesState(
    val loading: Boolean = true,
    val lotteries: List<Lottery> = emptyList(),
    val now: Long = 0L,
    val campsites: Map<String, Campsite> = emptyMap(),
)

/** All lotteries the owner runs. */
class OwnerLotteriesViewModel(container: AppContainer) : ViewModel() {
    /** Lotteries list state. */
    val state: StateFlow<OwnerLotteriesState> = combine(
        container.lotteries.observeLotteriesByOwner(container.userId), nowTicker(container.clock),
        container.campsites.observeCampsitesByOwner(container.userId),
    ) { list, now, sites -> OwnerLotteriesState(false, list, now, sites.associateBy { it.id }) }.stateIn(viewModelScope, sharing, OwnerLotteriesState())
}

/** State of the waiting-list screen. */
data class WaitlistState(val loading: Boolean = true, val lottery: Lottery? = null, val entries: List<WaitlistEntry> = emptyList(), val now: Long = 0L, val drawing: Boolean = false)

/** A lottery's waiting list and, after the draw, its winners (US 02.09.01, US 02.05.01). */
class WaitlistViewModel(private val container: AppContainer, private val lotteryId: String) : ViewModel() {
    private val drawing = MutableStateFlow(false)

    /** Waiting-list state. */
    val state: StateFlow<WaitlistState> = combine(
        container.lotteries.observeLottery(lotteryId),
        container.lotteries.observeEntries(lotteryId),
        nowTicker(container.clock, 15_000),
        drawing,
    ) { lot, entries, now, d -> WaitlistState(false, lot, entries, now, d) }.stateIn(viewModelScope, sharing, WaitlistState())

    /** Draws the winners now (registration has closed). */
    fun drawNow() {
        drawing.value = true
        viewModelScope.launch { runCatching { container.lotteries.runDrawIfDue(lotteryId, container.clock.now()) }; drawing.value = false }
    }
}
