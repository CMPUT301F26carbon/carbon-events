/*
 * CampsiteDetails.kt (Ansh)
 *
 * Campsite details: join or leave a lottery waitlist, plus promo QR links
 * that open a campsite straight from the camera app.
 *
 * Outstanding issues: ratings are shown but entrants can't rate yet (US 01.13.01 is not
 * in this release).
 */
package com.carbon.events

import androidx.compose.foundation.clickable

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Campsite details for entrants (Figma E-DETAILS). */
@Composable
fun CampsiteDetailsScreen(
    container: AppContainer,
    profile: UserProfile,
    campsiteId: String,
    onBack: () -> Unit,
    onMyLotteries: () -> Unit,
    onBook: (String) -> Unit = {},
    onChat: (Chat) -> Unit = {},
    onReservation: (String) -> Unit = {},
    onMap: () -> Unit = {},
) {
    // Joining is when results matter, so that is when Android asks to allow notifications.
    val askNotifications = rememberNotificationPermission {}
    val vm = containerViewModel(container, key = "details-$campsiteId") { CampsiteDetailsViewModel(it, campsiteId) }
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }
    var rating by remember { mutableStateOf(false) }
    var rateNote by remember { mutableStateOf(false) }
    LaunchedEffect(rateNote) { if (rateNote) { delay(3_500); rateNote = false } }
    val here = LocalDeviceLocation.current
    // Figma E-DETAILS-JOINED: a snackbar confirms the join for a few seconds.
    var joinRequested by remember { mutableStateOf(false) }
    var showJoined by remember { mutableStateOf(false) }
    // Figma E-DETAILS-LEFT: and confirms leaving.
    var leaveRequested by remember { mutableStateOf(false) }
    var showLeft by remember { mutableStateOf(false) }
    LaunchedEffect(state.action) {
        if (leaveRequested && state.action == LotteryAction.CAN_JOIN) {
            leaveRequested = false
            showLeft = true
            delay(4_000)
            showLeft = false
        }
    }
    LaunchedEffect(state.action) {
        if (joinRequested && state.action == LotteryAction.JOINED) {
            joinRequested = false
            showJoined = true
            delay(4_000)
            showJoined = false
        }
    }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    val site = state.campsite
    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = EventsColors.Coral) }
        site == null -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
            BackTopBar(null, onBack)
            EmptyState(R.drawable.ic_info, "Campsite not found", "This campsite is no longer listed.")
        }
        else -> Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(270.dp)) { CampsiteImage(site.id, site.imageBase64) }
                // The sheet overlaps the hero by 34dp (hero 270, sheet starts at 236 in Figma).
                Column(
                    Modifier
                        .offset(y = (-34).dp)
                        .fillMaxWidth()
                        .heightIn(min = screenHeight - 236.dp)
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(EventsColors.Surface)
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    DetailsBody(site, state.lottery, state.action, state.now, here, onMap)
                    // Figma E-NO-LOTTERY-NOTIFY: with no lottery running, ask to be told when one opens.
                    if (state.action == LotteryAction.NONE || state.action == LotteryAction.ENDED) {
                        NotifyMeCard(state.following) { vm.toggleFollow(profile.name) }
                    }
                }
            }
            Row(Modifier.statusBarsPadding().padding(horizontal = PageMargin, vertical = 8.dp)) { GlassIconButton(R.drawable.ic_arrow_back, "Back", onBack) }
            Column(Modifier.align(Alignment.BottomCenter)) {
                AnimatedVisibility(showJoined, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
                    state.lottery?.let { JoinedSnackbar("Joined! We’ll notify you after the ${formatDay(it.registrationEnd)} draw.") }
                }
                AnimatedVisibility(showLeft, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
                    state.lottery?.let { JoinedSnackbar("You left the waitlist. Rejoin any time before ${formatDay(it.registrationEnd)}.") }
                }
                AnimatedVisibility(rateNote, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
                    JoinedSnackbar("You can rate ${site.name} after you've stayed there.")
                }
                DetailsActionBar(
                    state, Modifier, onJoin = { showLeft = false; joinRequested = true; askNotifications(); vm.join(profile.name) }, onLeave = { showJoined = false; confirmLeave = true },
                    onMyLotteries = onMyLotteries, onBook = onBook, onReservation = onReservation,
                    onRate = { if (state.myEntry?.let(RatingPolicy::canRate) == true) rating = true else rateNote = true },
                )
            }
        }
    }
    if (rating) RateDialog(
        state.myRating, onDismiss = { rating = false }, campsiteName = site?.name.orEmpty(),
        stayed = state.myEntry?.let { e -> if (e.checkInDay != null && e.checkOutDay != null) formatStay(e.checkInDay, e.checkOutDay).substringBefore(" ·") else null },
    ) { stars, comment -> rating = false; vm.rate(profile.name, stars, comment) }
    if (confirmLeave) {
        ConfirmDialog(
            R.drawable.ic_logout, "Leave the Waitlist?",
            "You’ll lose your spot for ${site?.name ?: "this campsite"}. You can rejoin any time before registration closes on ${state.lottery?.let { formatDay(it.registrationEnd) } ?: "the closing date"}.",
            "Leave", onConfirm = { confirmLeave = false; leaveRequested = true; vm.leave() }, onDismiss = { confirmLeave = false }, dismissText = "Stay",
        )
    }
}

