/*
 * Reservation.kt (Suvir)
 *
 * One reservation: its details, cancelling it, and the Booking Cancelled screen.
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val keepAlive = SharingStarted.WhileSubscribed(5_000)

/** State of one reservation's details. */
data class ReservationState(
    val loading: Boolean = true,
    val entry: WaitlistEntry? = null,
    val lottery: Lottery? = null,
    val campsite: Campsite? = null,
    val myRating: Rating? = null,
    val today: Long = 0L,
    val working: Boolean = false,
    val message: String? = null,
)

/** A guest's reservation: details, cancel, rate. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReservationViewModel(private val container: AppContainer, private val lotteryId: String) : ViewModel() {
    private val ui = MutableStateFlow(ReservationState())
    private val lotteryFlow = container.lotteries.observeLottery(lotteryId)
    private val campsiteId: String get() = state.value.lottery?.campsiteId ?: Paths.campsiteOf(lotteryId)

    /** Screen state. */
    val state: StateFlow<ReservationState> = combine(
        myEntry(container, lotteryId),
        lotteryFlow,
        lotteryFlow.flatMapLatest { l -> l?.let { container.campsites.observeCampsite(it.campsiteId) } ?: flowOf(null) },
        lotteryFlow.flatMapLatest { l -> l?.let { container.social.observeRatings(it.campsiteId) } ?: flowOf(emptyList()) },
        combine(ui, nowTicker(container.clock, 60_000)) { u, now -> u to now },
    ) { mine, lot, site, ratings, (u, now) ->
        u.copy(
            loading = false, entry = mine, lottery = lot, campsite = site,
            myRating = ratings.firstOrNull { it.userId == container.userId }, today = dayOf(now),
        )
    }.stateIn(viewModelScope, keepAlive, ReservationState())

    /** Cancels the reservation; the spot goes to someone else. */
    fun cancel() = act(Throwable::cancelMessage) { container.lotteries.giveUpSpot(lotteryId, container.userId, container.clock.now()) }

    /** Rates the stay. */
    fun rate(name: String, stars: Int, comment: String) = act {
        container.social.rate(Rating(campsiteId, container.userId, name, stars, comment.trim().take(RatingPolicy.MAX_COMMENT), lotteryId, container.clock.now()))
    }

    private fun act(explain: (Throwable) -> String = Throwable::userMessage, block: suspend () -> Unit) {
        ui.value = ui.value.copy(working = true, message = null)
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { ui.value = ui.value.copy(working = false) }
                .onFailure { e -> ui.value = ui.value.copy(working = false, message = explain(e)) }
        }
    }
}

/**
 * One reservation (Figma E-RESERVATION): code, stay, guests, price, rules, Message owner,
 * Rate your stay and Cancel reservation.
 */
