/*
 * Explore.kt (Ansh)
 *
 * Explore, the entrant's home: featured lotteries, Lotteries Near Me and campsites.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import java.time.LocalTime
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning,"
    in 12..16 -> "Good afternoon,"
    else -> "Good evening,"
}

/** Icon for a vibe / tag chip, matching the Figma choices where they exist. */
fun vibeIcon(tag: String): Int = when {
    tag.contains("lake", true) || tag.contains("river", true) || tag.contains("water", true) -> R.drawable.ic_water
    tag.contains("fire", true) || tag.contains("camp", true) -> R.drawable.ic_fire
    tag.contains("wi-fi", true) || tag.contains("wifi", true) -> R.drawable.ic_wifi
    else -> R.drawable.ic_tent
}

/**
 * Explore (home) for entrants (Figma E-HOME).
 *
 * @param onSearch opens Search, optionally pre-filled with a vibe tag.
 * @param onOpen opens a campsite's details.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExploreScreen(
    container: AppContainer,
    profile: UserProfile,
    onSearch: (String) -> Unit,
    onOpen: (String) -> Unit,
    onNotifications: () -> Unit,
    onProfile: () -> Unit,
    onFilters: () -> Unit = {},
) {
    val vm = containerViewModel(container) { ExploreViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val here = LocalDeviceLocation.current
    val context = androidx.compose.ui.platform.LocalContext.current
    // No pop-up on launch: location is asked for only when you tap "Use my current location" or
    // open the map. Lotteries Near Me uses it if it's already allowed.
    LaunchedEffect(Unit) { if (DeviceLocation.hasPermission(context)) DeviceLocation.refresh(context) }
    val featured = remember(state.featured, here) { nearMe(state.featured, here) { it.campsite } }
    LazyColumn(Modifier.fillMaxSize().testTag("explore"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = PageMargin).padding(top = 8.dp)) {
                // Top row: bell + profile chip (Figma "Top"). Owner messages arrive as notifications.
                Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
                    BellButton(container, onNotifications)
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier
                            .height(46.dp)
                            .clip(EventsShapes.Pill)
                            .clickable(onClick = onProfile)
                            .clip(EventsShapes.Pill)
                            .background(EventsColors.Tint)
                            .border(1.dp, EventsColors.TintStroke, EventsShapes.Pill)
                            .padding(start = 4.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ProfileAvatar(profile, 36.dp)
                        Text(profile.firstName, style = EventsType.LabelSmall, color = EventsColors.Text)
                    }
                }
                // Greeting (Figma "Greeting"): starts 20dp below the top row.
                Column(Modifier.padding(top = 19.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(greeting(), style = EventsType.Greeting, color = EventsColors.TextSoft)
                    Text(profile.firstName, style = EventsType.Display, color = EventsColors.Coral)
                    Text("Where are we camping next?", style = EventsType.BodySmall, color = EventsColors.TextSoft)
                }
                Spacer(Modifier.height(20.dp))
                SearchBar("", {}, onClick = { onSearch("") }, modifier = Modifier.testTag("explore_search"), onFilters = onFilters)
                Spacer(Modifier.height(20.dp))
            }
        }
        if (state.tags.isNotEmpty()) {
            item { SectionTitle("Browse by Vibe", Modifier.padding(horizontal = PageMargin)) }
            item {
                LazyRow(Modifier.padding(top = 14.dp, bottom = 14.dp), contentPadding = PaddingValues(horizontal = PageMargin), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(state.tags) { i, tag -> EventsChip(tag, if (i == 0) ChipStyle.CORAL else ChipStyle.GLASS, { onSearch(tag) }, icon = vibeIcon(tag)) }
                }
            }
        }
        if (state.loading) {
            item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = EventsColors.Coral) } }
        }
        if (featured.isNotEmpty()) {
            item {
                SectionTitle("Lotteries Near Me", Modifier.padding(horizontal = PageMargin)) {
                    Text(if (here != null) "Within ${NEAR_ME_LOTTERIES_KM.toInt()} km" else "Closing soonest", style = EventsType.Caption, color = EventsColors.TextSoft)
                }
            }
            item {
                val pager = rememberPagerState { featured.size }
                BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    val side = ((maxWidth - 290.dp) / 2).coerceAtLeast(PageMargin)
                    HorizontalPager(pager, pageSize = PageSize.Fixed(290.dp), contentPadding = PaddingValues(horizontal = side), pageSpacing = 12.dp) { page ->
                        val f = featured[page]
                        val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                        FeaturedLotteryCard(
                            f.campsite, f.lottery!!, state.now, { onOpen(f.campsite.id) },
                            Modifier.graphicsLayer {
                                val scale = 1f - 0.14f * offset
                                scaleX = scale; scaleY = scale; alpha = 1f - 0.45f * offset
                            },
                            distance = distanceKm(here, f.campsite)?.let { "${formatKm(it)} away" },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 20.dp), horizontalArrangement = Arrangement.Center) {
                    CarouselDots(featured.size, pager.currentPage)
                }
            }
        }
        if (!state.loading) item { SectionTitle("All Campsites", Modifier.padding(horizontal = PageMargin).padding(bottom = 14.dp)) }
        if (!state.loading && state.all.isEmpty()) {
            item { EmptyState(R.drawable.ic_tent, "No campsites yet", "Owners haven't listed any campsites. Check back soon.") }
        }
        items(state.all, key = { it.campsite.id }) { item ->
            CampsiteListCard(
                item.campsite, { onOpen(item.campsite.id) }, Modifier.padding(horizontal = PageMargin).padding(bottom = 10.dp),
                distance = distanceKm(here, item.campsite)?.let { "${formatKm(it)} away" },
            )
        }
    }
}

private val sharing = SharingStarted.WhileSubscribed(5_000)

/**
 * The lottery to show for a campsite at time [now], so scheduling next month's lottery never
 * hides the one people can join today. In order: one taking entries now (closing soonest), one
 * waiting for its draw, the next one to open, then the most recently drawn.
 */
