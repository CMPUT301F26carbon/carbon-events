/*
 * Explore.kt (Ansh)
 *
 * Entrant side: the bottom-nav shell, Explore (home) and keyword search.
 */
package com.carbon.events

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
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.time.LocalTime
import kotlin.math.absoluteValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/** Entrant route names. */
object EntrantRoutes {
    const val EXPLORE = "explore"
    const val LOTTERIES = "lotteries"
    const val SCAN = "scan"
    const val BOOKINGS = "bookings"
    const val PROFILE = "profile"
    const val PROFILE_EDIT = "profile/edit"
    const val NOTIFICATIONS = "notifications"
    const val SEARCH = "search?q={q}"
    const val DETAILS = "campsite/{id}"

    /** Route to search, optionally pre-filled. */
    fun search(q: String = "") = "search?q=${android.net.Uri.encode(q)}"

    /** Route to a campsite's details. */
    fun details(id: String) = "campsite/$id"
}

private val tabs = listOf(
    NavItem(EntrantRoutes.EXPLORE, "Explore", R.drawable.ic_explore),
    NavItem(EntrantRoutes.LOTTERIES, "Lotteries", R.drawable.ic_ticket),
    NavItem(EntrantRoutes.SCAN, "Scan", R.drawable.ic_qr_scan, raised = true),
    NavItem(EntrantRoutes.BOOKINGS, "Bookings", R.drawable.ic_event_check),
    NavItem(EntrantRoutes.PROFILE, "Profile", R.drawable.ic_person),
)

