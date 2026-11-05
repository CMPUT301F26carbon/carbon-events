/*
 * Booking.kt (Suvir)
 *
 * After winning: pick nights and guests, review the reservation (price, campsite rules, pay at
 * check-in) and confirm; the entrant's Bookings tab, a reservation's details, cancelling it, and
 * rating the stay.
 *
 * Outstanding issues: Events takes no payments; guests pay the owner at check-in.
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.window.Dialog
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

/** GST charged on campsite stays in Alberta. */
const val GST_RATE = 0.05

/** Price of a stay: nights × nightly price, plus GST, in whole dollars. */
data class StayPrice(val nights: Long, val perNight: Int) {
    /** Nights × price. */
    val subtotal: Long get() = nights * perNight

    /** GST on the subtotal, rounded to the dollar. */
    val tax: Long get() = Math.round(subtotal * GST_RATE)

    /** What the guest pays at check-in. */
    val total: Long get() = subtotal + tax
}

/** A short code guests show at check-in, e.g. "EV-NOV26-7K3Q". */
fun reservationCode(lotteryId: String, entrantId: String): String {
    val key = Paths.keyOf(lotteryId).split('-').let { p -> (p.getOrNull(0).orEmpty().take(3) + p.getOrNull(1).orEmpty().takeLast(2)) }.uppercase()
    val chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    var h = (lotteryId + entrantId).hashCode().toLong() and 0x7fffffff
    val tail = buildString { repeat(4) { append(chars[(h % chars.length).toInt()]); h /= chars.length } }
    return "EV-${key.ifBlank { "LOT" }}-$tail"
}

/** State of the You Won / book your nights screen. */
data class BookDatesState(
    val loading: Boolean = true,
    val lottery: Lottery? = null,
    val campsite: Campsite? = null,
    val entry: WaitlistEntry? = null,
    val taken: Set<Long> = emptySet(),
    val takenRanges: List<Pair<Long, Long>> = emptyList(),
    val checkIn: Long? = null,
    val checkOut: Long? = null,
    val guests: Int = 1,
    val agreed: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null,
)