/** Figma E-DETAILS body: name, place and rating, description, three tiles, lottery card and map. */
@Composable
private fun DetailsBody(site: Campsite, lottery: Lottery?, action: LotteryAction, now: Long, here: GeoPoint?, onMap: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(site.name, style = EventsType.Headline, color = EventsColors.Text, modifier = Modifier.testTag("details_name"))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetaItem(R.drawable.ic_location, site.location, modifier = Modifier.weight(1f, fill = false))
            if (site.ratingCount > 0) RatingLine(site.rating, site.ratingCount, "· ${site.ratingCount} rating${if (site.ratingCount == 1) "" else "s"}")
            else Text("No ratings yet", style = EventsType.Caption, color = EventsColors.TextTertiary)
        }
        distanceKm(here, site)?.let { MetaItem(R.drawable.ic_my_location, "${formatKm(it)} from you", EventsColors.TextSecondary) }
    }
    val about = site.description.ifBlank { site.amenities }
    if (about.isNotBlank()) Text(about, style = EventsType.BodySmall, color = EventsColors.TextSecondary)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // Figma E-DETAILS tiles: guests, price, and the nights winners can book.
        InfoTile(R.drawable.ic_group, "${site.maxOccupancy} guests", "Max occupancy", Modifier.weight(1.1f))
        InfoTile(R.drawable.ic_money, "$${site.pricePerNight}", "per night", Modifier.weight(0.8f))
        InfoTile(
            R.drawable.ic_calendar,
            lottery?.takeIf { it.hasBookingPeriod }?.let { shortRange(it.bookingStartDay!!, it.bookingEndDay!!) } ?: "—",
            "Stays from", Modifier.weight(1.1f),
        )
    }
    LotteryCard(lottery, action, now)
    if (site.hasMapLocation) MapPreview(site.latitude!!, site.longitude!!, site.location, Modifier.testTag("details_map"), height = 96.dp, onClick = onMap)
}

/** "Nov 1 – 15", or "Oct 28 – Nov 3" across months (Figma tile). */
private fun shortRange(from: Long, until: Long): String {
    val a = java.time.LocalDate.ofEpochDay(from); val b = java.time.LocalDate.ofEpochDay(until)
    val m = java.time.format.DateTimeFormatter.ofPattern("MMM", java.util.Locale.CANADA)
    // Across months only the first night fits the tile ("Oct 21").
    return if (a.month == b.month) "${a.format(m)} ${a.dayOfMonth} – ${b.dayOfMonth}" else "${a.format(m)} ${a.dayOfMonth}"
}

/** Figma E-NO-LOTTERY-NOTIFY: "Notify me" when this campsite opens a lottery. */
@Composable
private fun NotifyMeCard(on: Boolean, onToggle: () -> Unit) {
    DuskCard(Modifier.fillMaxWidth().testTag("details_follow"), padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), color = EventsColors.PrimaryContainer, shape = RoundedCornerShape(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(R.drawable.ic_bell)
            Column(Modifier.weight(1f)) {
                Text("Notify me", style = EventsType.BodyStrong, color = EventsColors.Text)
                Text(if (on) "We'll tell you when a lottery opens here." else "Get a notification when a lottery opens.", style = EventsType.BodySmall, color = EventsColors.OnPrimaryContainer)
            }
            androidx.compose.material3.Switch(
                on, { onToggle() }, Modifier.testTag("details_follow_switch"),
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = EventsColors.OnPrimary, checkedTrackColor = EventsColors.Primary),
            )
        }
    }
}

