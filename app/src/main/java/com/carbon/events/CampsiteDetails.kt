/*
 * CampsiteDetails.kt (Ansh)
 *
 * Campsite details: join or leave a lottery waitlist, plus promo QR links
 * that open a campsite straight from the camera app.
 */
package com.carbon.events

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
fun CampsiteDetailsScreen(container: AppContainer, profile: UserProfile, campsiteId: String, onBack: () -> Unit, onMyLotteries: () -> Unit) {
    val vm = containerViewModel(container, key = "details-$campsiteId") { CampsiteDetailsViewModel(it, campsiteId) }
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }
    // Figma E-DETAILS-JOINED: a snackbar confirms the join for a few seconds.
    var joinRequested by remember { mutableStateOf(false) }
    var showJoined by remember { mutableStateOf(false) }
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
                ) { DetailsBody(site, state.lottery, state.action, state.now) }
            }
            Row(Modifier.statusBarsPadding().padding(horizontal = PageMargin, vertical = 8.dp)) { GlassIconButton(R.drawable.ic_arrow_back, "Back", onBack) }
            Column(Modifier.align(Alignment.BottomCenter)) {
                AnimatedVisibility(showJoined, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
                    state.lottery?.let { JoinedSnackbar("Joined! We’ll notify you after the ${formatDay(it.registrationEnd)} draw.") }
                }
                DetailsActionBar(state, Modifier, onJoin = { joinRequested = true; vm.join(profile.name) }, onLeave = { showJoined = false; confirmLeave = true }, onMyLotteries = onMyLotteries)
            }
        }
    }
    if (confirmLeave) {
        ConfirmDialog(
            R.drawable.ic_logout, "Leave the Waitlist?",
            "You’ll lose your spot for ${site?.name ?: "this campsite"}. You can rejoin any time before registration closes on ${state.lottery?.let { formatDay(it.registrationEnd) } ?: "the closing date"}.",
            "Leave", onConfirm = { confirmLeave = false; vm.leave() }, onDismiss = { confirmLeave = false }, dismissText = "Stay",
        )
    }
}

@Composable
private fun DetailsBody(site: Campsite, lottery: Lottery?, action: LotteryAction, now: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(site.name, style = EventsType.Headline, color = EventsColors.Text, modifier = Modifier.testTag("details_name"))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetaItem(R.drawable.ic_location, site.location)
            if (site.ratingCount > 0) RatingLine(site.rating, site.ratingCount, "· ${site.ratingCount} ratings")
        }
    }
    if (site.description.isNotBlank()) Text(site.description, style = EventsType.BodySmall, color = EventsColors.TextSecondary)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoTile(R.drawable.ic_group, "${site.maxOccupancy} guests", "Max occupancy", Modifier.weight(1f))
        InfoTile(R.drawable.ic_money, "$${site.pricePerNight}", "per night", Modifier.weight(1f))
        InfoTile(R.drawable.ic_trophy, lottery?.let { "${it.winnerCount} spots" } ?: "—", "Lottery winners", Modifier.weight(1f))
    }
    if (site.amenities.isNotBlank()) {
        DuskCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), color = EventsColors.SurfaceSoft, shape = RoundedCornerShape(16.dp), spacing = 4.dp) {
            Text("Amenities", style = EventsType.BodyStrong, color = EventsColors.Text)
            Text(site.amenities, style = EventsType.BodySmall, color = EventsColors.TextSecondary)
        }
    }
    LotteryCard(lottery, action, now)
    if (site.hasMapLocation) MapPreview(site.latitude!!, site.longitude!!, site.location, height = 96.dp)
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
                    joined -> "You’re in · ${lottery?.winnerCount ?: 0} spots"
                    lottery == null -> "Lottery"
                    else -> "Lottery · ${lottery.winnerCount} spots"
                },
                style = EventsType.BodyStrong, color = EventsColors.Text, modifier = Modifier.weight(1f),
            )
            if (joined) StatusTag("Joined", TagTone.GREEN) else LotteryStatusTag(lottery, now)
        }
        if (lottery == null) {
            Text("The owner hasn’t opened a lottery for this campsite yet.", style = EventsType.BodySmall, color = EventsColors.OnPrimaryContainer)
        } else {
            DetailRow(R.drawable.ic_calendar, "Registration", formatRange(lottery.registrationStart, lottery.registrationEnd))
            DetailRow(R.drawable.ic_trophy, "Draw · winners", "${formatDay(lottery.registrationEnd)} · ${lottery.winnerCount} spots")
            DetailRow(R.drawable.ic_group, "On waitlist", lottery.waitlistCap?.let { "${lottery.entrantCount} / $it" } ?: "${lottery.entrantCount}")
        }
    }
}