/**
 * Lets a lottery winner pick nights on the calendar, accept the rules and confirm (Figma E-WON,
 * E-RULES, E-CONFIRMED), or decline the spot (E-DECLINE).
 *
 * @param lotteryId the lottery the entrant won.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookDatesViewModel(private val container: AppContainer, private val lotteryId: String) : ViewModel() {
    private val form = MutableStateFlow(BookDatesState())

    /** Screen state. */
    val state: StateFlow<BookDatesState> = combine(
        container.lotteries.observeLottery(lotteryId),
        // Your own entry comes from your own list; the others' booked nights are readable only by winners.
        combine(myEntry(container, lotteryId), container.lotteries.observeEntries(lotteryId)) { mine, all -> mine to all },
        container.lotteries.observeLottery(lotteryId).flatMapLatest { l -> l?.let { container.campsites.observeCampsite(it.campsiteId) } ?: flowOf(null) },
        form,
    ) { lot, (mine, entries), site, f ->
        val others = entries.filter { it.entrantId != container.userId && it.isBooked }
        f.copy(
            loading = false, lottery = lot, campsite = site, entry = mine,
            taken = BookingPolicy.takenNights(others), takenRanges = others.map { it.checkInDay!! to it.checkOutDay!! }.sortedBy { it.first },
        )
    }.stateIn(viewModelScope, keepAlive, BookDatesState())

    /** Chooses the check-in and check-out days. */
    fun pick(checkIn: Long, checkOut: Long) { form.value = form.value.copy(checkIn = checkIn, checkOut = checkOut, message = null) }

    /**
     * A tap on the calendar (Figma E-WON-SITE7-CHECKIN): the first tap is check-in, the next
     * later day is check-out; tapping again starts over.
     */
    fun tapDay(day: Long) {
        val f = form.value
        form.value = when {
            f.checkIn == null || f.checkOut != null || day <= f.checkIn -> f.copy(checkIn = day, checkOut = null, message = null)
            else -> f.copy(checkOut = day, message = null)
        }.let { it.copy(message = if (it.checkOut != null) problem(state.value.copy(checkIn = it.checkIn, checkOut = it.checkOut, guests = it.guests)) else null) }
    }

    /** Sets how many guests are coming. */
    fun onGuests(n: Int) { form.value = form.value.copy(guests = n, message = null) }

    /** Ticks "I've read the campground rules & policies". */
    fun onAgreed(v: Boolean) { form.value = form.value.copy(agreed = v, message = null) }

    /** Checks the picked nights against the lottery and campsite rules; null when they're fine. */
    fun problem(s: BookDatesState = state.value): String? {
        val inDay = s.checkIn ?: return "Choose check-in and check-out."
        val outDay = s.checkOut ?: return "Choose check-in and check-out."
        val lot = s.lottery ?: return null
        val site = s.campsite
        val nights = outDay - inDay
        BookingPolicy.check(lot, s.entry, inDay, outDay, emptyList())?.let { return it.message }
        if (s.takenRanges.any { (a, b) -> BookingPolicy.overlaps(inDay, outDay, a, b) }) return BookingError.TAKEN.message
        if (site != null && nights < site.minNights) return "This campsite needs at least ${site.minNights} nights."
        if (site != null && nights > site.maxNights) return "This campsite allows at most ${site.maxNights} nights."
        if (site != null && s.guests > site.maxOccupancy) return "At most ${site.maxOccupancy} guests."
        return null
    }

    /** Shows why the picked nights can't be booked, if they can't. */
    fun review() { form.value = form.value.copy(message = problem()) }

    /** Confirm booking: saves the reservation, or shows why it was refused. */
    fun confirm() {
        val s = state.value
        problem(s)?.let { form.value = form.value.copy(message = it); return }
        if (!s.agreed) { form.value = form.value.copy(message = "Accept the campsite rules to book."); return }
        form.value = form.value.copy(saving = true, message = null)
        viewModelScope.launch {
            runCatching { container.lotteries.bookDates(lotteryId, container.userId, s.checkIn!!, s.checkOut!!, container.clock.now(), s.guests, true) }
                .onSuccess { form.value = form.value.copy(saving = false) }
                .onFailure { e -> form.value = form.value.copy(saving = false, message = e.userMessage()) }
        }
    }

    /** Turns the spot down (Decline); it goes to someone else who entered. */
    fun decline(onDone: () -> Unit) {
        form.value = form.value.copy(saving = true, message = null)
        viewModelScope.launch {
            runCatching { container.lotteries.giveUpSpot(lotteryId, container.userId, container.clock.now()) }
                .onSuccess { form.value = form.value.copy(saving = false); onDone() }
                .onFailure { e -> form.value = form.value.copy(saving = false, message = e.userMessage()) }
        }
    }
}

/**
 * You won: pick your nights and confirm (Figma E-WON → E-RULES → E-CONFIRMED).
 *
 * @param lotteryId the lottery the entrant won.
 * @param onBooked leaves after booking ("View my bookings").
 * @param onReservation opens the confirmed reservation.
 */
