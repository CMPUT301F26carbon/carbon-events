/*
 * Explore.kt (Ansh)
 *
 * Entrant side: the bottom-nav shell, Explore (home) and keyword search. The shell also
 * routes to booking dates after a win and to the Bookings tab.
 *
 * Outstanding issues: "Lotteries Near Me" lists every open lottery; there is no location
 * filter yet (US 01.01.02 is not in this release).
 */
package com.carbon.events

import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.fillMaxHeight

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
    const val SEARCH = "search?q={q}&filters={filters}"
    const val DETAILS = "campsite/{id}"
    const val BOOK = "book/{id}"
    const val RESERVATION = "reservation/{id}"
    const val CHAT = "chat/{id}"
    const val MESSAGES = "messages"
    const val MAP = "map"

    /** Route to pick dates for a won lottery. */
    fun book(lotteryId: String) = "book/${android.net.Uri.encode(lotteryId)}" // ID contains a "/"

    /** Route to search, optionally pre-filled, optionally opening Filters straight away. */
    fun search(q: String = "", filters: Boolean = false) = "search?q=${android.net.Uri.encode(q)}&filters=$filters"

    /** Route to a campsite's details. */
    fun details(id: String) = "campsite/${android.net.Uri.encode(id)}"

    /** Route to a reservation (the lottery ID). */
    fun reservation(lotteryId: String) = "reservation/${android.net.Uri.encode(lotteryId)}"

    /** Route to a chat; remembers [chat] so the screen can start it. */
    fun chat(chat: Chat) = "chat/${android.net.Uri.encode(ChatStarts.put(chat))}"
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
    val here by DeviceLocation.location.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { DeviceLocation.refresh(context) }
    androidx.compose.runtime.CompositionLocalProvider(LocalDeviceLocation provides here) {
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
                            onFilters = { nav.navigate(EntrantRoutes.search(filters = true)) },
                        )
                    }
                    composable(EntrantRoutes.MAP) { CampsiteMapScreen(container, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(EntrantRoutes.details(it)) }) }
                    composable(EntrantRoutes.MESSAGES) {
                        InboxScreen(container, asOwner = false, onOpen = { nav.navigate(EntrantRoutes.chat(it)) }, onBack = { nav.popBackStack() })
                    }
                    composable(EntrantRoutes.CHAT, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                        val id = it.arguments?.getString("id").orEmpty()
                        ChatStarts[id]?.let { start -> ChatScreen(container, profile, start, onBack = { nav.popBackStack() }) }
                    }
                    composable(EntrantRoutes.RESERVATION, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                        ReservationScreen(
                            container, profile, it.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() },
                            onCampsite = { id -> nav.navigate(EntrantRoutes.details(id)) }, onChat = { c -> nav.navigate(EntrantRoutes.chat(c)) },
                        )
                    }
                    composable(
                        EntrantRoutes.SEARCH,
                        arguments = listOf(
                            navArgument("q") { type = NavType.StringType; defaultValue = "" },
                            navArgument("filters") { type = NavType.BoolType; defaultValue = false },
                        ),
                    ) {
                        SearchScreen(
                            container, it.arguments?.getString("q").orEmpty(), onBack = { nav.popBackStack() }, onOpen = { id -> nav.navigate(EntrantRoutes.details(id)) },
                            openFilters = it.arguments?.getBoolean("filters") ?: false,
                        )
                    }
                    composable(EntrantRoutes.DETAILS, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                        val id = it.arguments?.getString("id").orEmpty()
                        CampsiteDetailsScreen(
                            container, profile, id, onBack = { nav.popBackStack() }, onMyLotteries = { nav.goToTab(EntrantRoutes.LOTTERIES) },
                            onBook = { lot -> nav.navigate(EntrantRoutes.book(lot)) }, onChat = { c -> nav.navigate(EntrantRoutes.chat(c)) },
                            onReservation = { lot -> nav.navigate(EntrantRoutes.reservation(lot)) },
                            onMap = { nav.navigate(EntrantRoutes.MAP) },
                        )
                    }
                    composable(EntrantRoutes.LOTTERIES) {
                        MyLotteriesScreen(
                            container, onOpen = { nav.navigate(EntrantRoutes.details(it)) }, onExplore = { nav.goToTab(EntrantRoutes.EXPLORE) },
                            onNotifications = { nav.navigate(EntrantRoutes.NOTIFICATIONS) }, onBook = { nav.navigate(EntrantRoutes.book(it)) },
                        )
                    }
                    composable(EntrantRoutes.BOOK, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                        BookDatesScreen(
                            container, profile, it.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() },
                            onBooked = { nav.navigate(EntrantRoutes.BOOKINGS) { popUpTo(EntrantRoutes.LOTTERIES) } },
                            onReservation = { lot -> nav.navigate(EntrantRoutes.reservation(lot)) { popUpTo(EntrantRoutes.LOTTERIES) } },
                        )
                    }
                    composable(EntrantRoutes.NOTIFICATIONS) {
                        NotificationsScreen(
                            container, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(EntrantRoutes.details(it)) },
                            onChat = { nav.navigate(EntrantRoutes.chat(it)) }, onManage = { nav.goToTab(EntrantRoutes.PROFILE) },
                        )
                    }
                    composable(EntrantRoutes.SCAN) {
                        ScanScreen(onClose = { nav.popBackStack() }, onCampsite = { id ->
                            nav.navigate(EntrantRoutes.details(id)) { popUpTo(EntrantRoutes.SCAN) { inclusive = true } }
                        }, campsite = container.campsites::observeCampsite)
                    }
                    composable(EntrantRoutes.BOOKINGS) {
                        EntrantBookingsScreen(
                            container, onOpen = { nav.navigate(EntrantRoutes.reservation(it)) }, onExplore = { nav.goToTab(EntrantRoutes.EXPLORE) },
                            onChat = { nav.navigate(EntrantRoutes.chat(it)) }, profile = profile,
                        )
                    }
                    profilePages(EntrantRoutes.PROFILE, nav, { profile }, profileVm)
                }
            }
        }
    }
    }
}