/** Lottery card (Figma): radius 16, #3E3A8A, or green once the entrant has joined. */
@Composable
private fun LotteryCard(lottery: Lottery?, action: LotteryAction, now: Long) {
    val joined = action == LotteryAction.JOINED
    DuskCard(
        Modifier.fillMaxWidth().testTag("lottery_card"),
        padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        color = if (joined) EventsColors.GreenContainer else EventsColors.PrimaryContainer,
        shape = RoundedCornerShape(16.dp),
        spacing = 8.dp,
    ) {
        Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    joined -> "You’re in · ${spots(lottery?.winnerCount ?: 0)}"
                    lottery == null -> "Lottery"
                    else -> "Lottery · ${spots(lottery.winnerCount)}"
                },
                style = EventsType.BodyStrong, color = EventsColors.Text, modifier = Modifier.weight(1f),
            )
            if (joined) StatusTag("Joined", TagTone.GREEN) else LotteryStatusTag(lottery, now)
        }
        if (lottery == null) {
            Text("The owner hasn’t opened a lottery for this campsite yet.", style = EventsType.BodySmall, color = EventsColors.OnPrimaryContainer)
        } else {
            DetailRow(R.drawable.ic_calendar, "Registration", formatRange(lottery.registrationStart, lottery.registrationEnd))
            DetailRow(R.drawable.ic_trophy, "Draw · winners", "${formatDay(lottery.registrationEnd)} · ${spots(lottery.winnerCount)}")
            DetailRow(R.drawable.ic_group, "On waitlist", lottery.waitlistCap?.let { "${lottery.entrantCount} / $it" } ?: "${lottery.entrantCount}")
        }
    }
}

@Composable
private fun DetailsActionBar(
    state: DetailsState, modifier: Modifier, onJoin: () -> Unit, onLeave: () -> Unit, onMyLotteries: () -> Unit, onBook: (String) -> Unit,
    onRate: () -> Unit, onReservation: (String) -> Unit,
) {
    ActionBar(modifier, header = {
        if (state.message != null) Text(state.message, style = EventsType.BodySmall, color = EventsColors.Danger, modifier = Modifier.padding(bottom = 10.dp).testTag("details_message"))
    }) {
        val full = Modifier.weight(1.4f).testTag("lottery_action")
        // Figma E-DETAILS: "Rate" sits beside the main button.
        val rate = @Composable { SecondaryButton(if (state.myRating == null) "Rate" else "Edit rating", onRate, Modifier.weight(1f).testTag("details_rate"), icon = R.drawable.ic_star) }
        when (state.action) {
            LotteryAction.CAN_JOIN -> { rate(); PrimaryButton("Join lottery", onJoin, full, icon = R.drawable.ic_ticket, loading = state.working) }
            LotteryAction.JOINED -> {
                SecondaryButton("Leave waitlist", onLeave, Modifier.weight(1f).testTag("lottery_action"))
                SecondaryButton("My lotteries", onMyLotteries, Modifier.weight(1f))
            }
            LotteryAction.WON -> {
                val entry = state.myEntry
                if (entry != null && entry.isBooked) {
                    rate()
                    PrimaryButton("Your reservation", { onReservation(entry.lotteryId) }, full, icon = R.drawable.ic_event_check)
                } else PrimaryButton("You won! Book your dates", { onBook(state.lottery!!.id) }, Modifier.weight(1f).testTag("lottery_action"), icon = R.drawable.ic_calendar)
            }
            LotteryAction.LOST -> ResultBanner("Not selected this time. Good luck next draw.", TagTone.CORAL)
            LotteryAction.UPCOMING -> { rate(); PrimaryButton("Opens ${formatDay(state.lottery!!.registrationStart)}", {}, full, enabled = false) }
            LotteryAction.FULL -> { rate(); PrimaryButton("Waitlist full", {}, full, enabled = false) }
            LotteryAction.CLOSED -> { rate(); PrimaryButton("Waiting for the draw", {}, full, enabled = false) }
            LotteryAction.GAVE_UP -> ResultBanner(
                when (state.myEntry?.status) {
                    EntryStatus.REJECTED -> "The owner rejected your reservation."
                    EntryStatus.CANCELLED -> "You cancelled this reservation."
                    else -> "You gave your spot back."
                }, TagTone.AMBER,
            )
            LotteryAction.ENDED -> { rate(); PrimaryButton("Winners drawn", {}, full, enabled = false) }
            LotteryAction.NONE -> { rate(); PrimaryButton("No active lottery", {}, full, enabled = false) }
        }
    }
}