@Composable
fun BookDatesScreen(
    container: AppContainer,
    profile: UserProfile,
    lotteryId: String,
    onBack: () -> Unit,
    onBooked: () -> Unit,
    onReservation: (String) -> Unit = { onBooked() },
) {
    val vm = containerViewModel(container, key = "book-$lotteryId") { BookDatesViewModel(it, lotteryId) }
    val s by vm.state.collectAsStateWithLifecycle()
    var confirmDecline by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    val lot = s.lottery
    val site = s.campsite
    if (s.loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = EventsColors.Coral) }; return }
    val entry = s.entry
    if (lot == null || entry == null || entry.status != EntryStatus.WON) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            BackTopBar("You won", onBack)
            DuskCard(Modifier.padding(horizontal = PageMargin)) {
                EmptyState(R.drawable.ic_info, "Nothing to book", if (entry?.status?.gaveUpSpot == true) "You gave this spot back." else "Only lottery winners can book dates for this campsite.")
            }
        }
        return
    }
    if (entry.isBooked) {
        ConfirmedContent(lot, site, entry, profile, onBooked = onBooked, onReservation = { onReservation(lot.id) })
        return
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(170.dp)) { CampsiteImage(lot.campsiteId, site?.imageBase64) }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = PageMargin).padding(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.offset(y = (-30).dp).size(60.dp).clip(CircleShape).background(EventsColors.Surface).padding(4.dp)
                        .clip(CircleShape).background(EventsColors.PrimaryContainer),
                    contentAlignment = Alignment.Center,
                ) { EventsIcon(R.drawable.ic_trophy, tint = EventsColors.Amber, size = 28.dp) }
                Column(Modifier.offset(y = (-26).dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusTag("Lottery won", TagTone.GREEN)
                    Text("You Won ${lot.campsiteName}!", style = EventsType.Headline, color = EventsColors.Text, modifier = Modifier.testTag("book_title"))
                    Text(
                        "Choose your dates${lot.bookingEndDay?.let { " between ${formatStay(lot.bookingStartDay!!, it).substringBefore(" ·")}" } ?: ""}. If none work, decline and your spot goes to the next person on the waitlist.",
                        style = EventsType.BodySmall, color = EventsColors.TextSecondary,
                    )
                    if (!lot.hasBookingPeriod) {
                        DuskCard { EmptyState(R.drawable.ic_clock, "Dates not open yet", "The owner hasn’t set a booking period for this lottery. Check back soon.") }
                    } else {
                        StayCalendar(lot.bookingStartDay!!, lot.bookingEndDay!!, s.checkIn, s.checkOut, s.taken, vm::tapDay)
                        DuskCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                            Stepper(s.guests, vm::onGuests, 1, site?.maxOccupancy ?: 1, "Guests", supporting = "Up to ${site?.maxOccupancy ?: 1} people", modifier = Modifier.testTag("book_guests"))
                        }
                        val inDay = s.checkIn
                        val outDay = s.checkOut
                        if (inDay != null && outDay != null && site != null) {
                            val price = StayPrice(outDay - inDay, site.pricePerNight)
                            MetaItem(R.drawable.ic_money, "$${price.perNight} × ${price.nights} night${if (price.nights == 1L) "" else "s"} + GST = $${price.total} · pay at check-in", EventsColors.TextSecondary)
                        }
                        Row(Modifier.fillMaxWidth().clickable { vm.onAgreed(!s.agreed) }.testTag("book_agree"), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(s.agreed, vm::onAgreed, colors = CheckboxDefaults.colors(checkedColor = EventsColors.Primary, uncheckedColor = EventsColors.TextSecondary, checkmarkColor = EventsColors.OnPrimary))
                            Text("I've read the campground ", style = EventsType.BodySmall, color = EventsColors.Text)
                            Text("rules & policies", style = EventsType.BodySmall, color = EventsColors.Coral, modifier = Modifier.clickable { showRules = true }.testTag("book_rules"))
                        }
                    }
                    s.message?.let { Text(it, style = EventsType.BodySmall, color = EventsColors.Danger, modifier = Modifier.testTag("book_error")) }
                }
            }
        }
        Row(Modifier.statusBarsPadding().padding(horizontal = PageMargin, vertical = 8.dp)) { GlassIconButton(R.drawable.ic_arrow_back, "Back", onBack) }
        if (lot.hasBookingPeriod) ActionBar(Modifier.align(Alignment.BottomCenter)) {
            SecondaryButton("Decline", { confirmDecline = true }, Modifier.weight(0.8f).testTag("book_decline"))
            PrimaryButton(
                "Confirm booking", vm::confirm, Modifier.weight(1.2f).testTag("book_confirm"), loading = s.saving,
                enabled = s.checkIn != null && s.checkOut != null && s.agreed,
            )
        }
    }
    if (showRules) RulesSheet(lot.campsiteName, site, s.agreed, onAgreed = vm::onAgreed) { showRules = false }
    if (confirmDecline) ConfirmDialog(
        R.drawable.ic_warning, "Decline Reservation?",
        "Your spot at ${lot.campsiteName} will go to the next person on the waitlist. You can't undo this.",
        "Decline", onConfirm = { confirmDecline = false; vm.decline(onBack) }, onDismiss = { confirmDecline = false }, dismissText = "Keep my spot",
    )
}

/**
 * The inline month calendar from Figma E-WON: nights in the booking period can be picked,
 * nights other winners booked are struck out, and the chosen stay is highlighted.
 */