/** Chats being opened, by ID, so a route only needs to carry the ID. */
object ChatStarts {
    private val open = mutableMapOf<String, Chat>()

    /** The chat for [id], if it was opened in this run of the app. */
    operator fun get(id: String): Chat? = open[id]

    /** Remembers [chat] and returns its ID. */
    fun put(chat: Chat): String { open[chat.id] = chat; return chat.id }
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
    onFilters: () -> Unit = {},
) {
    val vm = containerViewModel(container) { ExploreViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val here = LocalDeviceLocation.current
    val context = androidx.compose.ui.platform.LocalContext.current
    // No pop-up on launch: location is asked for only when you tap "Use my current location" or
    // open the map. Lotteries Near Me uses it if it's already allowed.
    LaunchedEffect(Unit) { if (DeviceLocation.hasPermission(context)) DeviceLocation.refresh(context) }
    val featured = remember(state.featured, here) { nearestFirst(state.featured, here) { it.campsite } }
    LazyColumn(Modifier.fillMaxSize().testTag("explore"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = PageMargin).padding(top = 8.dp)) {
                // Top row: bell + profile chip (Figma "Top"). Owner messages arrive as notifications.
                Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(R.drawable.ic_bell, "Notifications", onNotifications, badge = state.unreadCount > 0 || state.unreadChats > 0)
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
                    Text(if (here != null) "Nearest first" else "Closing soonest", style = EventsType.Caption, color = EventsColors.TextSoft)
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

/**
 * Keyword search with filters (Figma E-SEARCH, E-SEARCH-FILTERED, E-FILTERS, US 01.01.01-03).
 * Results show each campsite's summary (US 01.01.04).
 *
 * @param initialQuery text to start with (e.g. a vibe tag from Explore).
 * @param openFilters opens the Filters sheet straight away (Explore's filter button).
 */
@Composable
fun SearchScreen(container: AppContainer, initialQuery: String, onBack: () -> Unit, onOpen: (String) -> Unit, openFilters: Boolean = false) {
    val vm = containerViewModel(container, key = "search") { SearchViewModel(it, initialQuery) }
    val state by vm.state.collectAsStateWithLifecycle()
    val here = LocalDeviceLocation.current
    LaunchedEffect(here) { vm.onLocation(here) }
    val focus = remember { FocusRequester() }
    var filtersOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(openFilters) }
    LaunchedEffect(Unit) { if (initialQuery.isEmpty() && !openFilters) runCatching { focus.requestFocus() } }
    val f = state.filters
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassIconButton(R.drawable.ic_arrow_back, "Back", onBack)
                SearchBar(state.query, vm::onQueryChange, Modifier.weight(1f), focusRequester = focus, onFilters = { filtersOpen = true }, filtersOn = f.count > 0)
            }
        }
        // Figma quick-filter chips: each opens the Filters sheet.
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("search_chips")) {
                item {
                    val dates = if (f.stayFrom != null && f.stayUntil != null) formatStay(f.stayFrom, f.stayUntil).substringBefore(" ·") else "Any dates"
                    EventsChip(dates, if (f.stayFrom != null) ChipStyle.SELECTED else ChipStyle.GLASS, { filtersOpen = true }, icon = R.drawable.ic_calendar)
                }
                item { EventsChip(f.guests?.let { "$it guests" } ?: "Guests", if (f.guests != null) ChipStyle.SELECTED else ChipStyle.GLASS, { filtersOpen = true }, icon = R.drawable.ic_group) }
                item { EventsChip(f.tags.firstOrNull()?.let { if (f.tags.size > 1) "$it +${f.tags.size - 1}" else it } ?: "Vibe", if (f.tags.isNotEmpty()) ChipStyle.SELECTED else ChipStyle.GLASS, { filtersOpen = true }, icon = R.drawable.ic_tent) }
                item { EventsChip(f.amenities.firstOrNull()?.let { if (f.amenities.size > 1) "$it +${f.amenities.size - 1}" else it } ?: "Amenities", if (f.amenities.isNotEmpty()) ChipStyle.SELECTED else ChipStyle.GLASS, { filtersOpen = true }, icon = R.drawable.ic_fire) }
            }
        }
        item {
            val near = f.location?.let { " near $it" } ?: if (state.query.isNotBlank()) " for “${state.query}”" else ""
            Text(
                "${state.results.size} campsite${if (state.results.size == 1) "" else "s"}$near",
                style = EventsType.LabelSmall, color = EventsColors.TextSoft, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp).testTag("result_count"),
            )
        }
        if (!state.loading && state.results.isEmpty()) {
            item { EmptyState(R.drawable.ic_search, "No campsites found", "Try another name, town or vibe such as “Lakeside”, or loosen the filters.") }
        }
        items(state.results, key = { it.id }) { site ->
            CampsiteListCard(site, { onOpen(site.id) }, distance = distanceKm(here, site)?.let { "${formatKm(it)} away" })
        }
    }
    if (filtersOpen) FiltersDialog(
        f, state.allTags, state.allLocations, count = vm::countFor,
        onDismiss = { filtersOpen = false }, onApply = { vm.onFilters(it); filtersOpen = false },
    )
}