/** Snackbar from Figma: 48dp, radius 16, deep night fill, green check. */
@Composable
private fun JoinedSnackbar(text: String) {
    Row(
        Modifier.padding(horizontal = PageMargin, vertical = 10.dp).fillMaxWidth().heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp)).background(EventsColors.SkyTop.copy(alpha = 0.94f))
            .padding(horizontal = 16.dp, vertical = 12.dp).testTag("joined_snackbar"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EventsIcon(R.drawable.ic_check, tint = EventsColors.Green, size = 18.dp)
        Text(text, style = EventsType.BodySmall, color = EventsColors.TextSoft)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ResultBanner(text: String, tone: TagTone) {
    Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(26.dp)).background(tone.bg).padding(horizontal = 18.dp).testTag("lottery_result"), contentAlignment = Alignment.CenterStart) {
        Text(text, style = EventsType.Label, color = tone.fg)
    }
}

/** What the details screen's action bar offers, based on the lottery and the entrant's entry. */
enum class LotteryAction {
    /** No lottery has been listed for this campsite. */
    NONE,
    /** Registration opens later. */
    UPCOMING,
    /** Registration is open and the entrant may join. */
    CAN_JOIN,
    /** The entrant is on the waiting list and may leave. */
    JOINED,
    /** The waiting list is full. */
    FULL,
    /** Registration closed; the draw is running. */
    CLOSED,
    /** Drawn: the entrant won. */
    WON,
    /** Drawn: the entrant was not selected. */
    LOST,
    /** Drawn, and the entrant did not enter. */
    ENDED,
    /** The entrant won, then declined, cancelled or was rejected. */
    GAVE_UP,
}

/** State of the campsite details screen. */
data class DetailsState(
    val loading: Boolean = true,
    val campsite: Campsite? = null,
    val lottery: Lottery? = null,
    val myEntry: WaitlistEntry? = null,
    val action: LotteryAction = LotteryAction.NONE,
    val now: Long = 0L,
    val working: Boolean = false,
    val message: String? = null,
    val ratings: List<Rating> = emptyList(),
    val myRating: Rating? = null,
    val following: Boolean = false,
    val note: String? = null,
) {
    /** True once loading finished and the campsite no longer exists (e.g. removed by an admin). */
    val notFound: Boolean get() = !loading && campsite == null
}

/** Decides [LotteryAction] from the lottery, the entrant's entry and the time. */
fun lotteryAction(lottery: Lottery?, entry: WaitlistEntry?, now: Long): LotteryAction = when {
    lottery == null -> LotteryAction.NONE
    lottery.status == LotteryStatus.DRAWN -> when (entry?.status) {
        EntryStatus.WON -> LotteryAction.WON
        EntryStatus.LOST -> LotteryAction.LOST
        EntryStatus.DECLINED, EntryStatus.CANCELLED, EntryStatus.REJECTED -> LotteryAction.GAVE_UP
        else -> LotteryAction.ENDED
    }
    entry != null && lottery.isRegistrationOpen(now) -> LotteryAction.JOINED
    lottery.isUpcoming(now) -> LotteryAction.UPCOMING
    !lottery.isRegistrationOpen(now) -> LotteryAction.CLOSED
    lottery.waitlistCap != null && lottery.entrantCount >= lottery.waitlistCap -> LotteryAction.FULL
    else -> LotteryAction.CAN_JOIN
}

