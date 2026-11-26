/*
 * CreateLottery.kt (Aditya, Vaibhav)
 *
 * Creating and editing a lottery: campsites (yours, and other owners' by invitation), registration
 * period, booking period, number of winners and waitlist cap (US 02.04.01 to 02.04.08).
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val MIN = 60_000L

private const val DAY = 24 * 60 * MIN

/**
 * New / edit lottery form (Figma O-LOTTERY-NEW, O-LOTTERY-EDIT, O-LOTTERY-NEW-BOOK).
 * Sets the campsite (US 02.04.01), registration period (US 02.04.03), booking period
 * (US 02.04.04), number of winners (US 02.04.05) and an optional waitlist cap (US 02.04.07);
 * in edit mode it updates the published lottery (US 02.04.06).
 *
 * @param presetCampsiteId campsite chosen before opening, or null to pick here.
 * @param onPublished called with the lottery's ID after saving.
 * @param lotteryId the lottery to edit, or null to create a new one.
 */
@Composable
fun CreateLotteryScreen(container: AppContainer, presetCampsiteId: String?, onBack: () -> Unit, onPublished: (String) -> Unit, lotteryId: String? = null) {
    val vm = containerViewModel(container, key = "lottery-${lotteryId ?: "new"}-${presetCampsiteId.orEmpty()}") { CreateLotteryViewModel(it, presetCampsiteId, lotteryId) }
    val s by vm.state.collectAsStateWithLifecycle()
    val now = { container.clock.now() }
    val startLocked = s.editing && s.original?.let { now() >= it.registrationStart } == true
    Column(Modifier.fillMaxSize().imePadding()) {
        BackTopBar(if (s.editing) "Edit Lottery" else "New Lottery", onBack, Modifier.statusBarsPadding())
        if (s.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = EventsColors.Coral) }
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PageMargin).padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (s.editing) "Campsite in this lottery" else "Campsites in this lottery", style = EventsType.LabelSmall, color = EventsColors.TextSoft)
            if (s.campsites.isEmpty()) {
                Text("You don’t have any campsites yet. Register one first.", style = EventsType.BodySmall, color = EventsColors.TextSecondary)
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // A published lottery stays on its campsite, so edit mode shows only that one.
                    s.campsites.filter { !s.editing || it.id == s.selectedId }.forEach { site ->
                        val on = site.id in s.chosenIds
                        EventsChip(
                            site.name, if (on) ChipStyle.CORAL else ChipStyle.GLASS, { if (!s.editing) vm.select(site.id) },
                            Modifier.testTag("lottery_site_${site.id}"), if (on) R.drawable.ic_check else R.drawable.ic_tent,
                        )
                    }
                }
                // Figma O-LOTTERY-NEW-CAMPSITES: "Two campsites in one lottery."
                if (!s.editing && s.chosenIds.size > 1) Text(
                    "${s.chosenIds.size} campsites in one lottery. Entrants join once for all of them, and the winners and waitlist cap below apply to each campsite.",
                    style = EventsType.Caption, color = EventsColors.TextSecondary, modifier = Modifier.testTag("lottery_sites_note"),
                )
                if (!s.editing) InviteOthersSection(s, vm)
            }
            // Editing one of several campsites in a lottery (B10).
            if (s.editing && s.siblings.isNotEmpty()) Text(
                "Changes also apply to ${s.siblings.joinToString(" and ") { it.campsiteName }}, in the same lottery.",
                style = EventsType.Caption, color = EventsColors.TextSecondary, modifier = Modifier.testTag("lottery_group_note"),
            )
            if (s.editing && s.sharedWithOthers) Text(
                LotteryGroups.SHARED_DATES_LOCKED, style = EventsType.Caption, color = EventsColors.Amber, modifier = Modifier.testTag("lottery_dates_locked"),
            )
            val datesLocked = s.editing && s.sharedWithOthers
            // Figma O-LOTTERY-NEW: fields sit straight on the sky, no cards.
            if (datesLocked) {
                MetaItem(R.drawable.ic_clock, "Waitlist ${formatRange(s.registrationStart ?: 0L, s.registrationEnd ?: 0L)}")
                val stayFrom = s.bookingStartDay; val stayUntil = s.bookingEndDay
                if (stayFrom != null && stayUntil != null) MetaItem(R.drawable.ic_event_check, "Stays ${formatStay(stayFrom, stayUntil)}")
            } else if (startLocked) {
                MetaItem(R.drawable.ic_clock, "Opened ${formatDayTime(s.registrationStart ?: 0L)} · start is locked")
            } else {
                DateTimeField(
                    "Waitlist opens (registration)", s.registrationStart, vm::setStart, error = s.errors[Field.REG_START], testTag = "start",
                    presets = listOf(TimePreset("Now") { now() }, TimePreset("Tomorrow") { now() + DAY }),
                )
            }
            if (!datesLocked) {
                DateTimeField(
                    "Waitlist closes · lottery draw (auto)", s.registrationEnd, vm::setEnd, error = s.errors[Field.REG_END], testTag = "end",
                    presets = listOf(
                        TimePreset("In 1 hour") { now() + 60 * MIN }, TimePreset("In 3 days") { now() + 3 * DAY },
                        TimePreset("In 1 week") { now() + 7 * DAY }, TimePreset("In 2 weeks") { now() + 14 * DAY },
                    ),
                )
                val firstStayDay = dayOf(s.registrationEnd ?: now())
                DateRangeField(
                    "Booking period · nights winners can book", s.bookingStartDay, s.bookingEndDay, vm::setBooking,
                    error = s.errors[Field.BOOK_START] ?: s.errors[Field.BOOK_END], placeholder = "Pick stay dates", testTag = "booking",
                    initialDay = firstStayDay, isSelectable = { it >= firstStayDay },
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("1 week" to 7L, "2 weeks" to 14L, "1 month" to 30L).forEach { (label, nights) ->
                        // Stays start the day after the draw.
                        EventsChip(label, ChipStyle.GLASS, { vm.setBooking(firstStayDay + 1, firstStayDay + 1 + nights) }, Modifier.testTag("preset_booking_$label"))
                    }
                }
            }
            Text(
                "Winners are drawn automatically when the waitlist closes, then book nights inside the booking period.",
                style = EventsType.BodySmall, color = EventsColors.TextSecondary,
            )
            // Figma O-LOTTERY-NEW / O-LOTTERY-EDIT: both numbers can be tapped up and down or typed.
            Stepper(s.winners, vm::setWinners, 1, 99, "Number of winners", supporting = "Drawn from the waitlist", typeTag = "lottery_winners_input")
            s.errors[Field.WINNERS]?.let { Text(it, style = EventsType.Caption, color = EventsColors.Danger) }
            // The waitlist cap is optional (US 02.04.07): 0 means anyone can join.
            Stepper(
                s.cap ?: 0, vm::setCap, 0, MAX_WAITLIST_CAP, "Waitlist cap", Modifier.testTag("lottery_cap"),
                supporting = when {
                    s.cap == null -> "Optional · no limit"
                    s.editing -> "Max entrants allowed to join"
                    else -> "Optional · max entrants"
                },
                icon = R.drawable.ic_group, typeTag = "lottery_cap_input",
            )
            s.errors[Field.CAP]?.let { Text(it, style = EventsType.Caption, color = EventsColors.Danger) }
            if (s.message != null) Text(s.message!!, style = EventsType.BodySmall, color = EventsColors.Danger, modifier = Modifier.testTag("lottery_message"))
        }
        ActionBar {
            PrimaryButton(
                if (s.editing) "Save changes" else "Publish lottery", { vm.publish(onPublished) },
                Modifier.weight(1f).testTag("lottery_publish"), loading = s.saving,
            )
        }
    }
}

