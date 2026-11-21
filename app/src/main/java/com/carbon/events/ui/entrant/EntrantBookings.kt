/*
 * EntrantBookings.kt (Suvir)
 *
 * The entrant's Bookings tab: the next stay as a card, later stays and past ones.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val keepAlive = SharingStarted.WhileSubscribed(5_000)

// ---- Bookings tab and reservation details ----

/** State of the entrant's Bookings tab. */
data class EntrantBookingsState(
    val loading: Boolean = true,
    val reservations: List<Reservation> = emptyList(),
    val ended: List<Reservation> = emptyList(),
    val campsites: Map<String, Campsite> = emptyMap(),
    val today: Long = 0L,
)

/** The entrant's reservations (active, past, cancelled or rejected), with campsite photos. */
class EntrantBookingsViewModel(private val container: AppContainer) : ViewModel() {
    private val working = MutableStateFlow<String?>(null)
    private val error = MutableStateFlow<String?>(null)

    /** Bookings state. */
    val state: StateFlow<EntrantBookingsState> = combine(
        container.lotteries.observeEntriesForEntrant(container.userId),
        container.campsites.observeCampsites(),
        nowTicker(container.clock, 60_000),
    ) { entries, sites, now ->
        EntrantBookingsState(
            false, entries.mapNotNull { it.toReservation() }, entries.mapNotNull { it.toEndedReservation() },
            sites.associateBy { it.id }, dayOf(now),
        )
    }.stateIn(viewModelScope, keepAlive, EntrantBookingsState())

    /** The reservation being cancelled, if any (for the button spinner). */
    val cancelling: StateFlow<String?> = working

    /** Why the last cancel didn't go through, until it is dismissed or tried again. */
    val cancelError: StateFlow<String?> = error

    /** Hides [cancelError]. */
    fun dismissError() { error.value = null }

    /**
     * Cancels a booking (Figma E-CANCEL); the spot goes to someone else on the waitlist. Once it
     * is cancelled, [onCancelled] opens its confirmation (Figma E-CANCELLED).
     */
    fun cancel(lotteryId: String, onCancelled: (String) -> Unit = {}) {
        if (working.value != null) return
        working.value = lotteryId
        error.value = null
        viewModelScope.launch {
            runCatching { container.lotteries.giveUpSpot(lotteryId, container.userId, container.clock.now()) }
                .onSuccess { working.value = null; onCancelled(lotteryId) }
                // Say why instead of leaving the guest to guess whether it worked.
                .onFailure { e -> working.value = null; error.value = e.cancelMessage() }
        }
    }
}

/**
 * The entrant's Bookings tab (Figma E-BOOKINGS, E-CANCEL): the next stay as a photo card with
 * Cancel booking and a chat button, later stays as rows with a chat button (open one to cancel
 * it); Past holds finished, cancelled and rejected stays.
 */