@Composable
private fun DetailsActionBar(state: DetailsState, modifier: Modifier, onJoin: () -> Unit, onLeave: () -> Unit, onMyLotteries: () -> Unit) {
    ActionBar(modifier, header = {
        if (state.message != null) Text(state.message, style = EventsType.BodySmall, color = EventsColors.Danger, modifier = Modifier.padding(bottom = 10.dp).testTag("details_message"))
    }) {
        val full = Modifier.weight(1f).testTag("lottery_action")
        when (state.action) {
            LotteryAction.CAN_JOIN -> PrimaryButton("Join lottery", onJoin, full, icon = R.drawable.ic_ticket, loading = state.working)
            LotteryAction.JOINED -> {
                SecondaryButton("Leave waitlist", onLeave, Modifier.weight(1f).testTag("lottery_action"))
                SecondaryButton("My lotteries", onMyLotteries, Modifier.weight(1f))
            }
            LotteryAction.WON -> ResultBanner("You won this lottery! Check your notifications.", TagTone.GREEN)
            LotteryAction.LOST -> ResultBanner("Not selected this time. Good luck next draw.", TagTone.CORAL)
            LotteryAction.UPCOMING -> PrimaryButton("Registration opens ${formatDay(state.lottery!!.registrationStart)}", {}, full, enabled = false)
            LotteryAction.FULL -> PrimaryButton("Waitlist full", {}, full, enabled = false)
            LotteryAction.CLOSED -> PrimaryButton("Registration closed · drawing winners", {}, full, enabled = false)
            LotteryAction.ENDED -> PrimaryButton("Winners have been drawn", {}, full, enabled = false)
            LotteryAction.NONE -> PrimaryButton("No lottery open", {}, full, enabled = false)
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
class CampsiteDetailsViewModel(private val container: AppContainer, campsiteId: String) : ViewModel() {
    private val ui = MutableStateFlow(Pair(false, null as String?))

    private val lotteryFlow = container.lotteries.observeLotteriesForCampsite(campsiteId).map { currentLottery(it) }
    private val entryFlow = lotteryFlow.flatMapLatest { lot ->
        if (lot == null) flowOf(null)
        else container.lotteries.observeEntriesForEntrant(container.deviceId).map { list -> list.firstOrNull { it.lotteryId == lot.id } }
    }

    /** Details screen state. */
    val state: StateFlow<DetailsState> = combine(
        container.campsites.observeCampsite(campsiteId), lotteryFlow, entryFlow, nowTicker(container.clock, 15_000), ui,
    ) { site, lottery, entry, now, (working, message) ->
        DetailsState(false, site, lottery, entry, lotteryAction(lottery, entry, now), now, working, message)
    }.stateIn(viewModelScope, sharing, DetailsState())

    /** Joins the current lottery's waiting list (US 01.01.06, US 01.02.01). */
    fun join(entrantName: String) = act { lot -> container.lotteries.joinWaitlist(lot.id, container.deviceId, entrantName, container.clock.now()) }

    /** Leaves the current lottery's waiting list (US 01.02.02). */
    fun leave() = act { lot -> container.lotteries.leaveWaitlist(lot.id, container.deviceId, container.clock.now()) }

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