/**
 * Other owners' campsites to invite into the new lottery (US 02.04.08): search by campsite,
 * place or owner, tap to invite. Each owner gets an invitation and adds their campsite by
 * accepting it; they then run its draw themselves.
 */
@Composable
private fun InviteOthersSection(s: CreateLotteryState, vm: CreateLotteryViewModel) {
    EventsTextField(
        s.inviteQuery, vm::setInviteQuery, "Invite other owners' campsites (optional)", Modifier.testTag("lottery_invite_search"),
        placeholder = "Search by campsite, place or owner", leadingIcon = R.drawable.ic_search,
    )
    val invited = s.inviteIds.mapNotNull { id -> s.others.firstOrNull { it.id == id } }
    val found = (invited + LotteryInvites.searchOthers(s.others, s.ownerId, s.inviteQuery)).distinctBy { it.id }
    if (s.inviteQuery.isNotBlank() && found.isEmpty()) Text("No other owners' campsites match.", style = EventsType.Caption, color = EventsColors.TextSecondary)
    if (found.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        found.forEach { site ->
            val on = site.id in s.inviteIds
            EventsChip(
                "${site.name} · ${site.ownerName.ifBlank { "Owner" }}", if (on) ChipStyle.CORAL else ChipStyle.GLASS, { vm.toggleInvite(site.id) },
                Modifier.testTag("lottery_invite_${site.id}"), if (on) R.drawable.ic_check else R.drawable.ic_group,
            )
        }
    }
    if (s.inviteIds.isNotEmpty()) Text(
        "${s.inviteIds.size} owner invitation${if (s.inviteIds.size == 1) "" else "s"} will be sent. Each campsite joins this lottery when its owner accepts, with the same dates, winners and cap, and its owner runs its draw.",
        style = EventsType.Caption, color = EventsColors.TextSecondary, modifier = Modifier.testTag("lottery_invite_note"),
    )
}