@Composable
fun StayCalendar(start: Long, end: Long, checkIn: Long?, checkOut: Long?, taken: Set<Long>, onTap: (Long) -> Unit) {
    val first = java.time.LocalDate.ofEpochDay(start).withDayOfMonth(1)
    val last = java.time.LocalDate.ofEpochDay(end).withDayOfMonth(1)
    var month by remember(start) { mutableStateOf(java.time.LocalDate.ofEpochDay(checkIn ?: start).withDayOfMonth(1)) }
    val title = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.CANADA)
    DuskCard(Modifier.fillMaxWidth().testTag("stay_calendar"), padding = PaddingValues(12.dp), spacing = 6.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(month.format(title), style = EventsType.BodyStrong, color = EventsColors.Text, modifier = Modifier.weight(1f))
            val canBack = month > first
            val canNext = month < last
            Box(Modifier.size(32.dp).clip(CircleShape).clickable(enabled = canBack) { month = month.minusMonths(1) }, contentAlignment = Alignment.Center) {
                EventsIcon(R.drawable.ic_arrow_back, size = 16.dp, tint = if (canBack) EventsColors.Text else EventsColors.TextTertiary)
            }
            Box(Modifier.size(32.dp).clip(CircleShape).clickable(enabled = canNext) { month = month.plusMonths(1) }.testTag("calendar_next"), contentAlignment = Alignment.Center) {
                EventsIcon(R.drawable.ic_chevron_right, size = 18.dp, tint = if (canNext) EventsColors.Text else EventsColors.TextTertiary)
            }
        }
        Row {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                Text(d, style = EventsType.Caption, color = EventsColors.TextTertiary, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        val lead = month.dayOfWeek.value % 7 // Sunday first
        val days = month.lengthOfMonth()
        val cells = ((lead + days + 6) / 7) * 7
        (0 until cells).chunked(7).forEach { week ->
            Row {
                week.forEach { cell ->
                    val dom = cell - lead + 1
                    Box(Modifier.weight(1f).height(36.dp), contentAlignment = Alignment.Center) {
                        if (dom in 1..days) {
                            val day = month.withDayOfMonth(dom).toEpochDay()
                            val inPeriod = day in start..end
                            val booked = day in taken
                            val isEnd = day == checkIn || day == checkOut
                            val between = checkIn != null && checkOut != null && day > checkIn && day < checkOut
                            val tappable = inPeriod && !(booked && (checkIn == null || checkOut != null))
                            Box(
                                Modifier.size(34.dp).clip(CircleShape)
                                    .background(
                                        when {
                                            isEnd -> EventsColors.Coral
                                            between -> EventsColors.Coral.copy(alpha = 0.28f)
                                            else -> androidx.compose.ui.graphics.Color.Transparent
                                        },
                                    )
                                    .clickable(enabled = tappable) { onTap(day) }.testTag("day_$day"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$dom",
                                    style = EventsType.BodySmall.copy(textDecoration = if (booked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null),
                                    color = when {
                                        isEnd -> EventsColors.OnPrimary
                                        !inPeriod || booked -> EventsColors.TextTertiary.copy(alpha = 0.6f)
                                        else -> EventsColors.Text
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        CardDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            MetaItem(
                R.drawable.ic_calendar,
                when {
                    checkIn != null && checkOut != null -> "${formatDayNum(checkIn).substringAfter(", ")} – ${formatDayNum(checkOut).substringAfter(", ")} · ${checkOut - checkIn} night${if (checkOut - checkIn == 1L) "" else "s"}"
                    checkIn != null -> "Now pick check-out"
                    else -> "Select your dates"
                },
                modifier = Modifier.weight(1f),
            )
            Text("Struck-out = booked", style = EventsType.Caption, color = EventsColors.TextTertiary)
        }
    }
}

/** Figma E-RULES: the campsite's rules and the cancellation policy, with a tick box and Got it. */
@Composable
private fun RulesSheet(campsiteName: String, site: Campsite?, agreed: Boolean, onAgreed: (Boolean) -> Unit, onDismiss: () -> Unit) {
    var ticked by remember { mutableStateOf(agreed) }
    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(EventsColors.SkyTop.copy(alpha = 0.55f)).clickable(onClick = onDismiss), contentAlignment = Alignment.BottomCenter) {
            RulesCard(campsiteName, site, ticked, { ticked = it }) { onAgreed(ticked); onDismiss() }
        }
    }
}

/** The card inside [RulesSheet] (separate so it can be screenshotted). */
@Composable
fun RulesCard(campsiteName: String, site: Campsite?, ticked: Boolean, onTick: (Boolean) -> Unit, onGotIt: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(EventsColors.Surface).clickable(enabled = false) {}.navigationBarsPadding().padding(PageMargin).testTag("rules_sheet"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(campsiteName, style = EventsType.Title, color = EventsColors.Text)
        Text("Campsite rules & cancellation policy", style = EventsType.BodySmall, color = EventsColors.TextSecondary)
        DuskCard(Modifier.fillMaxWidth(), color = EventsColors.SurfaceSoft, spacing = 8.dp) {
            Text("Rules", style = EventsType.BodyStrong, color = EventsColors.Text)
            rulesList(site?.rules?.ifBlank { null } ?: DEFAULT_RULES, site).forEach { rule ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(18.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(5.dp)).background(EventsColors.GreenContainer), contentAlignment = Alignment.Center) {
                        EventsIcon(R.drawable.ic_check, size = 12.dp, tint = EventsColors.Green)
                    }
                    Text(rule, style = EventsType.BodySmall, color = EventsColors.Text)
                }
            }
        }
        DuskCard(Modifier.fillMaxWidth(), color = EventsColors.SurfaceSoft, spacing = 6.dp) {
            Text("Cancellation", style = EventsType.BodyStrong, color = EventsColors.Text)
            Text("${(site?.cancellation ?: CancellationPolicy.FLEXIBLE).summary} $CANCELLATION_POLICY", style = EventsType.BodySmall, color = EventsColors.TextSecondary)
        }
        Row(Modifier.fillMaxWidth().clickable { onTick(!ticked) }.testTag("rules_tick"), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(ticked, onTick, colors = CheckboxDefaults.colors(checkedColor = EventsColors.Primary, uncheckedColor = EventsColors.TextSecondary, checkmarkColor = EventsColors.OnPrimary))
            Text("I've read the rules and cancellation policy", style = EventsType.BodySmall, color = EventsColors.Text)
        }
        PrimaryButton("Got it", onGotIt, Modifier.fillMaxWidth().testTag("rules_got_it"))
    }
}

/** The owner's rules as a list (one per sentence), plus the guest limit and check-in times. */
fun rulesList(rules: String, site: Campsite?): List<String> = buildList {
    addAll(rules.split(Regex("(?<=[.!])\\s+|\\n+")).map { it.trim().trimEnd('.') }.filter { it.isNotEmpty() })
    site?.let {
        add("Max ${it.maxOccupancy} guests per site")
        add("Check-in ${it.checkInTime}, check-out ${it.checkOutTime}")
    }
}

/** Cancellation terms shown before booking (Figma E-RULES "Cancellation"). */
const val CANCELLATION_POLICY = "You pay the owner at check-in. If the owner rejects your booking (e.g. a fire warning), you're told why and pay nothing."

/** Rules shown when an owner hasn't written their own. */
const val DEFAULT_RULES = "Quiet hours 10 PM – 7 AM. Fires only in the fire pit. Pack out what you pack in. Pets on a leash. No more guests than booked."

/** Figma E-CONFIRMED: "You're Booked!" with the stay, guests, amount due and free cancellation. */
@Composable
private fun ConfirmedContent(lot: Lottery, site: Campsite?, entry: WaitlistEntry, profile: UserProfile, onBooked: () -> Unit, onReservation: () -> Unit) {
    val code = reservationCode(lot.id, profile.id)
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = PageMargin).padding(top = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(EventsColors.GreenContainer), contentAlignment = Alignment.Center) {
                EventsIcon(R.drawable.ic_check, tint = EventsColors.Green, size = 32.dp)
            }
            Text("You're Booked!", style = EventsType.Headline, color = EventsColors.Text, modifier = Modifier.testTag("book_done"))
            Text("Pay at check-in · your confirmation is in Bookings.", style = EventsType.BodySmall, color = EventsColors.TextSecondary)
            DuskCard(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                Box(Modifier.fillMaxWidth().height(110.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))) {
                    CampsiteImage(lot.campsiteId, site?.imageBase64)
                }
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(lot.campsiteName, style = EventsType.BodyStrong, color = EventsColors.Text)
                    SummaryRow("Dates", formatStay(entry.checkInDay!!, entry.checkOutDay!!).substringBefore(" ·"))
                    SummaryRow("Guests", "${entry.guests ?: 1}")
                    if (site != null) {
                        val price = StayPrice(entry.checkOutDay - entry.checkInDay, site.pricePerNight)
                        SummaryRow("Due at check-in", "$${price.perNight} × ${price.nights} nights + GST = $${price.total}")
                    }
                    SummaryRow("Free cancellation", "Until ${formatDayNum((site?.cancellation ?: CancellationPolicy.FLEXIBLE).freeUntil(entry.checkInDay))}", valueColor = EventsColors.Green)
                    SummaryRow("Code", code, valueColor = EventsColors.Coral)
                }
            }
            entry.toReservation()?.let { r ->
                CsvDownloadButton("Download confirmation (CSV)", ReceiptCsv.fileName(r, code), { ReceiptCsv.build(r, code, site) }, Modifier.fillMaxWidth())
            }
            TextLinkButton("View reservation", onReservation, Modifier.testTag("view_reservation"))
        }
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = PageMargin).padding(bottom = 16.dp)) {
            PrimaryButton("View my bookings", onBooked, Modifier.fillMaxWidth().testTag("view_bookings"))
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = EventsColors.Text) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = EventsType.BodySmall, color = EventsColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, style = EventsType.BodyStrong, color = valueColor)
    }
}