/** Navigates to a top-level tab without stacking copies. */
fun NavHostController.goToTab(route: String) = navigate(route) {
    popUpTo(graph.startDestinationId) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/**
 * The entrant experience.
 *
 * @param deepLinkCampsiteId campsite to open from a promo QR link, if any.
 */
@Composable
fun EntrantShell(
    container: AppContainer,
    profile: UserProfile,
    profileVm: ProfileViewModel,
    deepLinkCampsiteId: String?,
    onDeepLinkConsumed: () -> Unit,
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    LaunchedEffect(deepLinkCampsiteId) {
        if (deepLinkCampsiteId != null) {
            nav.navigate(EntrantRoutes.details(deepLinkCampsiteId))
            onDeepLinkConsumed()
        }
    }
    TwilightSky(variant = if (route == EntrantRoutes.EXPLORE) SkyVariant.MOON else SkyVariant.SCREEN) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                // Figma E-NOTIFS keeps the nav bar with Lotteries selected.
                val selected = if (route == EntrantRoutes.NOTIFICATIONS) EntrantRoutes.LOTTERIES else route
                if (route in setOf(EntrantRoutes.EXPLORE, EntrantRoutes.LOTTERIES, EntrantRoutes.BOOKINGS, EntrantRoutes.PROFILE, EntrantRoutes.NOTIFICATIONS)) {
                    EventsNavBar(tabs, selected) { item -> if (item.raised) nav.navigate(item.route) else nav.goToTab(item.route) }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                NavHost(nav, startDestination = EntrantRoutes.EXPLORE) {
                    composable(EntrantRoutes.EXPLORE) {
                        ExploreScreen(
                            container, profile,
                            onSearch = { nav.navigate(EntrantRoutes.search(it)) },
                            onOpen = { nav.navigate(EntrantRoutes.details(it)) },
                            onNotifications = { nav.navigate(EntrantRoutes.NOTIFICATIONS) },
                            onProfile = { nav.goToTab(EntrantRoutes.PROFILE) },
                        )
                    }
                    composable(EntrantRoutes.SEARCH, arguments = listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" })) {
                        SearchScreen(container, it.arguments?.getString("q").orEmpty(), onBack = { nav.popBackStack() }, onOpen = { id -> nav.navigate(EntrantRoutes.details(id)) })
                    }
                    composable(EntrantRoutes.DETAILS, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                        val id = it.arguments?.getString("id").orEmpty()
                        CampsiteDetailsScreen(container, profile, id, onBack = { nav.popBackStack() }, onMyLotteries = { nav.goToTab(EntrantRoutes.LOTTERIES) })
                    }
                    composable(EntrantRoutes.LOTTERIES) {
                        MyLotteriesScreen(container, onOpen = { nav.navigate(EntrantRoutes.details(it)) }, onExplore = { nav.goToTab(EntrantRoutes.EXPLORE) }, onNotifications = { nav.navigate(EntrantRoutes.NOTIFICATIONS) })
                    }
                    composable(EntrantRoutes.NOTIFICATIONS) {
                        NotificationsScreen(container, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(EntrantRoutes.details(it)) })
                    }
                    composable(EntrantRoutes.SCAN) {
                        ScanScreen(onClose = { nav.popBackStack() }, onCampsite = { id ->
                            nav.navigate(EntrantRoutes.details(id)) { popUpTo(EntrantRoutes.SCAN) { inclusive = true } }
                        }, campsite = container.campsites::observeCampsite)
                    }
                    composable(EntrantRoutes.BOOKINGS) {
                        TabPlaceholder("Bookings", R.drawable.ic_event_check, "No bookings yet", "When you win a lottery and book your dates, your reservations will show up here.")
                    }
                    composable(EntrantRoutes.PROFILE) { ProfileScreen(profile, profileVm, onEdit = { nav.navigate(EntrantRoutes.PROFILE_EDIT) }) }
                    composable(EntrantRoutes.PROFILE_EDIT) { EditProfileScreen(profile, profileVm, onDone = { nav.popBackStack() }) }
                }
            }
        }
    }
}

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
) {
    val vm = containerViewModel(container) { ExploreViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().testTag("explore"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = PageMargin).padding(top = 8.dp)) {
                // Top row: bell + profile chip (Figma "Top").
                Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(R.drawable.ic_bell, "Notifications", onNotifications, badge = state.unreadCount > 0)
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier
                            .height(46.dp)
                            .clip(EventsShapes.Pill)
                            .background(EventsColors.Tint)
                            .border(1.dp, EventsColors.TintStroke, EventsShapes.Pill)
                            .padding(start = 4.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Avatar(profile.initials, size = 36.dp)
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
                SearchBar("", {}, onClick = { onSearch("") }, modifier = Modifier.testTag("explore_search"))
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
        if (state.featured.isNotEmpty()) {
            item { SectionTitle("Lotteries Near Me", Modifier.padding(horizontal = PageMargin)) }
            item {
                val pager = rememberPagerState { state.featured.size }
                BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    val side = ((maxWidth - 290.dp) / 2).coerceAtLeast(PageMargin)
                    HorizontalPager(pager, pageSize = PageSize.Fixed(290.dp), contentPadding = PaddingValues(horizontal = side), pageSpacing = 12.dp) { page ->
                        val f = state.featured[page]
                        val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                        FeaturedLotteryCard(
                            f.campsite, f.lottery!!, state.now, { onOpen(f.campsite.id) },
                            Modifier.graphicsLayer {
                                val scale = 1f - 0.14f * offset
                                scaleX = scale; scaleY = scale; alpha = 1f - 0.45f * offset
                            },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 20.dp), horizontalArrangement = Arrangement.Center) {
                    CarouselDots(state.featured.size, pager.currentPage)
                }
            }
        }
        if (!state.loading) item { SectionTitle("All Campsites", Modifier.padding(horizontal = PageMargin).padding(bottom = 14.dp)) }
        if (!state.loading && state.all.isEmpty()) {
            item { EmptyState(R.drawable.ic_tent, "No campsites yet", "Owners haven't listed any campsites. Check back soon.") }
        }
        items(state.all, key = { it.campsite.id }) { item ->
            CampsiteListCard(item.campsite, { onOpen(item.campsite.id) }, Modifier.padding(horizontal = PageMargin).padding(bottom = 10.dp))
        }
    }
}

/**
 * Keyword search (Figma E-SEARCH, US 01.01.01). Results show each campsite's summary
 * (US 01.01.04).
 *
 * @param initialQuery text to start with (e.g. a vibe tag from Explore).
 */
@Composable
fun SearchScreen(container: AppContainer, initialQuery: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val vm = containerViewModel(container, key = "search") { SearchViewModel(it, initialQuery) }
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (initialQuery.isEmpty()) runCatching { focus.requestFocus() } }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassIconButton(R.drawable.ic_arrow_back, "Back", onBack)
                SearchBar(state.query, vm::onQueryChange, focusRequester = focus)
            }
        }
        item {
            Text(
                if (state.query.isBlank()) "${state.results.size} campsites" else "${state.results.size} campsites for “${state.query}”",
                style = EventsType.LabelSmall, color = EventsColors.TextSoft, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp).testTag("result_count"),
            )
        }
        if (!state.loading && state.results.isEmpty()) {
            item { EmptyState(R.drawable.ic_search, "No campsites found", "Try another name, town, or vibe such as “Lakeside”.") }
        }
        items(state.results, key = { it.id }) { site -> CampsiteListCard(site, { onOpen(site.id) }) }
    }
}