@Composable
fun ReservationScreen(container: AppContainer, profile: UserProfile, lotteryId: String, onBack: () -> Unit, onCampsite: (String) -> Unit, onChat: (Chat) -> Unit) {
    val vm = containerViewModel(container, key = "reservation-$lotteryId") { ReservationViewModel(it, lotteryId) }
    val s by vm.state.collectAsStateWithLifecycle()
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    var rating by rememberSaveable { mutableStateOf(false) }
    // A cancelled stay opens on its confirmation (Figma E-CANCELLED), right after cancelling too.
    val cancelled = s.entry?.takeIf { it.status == EntryStatus.CANCELLED }?.toEndedReservation()
    if (cancelled != null) {
        BookingCancelledScreen(cancelled, s.campsite, reservationCode(lotteryId, profile.id), onBack)
        return
    }
    Column(Modifier.fillMaxSize()) {
        BackTopBar("Reservation", onBack, Modifier.statusBarsPadding())
        val entry = s.entry
        val lot = s.lottery
        val site = s.campsite
        if (s.loading) return@Column
        if (entry == null || entry.checkInDay == null || entry.checkOutDay == null || lot == null) {
            DuskCard(Modifier.padding(horizontal = PageMargin)) { EmptyState(R.drawable.ic_info, "Reservation not found", "It may have been removed by the owner.") }
            return@Column
        }
        val r = (entry.toReservation() ?: entry.toEndedReservation())!!
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PageMargin).padding(top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DuskCard(Modifier.fillMaxWidth(), onClick = { onCampsite(lot.campsiteId) }, padding = PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(60.dp).clip(EventsShapes.Medium)) { CampsiteImage(lot.campsiteId, site?.imageBase64) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(lot.campsiteName, style = EventsType.BodyStrong, color = EventsColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        site?.let { MetaItem(R.drawable.ic_location, it.location) }
                        ReservationTag(r, s.today)
                    }
                    EventsIcon(R.drawable.ic_chevron_right, tint = EventsColors.TextSecondary, size = 20.dp)
                }
            }
            if (r.status == EntryStatus.REJECTED) DuskCard(Modifier.fillMaxWidth(), color = EventsColors.DangerContainer, spacing = 4.dp) {
                Text("Rejected by the owner", style = EventsType.BodyStrong, color = EventsColors.Text)
                Text(r.note.ifBlank { "No reason given." }, style = EventsType.BodySmall, color = EventsColors.Text)
            }
            DuskCard(Modifier.fillMaxWidth().testTag("reservation_details"), spacing = 10.dp) {
                if (r.status == EntryStatus.WON) {
                    Text(reservationCode(lot.id, profile.id), style = EventsType.Title, color = EventsColors.Coral)
                    Text("Show this code at check-in", style = EventsType.Caption, color = EventsColors.TextTertiary)
                    CardDivider()
                }
                DetailRow(R.drawable.ic_calendar, "Check-in", "${formatDayNum(r.checkInDay)} · ${site?.checkInTime.orEmpty()}")
                DetailRow(R.drawable.ic_calendar, "Check-out", "${formatDayNum(r.checkOutDay)} · ${site?.checkOutTime.orEmpty()}")
                DetailRow(R.drawable.ic_group, "Guests", "${r.guests}")
                DetailRow(R.drawable.ic_person, "Host", site?.ownerName.orEmpty())
            }
            if (site != null) PriceCard(StayPrice(r.nights, site.pricePerNight))
            val code = reservationCode(lot.id, profile.id)
            CsvDownloadButton(
                when (ReceiptCsv.kindOf(r)) {
                    ReceiptCsv.Kind.RESERVATION -> "Download confirmation (CSV)"
                    ReceiptCsv.Kind.CANCELLATION -> "Download cancellation (CSV)"
                    ReceiptCsv.Kind.REJECTION -> "Download rejection notice (CSV)"
                },
                ReceiptCsv.fileName(r, code), { ReceiptCsv.build(r, code, site) }, Modifier.fillMaxWidth(),
            )
            DuskCard(Modifier.fillMaxWidth(), spacing = 6.dp) {
                Text("Campsite rules", style = EventsType.TitleSmall, color = EventsColors.Text)
                Text(site?.rules?.ifBlank { null } ?: DEFAULT_RULES, style = EventsType.BodySmall, color = EventsColors.TextSecondary)
            }
            if (r.status == EntryStatus.WON) {
                val mine = s.myRating
                DuskCard(Modifier.fillMaxWidth(), spacing = 8.dp) {
                    Text(if (mine == null) "How was your stay?" else "Your rating", style = EventsType.TitleSmall, color = EventsColors.Text)
                    if (mine != null) {
                        StarRow(mine.stars, null, size = 22)
                        if (mine.comment.isNotBlank()) Text("“${mine.comment}”", style = EventsType.BodySmall, color = EventsColors.TextSecondary)
                    }
                    SecondaryButton(if (mine == null) "Rate your stay" else "Edit rating", { rating = true }, Modifier.fillMaxWidth().testTag("rate_stay"), icon = R.drawable.ic_star, size = ButtonSize.SMALL)
                }
            }
            s.message?.let { Text(it, style = EventsType.BodySmall, color = EventsColors.Danger) }
        }
        if (site != null) ActionBar {
            // Only entrants selected in the lottery can message the owner.
            // Message the campsite owner about this reservation (US 01.14.01).
            if (r.status == EntryStatus.WON) SecondaryButton("Message host", {
                onChat(Chat(Chat.idFor(site.id, profile.id), site.id, site.name, site.ownerId, site.ownerName, profile.id, profile.name, lotteryKey = Paths.keyOf(lot.id)))
            }, Modifier.weight(1f).testTag("reservation_message"), icon = R.drawable.ic_chat)
            if (r.status == EntryStatus.WON && r.checkInDay > s.today) {
                DangerButton("Cancel", { confirmCancel = true }, Modifier.weight(1f).testTag("reservation_cancel"), loading = s.working)
            }
        }
    }
    if (confirmCancel) ConfirmDialog(
        R.drawable.ic_warning, "Cancel reservation?",
        "Your nights will be released and the spot offered to someone else on the waiting list. " +
            (s.campsite?.let { c -> s.entry?.checkInDay?.let { c.cancellation.chargeLine(it, s.today, c.pricePerNight) } } ?: "Nothing is charged."),
        "Cancel reservation", onConfirm = { confirmCancel = false; vm.cancel() }, onDismiss = { confirmCancel = false },
    )
    if (rating) RateDialog(
        s.myRating, onDismiss = { rating = false }, campsiteName = s.lottery?.campsiteName.orEmpty(),
        stayed = s.entry?.let { e -> if (e.checkInDay != null && e.checkOutDay != null) formatStay(e.checkInDay, e.checkOutDay).substringBefore(" ·") else null },
    ) { stars, comment -> rating = false; vm.rate(profile.name, stars, comment) }
}