/** Nights × price, GST and total. */
@Composable
fun PriceCard(price: StayPrice) {
    DuskCard(Modifier.fillMaxWidth().testTag("price_card"), spacing = 8.dp) {
        DetailRow(R.drawable.ic_money, "$${price.perNight} × ${price.nights} night${if (price.nights == 1L) "" else "s"}", "$${price.subtotal}")
        DetailRow(R.drawable.ic_info, "GST (5%)", "$${price.tax}")
        CardDivider()
        Row(Modifier.fillMaxWidth()) {
            Text("Total at check-in", style = EventsType.BodyStrong, color = EventsColors.Text, modifier = Modifier.weight(1f))
            Text("$${price.total}", style = EventsType.BodyStrong, color = EventsColors.Text)
        }
    }
}

// ---- Downloadable receipts (US 01.05.02, 01.07.02) ----

/** Builds the CSV receipts guests can download for a booking or a cancellation. */
object ReceiptCsv {
    /** What a receipt confirms. */
    enum class Kind(val title: String) {
        RESERVATION("Reservation confirmation"),
        CANCELLATION("Cancellation confirmation"),
        REJECTION("Reservation rejected by owner"),
    }

    /** The kind of receipt for [r]'s current status. */
    fun kindOf(r: Reservation): Kind = when (r.status) {
        EntryStatus.CANCELLED -> Kind.CANCELLATION
        EntryStatus.REJECTED -> Kind.REJECTION
        else -> Kind.RESERVATION
    }