/** Short labels for the filters that are on. */
fun filterSummary(f: SearchFilters): List<String> = buildList {
    f.location?.let { add(it) }
    if (f.stayFrom != null && f.stayUntil != null) add(formatStay(f.stayFrom, f.stayUntil).substringBefore(" ·"))
    f.guests?.let { add("$it guests") }
    addAll(f.tags)
    addAll(f.amenities)
    if (f.sort != SearchSort.BEST) add(f.sort.label)
    f.maxPrice?.let { add("Under $$it") }
    f.withinKm?.let { add("Within $it km") }
    if (f.openOnly) add("Open lotteries")
}

/**
 * Filters (Figma E-FILTERS): a sheet over the results with Location, Dates, Guests, Vibe and
 * Amenities, Reset, and "Show N campsites".
 *
 * @param count how many campsites the given filters would show.
 */
@Composable
fun FiltersDialog(
    current: SearchFilters,
    tags: List<String>,
    locations: List<String>,
    count: (SearchFilters) -> Int,
    onDismiss: () -> Unit,
    onApply: (SearchFilters) -> Unit,
) {
    var f by remember { mutableStateOf(current) }
    var pickLocation by remember { mutableStateOf(false) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        FiltersSheet(f, { f = it }, tags, locations, count(f), pickLocation, { pickLocation = it }, onApply)
    }
}