/**
 * Campsite details with join / leave waiting list.
 *
 * @param campsiteId the campsite shown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CampsiteDetailsViewModel(private val container: AppContainer, private val campsiteId: String) : ViewModel() {
    private val ui = MutableStateFlow(Pair(false, null as String?))
    private val note = MutableStateFlow<String?>(null)
    private val social = combine(
        container.social.observeRatings(campsiteId), container.social.observeFollowing(campsiteId, container.userId), note,
    ) { r, f, n -> Triple(r, f, n) }

    private val lotteryFlow = container.lotteries.observeLotteriesForCampsite(campsiteId).map { currentLottery(it) }
    private val entryFlow = lotteryFlow.flatMapLatest { lot ->
        if (lot == null) flowOf(null)
        else container.lotteries.observeEntriesForEntrant(container.userId).map { list -> list.firstOrNull { it.lotteryId == lot.id } }
    }

    /** Details screen state. */
    val state: StateFlow<DetailsState> = combine(
        container.campsites.observeCampsite(campsiteId), lotteryFlow, entryFlow, combine(nowTicker(container.clock, 15_000), ui) { n, u -> n to u }, social,
    ) { site, lottery, entry, (now, u), (ratings, following, n) ->
        DetailsState(
            false, site, lottery, entry, lotteryAction(lottery, entry, now), now, u.first, u.second,
            ratings, ratings.firstOrNull { it.userId == container.userId }, following, n,
        )
    }.stateIn(viewModelScope, sharing, DetailsState())

    /** Follows or unfollows the campsite ("notify me when a lottery opens"). */
    fun toggleFollow(name: String) {
        val follow = !state.value.following
        viewModelScope.launch {
            runCatching { container.social.setFollowing(campsiteId, container.userId, name, follow, container.clock.now()) }
                .onSuccess { note.value = if (follow) "We'll tell you when ${state.value.campsite?.name ?: "it"} opens a lottery." else null }
                .onFailure { ui.value = false to it.userMessage() }
        }
    }

    /** Rates the campsite (only after booking a stay through its lottery). */
    fun rate(name: String, stars: Int, comment: String) {
        val entry = state.value.myEntry?.takeIf(RatingPolicy::canRate) ?: return
        viewModelScope.launch {
            runCatching { container.social.rate(Rating(campsiteId, container.userId, name, stars, comment.trim().take(RatingPolicy.MAX_COMMENT), entry.lotteryId, container.clock.now())) }
                .onSuccess { note.value = "Thanks for rating your stay." }
                .onFailure { ui.value = false to it.userMessage() }
        }
    }

    /** Reports the campsite to the admins. */
    fun report(profile: UserProfile, reason: String) {
        val site = state.value.campsite ?: return
        viewModelScope.launch {
            runCatching { container.social.report(Report("", site.id, site.name, profile.id, profile.name, reason, container.clock.now())) }
                .onSuccess { note.value = "Thanks. An admin will review ${site.name}." }
                .onFailure { ui.value = false to it.userMessage() }
        }
    }

    /** Joins the current lottery's waiting list (US 01.01.06, US 01.02.01). */
    fun join(entrantName: String) = act { lot -> container.lotteries.joinWaitlist(lot.id, container.userId, entrantName, container.clock.now()) }

    /** Leaves the current lottery's waiting list (US 01.02.02). */
    fun leave() = act { lot -> container.lotteries.leaveWaitlist(lot.id, container.userId, container.clock.now()) }

    /** Clears the last error / info message. */
    fun clearMessage() { ui.value = ui.value.copy(second = null) }

    private fun act(block: suspend (Lottery) -> Unit) {
        val lottery = state.value.lottery ?: return
        ui.value = true to null
        viewModelScope.launch {
            val result = runCatching { block(lottery) }
            ui.value = false to result.exceptionOrNull()?.userMessage()
        }
    }
}

/** Promo QR links (carbon-events://campsite/<id>) waiting to open on the details screen. */
object PromoLinks {
    /** Campsite to open, or null when there is nothing waiting. */
    var pending by mutableStateOf<String?>(null)
        private set

    /** Remembers the campsite from a link the app was opened with. */
    fun handle(intent: Intent?) {
        val data = intent?.dataString ?: return
        QrCodes.parseCampsiteId(data)?.let { pending = it }
    }

    /** Called once the details screen has opened the campsite. */
    fun consume() {
        pending = null
    }
}

/** Keep flows alive for 5s after the screen goes away (survives rotation). */
private val sharing = SharingStarted.WhileSubscribed(5_000)

/** "1 spot", "8 spots". */
internal fun spots(n: Int) = if (n == 1) "1 spot" else "$n spots"