    /**
     * A two-column "Field,Value" CSV describing [r]: the code, campsite, guest, dates, guests,
     * price with GST, and (when cancelled or rejected) when and why.
     */
    fun build(r: Reservation, code: String, site: Campsite?): String {
        val kind = kindOf(r)
        val rows = buildList {
            add("Receipt" to kind.title)
            add("Reservation code" to code)
            add("Campsite" to r.campsiteName)
            site?.let { add("Location" to it.location); add("Host" to it.ownerName) }
            add("Guest" to r.entrantName)
            add("Check-in" to (isoDay(r.checkInDay) + (site?.checkInTime?.let { " $it" } ?: "")))
            add("Check-out" to (isoDay(r.checkOutDay) + (site?.checkOutTime?.let { " $it" } ?: "")))
            add("Nights" to "${r.nights}")
            add("Guests" to "${r.guests}")
            if (site != null) {
                val price = StayPrice(r.nights, site.pricePerNight)
                add("Price per night (CAD)" to "${price.perNight}")
                add("Subtotal (CAD)" to "${price.subtotal}")
                add("GST 5% (CAD)" to "${price.tax}")
                add((if (kind == Kind.RESERVATION) "Total due at check-in (CAD)" else "Amount charged (CAD)") to if (kind == Kind.RESERVATION) "${price.total}" else "0")
            }
            add("Booked at" to isoTime(r.bookedAt))
            r.endedAt?.let { add((if (kind == Kind.CANCELLATION) "Cancelled at" else "Rejected at") to isoTime(it)) }
            if (kind == Kind.REJECTION) add("Reason" to r.note.ifBlank { "No reason given" })
        }
        return (listOf("Field" to "Value") + rows).joinToString("\n", postfix = "\n") { (k, v) -> "${cell(k)},${cell(v)}" }
    }

    /** A safe file name like "events-reservation-EV-TWO12-AB3C.csv". */
    fun fileName(r: Reservation, code: String): String =
        "events-${kindOf(r).name.lowercase()}-${code.replace(Regex("[^A-Za-z0-9-]"), "")}.csv"

    /** Quotes a CSV cell when it holds a comma, quote or line break. */
    fun cell(v: String): String = if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    private fun isoDay(day: Long) = java.time.LocalDate.ofEpochDay(day).toString()
    private fun isoTime(ms: Long) = java.time.Instant.ofEpochMilli(ms).toString()
}

/**
 * "Download CSV" button: asks Android where to save (no storage permission needed) and writes
 * [csv] there.
 */
