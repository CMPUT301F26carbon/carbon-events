/*
 * Lotteries.kt (Suvir)
 *
 * My Lotteries: the waitlists I joined and their results, and the bell with its unread count.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The entrant's lotteries: waiting lists they are on, and draw results (Figma E-LOTTERIES). */
@Composable
fun MyLotteriesScreen(container: AppContainer, onOpen: (String) -> Unit, onExplore: () -> Unit, onNotifications: () -> Unit, onBook: (String) -> Unit = {}, onChat: (Chat) -> Unit = {}) {
    val vm = containerViewModel(container) { MyLotteriesViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val items = if (tab == 0) state.active else state.results
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("My Lotteries", style = EventsType.Headline, color = EventsColors.Text, modifier = Modifier.weight(1f))
                BellButton(container, onNotifications)
            }
        }
        item { SegmentedControl(listOf("Active · ${state.active.size}", "Results · ${state.results.size}"), tab, { tab = it }) }
        if (!state.loading && items.isEmpty()) {
            item {
                DuskCard {
                    EmptyState(
                        R.drawable.ic_ticket,
                        if (tab == 0) "No waiting lists yet" else "No results yet",
                        if (tab == 0) "Join a lottery from any campsite’s details page." else "Results appear here after each draw.",
                    ) { if (tab == 0) PrimaryButton("Explore campsites", onExplore) }
                }
            }
        }
        items(items, key = { it.entry.lotteryId }) { item ->
            val lot = item.lottery!!
            DuskCard(Modifier.fillMaxWidth().testTag("my_lottery_${lot.id}"), onClick = { onOpen(lot.campsiteId) }, padding = PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(60.dp).clip(EventsShapes.Medium)) { CampsiteImage(lot.campsiteId, item.campsite?.imageBase64) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(lot.campsiteName, style = EventsType.BodyStrong, color = EventsColors.Text, maxLines = 1)
                        MetaItem(
                            R.drawable.ic_clock,
                            when {
                                item.entry.status != EntryStatus.WAITING -> "Drawn ${formatDay(lot.drawnAt ?: lot.registrationEnd)}"
                                lot.isDrawDue(state.now) -> "Closed · waiting for the owner to draw"
                                else -> "${closesIn(lot, state.now).replace("Closes", "Draw")} · ${lot.entrantCount} entered"
                            },
                        )
                        when (item.entry.status) {
                            // Figma E-LOTTERIES: "IN THE DRAW" while waiting (or "Opens …" before registration).
                            EntryStatus.WAITING -> if (lot.isUpcoming(state.now)) LotteryStatusTag(lot, state.now, openTone = TagTone.BLUE) else StatusTag("In the draw", TagTone.BLUE)
                            EntryStatus.WON -> StatusTag("You won!", TagTone.GREEN)
                            EntryStatus.LOST -> StatusTag("Not selected", TagTone.GREY)
                            EntryStatus.DECLINED -> StatusTag("Declined", TagTone.AMBER)
                            EntryStatus.CANCELLED -> StatusTag("Cancelled", TagTone.AMBER)
                            EntryStatus.REJECTED -> StatusTag("Rejected", TagTone.RED)
                        }
                    }
                    // The conversation with this campsite's owner: notices for everyone, replies once you win.
                    item.campsite?.let { site ->
                        Box(
                            Modifier.size(40.dp).clip(EventsShapes.Medium).background(EventsColors.CoralContainer)
                                .clickable {
                                    onChat(Chat(Chat.idFor(site.id, container.userId), site.id, site.name, site.ownerId, site.ownerName,
                                        container.userId, item.entry.entrantName, lotteryKey = Paths.keyOf(lot.id)))
                                }.testTag("lottery_chat_${lot.id}"),
                            contentAlignment = Alignment.Center,
                        ) { EventsIcon(R.drawable.ic_chat, size = 20.dp, tint = EventsColors.Coral, contentDescription = "Messages from ${site.ownerName}") }
                    }
                }
                if (item.entry.status == EntryStatus.LOST && lot.status == LotteryStatus.DRAWN) {
                    Text("If a winner gives their spot back, you could still be drawn.", style = EventsType.Caption, color = EventsColors.TextTertiary)
                }
                if (item.entry.status == EntryStatus.WON) {
                    if (item.entry.isBooked) {
                        MetaItem(R.drawable.ic_event_check, "Booked · ${formatStay(item.entry.checkInDay!!, item.entry.checkOutDay!!)}", color = EventsColors.Green)
                    } else {
                        PrimaryButton("Choose your dates", { onBook(lot.id) }, Modifier.fillMaxWidth().testTag("book_${lot.id}"), icon = R.drawable.ic_calendar, size = ButtonSize.SMALL)
                    }
                }
            }
        }
    }
}

/**
 * One row of My Lotteries.
 *
 * @property entry the entrant's waiting-list entry.
 * @property lottery the lottery it belongs to (null if it was removed).
 * @property campsite the lottery's campsite, for the photo.
 */
data class MyLotteryItem(val entry: WaitlistEntry, val lottery: Lottery?, val campsite: Campsite?)

/** State of the My Lotteries screen. */
data class MyLotteriesState(val loading: Boolean = true, val active: List<MyLotteryItem> = emptyList(), val results: List<MyLotteryItem> = emptyList(), val now: Long = 0L)

/** Lotteries the entrant has entered, split into active and results. */
class MyLotteriesViewModel(container: AppContainer) : ViewModel() {
    /** My Lotteries state. */
    val state: StateFlow<MyLotteriesState> = combine(
        container.lotteries.observeEntriesForEntrant(container.userId),
        container.lotteries.observeLotteries(),
        container.campsites.observeCampsites(),
        nowTicker(container.clock),
    ) { entries, lotteries, sites, now ->
        val lotById = lotteries.associateBy { it.id }
        val siteById = sites.associateBy { it.id }
        val items = entries.map { e -> MyLotteryItem(e, lotById[e.lotteryId], lotById[e.lotteryId]?.let { siteById[it.campsiteId] }) }
            .filter { it.lottery != null }
        MyLotteriesState(
            loading = false,
            active = items.filter { it.entry.status == EntryStatus.WAITING }.sortedBy { it.lottery!!.registrationEnd },
            results = items.filter { it.entry.status != EntryStatus.WAITING }.sortedByDescending { it.lottery!!.drawnAt ?: 0L },
            now = now,
        )
    }.stateIn(viewModelScope, sharing, MyLotteriesState())
}

/** Keep flows alive for 5s after the screen goes away (survives rotation). */
private val sharing = SharingStarted.WhileSubscribed(5_000)

/**
 * The bell with its red unread count (conversations plus app updates). Used on every screen that
 * has a bell, for entrants and owners.
 */
@Composable
fun BellButton(container: AppContainer, onClick: () -> Unit, modifier: Modifier = Modifier, contentDescription: String = "Notifications") {
    val chats by remember(container) { container.social.observeChatsFor(container.userId) }.collectAsStateWithLifecycle(emptyList())
    val notes by remember(container) { container.notifications.observeNotifications(container.userId) }.collectAsStateWithLifecycle(emptyList())
    val badge = bellBadge(buildInbox(container.userId, chats.filter { it.lastText.isNotBlank() }, notes))
    GlassIconButton(R.drawable.ic_bell, contentDescription, onClick, modifier, badgeText = badge)
}
