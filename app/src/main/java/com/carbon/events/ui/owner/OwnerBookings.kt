/*
 * OwnerBookings.kt (Suvir, Vaibhav)
 *
 * Bookings made by lottery winners at the owner's campsites: active and past reservations,
 * booked dates, rejecting one (US 02.06.01, US 02.07.01, US 02.07.02).
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val sharing = SharingStarted.WhileSubscribed(5_000)

/**
 * Reservations at my campsites (Figma O-BOOKINGS): a strip of booked dates (US 02.06.01) and
 * the active (US 02.07.01) and past (US 02.07.02) reservations, optionally for one campsite.
 *
 * @param campsiteId show one campsite only (opened from Manage), or null for the Bookings tab.
 * @param onBack shown as a back button when opened from a campsite.
 */
@Composable
fun OwnerBookingsScreen(
    container: AppContainer,
    profile: UserProfile? = null,
    campsiteId: String? = null,
    onBack: (() -> Unit)? = null,
    onChat: (Chat) -> Unit = {},
) {
    val vm = containerViewModel(container, key = "owner-bookings-${campsiteId.orEmpty()}") { OwnerBookingsViewModel(it, campsiteId) }
    val s by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<Reservation?>(null) }
    var startReject by remember { mutableStateOf(false) }
    val shown = s.reservations.filter { if (s.siteFilter == null) true else it.campsiteId == s.siteFilter }
    val ended = s.ended.filter { if (s.siteFilter == null) true else it.campsiteId == s.siteFilter }
    val (active, past) = shown.partition { it.isActive(s.today) }
    val list = when (tab) { 0 -> active.sortedBy { it.checkInDay }; 1 -> past.sortedByDescending { it.checkInDay }; else -> ended }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
    if (onBack != null) BackTopBar(s.campsites.firstOrNull { it.id == campsiteId }?.let { "Bookings · ${it.name}" } ?: "Bookings", onBack)
    LazyColumn(
        Modifier.fillMaxSize().testTag("owner_bookings"),
        contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack == null) item {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Bookings", style = EventsType.DisplaySmall, color = EventsColors.Coral, modifier = Modifier.weight(1f))
            }
        }
        if (campsiteId == null && s.campsites.size > 1) item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EventsChip("All", if (s.siteFilter == null) ChipStyle.CORAL else ChipStyle.GLASS, { vm.filter(null) }, Modifier.testTag("bookings_site_all"))
                s.campsites.forEach { site ->
                    EventsChip(site.name, if (s.siteFilter == site.id) ChipStyle.CORAL else ChipStyle.GLASS, { vm.filter(site.id) }, Modifier.testTag("bookings_site_${site.id}"))
                }
            }
        }
        item { BookedDatesStrip(s.today, shown) }
        item { SegmentedControl(listOf("Active · ${active.size}", "Past · ${past.size}", "Cancelled · ${ended.size}"), tab, { tab = it }, tagPrefix = "bookings_tab_") }
        if (!s.loading && list.isEmpty()) item {
            DuskCard {
                EmptyState(
                    R.drawable.ic_event_check,
                    when (tab) { 0 -> "No active bookings"; 1 -> "No past bookings"; else -> "Nothing cancelled" },
                    when (tab) {
                        0 -> "When lottery winners pick their nights, their stays appear here."
                        1 -> "Finished stays will be listed here."
                        else -> "Reservations guests cancel or you reject show up here."
                    },
                )
            }
        }
        if (list.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().clip(EventsShapes.Large).background(EventsColors.Surface).padding(6.dp)) {
                list.forEach { r ->
                    val site = s.campsites.firstOrNull { it.id == r.campsiteId }
                    GuestReservationRow(
                        r, showCampsite = s.siteFilter == null && s.campsites.size > 1, today = s.today, onClick = { open = r },
                        onChat = { onChat(Chat(Chat.idFor(r.campsiteId, r.entrantId), r.campsiteId, r.campsiteName, r.ownerId, site?.ownerName ?: profile?.name.orEmpty(), r.entrantId, r.entrantName, lotteryKey = Paths.keyOf(r.lotteryId))) },
                        onReject = if (r.status == EntryStatus.WON && r.isActive(s.today)) ({ open = r; startReject = true }) else null,
                    )
                }
            }
        }
    }
    }
    open?.let { r ->
        val site = s.campsites.firstOrNull { it.id == r.campsiteId }
        GuestReservationDialog(
            r, site, s.today, s.working, startRejecting = startReject, onDismiss = { open = null; startReject = false },
            onMessage = {
                open = null
                onChat(Chat(Chat.idFor(r.campsiteId, r.entrantId), r.campsiteId, r.campsiteName, r.ownerId, site?.ownerName ?: profile?.name.orEmpty(), r.entrantId, r.entrantName, lotteryKey = Paths.keyOf(r.lotteryId)))
            },
            onReject = { reason -> vm.reject(r, reason); open = null; startReject = false },
        )
    }
}

