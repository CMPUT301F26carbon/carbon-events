/*
 * Owner.kt (Rayner, Aditya, Vaibhav, Suvir)
 *
 * Owner side: the bottom-nav shell and its routes. Each screen lives in its own file:
 * OwnerCampsites, CampsiteForm, ManageCampsite, Availability, CreateLottery, OwnerLotteries
 * (with the waitlist) and OwnerBookings.
 *
 * Draws also run in the background on the owner's phone (BackgroundSync.kt).
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.map

/** Owner route names. */
object OwnerRoutes {
    const val CAMPSITES = "owner/campsites"
    const val LOTTERIES = "owner/lotteries"
    const val BOOKINGS = "owner/bookings"
    const val PROFILE = "owner/profile"
    const val NEW = "owner/new"
    const val EDIT = "owner/edit/{id}"
    const val PUBLISHED = "owner/published/{id}"
    const val MANAGE = "owner/campsite/{id}"
    const val NEW_LOTTERY = "owner/lottery/new?campsiteId={campsiteId}"
    const val WAITLIST = "owner/waitlist/{id}"
    const val EDIT_LOTTERY = "owner/lottery/edit/{id}"
    const val CAMPSITE_BOOKINGS = "owner/bookings/{id}"
    const val INBOX = "owner/inbox"
    const val CHAT = "owner/chat/{id}"
    const val NOTIFY = "owner/notify/{id}"
    const val AVAILABILITY = "owner/availability/{id}"

    /** Route to edit campsite [id]. */
    fun edit(id: String) = "owner/edit/${arg(id)}"

    /** Route to the "your campsite is live" screen for [id]. */
    fun published(id: String) = "owner/published/${arg(id)}"

    /** Route to manage campsite [id]. */
    fun manage(id: String) = "owner/campsite/${arg(id)}"

    /** Route to a new lottery, optionally for [campsiteId]. */
    fun newLottery(campsiteId: String? = null) = "owner/lottery/new?campsiteId=${arg(campsiteId.orEmpty())}"

    /** Route to lottery [id]'s waiting list. */
    fun waitlist(id: String) = "owner/waitlist/${arg(id)}"

    /** Route to edit lottery [id]. */
    fun editLottery(id: String) = "owner/lottery/edit/${arg(id)}"

    /** Route to campsite [id]'s bookings. */
    fun campsiteBookings(id: String) = "owner/bookings/${arg(id)}"

    /** Route to a chat with a guest; remembers [chat] so the screen can start it. */
    fun chat(chat: Chat) = "owner/chat/${arg(ChatStarts.put(chat))}"

    /** Route to Notify waitlist for lottery [id]. */
    fun notify(id: String) = "owner/notify/${arg(id)}"

    /** Route to Availability & price for campsite [id]. */
    fun availability(id: String) = "owner/availability/${arg(id)}"

    // Lottery IDs look like "two-jack-lakeside-7f3k/nov-2026"; the slash must be escaped so it
    // stays one route argument (Navigation decodes it again).
    private fun arg(value: String) = android.net.Uri.encode(value)
}

private val tabs = listOf(
    NavItem(OwnerRoutes.CAMPSITES, "Campsites", R.drawable.ic_tent),
    NavItem(OwnerRoutes.LOTTERIES, "Lotteries", R.drawable.ic_ticket),
    NavItem(OwnerRoutes.BOOKINGS, "Bookings", R.drawable.ic_event_check),
    NavItem(OwnerRoutes.INBOX, "Inbox", R.drawable.ic_chat),
    NavItem(OwnerRoutes.PROFILE, "Profile", R.drawable.ic_person),
)

private val idArg = listOf(navArgument("id") { type = NavType.StringType })