fun currentLottery(lotteries: List<Lottery>, now: Long): Lottery? =
    lotteries.filter { it.isRegistrationOpen(now) }.minByOrNull { it.registrationEnd }
        ?: lotteries.filter { it.isDrawDue(now) }.maxByOrNull { it.registrationEnd }
        ?: lotteries.filter { it.isUpcoming(now) }.minByOrNull { it.registrationStart }
        ?: lotteries.maxByOrNull { it.drawnAt ?: it.registrationStart }

/** A campsite with its current lottery, if any. */
data class CampsiteWithLottery(val campsite: Campsite, val lottery: Lottery?)

/** State of the Explore (home) screen. */
data class ExploreState(
    val loading: Boolean = true,
    val featured: List<CampsiteWithLottery> = emptyList(),
    val all: List<CampsiteWithLottery> = emptyList(),
    val tags: List<String> = emptyList(),
    val unreadCount: Int = 0,
    val unreadChats: Int = 0,
    val now: Long = 0L,
)

/** Home feed: campsites with open lotteries first, then everything. */
class ExploreViewModel(container: AppContainer) : ViewModel() {
    /** Explore screen state. */
    val state: StateFlow<ExploreState> = combine(
        container.campsites.observeCampsites(),
        container.lotteries.observeLotteries(),
        container.notifications.observeNotifications(container.userId),
        nowTicker(container.clock),
        container.social.observeChatsFor(container.userId),
    ) { sites, lotteries, notes, now, chats ->
        val byCampsite = lotteries.groupBy { it.campsiteId }
        val all = sites.map { CampsiteWithLottery(it, currentLottery(byCampsite[it.id].orEmpty(), now)) }
        ExploreState(
            loading = false,
            featured = all.filter { it.lottery?.let { l -> l.isRegistrationOpen(now) || l.isUpcoming(now) } == true }
                .sortedBy { it.lottery!!.registrationEnd },
            all = all,
            tags = sites.flatMap { it.tags }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(6),
            unreadCount = notes.count { !it.read },
            unreadChats = chats.count { it.entrantId == container.userId && it.unreadFor(container.userId) },
            now = now,
        )
    }.stateIn(viewModelScope, sharing, ExploreState())
}

/** How far "Use my current location" looks around the phone. */
private const val NEAR_ME_KM = 50