/** State of the New / Edit Lottery form. */
/** The largest waitlist cap the form takes. */
private const val MAX_WAITLIST_CAP = 9999

data class CreateLotteryState(
    val loading: Boolean = false,
    val editing: Boolean = false,
    val original: Lottery? = null,
    val campsites: List<Campsite> = emptyList(),
    val selectedId: String? = null,
    /** More campsites listed in the same new lottery (Figma O-LOTTERY-NEW-CAMPSITES), in the order picked. */
    val moreIds: List<String> = emptyList(),
    val registrationStart: Long? = null,
    val registrationEnd: Long? = null,
    val bookingStartDay: Long? = null,
    val bookingEndDay: Long? = null,
    val winners: Int = 4,
    val cap: Int? = null,
    val errors: Map<Field, String> = emptyMap(),
    val saving: Boolean = false,
    val message: String? = null,
    /** The signed-in owner, so their own campsites aren't offered as invitations. */
    val ownerId: String = "",
    /** Every other owner's campsite, which can be invited into a new lottery (US 02.04.08). */
    val others: List<Campsite> = emptyList(),
    /** What the owner typed to find other owners' campsites. */
    val inviteQuery: String = "",
    /** Other owners' campsites picked to invite, in the order picked. */
    val inviteIds: List<String> = emptyList(),
    /** Editing: the other lotteries of this owner's campsites in the same group, which change too. */
    val siblings: List<Lottery> = emptyList(),
    /** Editing: the group includes other owners' campsites, so its dates are locked. */
    val sharedWithOthers: Boolean = false,
) {
    /** Every campsite picked for this lottery, first pick first. */
    val chosenIds: List<String> get() = listOfNotNull(selectedId) + moreIds
}

/**
 * Creates a lottery for one campsite (US 02.04.01) or several at once (US 02.04.02) with a registration period (US 02.04.03),
 * booking period (US 02.04.04), number of winners (US 02.04.05) and optional waitlist cap
 * (US 02.04.07), or edits a published one (US 02.04.06).
 *
 * @param presetCampsiteId campsite chosen before opening the form, if any.
 * @param lotteryId lottery to edit, or null to create a new one.
 */
