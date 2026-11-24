/*
 * Booking.kt (Suvir)
 *
 * After winning: pick nights and guests, review the reservation (price, campsite rules, pay
 * at check-in) and confirm.
 *
 * Outstanding issues: Events takes no payments; guests pay the owner at check-in.
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
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
    /** The sites this person won in a lottery over several campsites and hasn't booked yet (US 01.04.02). */
    val wonSites: List<Lottery> = emptyList(),
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
    }.combine(wonSites()) { s, sites -> s.copy(wonSites = sites) }.stateIn(viewModelScope, keepAlive, BookDatesState())

    /** Every site this person won in the same lottery and still has to choose between. */
    private fun wonSites(): Flow<List<Lottery>> = container.lotteries.observeLottery(lotteryId).flatMapLatest { lot ->
        if (lot == null || lot.groupId.isBlank()) flowOf(emptyList())
        else combine(container.lotteries.observeLotteries(), container.lotteries.observeEntriesForEntrant(container.userId)) { all, mine ->
            LotteryGroups.openWins(lot, all, mine, container.userId)
        }
    }

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
        if (inDay < dayOf(container.clock.now())) return "Those nights have already passed. Pick from today on."
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
                .onSuccess {
                    // Won more than one site in this lottery: the ones not chosen go to the next person on the waitlist.
                    s.lottery?.let { booked -> runCatching { LotteryGroups.giveBackOthers(container.lotteries, booked, container.userId, container.clock.now()) } }
                    form.value = form.value.copy(saving = false)
                }
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
    // A winner of several sites in one lottery can switch between them here (Figma E-WON-PICK).
    var chosen by androidx.compose.runtime.saveable.rememberSaveable(lotteryId) { mutableStateOf(lotteryId) }
    androidx.compose.runtime.key(chosen) { BookDatesContent(container, profile, chosen, onBack, onBooked, onReservation) { chosen = it } }
}

/** The booking screen for one site; [onChooseSite] switches to another site won in the same lottery. */
@Composable
private fun BookDatesContent(
    container: AppContainer,
    profile: UserProfile,
    lotteryId: String,
    onBack: () -> Unit,
    onBooked: () -> Unit,
    onReservation: (String) -> Unit,
    onChooseSite: (String) -> Unit,
) {
    val vm = containerViewModel(container, key = "book-$lotteryId") { BookDatesViewModel(it, lotteryId) }
    val s by vm.state.collectAsStateWithLifecycle()
    var confirmDecline by rememberSaveable { mutableStateOf(false) }
    var showRules by rememberSaveable { mutableStateOf(false) }
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
                    val severalSites = s.wonSites.size > 1
                    Text(
                        if (severalSites) "You Won ${s.wonSites.size} Sites!" else "You Won ${lot.campsiteName}!",
                        style = EventsType.Headline, color = EventsColors.Text, modifier = Modifier.testTag("book_title"),
                    )
                    Text(
                        if (severalSites) "Choose a site, then your dates${lot.bookingEndDay?.let { " between ${formatStay(lot.bookingStartDay!!, it).substringBefore(" ·")}" } ?: ""}. The other site${if (s.wonSites.size > 2) "s go" else " goes"} to the next person on the waitlist."
                        else "Choose your dates${lot.bookingEndDay?.let { " between ${formatStay(lot.bookingStartDay!!, it).substringBefore(" ·")}" } ?: ""}. If none work, decline and your spot goes to the next person on the waitlist.",
                        style = EventsType.BodySmall, color = EventsColors.TextSecondary,
                    )
                    if (severalSites) {
                        // Figma E-WON-PICK "Choose your site".
                        Text("Choose your site", style = EventsType.LabelSmall, color = EventsColors.TextSoft)
                        Row(Modifier.horizontalScroll(rememberScrollState()).testTag("site_choices"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            s.wonSites.forEach { won ->
                                val on = won.id == lot.id
                                EventsChip(
                                    won.campsiteName, if (on) ChipStyle.CORAL else ChipStyle.GLASS, { if (!on) onChooseSite(won.id) },
                                    Modifier.testTag("site_choice_${won.campsiteId}"), if (on) R.drawable.ic_check else R.drawable.ic_tent,
                                )
                            }
                        }
                    }
                    if (!lot.hasBookingPeriod) {
                        DuskCard { EmptyState(R.drawable.ic_clock, "Dates not open yet", "The owner hasn’t set a booking period for this lottery. Check back soon.") }
                    } else {
                        // Nights that have already gone by can't be booked, even inside the booking period.
                        StayCalendar(maxOf(lot.bookingStartDay!!, dayOf(container.clock.now())), lot.bookingEndDay!!, s.checkIn, s.checkOut, s.taken, vm::tapDay)
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
internal fun SummaryRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = EventsColors.Text) {
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