/**
 * Figma E-CANCELLED: the stay, when it was cancelled, what it cost (nothing before the
 * free-cancellation date, then the first night under the campsite's policy; guests pay at
 * check-in, so there is no refund to wait for), that the confirmation is in the notifications,
 * the CSV to keep, and Back to bookings (US 01.07.02).
 */
@Composable
fun BookingCancelledScreen(r: Reservation, site: Campsite?, code: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = PageMargin, vertical = 32.dp).testTag("booking_cancelled"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.padding(top = 24.dp).size(72.dp).clip(CircleShape).background(EventsColors.PrimaryContainer), contentAlignment = Alignment.Center) {
            EventsIcon(R.drawable.ic_event_check, tint = EventsColors.Text, size = 32.dp)
        }
        Text("Booking Cancelled", style = EventsType.Headline, color = EventsColors.Text)
        Text(
            "${r.campsiteName} · ${formatStay(r.checkInDay, r.checkOutDay).substringBefore(" ·")}. Your spot goes to the next person on the waitlist.",
            style = EventsType.BodySmall, color = EventsColors.TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        DuskCard(Modifier.fillMaxWidth(), spacing = 10.dp) {
            r.endedAt?.let { SummaryRow("Cancelled on", formatDay(it)) }
            val owed = cancellationCharge(r, site)
            if (owed == 0) SummaryRow("Refund", "Nothing charged", EventsColors.Green)
            else SummaryRow("First night not refunded", "$$owed", EventsColors.Amber)
            SummaryRow("Reservation code", code)
        }
        DuskCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EventsIcon(R.drawable.ic_bell, tint = EventsColors.TextSecondary, size = 18.dp)
                Text("Cancellation confirmation sent to your notifications.", style = EventsType.BodySmall, color = EventsColors.TextSecondary)
            }
        }
        CsvDownloadButton("Download cancellation (CSV)", ReceiptCsv.fileName(r, code), { ReceiptCsv.build(r, code, site) }, Modifier.fillMaxWidth())
        PrimaryButton("Back to bookings", onBack, Modifier.fillMaxWidth().testTag("back_to_bookings"))
    }
}

/** What cancelling [r] cost under [site]'s policy, judged on the day it was cancelled; 0 if unknown. */
fun cancellationCharge(r: Reservation, site: Campsite?): Int =
    if (site == null || r.status != EntryStatus.CANCELLED) 0
    else site.cancellation.charge(r.checkInDay, dayOf(r.endedAt ?: r.bookedAt), site.pricePerNight)

/** Shown when a booking changed (rejected or already cancelled) before the guest's cancel arrived. */
const val CANCEL_CHANGED_MESSAGE = "This booking changed before it could be cancelled. It may have been rejected by the owner; check Past."

/** What to tell a guest whose cancel didn't go through. */
fun Throwable.cancelMessage(): String {
    var t: Throwable? = this
    while (t != null) {
        if (t is BookingException && t.error == BookingError.NOT_A_WINNER) return CANCEL_CHANGED_MESSAGE
        t = t.cause
    }
    return userMessage()
}

/** The signed-in person's own entry in [lotteryId], or null if they have none. */
internal fun myEntry(container: AppContainer, lotteryId: String): Flow<WaitlistEntry?> =
    container.lotteries.observeEntriesForEntrant(container.userId).map { list -> list.firstOrNull { it.lotteryId == lotteryId } }