class CreateLotteryViewModel(private val container: AppContainer, presetCampsiteId: String?, lotteryId: String? = null) : ViewModel() {
    private val _state = MutableStateFlow(CreateLotteryState(selectedId = presetCampsiteId, editing = lotteryId != null, loading = lotteryId != null, ownerId = container.userId))

    /** Form state. */
    val state: StateFlow<CreateLotteryState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.campsites.observeCampsitesByOwner(container.userId).collect { sites ->
                // An owner with one campsite has nothing to choose; with several, they tick the ones they want.
                _state.value = _state.value.copy(campsites = sites, selectedId = _state.value.selectedId ?: sites.singleOrNull()?.id)
            }
        }
        if (lotteryId == null) viewModelScope.launch {
            // Other owners' campsites, to invite into a shared lottery (US 02.04.08).
            container.campsites.observeCampsites().collect { all -> _state.value = _state.value.copy(others = all.filter { it.ownerId != container.userId }) }
        }
        if (lotteryId != null) viewModelScope.launch {
            val lot = container.lotteries.observeLottery(lotteryId).first()
            val all = if (lot == null || lot.groupId.isBlank()) emptyList() else container.lotteries.observeLotteries().first()
            _state.value = if (lot == null) {
                _state.value.copy(loading = false, message = "This lottery no longer exists.")
            } else {
                _state.value.copy(
                    loading = false, original = lot, selectedId = lot.campsiteId, registrationStart = lot.registrationStart,
                    registrationEnd = lot.registrationEnd, bookingStartDay = lot.bookingStartDay, bookingEndDay = lot.bookingEndDay,
                    winners = lot.winnerCount, cap = lot.waitlistCap,
                    siblings = LotteryGroups.ownSiblings(lot, all, container.userId), sharedWithOthers = LotteryGroups.sharedWithOthers(lot, all),
                )
            }
        }
    }

    private fun edit(vararg cleared: Field, change: (CreateLotteryState) -> CreateLotteryState) {
        _state.value = change(_state.value).let { it.copy(errors = it.errors - cleared.toSet(), message = null) }
    }

    /**
     * Adds a campsite to the new lottery, or takes it out again. A published lottery stays on
     * its campsite, so this does nothing while editing.
     */
    fun select(id: String) = edit { s ->
        if (s.editing) return@edit s
        val picked = if (id in s.chosenIds) s.chosenIds - id else s.chosenIds + id
        s.copy(selectedId = picked.firstOrNull(), moreIds = picked.drop(1))
    }

    /** Sets the search for other owners' campsites. */
    fun setInviteQuery(q: String) = edit { it.copy(inviteQuery = q) }

    /** Picks another owner's campsite to invite into the new lottery, or un-picks it (US 02.04.08). */
    fun toggleInvite(id: String) = edit { s ->
        if (s.editing) return@edit s
        s.copy(inviteIds = if (id in s.inviteIds) s.inviteIds - id else s.inviteIds + id)
    }

    /** Sets when the waiting list opens. */
    fun setStart(millis: Long) = edit(Field.REG_START, Field.REG_END) { it.copy(registrationStart = millis) }

    /** Sets when the waiting list closes (the draw runs then). */
    fun setEnd(millis: Long) = edit(Field.REG_END, Field.BOOK_START) { it.copy(registrationEnd = millis) }

    /** Sets the booking period: first night and last check-out day (day numbers). */
    fun setBooking(startDay: Long, endDay: Long) = edit(Field.BOOK_START, Field.BOOK_END) { it.copy(bookingStartDay = startDay, bookingEndDay = endDay) }

    /** Sets the number of winners. */
    fun setWinners(n: Int) = edit(Field.WINNERS) { it.copy(winners = n) }

    /** Sets the waitlist cap; 0 (or less) takes it off, so anyone can join. */
    fun setCap(n: Int) = edit(Field.CAP) { it.copy(cap = n.takeIf { it > 0 }) }

    /**
     * Validates and publishes the new lottery, or saves the changes to an existing one.
     * @param onPublished called with the lottery's ID.
     */
    fun publish(onPublished: (String) -> Unit) {
        val s = _state.value
        val now = container.clock.now()
        val site = s.campsites.firstOrNull { it.id == s.selectedId }
        val more = if (s.original == null) s.moreIds.mapNotNull { id -> s.campsites.firstOrNull { it.id == id } } else emptyList()
        val errors = buildMap {
            putAll(LotteryValidator.validate(s.registrationStart, s.registrationEnd, s.winners, now))
            putAll(LotteryValidator.validateBooking(s.registrationEnd?.let(::dayOf), s.bookingStartDay, s.bookingEndDay))
            LotteryValidator.validateCap(s.cap)?.let { put(Field.CAP, it) }
        }
        if (site == null || errors.isNotEmpty()) {
            _state.value = s.copy(errors = errors, message = when {
                site != null -> null
                s.campsites.isEmpty() -> "Register a campsite first."
                else -> "Choose at least one campsite for this lottery."
            })
            return
        }
        val base = s.original ?: Lottery(
            id = "", campsiteId = site.id, campsiteName = site.name, ownerId = container.userId,
            registrationStart = 0L, registrationEnd = 0L, winnerCount = 1,
        )
        val lottery = base.copy(
            registrationStart = s.registrationStart!!, registrationEnd = s.registrationEnd!!, winnerCount = s.winners,
            waitlistCap = s.cap, bookingStartDay = s.bookingStartDay, bookingEndDay = s.bookingEndDay,
        )
        _state.value = s.copy(saving = true, message = null)
        viewModelScope.launch {
            // Each campsite's stays must not overlap one of its other lotteries; check them all
            // before publishing any, so a lottery over several campsites is all or nothing.
            val mine = runCatching { container.lotteries.observeLotteriesByOwner(container.userId).first() }.getOrDefault(emptyList())
            val clash = (listOf(site) + more).firstNotNullOfOrNull { c ->
                StayPeriods.clash(lottery.copy(campsiteId = c.id), mine)?.let { StayPeriods.message(c.name, it) }
            }
            if (clash != null) {
                _state.value = _state.value.copy(saving = false, errors = mapOf(Field.BOOK_START to clash), message = clash)
                return@launch
            }
            runCatching {
                if (s.original == null) {
                    // Several campsites in one lottery: one waiting list each, tied together by a group ID.
                    val invited = s.inviteIds.mapNotNull { id -> s.others.firstOrNull { it.id == id } }
                    val groupId = if (more.isEmpty() && invited.isEmpty()) "" else LotteryGroups.newGroupId(now)
                    val ids = (listOf(site) + more).map { c ->
                        val one = lottery.copy(campsiteId = c.id, campsiteName = c.name, groupId = groupId)
                        container.lotteries.createLottery(one).also { id ->
                            // "Notify me when a lottery opens": followers hear about it now.
                            runCatching { container.social.notifyFollowers(one.copy(id = id), now) }
                        }
                    }
                    // Other owners' campsites join when their owners accept (US 02.04.08).
                    val published = lottery.copy(id = ids.first(), groupId = groupId)
                    LotteryInvites.forCampsites(published, groupId, site.ownerName, site, invited, now).forEach { container.lotteries.inviteToLottery(it) }
                    ids.first()
                }
                // One of several campsites in a lottery: the change goes to all of this owner's (B10).
                else lottery.id.also { LotteryGroups.saveEdit(container.lotteries, s.original, lottery, container.userId, now) }
            }
                .onSuccess { _state.value = _state.value.copy(saving = false); onPublished(it) }
                .onFailure { _state.value = _state.value.copy(saving = false, message = it.userMessage()) }
        }
    }
}