@Composable
fun CsvDownloadButton(label: String, fileName: String, csv: () -> String, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var saved by remember { mutableStateOf<Boolean?>(null) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) saved = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(csv().toByteArray()) } != null
        }.getOrDefault(false)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SecondaryButton(label, { launcher.launch(fileName) }, Modifier.fillMaxWidth().testTag("download_csv"), icon = R.drawable.ic_download, size = ButtonSize.SMALL)
        saved?.let { ok ->
            Text(if (ok) "Saved $fileName" else "Couldn't save the file.", style = EventsType.Caption, color = if (ok) EventsColors.Green else EventsColors.Danger, modifier = Modifier.testTag("csv_result"))
        }
    }
}

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

    /** Cancels a booking (Figma E-CANCEL); the spot goes to someone else on the waitlist. */
    fun cancel(lotteryId: String) {
        working.value = lotteryId
        viewModelScope.launch {
            runCatching { container.lotteries.giveUpSpot(lotteryId, container.userId, container.clock.now()) }
            working.value = null
        }
    }
}

/**
 * The entrant's Bookings tab (Figma E-BOOKINGS, E-PAST, E-CANCEL): Active stays as photo cards
 * with Cancel booking and a chat button; Past holds finished, cancelled and rejected stays.
 */
@Composable
fun EntrantBookingsScreen(container: AppContainer, onOpen: (String) -> Unit, onExplore: () -> Unit, onChat: (Chat) -> Unit = {}, profile: UserProfile? = null) {
    val vm = containerViewModel(container) { EntrantBookingsViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val cancelling by vm.cancelling.collectAsStateWithLifecycle()
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
            if (tab == 0) ActiveBookingCard(r, site, s.today, cancelling == r.lotteryId, { onOpen(r.lotteryId) }, { confirmCancel = r }, chat)
            else DuskCard(Modifier.fillMaxWidth().testTag("my_booking_${r.lotteryId}"), onClick = { onOpen(r.lotteryId) }, padding = PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(60.dp).clip(EventsShapes.Medium)) { CampsiteImage(r.campsiteId, site?.imageBase64) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(r.campsiteName, style = EventsType.BodyStrong, color = EventsColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        MetaItem(R.drawable.ic_calendar, "${formatStay(r.checkInDay, r.checkOutDay).substringBefore(" ·")} · ${r.guests} guest${if (r.guests == 1) "" else "s"}")
                        ReservationTag(r, s.today)
                    }
                    EventsIcon(R.drawable.ic_chevron_right, tint = EventsColors.TextSecondary, size = 20.dp)
                }
            }
        }
    }
    confirmCancel?.let { r ->
        ConfirmDialog(
            R.drawable.ic_delete, "Cancel This Booking?",
            "${r.campsiteName} · ${formatStay(r.checkInDay, r.checkOutDay).substringBefore(" ·")}. Your spot goes to the next person on the waitlist. You'll get a cancellation confirmation in Bookings › Past.",
            "Cancel booking", onConfirm = { confirmCancel = null; vm.cancel(r.lotteryId) }, onDismiss = { confirmCancel = null }, dismissText = "Keep it",
        )
    }
}