/** A guest's reservation for the owner: details, message the guest, or reject with a reason (refund note). */
@Composable
private fun GuestReservationDialog(r: Reservation, site: Campsite?, today: Long, working: Boolean, startRejecting: Boolean = false, onDismiss: () -> Unit, onMessage: () -> Unit, onReject: (String) -> Unit) {
    var rejecting by remember { mutableStateOf(startRejecting) }
    var reason by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        DuskCard(Modifier.fillMaxWidth().testTag("guest_dialog"), padding = PaddingValues(20.dp), spacing = 10.dp) {
            Text(r.entrantName.ifBlank { "Guest" }, style = EventsType.Title, color = EventsColors.Text)
            ReservationTag(r, today)
            DetailRow(R.drawable.ic_tent, "Campsite", r.campsiteName)
            DetailRow(R.drawable.ic_calendar, "Stay", formatStay(r.checkInDay, r.checkOutDay))
            DetailRow(R.drawable.ic_group, "Guests", "${r.guests}")
            if (site != null) DetailRow(R.drawable.ic_money, "Pays at check-in", "$${StayPrice(r.nights, site.pricePerNight).total}")
            if (r.status == EntryStatus.REJECTED) Text("Rejected: ${r.note.ifBlank { "no reason given" }}", style = EventsType.BodySmall, color = EventsColors.Danger)
            if (rejecting) {
                EventsTextField(reason, { if (it.length <= 200) reason = it }, "Reason (sent to the guest)", Modifier.testTag("reject_reason"), "e.g. The site is closed for repairs that week", singleLine = false, minLines = 2)
                Text("Nothing has been paid, so there's nothing to refund. The spot is offered to the next entrant.", style = EventsType.Caption, color = EventsColors.TextTertiary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Back", { rejecting = false }, Modifier.weight(1f), size = ButtonSize.SMALL)
                    DangerButton("Reject", { onReject(reason.trim()) }, Modifier.weight(1f).testTag("reject_confirm"), size = ButtonSize.SMALL, loading = working)
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Message", onMessage, Modifier.weight(1f).testTag("guest_message"), icon = R.drawable.ic_chat, size = ButtonSize.SMALL)
                if (r.status == EntryStatus.WON && r.isActive(today)) DangerButton("Reject", { rejecting = true }, Modifier.weight(1f).testTag("guest_reject"), size = ButtonSize.SMALL)
            }
        }
    }
}

/** Figma "Booked dates" card: the next two weeks with a dot on every booked night. */
@Composable
private fun BookedDatesStrip(today: Long, reservations: List<Reservation>) {
    val booked = remember(reservations) { reservations.flatMap { it.nightDays() }.toSet() }
    val days = (today until today + 14).toList()
    DuskCard(Modifier.fillMaxWidth().testTag("booked_dates"), padding = PaddingValues(14.dp), spacing = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Booked dates · ${monthLabel(today)}", style = EventsType.BodyStrong, color = EventsColors.Text, modifier = Modifier.weight(1f))
            Text("${days.count { it in booked }} of 14 nights", style = EventsType.Caption, color = EventsColors.TextSecondary)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach { d ->
                val date = java.time.LocalDate.ofEpochDay(d)
                val on = d in booked
                Column(
                    Modifier.width(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (d == today) EventsColors.PrimaryContainer else Color.Transparent).padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.CANADA), style = EventsType.Caption, color = EventsColors.TextSecondary)
                    Text("${date.dayOfMonth}", style = EventsType.BodyStrong, color = EventsColors.Text)
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (on) EventsColors.Coral else Color.Transparent))
                }
            }
        }
    }
}

private fun monthLabel(day: Long): String =
    java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.CANADA).format(java.time.LocalDate.ofEpochDay(day))