@Composable
fun EntrantBookingsScreen(container: AppContainer, onOpen: (String) -> Unit, onExplore: () -> Unit, onChat: (Chat) -> Unit = {}, profile: UserProfile? = null) {
    val vm = containerViewModel(container) { EntrantBookingsViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val cancelling by vm.cancelling.collectAsStateWithLifecycle()
    val cancelError by vm.cancelError.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var confirmCancel by remember { mutableStateOf<Reservation?>(null) }
    val (active, past) = s.reservations.partition { it.isActive(s.today) }
    val pastAll = (past + s.ended).sortedByDescending { it.checkInDay }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().testTag("entrant_bookings"),
        contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) { Text("Bookings", style = EventsType.Headline, color = EventsColors.Text) } }
        item { SegmentedControl(listOf("Active · ${active.size}", "Past · ${pastAll.size}"), tab, { tab = it }, tagPrefix = "my_bookings_tab_") }
        cancelError?.let { message ->
            item {
                DuskCard(Modifier.fillMaxWidth().testTag("cancel_error"), color = EventsColors.DangerContainer, padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        EventsIcon(R.drawable.ic_warning, tint = EventsColors.Text, size = 18.dp)
                        Text(message, style = EventsType.BodySmall, color = EventsColors.Text, modifier = Modifier.weight(1f))
                        Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = vm::dismissError).testTag("cancel_error_dismiss"), contentAlignment = Alignment.Center) {
                            EventsIcon(R.drawable.ic_close, tint = EventsColors.Text, size = 16.dp, contentDescription = "Dismiss")
                        }
                    }
                }
            }
        }
        val list = if (tab == 0) active.sortedBy { it.checkInDay } else pastAll
        if (!s.loading && list.isEmpty()) item {
            DuskCard {
                EmptyState(
                    R.drawable.ic_event_check,
                    if (tab == 0) "No upcoming stays" else "No past stays",
                    if (tab == 0) "Win a lottery, then pick your nights to see your booking here." else "Finished, cancelled and rejected stays show up here.",
                ) { if (tab == 0) PrimaryButton("Explore campsites", onExplore) }
            }
        }
        items(list, key = { it.lotteryId + it.status }) { r ->
            val site = s.campsites[r.campsiteId]
            val chat = {
                if (site != null && profile != null) onChat(Chat(Chat.idFor(site.id, profile.id), site.id, site.name, site.ownerId, site.ownerName, profile.id, profile.name, lotteryKey = Paths.keyOf(r.lotteryId)))
            }
            if (tab == 0 && r == list.first()) ActiveBookingCard(r, site, s.today, cancelling == r.lotteryId, { onOpen(r.lotteryId) }, { confirmCancel = r }, chat)
            else DuskCard(Modifier.fillMaxWidth().testTag("my_booking_${r.lotteryId}"), onClick = { onOpen(r.lotteryId) }, padding = PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(60.dp).clip(EventsShapes.Medium)) { CampsiteImage(r.campsiteId, site?.imageBase64) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(r.campsiteName, style = EventsType.BodyStrong, color = EventsColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        MetaItem(R.drawable.ic_calendar, "${formatStay(r.checkInDay, r.checkOutDay).substringBefore(" ·")} · ${r.guests} guest${if (r.guests == 1) "" else "s"}")
                        ReservationTag(r, s.today)
                    }
                    // Figma E-BOOKINGS: later active stays keep the chat button; past ones open on tap.
                    if (tab == 0) ChatTile(r.lotteryId, chat)
                    else EventsIcon(R.drawable.ic_chevron_right, tint = EventsColors.TextSecondary, size = 20.dp)
                }
            }
        }
    }
    confirmCancel?.let { r ->
        ConfirmDialog(
            R.drawable.ic_delete, "Cancel This Booking?",
            "${r.campsiteName} · ${formatStay(r.checkInDay, r.checkOutDay).substringBefore(" ·")}. Your spot goes to the next person on the waitlist, and you'll get a cancellation confirmation.",
            "Cancel booking", onConfirm = { confirmCancel = null; vm.cancel(r.lotteryId, onOpen) }, onDismiss = { confirmCancel = null }, dismissText = "Keep it",
        )
    }
}

/** Figma E-BOOKINGS card for the next stay: photo, name and status, dates · nights, guests, Cancel booking and chat. */
@Composable
private fun ActiveBookingCard(r: Reservation, site: Campsite?, today: Long, cancelling: Boolean, onOpen: () -> Unit, onCancel: () -> Unit, onChat: () -> Unit) {
    DuskCard(Modifier.fillMaxWidth().testTag("my_booking_${r.lotteryId}"), onClick = onOpen, padding = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxWidth().height(96.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).testTag("featured_booking_${r.lotteryId}")) {
            CampsiteImage(r.campsiteId, site?.imageBase64)
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.campsiteName, style = EventsType.Title, color = EventsColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                ReservationTag(r, today)
            }
            MetaItem(R.drawable.ic_calendar, "${formatStay(r.checkInDay, r.checkOutDay).substringBefore(" ·")} · ${r.nights} night${if (r.nights == 1L) "" else "s"}")
            MetaItem(R.drawable.ic_group, "${r.guests} guest${if (r.guests == 1) "" else "s"}")
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (r.checkInDay > today) DangerButton("Cancel booking", onCancel, Modifier.testTag("cancel_${r.lotteryId}"), size = ButtonSize.SMALL, loading = cancelling)
                Box(Modifier.weight(1f))
                ChatTile(r.lotteryId, onChat)
            }
        }
    }
}

/** Figma E-BOOKINGS: the chat button is a tinted rounded-square tile. */
@Composable
private fun ChatTile(lotteryId: String, onChat: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(EventsShapes.Medium).background(EventsColors.CoralContainer).clickable(onClick = onChat).testTag("chat_$lotteryId"),
        contentAlignment = Alignment.Center,
    ) { EventsIcon(R.drawable.ic_chat, size = 18.dp, tint = EventsColors.Coral, contentDescription = "Message host") }
}

/** Status tag for a reservation. */
@Composable
fun ReservationTag(r: Reservation, today: Long) = when {
    r.status == EntryStatus.CANCELLED -> StatusTag("Cancelled", TagTone.AMBER)
    r.status == EntryStatus.REJECTED -> StatusTag("Rejected", TagTone.RED)
    r.isPast(today) -> StatusTag("Completed", TagTone.GREY)
    r.checkInDay <= today -> StatusTag("Staying now", TagTone.GREEN)
    else -> StatusTag("Confirmed", TagTone.BLUE)
}