private val sharing = SharingStarted.WhileSubscribed(5_000)

/** The lottery entrants should see for a campsite: the newest one. */
fun currentLottery(lotteries: List<Lottery>): Lottery? = lotteries.maxByOrNull { it.registrationStart }

/** A campsite with its current lottery, if any. */
data class CampsiteWithLottery(val campsite: Campsite, val lottery: Lottery?)

/** State of the Explore (home) screen. */
data class ExploreState(
    val loading: Boolean = true,
    val featured: List<CampsiteWithLottery> = emptyList(),
    val all: List<CampsiteWithLottery> = emptyList(),
    val tags: List<String> = emptyList(),
    val unreadCount: Int = 0,
    val now: Long = 0L,
)

/** Home feed: campsites with open lotteries first, then everything. */
class ExploreViewModel(container: AppContainer) : ViewModel() {
    /** Explore screen state. */
    val state: StateFlow<ExploreState> = combine(
        container.campsites.observeCampsites(),
        container.lotteries.observeLotteries(),
        container.notifications.observeNotifications(container.deviceId),
        nowTicker(container.clock),
    ) { sites, lotteries, notes, now ->
        val byCampsite = lotteries.groupBy { it.campsiteId }
        val all = sites.map { CampsiteWithLottery(it, currentLottery(byCampsite[it.id].orEmpty())) }
        ExploreState(
            loading = false,
            featured = all.filter { it.lottery?.let { l -> l.isRegistrationOpen(now) || l.isUpcoming(now) } == true }
                .sortedBy { it.lottery!!.registrationEnd },
            all = all,
            tags = sites.flatMap { it.tags }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(6),
            unreadCount = notes.count { !it.read },
            now = now,
        )
    }.stateIn(viewModelScope, sharing, ExploreState())
}

/** State of the Search screen. */
data class SearchState(val query: String = "", val results: List<Campsite> = emptyList(), val loading: Boolean = true)

/** Keyword search over all campsites (US 01.01.01). */
class SearchViewModel(container: AppContainer, initialQuery: String = "") : ViewModel() {
    private val query = MutableStateFlow(initialQuery)

    /** Search screen state; results update as the user types. */
    val state: StateFlow<SearchState> = combine(container.campsites.observeCampsites(), query) { sites, q ->
        SearchState(q, CampsiteSearch.search(sites, q), loading = false)
    }.stateIn(viewModelScope, sharing, SearchState(initialQuery))

    /** Updates the search text. */
    fun onQueryChange(q: String) { query.value = q }
}