/** The Filters sheet itself (separate so it can be previewed and screenshotted). */
@Composable
fun FiltersSheet(
    f: SearchFilters,
    onChange: (SearchFilters) -> Unit,
    tags: List<String>,
    locations: List<String>,
    resultCount: Int,
    pickLocation: Boolean,
    onPickLocation: (Boolean) -> Unit,
    onApply: (SearchFilters) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(EventsColors.SkyTop.copy(alpha = 0.55f)), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.86f).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(EventsColors.Surface)
                .navigationBarsPadding().testTag("filters"),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 40.dp, height = 4.dp).clip(EventsShapes.Pill).background(EventsColors.TextTertiary))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = PageMargin, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", style = EventsType.Headline, color = EventsColors.Text, modifier = Modifier.weight(1f))
                TextLinkButton("Reset", { onChange(SearchFilters()) }, Modifier.testTag("filters_reset"))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PageMargin), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LocationField(f, onChange, locations, pickLocation, onPickLocation)
                DateRangeField(
                    "Dates", f.stayFrom, f.stayUntil, { a, b -> onChange(f.copy(stayFrom = a, stayUntil = b)) },
                    placeholder = "Any dates", testTag = "filter_dates", isSelectable = { it >= dayOf(System.currentTimeMillis()) },
                )
                Stepper(f.guests ?: 1, { onChange(f.copy(guests = it.takeIf { n -> n > 1 })) }, 1, CampsiteValidator.MAX_OCCUPANCY, "Guests", supporting = "Group size incl. you", modifier = Modifier.testTag("filter_guests"))
                if (tags.isNotEmpty()) {
                    FilterLabel("Vibe")
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { t ->
                            val on = t in f.tags
                            EventsChip(t, if (on) ChipStyle.CORAL else ChipStyle.DUSK, { onChange(f.copy(tags = if (on) f.tags - t else f.tags + t)) })
                        }
                    }
                }
                FilterLabel("Amenities")
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AMENITY_FILTERS.keys.forEach { a ->
                        val on = a in f.amenities
                        EventsChip(a, if (on) ChipStyle.CORAL else ChipStyle.DUSK, { onChange(f.copy(amenities = if (on) f.amenities - a else f.amenities + a)) }, icon = vibeIcon(a))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = PageMargin).padding(top = 8.dp, bottom = 16.dp)) {
                PrimaryButton(
                    "Show $resultCount campsite${if (resultCount == 1) "" else "s"}", { onApply(f) },
                    Modifier.fillMaxWidth().testTag("filters_apply"),
                )
            }
        }
    }
}

@Composable
private fun FilterLabel(text: String) = Text(text, style = EventsType.LabelSmall, color = EventsColors.TextSoft)

/** A field-looking button with an icon, value and dropdown chevron (Figma Location). */
@Composable
private fun PickerField(icon: Int, value: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(EventsShapes.Medium).background(EventsColors.SurfaceSoft)
            .border(1.dp, EventsColors.TintStroke, EventsShapes.Medium).clickable(onClick = onClick).padding(horizontal = 14.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EventsIcon(icon, size = 18.dp, tint = EventsColors.TextSecondary)
        Text(value, style = EventsType.Body, color = EventsColors.Text, modifier = Modifier.weight(1f))
        EventsIcon(R.drawable.ic_chevron_down, size = 18.dp, tint = EventsColors.TextSecondary)
    }
}

