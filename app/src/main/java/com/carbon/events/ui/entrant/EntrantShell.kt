/*
 * EntrantShell.kt (Ansh)
 *
 * The entrant's bottom-nav shell and routes, including booking dates after a win and the
 * Bookings tab.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.map

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
                            onChat = { nav.navigate(EntrantRoutes.chat(it)) },
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
                NewMessageBannerHost(container, Modifier.align(Alignment.TopCenter)) { nav.navigate(EntrantRoutes.chat(it)) }
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