/** Figma E-BOOKINGS card: photo, name and status, dates · nights, guests, Cancel booking and chat. */
@Composable
private fun ActiveBookingCard(r: Reservation, site: Campsite?, today: Long, cancelling: Boolean, onOpen: () -> Unit, onCancel: () -> Unit, onChat: () -> Unit) {
    DuskCard(Modifier.fillMaxWidth().testTag("my_booking_${r.lotteryId}"), onClick = onOpen, padding = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxWidth().height(96.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))) {
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
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(EventsColors.Tint).clickable(onClick = onChat).testTag("chat_${r.lotteryId}"),
                    contentAlignment = Alignment.Center,
                ) { EventsIcon(R.drawable.ic_chat, size = 18.dp, tint = EventsColors.Text, contentDescription = "Message host") }
            }
        }
    }
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
    fun cancel() = act { container.lotteries.giveUpSpot(lotteryId, container.userId, container.clock.now()) }

    /** Rates the stay. */
    fun rate(name: String, stars: Int, comment: String) = act {
        container.social.rate(Rating(campsiteId, container.userId, name, stars, comment.trim().take(RatingPolicy.MAX_COMMENT), lotteryId, container.clock.now()))
    }

    private fun act(block: suspend () -> Unit) {
        ui.value = ui.value.copy(working = true, message = null)
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { ui.value = ui.value.copy(working = false) }
                .onFailure { e -> ui.value = ui.value.copy(working = false, message = e.userMessage()) }
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
    var confirmCancel by remember { mutableStateOf(false) }
    var rating by remember { mutableStateOf(false) }
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
                        StarRow(mine.stars, {}, size = 22)
                        if (mine.comment.isNotBlank()) Text("“${mine.comment}”", style = EventsType.BodySmall, color = EventsColors.TextSecondary)
                    }
                    SecondaryButton(if (mine == null) "Rate your stay" else "Edit rating", { rating = true }, Modifier.fillMaxWidth().testTag("rate_stay"), icon = R.drawable.ic_star, size = ButtonSize.SMALL)
                }
            }
            s.message?.let { Text(it, style = EventsType.BodySmall, color = EventsColors.Danger) }
        }
        if (site != null) ActionBar {
            // Only entrants selected in the lottery can message the owner.
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
        "Your nights will be released and the spot offered to someone else on the waiting list. Nothing is charged.",
        "Cancel reservation", onConfirm = { confirmCancel = false; vm.cancel() }, onDismiss = { confirmCancel = false },
    )
    if (rating) RateDialog(
        s.myRating, onDismiss = { rating = false }, campsiteName = s.lottery?.campsiteName.orEmpty(),
        stayed = s.entry?.let { e -> if (e.checkInDay != null && e.checkOutDay != null) formatStay(e.checkInDay, e.checkOutDay).substringBefore(" ·") else null },
    ) { stars, comment -> rating = false; vm.rate(profile.name, stars, comment) }
}

/** Five tappable stars. */
@Composable
fun StarRow(stars: Int, onChange: (Int) -> Unit, size: Int = 32, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 1..5) {
            Box(Modifier.size(size.dp).clickable { onChange(i) }.testTag("star_$i"), contentAlignment = Alignment.Center) {
                EventsIcon(R.drawable.ic_star, tint = if (i <= stars) EventsColors.Star else EventsColors.Outline, size = size.dp)
            }
        }
    }
}

/**
 * Rate a campsite (Figma E-RATE): a sheet with the stay dates, five stars, "n of 5 stars" and
 * Submit rating / Not now.
 *
 * @param stayed the stay being rated, e.g. "Sep 12 – 14, 2026", or null.
 */
@Composable
fun RateDialog(current: Rating?, onDismiss: () -> Unit, campsiteName: String = "", stayed: String? = null, onSubmit: (Int, String) -> Unit) {
    var stars by remember { mutableIntStateOf(current?.stars ?: 0) }
    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(EventsColors.SkyTop.copy(alpha = 0.55f)).clickable(onClick = onDismiss), contentAlignment = Alignment.BottomCenter) {
            RateCard(campsiteName, stayed, stars, { stars = it }, { onSubmit(stars, current?.comment.orEmpty()) }, onDismiss)
        }
    }
}

/** The card inside [RateDialog]. */
@Composable
fun RateCard(campsiteName: String, stayed: String?, stars: Int, onStars: (Int) -> Unit, onSubmit: () -> Unit, onNotNow: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(EventsColors.Surface).clickable(enabled = false) {}.navigationBarsPadding().padding(PageMargin).testTag("rate_dialog"),
        verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width = 40.dp, height = 4.dp).clip(EventsShapes.Pill).background(EventsColors.TextTertiary))
        Text(if (campsiteName.isBlank()) "Rate your stay" else "Rate $campsiteName", style = EventsType.Title, color = EventsColors.Text)
        stayed?.let { MetaItem(R.drawable.ic_calendar, "You stayed $it", EventsColors.TextSecondary) }
        StarRow(stars, onStars, size = 40)
        Text(if (stars == 0) "Tap a star to rate" else "$stars of 5 stars · tap a star to change", style = EventsType.BodySmall, color = EventsColors.TextSecondary)
        PrimaryButton("Submit rating", onSubmit, Modifier.fillMaxWidth().testTag("rate_submit"), enabled = stars > 0)
        TextLinkButton("Not now", onNotNow, color = EventsColors.TextSecondary)
    }
}

/** The signed-in person's own entry in [lotteryId], or null if they have none. */
private fun myEntry(container: AppContainer, lotteryId: String): Flow<WaitlistEntry?> =
    container.lotteries.observeEntriesForEntrant(container.userId).map { list -> list.firstOrNull { it.lotteryId == lotteryId } }