/** The campsite owner experience. */
@Composable
fun OwnerShell(container: AppContainer, profile: UserProfile, profileVm: ProfileViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    TwilightSky {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            // Figma O-MANAGE keeps the nav bar, with Campsites selected.
            bottomBar = {
                val selected = if (route == OwnerRoutes.MANAGE) OwnerRoutes.CAMPSITES else route
                if (route in tabs.map { it.route } || route == OwnerRoutes.MANAGE) EventsNavBar(tabs, selected) { nav.goToTab(it.route) }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                NavHost(nav, startDestination = OwnerRoutes.CAMPSITES) {
                    composable(OwnerRoutes.CAMPSITES) {
                        OwnerCampsitesScreen(
                            container, profile, onAdd = { nav.navigate(OwnerRoutes.NEW) }, onOpen = { nav.navigate(OwnerRoutes.manage(it)) },
                            onBell = { nav.goToTab(OwnerRoutes.INBOX) },
                        )
                    }
                    composable(OwnerRoutes.NEW) {
                        CampsiteFormScreen(container, profile, null, onBack = { nav.popBackStack() }, onSaved = { id ->
                            nav.navigate(OwnerRoutes.published(id)) { popUpTo(OwnerRoutes.NEW) { inclusive = true } }
                        })
                    }
                    composable(OwnerRoutes.EDIT, arguments = idArg) {
                        val id = it.arguments?.getString("id").orEmpty()
                        CampsiteFormScreen(container, profile, id, onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack() })
                    }
                    composable(OwnerRoutes.PUBLISHED, arguments = idArg) {
                        val id = it.arguments?.getString("id").orEmpty()
                        PublishedScreen(container, id, onCreateLottery = { nav.navigate(OwnerRoutes.newLottery(id)) { popUpTo(OwnerRoutes.CAMPSITES) } }, onDone = {
                            nav.navigate(OwnerRoutes.manage(id)) { popUpTo(OwnerRoutes.CAMPSITES) }
                        })
                    }
                    composable(OwnerRoutes.MANAGE, arguments = idArg) {
                        val id = it.arguments?.getString("id").orEmpty()
                        ManageCampsiteScreen(
                            container, id, onBack = { nav.popBackStack() }, onEdit = { nav.navigate(OwnerRoutes.edit(id)) },
                            onCreateLottery = { nav.navigate(OwnerRoutes.newLottery(id)) }, onWaitlist = { lot -> nav.navigate(OwnerRoutes.waitlist(lot)) },
                            onBookings = { nav.navigate(OwnerRoutes.campsiteBookings(id)) },
                            onEditLottery = { lot -> nav.navigate(OwnerRoutes.editLottery(lot)) },
                            onNotify = { lot -> nav.navigate(OwnerRoutes.notify(lot)) },
                            onAvailability = { nav.navigate(OwnerRoutes.availability(id)) },
                        )
                    }
                    composable(OwnerRoutes.NEW_LOTTERY, arguments = listOf(navArgument("campsiteId") { type = NavType.StringType; defaultValue = "" })) {
                        val preset = it.arguments?.getString("campsiteId")?.takeIf { s -> s.isNotBlank() }
                        CreateLotteryScreen(container, preset, onBack = { nav.popBackStack() }, onPublished = { lotId ->
                            nav.navigate(OwnerRoutes.waitlist(lotId)) { popUpTo(OwnerRoutes.NEW_LOTTERY) { inclusive = true } }
                        })
                    }
                    composable(OwnerRoutes.LOTTERIES) {
                        OwnerLotteriesScreen(container, onOpen = { nav.navigate(OwnerRoutes.waitlist(it)) }, onNew = { nav.navigate(OwnerRoutes.newLottery()) })
                    }
                    composable(OwnerRoutes.EDIT_LOTTERY, arguments = idArg) {
                        CreateLotteryScreen(container, null, onBack = { nav.popBackStack() }, onPublished = { nav.popBackStack() }, lotteryId = it.arguments?.getString("id").orEmpty())
                    }
                    composable(OwnerRoutes.WAITLIST, arguments = idArg) {
                        WaitlistScreen(
                            container, it.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() },
                            onEdit = { lot -> nav.navigate(OwnerRoutes.editLottery(lot)) }, onNotify = { lot -> nav.navigate(OwnerRoutes.notify(lot)) },
                            onChat = { c -> nav.navigate(OwnerRoutes.chat(c)) }, ownerName = profile.name,
                        )
                    }
                    composable(OwnerRoutes.NOTIFY, arguments = idArg) { NotifyWaitlistScreen(container, it.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() }) }
                    composable(OwnerRoutes.AVAILABILITY, arguments = idArg) { AvailabilityScreen(container, it.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() }) }
                    composable(OwnerRoutes.BOOKINGS) { OwnerBookingsScreen(container, profile, onChat = { c -> nav.navigate(OwnerRoutes.chat(c)) }) }
                    composable(OwnerRoutes.CAMPSITE_BOOKINGS, arguments = idArg) {
                        OwnerBookingsScreen(container, profile, it.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() }, onChat = { c -> nav.navigate(OwnerRoutes.chat(c)) })
                    }
                    composable(OwnerRoutes.INBOX) { InboxScreen(container, asOwner = true, onOpen = { c -> nav.navigate(OwnerRoutes.chat(c)) }) }
                    composable(OwnerRoutes.CHAT, arguments = idArg) {
                        ChatStarts[it.arguments?.getString("id").orEmpty()]?.let { start -> ChatScreen(container, profile, start, onBack = { nav.popBackStack() }) }
                    }
                    profilePages(OwnerRoutes.PROFILE, nav, { profile }, profileVm)
                }
                NewMessageBannerHost(container, Modifier.align(Alignment.TopCenter)) { nav.navigate(OwnerRoutes.chat(it)) }
                OpenTappedChat(container) { nav.navigate(OwnerRoutes.chat(it)) }
            }
        }
    }
}

