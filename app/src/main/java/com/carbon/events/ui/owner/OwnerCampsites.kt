/*
 * OwnerCampsites.kt (Rayner)
 *
 * My Campsites: the owner's home with their listings (US 02.03.01).
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

private val sharing = SharingStarted.WhileSubscribed(5_000)

/** My campsites (Figma O-HOME, US 02.03.01). */
@Composable
fun OwnerCampsitesScreen(container: AppContainer, profile: UserProfile, onAdd: () -> Unit, onOpen: (String) -> Unit, onBell: () -> Unit = {}) {
    val vm = containerViewModel(container) { OwnerCampsitesViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().testTag("owner_campsites"),
        contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(profile.name, style = EventsType.DisplaySmall, color = EventsColors.Coral, modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    StatusTag("Owner", TagTone.BLUE)
                }
                // Figma O-HOME: bell at the top right (guest messages live in the Inbox).
                BellButton(container, onBell, Modifier.testTag("owner_bell"), "Messages")
            }
        }
        item { SectionTitle("My Campsites") { PrimaryButton("Add", onAdd, Modifier.testTag("owner_add"), icon = R.drawable.ic_add, size = ButtonSize.SMALL) } }
        if (state.loading) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = EventsColors.Coral) } }
        if (!state.loading && state.items.isEmpty()) {
            item { DuskCard { EmptyState(R.drawable.ic_tent, "List your first campsite", "Register a campsite to get a promo QR code and run a fair lottery.") { PrimaryButton("Register campsite", onAdd) } } }
        }
        items(state.items, key = { it.campsite.id }) { item ->
            CampsiteListCard(item.campsite, { onOpen(item.campsite.id) }, thumb = 72.dp) {
                // Tag and count stack so neither gets cut off on narrow phones.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val lot = item.lottery
                    when {
                        lot == null -> StatusTag("No lottery", TagTone.BLUE)
                        lot.status == LotteryStatus.DRAWN -> StatusTag("Winners drawn", TagTone.CORAL)
                        lot.isRegistrationOpen(state.now) -> StatusTag("Lottery live", TagTone.GREEN)
                        else -> LotteryStatusTag(lot, state.now)
                    }
                    if (lot != null) Text("${lot.entrantCount} on waitlist", style = EventsType.Caption, color = EventsColors.TextSecondary, maxLines = 1)
                }
            }
        }
    }
}

/** State of the owner's campsites list. */
data class OwnerCampsitesState(val loading: Boolean = true, val items: List<CampsiteWithLottery> = emptyList(), val now: Long = 0L)

/** The owner's campsites with their current lottery (US 02.03.01). */
class OwnerCampsitesViewModel(container: AppContainer) : ViewModel() {
    /** Campsites list state. */
    val state: StateFlow<OwnerCampsitesState> = combine(
        container.campsites.observeCampsitesByOwner(container.userId),
        container.lotteries.observeLotteriesByOwner(container.userId),
        nowTicker(container.clock),
    ) { sites, lotteries, now ->
        val byCampsite = lotteries.groupBy { it.campsiteId }
        OwnerCampsitesState(false, sites.map { CampsiteWithLottery(it, currentLottery(byCampsite[it.id].orEmpty(), now)) }, now)
    }.stateIn(viewModelScope, sharing, OwnerCampsitesState())
}