/**
 * Every campsite with a pin on a full-screen map (Figma E-MAP). Tapping a pin's label opens it.
 */
@Composable
fun CampsiteMapScreen(container: AppContainer, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val sites by container.campsites.observeCampsites().collectAsStateWithLifecycle(emptyList())
    val here = LocalDeviceLocation.current
    val pinned = sites.filter { it.hasMapLocation }
    Box(Modifier.fillMaxSize().testTag("campsite_map")) {
        val center = here?.let { it.latitude to it.longitude }
            ?: pinned.firstOrNull()?.let { it.latitude!! to it.longitude!! }
            ?: (53.5461 to -113.4938)
        OsmMap(
            center.first, center.second, 6.0,
            pinned.map { MapPin(it.id, it.latitude!!, it.longitude!!, it.name, "$${it.pricePerNight}/night · tap again to open") },
            Modifier.fillMaxSize(), onPinOpen = onOpen,
        )
        Row(Modifier.statusBarsPadding().padding(horizontal = PageMargin, vertical = 8.dp)) { GlassIconButton(R.drawable.ic_arrow_back, "Back", onBack) }
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
        val all = sites.map { CampsiteWithLottery(it, currentLottery(byCampsite[it.id].orEmpty())) }
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

/** State of the Search screen. */
data class SearchState(
    val query: String = "",
    val results: List<Campsite> = emptyList(),
    val loading: Boolean = true,
    val filters: SearchFilters = SearchFilters(),
    val allTags: List<String> = emptyList(),
    val allLocations: List<String> = emptyList(),
)

/** Keyword search with filters over all campsites (US 01.01.01). */
class SearchViewModel(private val container: AppContainer, initialQuery: String = "") : ViewModel() {
    private val query = MutableStateFlow(initialQuery)
    private val filters = MutableStateFlow(SearchFilters())
    private val here = MutableStateFlow<GeoPoint?>(null)

    /** Search screen state; results update as the user types or changes filters. */
    val state: StateFlow<SearchState> = combine(
        container.campsites.observeCampsites(), container.lotteries.observeLotteries(), query, filters, here,
    ) { sites, lotteries, q, f, loc ->
        val byCampsite = lotteries.groupBy { it.campsiteId }
        val found = CampsiteFilter.apply(CampsiteSearch.search(sites, q), f, { currentLottery(byCampsite[it].orEmpty()) }, loc, container.clock.now())
        latest = Triple(sites, byCampsite, loc)
        SearchState(
            q, found, loading = false, filters = f, allTags = sites.flatMap { it.tags }.distinct().sorted(),
            allLocations = sites.map { it.location.trim() }.filter { it.isNotEmpty() }.distinct().sorted(),
        )
    }.stateIn(viewModelScope, sharing, SearchState(initialQuery))

    private var latest: Triple<List<Campsite>, Map<String, List<Lottery>>, GeoPoint?> = Triple(emptyList(), emptyMap(), null)

    /** How many campsites [f] would show for the current search (Figma "Show 7 campsites"). */
    fun countFor(f: SearchFilters): Int {
        val (sites, byCampsite, loc) = latest
        return CampsiteFilter.apply(CampsiteSearch.search(sites, query.value), f, { currentLottery(byCampsite[it].orEmpty()) }, loc, container.clock.now()).size
    }

    /** Updates the search text. */
    fun onQueryChange(q: String) { query.value = q }

    /** Applies new filters. */
    fun onFilters(f: SearchFilters) { filters.value = f }

    /** The phone's location, for distance filters and sorting. */
    fun onLocation(p: GeoPoint?) { here.value = p }
}

/** Full province or territory name for a two-letter code ("AB" → "Alberta"). */
private val PROVINCES = mapOf(
    "AB" to "Alberta", "BC" to "British Columbia", "MB" to "Manitoba", "NB" to "New Brunswick", "NL" to "Newfoundland and Labrador",
    "NS" to "Nova Scotia", "NT" to "Northwest Territories", "NU" to "Nunavut", "ON" to "Ontario", "PE" to "Prince Edward Island",
    "QC" to "Quebec", "SK" to "Saskatchewan", "YT" to "Yukon",
)

/** "Banff, AB" → ("Banff", "Alberta"); anything else is shown as it is. */
internal fun placeParts(location: String): Pair<String, String> {
    val parts = location.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size < 2) return location.trim() to ""
    val region = parts.last()
    return parts.dropLast(1).joinToString(", ") to (PROVINCES[region.uppercase()] ?: region)
}

/** How far "Use my current location" looks around the phone. */
private const val NEAR_ME_KM = 50

/**
 * Figma E-FILTERS-LOCATION: a box you type in; the list underneath narrows as you type, starts
 * with "Use my current location", and shows each place with its province.
 */
@Composable
private fun LocationField(
    f: SearchFilters,
    onChange: (SearchFilters) -> Unit,
    locations: List<String>,
    open: Boolean,
    onOpen: (Boolean) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val nearMe = f.withinKm != null && f.location == null
    val shown = when {
        nearMe -> "Near me · within ${f.withinKm} km"
        else -> f.location.orEmpty()
    }
    var typed by remember(shown) { mutableStateOf(shown) }
    // "Use my current location" asks for the location only now, when you choose it.
    val askLocation = rememberLocationRequest()
    val matches = remember(typed, locations, shown) {
        if (typed.isBlank() || typed == shown) locations else locations.filter { it.contains(typed.trim(), ignoreCase = true) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EventsTextField(
            typed, { typed = it; onOpen(true) }, "Location",
            Modifier.fillMaxWidth().onFocusChanged { if (it.hasFocus) onOpen(true) }.testTag("filter_location"),
            placeholder = "Anywhere", leadingIcon = R.drawable.ic_location,
        )
        if (open) DuskCard(Modifier.fillMaxWidth().heightIn(max = 280.dp), padding = PaddingValues(6.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                LocationRow(R.drawable.ic_location, "Use my current location", null, selected = nearMe, tag = "filter_location_near_me") {
                    if (!DeviceLocation.hasPermission(context)) askLocation() else DeviceLocation.refresh(context)
                    onChange(f.copy(location = null, withinKm = NEAR_ME_KM, sort = SearchSort.NEAREST)); onOpen(false)
                }
                CardDivider()
                if (typed.isBlank()) LocationRow(null, "Anywhere", null, selected = f.location == null && !nearMe, tag = "filter_location_any") {
                    onChange(f.copy(location = null, withinKm = null)); onOpen(false)
                }
                matches.forEach { loc ->
                    val (place, region) = placeParts(loc)
                    LocationRow(null, place, region.ifBlank { null }, selected = f.location == loc, tag = "filter_location_$loc") {
                        onChange(f.copy(location = loc, withinKm = null)); onOpen(false)
                    }
                }
                if (matches.isEmpty()) Text(
                    "No campsites in “${typed.trim()}”.", style = EventsType.BodySmall, color = EventsColors.TextSecondary,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }
}

@Composable
private fun LocationRow(icon: Int?, title: String, subtitle: String?, selected: Boolean, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(EventsShapes.Medium).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) EventsIcon(icon, size = 18.dp, tint = EventsColors.Coral)
        Column(Modifier.weight(1f)) {
            Text(title, style = EventsType.Body, color = if (selected) EventsColors.Coral else EventsColors.Text)
            if (subtitle != null) Text(subtitle, style = EventsType.Caption, color = EventsColors.TextSecondary)
        }
        if (selected) EventsIcon(R.drawable.ic_check, size = 18.dp, tint = EventsColors.Coral)
    }
}