/** One guest's stay (Figma O-BOOKINGS row): avatar, name, dates · guests, and message / reject buttons. */
@Composable
private fun GuestReservationRow(r: Reservation, showCampsite: Boolean, today: Long, onClick: () -> Unit, onChat: () -> Unit = {}, onReject: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(EventsShapes.Medium).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 12.dp).testTag("reservation_${r.lotteryId}_${r.entrantId}"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val tone = avatarTones[(r.entrantId.hashCode() and 0x7fffffff) % avatarTones.size]
        Avatar(initialsOf(r.entrantName), size = 40.dp, bg = tone.first, fg = tone.second)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(r.entrantName.ifBlank { "Guest" }, style = EventsType.BodyStrong, color = EventsColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${formatStayShort(r.checkInDay, r.checkOutDay).substringBefore(" ·")} · ${r.guests} guest${if (r.guests == 1) "" else "s"}",
                style = EventsType.BodySmall, color = EventsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (showCampsite) Text(r.campsiteName, style = EventsType.Caption, color = EventsColors.TextTertiary, maxLines = 1)
            if (r.status != EntryStatus.WON) ReservationTag(r, today)
        }
        RoundIconButton(R.drawable.ic_chat, "Message ${r.entrantName}", EventsColors.PrimaryContainer, EventsColors.Text, Modifier.testTag("guest_chat_${r.entrantId}"), onChat)
        if (onReject != null) RoundIconButton(R.drawable.ic_close, "Reject", EventsColors.DangerContainer, EventsColors.Danger, Modifier.testTag("guest_reject_${r.entrantId}"), onReject)
    }
}

@Composable
internal fun RoundIconButton(icon: Int, label: String, bg: Color, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.size(34.dp).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        EventsIcon(icon, size = 16.dp, tint = tint, contentDescription = label)
    }
}

private fun initialsOf(name: String) = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }

/** Avatar fills and initials colours used on the Figma waitlist rows. */
private val avatarTones = listOf(
    EventsColors.CoralContainer to EventsColors.Coral,
    EventsColors.PrimaryContainer to EventsColors.Primary,
    EventsColors.GreenContainer to EventsColors.Green,
    EventsColors.AmberContainer to EventsColors.Amber,
)

/** State of the owner's Bookings screen. */
data class OwnerBookingsState(
    val loading: Boolean = true,
    val reservations: List<Reservation> = emptyList(),
    val campsites: List<Campsite> = emptyList(),
    val siteFilter: String? = null,
    val today: Long = 0L,
    val ended: List<Reservation> = emptyList(),
    val working: Boolean = false,
)

/**
 * Reservations at the owner's campsites (US 02.06.01, US 02.07.01, US 02.07.02).
 *
 * @param campsiteId limit to one campsite, or null for all of them (filterable with chips).
 */
class OwnerBookingsViewModel(private val container: AppContainer, private val campsiteId: String? = null) : ViewModel() {
    private val filter = MutableStateFlow(campsiteId)
    private val working = MutableStateFlow(false)

    /** Bookings state. */
    val state: StateFlow<OwnerBookingsState> = combine(
        if (campsiteId != null) container.lotteries.observeReservationsForOwner(container.userId).map { list -> list.filter { it.campsiteId == campsiteId } }
        else container.lotteries.observeReservationsForOwner(container.userId),
        container.campsites.observeCampsitesByOwner(container.userId),
        combine(filter, working) { f, w -> f to w },
        nowTicker(container.clock, 60_000),
        container.lotteries.observeEndedReservationsForOwner(container.userId),
    ) { list, sites, (f, w), now, ended ->
        OwnerBookingsState(false, list, sites, f, dayOf(now), if (campsiteId == null) ended else ended.filter { it.campsiteId == campsiteId }, w)
    }.stateIn(viewModelScope, sharing, OwnerBookingsState(siteFilter = campsiteId))

    /** Rejects a guest's reservation with [reason]; the guest is told and the spot reopens. */
    fun reject(r: Reservation, reason: String) {
        working.value = true
        viewModelScope.launch {
            runCatching { container.lotteries.rejectReservation(r.lotteryId, r.entrantId, reason, container.clock.now()) }
            working.value = false
        }
    }

    /** Shows one campsite's bookings, or all with null (ignored when opened for one campsite). */
    fun filter(id: String?) { if (campsiteId == null) filter.value = id }
}
