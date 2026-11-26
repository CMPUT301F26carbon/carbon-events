/*
 * Admin.kt (Tausif)
 *
 * Admin console: the shell, browsing every campsite and removing one after confirming.
 *
 * Outstanding issues: admin is granted only by setting isAdmin in the Firebase console.
 */
package com.carbon.events

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private object AdminRoutes {
    const val CAMPSITES = "admin/campsites"
    const val CAMPSITE = "admin/campsite/{id}"
    const val PROFILES = "admin/profiles"
    const val PROFILE_DETAIL = "admin/profiles/{id}"
    const val PROFILE = "admin/profile"
    const val IMAGES = "admin/images"
    const val LOGS = "admin/logs"
    const val CHAT_LOG = "admin/logs/chat/{id}"
    fun campsite(id: String) = "admin/campsite/${android.net.Uri.encode(id)}"
    fun profileDetail(id: String) = "admin/profiles/${android.net.Uri.encode(id)}"
    fun chatLog(chat: Chat) = "admin/logs/chat/${android.net.Uri.encode(ChatStarts.put(chat))}"
}

private val tabs = listOf(
    NavItem(AdminRoutes.CAMPSITES, "Campsites", R.drawable.ic_tent),
    NavItem(AdminRoutes.PROFILES, "Profiles", R.drawable.ic_group),
    NavItem(AdminRoutes.IMAGES, "Images", R.drawable.ic_image),
    NavItem(AdminRoutes.LOGS, "Logs", R.drawable.ic_list),
    NavItem(AdminRoutes.PROFILE, "Account", R.drawable.ic_person),
)

/** The administrator experience. */
@Composable
fun AdminShell(container: AppContainer, profile: UserProfile, profileVm: ProfileViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    var removedName by remember { mutableStateOf<String?>(null) }
    TwilightSky {
        Scaffold(containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = { if (route in tabs.map { it.route }) EventsNavBar(tabs, route) { nav.goToTab(it.route) } }) { padding ->
            Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                NavHost(nav, startDestination = AdminRoutes.CAMPSITES) {
                    composable(AdminRoutes.CAMPSITES) { AdminCampsitesScreen(container, removedName, { removedName = null }) { nav.navigate(AdminRoutes.campsite(it)) } }
                    composable(AdminRoutes.CAMPSITE, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                        AdminCampsiteScreen(container, it.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() }, onRemoved = { name ->
                            removedName = name
                            nav.popBackStack()
                        })
                    }
                    composable(AdminRoutes.PROFILES) {
                        AdminProfilesScreen(container, onOpen = { id -> nav.navigate(AdminRoutes.profileDetail(id)) })
                    }
                    composable(AdminRoutes.PROFILE_DETAIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                        AdminProfileDetailScreen(container, it.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
                    }
                    composable(AdminRoutes.IMAGES) { AdminImagesScreen(container) }
                    composable(AdminRoutes.LOGS) { AdminLogsScreen(container, onChat = { c -> nav.navigate(AdminRoutes.chatLog(c)) }) }
                    composable(AdminRoutes.CHAT_LOG, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                        ChatStarts[it.arguments?.getString("id").orEmpty()]?.let { c -> AdminChatLogScreen(container, c, onBack = { nav.popBackStack() }) }
                    }
                    profilePages(AdminRoutes.PROFILE, nav, { profile }, profileVm)
                }
            }
        }
    }
}

/** Admin console: browse and search every campsite (US 03.04.01). */
@Composable
private fun AdminCampsitesScreen(container: AppContainer, removedName: String?, onBannerShown: () -> Unit, onOpen: (String) -> Unit) {
    val vm = containerViewModel(container) { AdminCampsitesViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(removedName) { if (removedName != null) { kotlinx.coroutines.delay(4_000); onBannerShown() } }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val shown = when (filter) {
        1 -> s.items.filter { it.lottery?.isRegistrationOpen(s.now) == true }
        2 -> s.items.filter { (s.flags[it.campsite.id] ?: 0) > 0 }.sortedByDescending { s.flags[it.campsite.id] ?: 0 }
        else -> s.items
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().testTag("admin_campsites"), contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            // Figma header: shield tile, "Admin console" over "Campsites", total tag.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(44.dp).clip(EventsShapes.Medium).background(EventsColors.SkyTop.copy(alpha = 0.6f)).border(1.dp, EventsColors.TintStroke, EventsShapes.Medium),
                    contentAlignment = Alignment.Center,
                ) { EventsIcon(R.drawable.ic_shield, size = 22.dp) }
                Column(Modifier.weight(1f)) {
                    Text("Admin console", style = EventsType.Caption, color = EventsColors.TextSoft)
                    Text("Campsites", style = EventsType.Headline, color = EventsColors.Text)
                }
                StatusTag("${s.total} total", TagTone.BLUE)
            }
        }
        item { SearchBar(s.query, vm::onQueryChange, placeholder = "Search campsites or owners…") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EventsChip("All", if (filter == 0) ChipStyle.CORAL else ChipStyle.GLASS, { filter = 0 })
                EventsChip("Live lotteries", if (filter == 1) ChipStyle.CORAL else ChipStyle.GLASS, { filter = 1 }, icon = R.drawable.ic_ticket)
                EventsChip("Flagged · ${s.flags.size}", if (filter == 2) ChipStyle.CORAL else ChipStyle.GLASS, { filter = 2 }, Modifier.testTag("admin_flagged"), icon = R.drawable.ic_warning)
            }
        }
        if (removedName != null) item {
            Row(Modifier.fillMaxWidth().clip(EventsShapes.Medium).background(EventsColors.GreenContainer).padding(14.dp).testTag("removed_banner"), verticalAlignment = Alignment.CenterVertically) {
                EventsIcon(R.drawable.ic_check, tint = EventsColors.Green, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text("$removedName was removed.", style = EventsType.BodyStrong, color = EventsColors.Green)
            }
        }
        if (!s.loading && shown.isEmpty()) item { EmptyState(R.drawable.ic_search, "No campsites", "Nothing matches that search.") }
        if (shown.isNotEmpty()) item {
            // Figma list: one Dusk card, 6dp padding, 66dp rows with a coral chevron button.
            Column(Modifier.fillMaxWidth().clip(EventsShapes.Large).background(EventsColors.Surface).padding(6.dp)) {
                shown.forEach { item ->
                    val c = item.campsite
                    Row(
                        Modifier.fillMaxWidth().clip(EventsShapes.Medium).clickable { onOpen(c.id) }.padding(horizontal = 10.dp, vertical = 8.dp).testTag("campsite_card_${c.id}"),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))) { CampsiteImage(c.id, c.imageBase64) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(c.name, style = EventsType.BodyStrong, color = EventsColors.Text, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                                s.flags[c.id]?.let { StatusTag("$it flag${if (it == 1) "" else "s"}", TagTone.RED) }
                            }
                            val lot = item.lottery
                            Text(
                                "${c.ownerName} · " + when {
                                    lot == null -> "no lottery"
                                    lot.status == LotteryStatus.DRAWN -> "winners drawn"
                                    else -> "${lot.entrantCount} waitlisted"
                                },
                                style = EventsType.BodySmall, color = EventsColors.TextSecondary, maxLines = 1,
                            )
                        }
                        Box(Modifier.size(40.dp).clip(CircleShape).background(EventsColors.DangerContainer), contentAlignment = Alignment.Center) {
                            EventsIcon(R.drawable.ic_chevron_right, tint = EventsColors.Coral, size = 20.dp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Review one campsite and remove it after confirming (US 03.01.01), and remove ratings or reviews
 * of it that break the app policy (US 03.09.01, US 03.10.01).
 */
@Composable
private fun AdminCampsiteScreen(container: AppContainer, campsiteId: String, onBack: () -> Unit, onRemoved: (String) -> Unit) {
    val vm = containerViewModel(container, key = "admin-$campsiteId") { AdminCampsiteViewModel(it, campsiteId) }
    val site by vm.campsite.collectAsStateWithLifecycle()
    val s by vm.state.collectAsStateWithLifecycle()
    var confirm by rememberSaveable { mutableStateOf(false) }
    // The rating (by user ID) whose rating or review the admin is about to remove.
    var removingRating by rememberSaveable { mutableStateOf<String?>(null) }
    var removingReview by rememberSaveable { mutableStateOf<String?>(null) }
    val ratings by vm.ratings.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    LaunchedEffect(site) { site?.let { name = it.name } }
    LaunchedEffect(s.removed) { if (s.removed) onRemoved(name) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackTopBar("Campsite Review", onBack)
        val c = site
        if (c == null) { if (!s.removed) EmptyState(R.drawable.ic_info, "Not found", "This campsite has already been removed."); return@Column }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PageMargin).padding(top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().height(180.dp).clip(EventsShapes.Large)) { CampsiteImage(c.id, c.imageBase64) }
            Text(c.name, style = EventsType.Headline, color = EventsColors.Text, modifier = Modifier.testTag("admin_campsite_name"))
            DuskCard(Modifier.fillMaxWidth(), spacing = 10.dp) {
                DetailRow(R.drawable.ic_person, "Owner", c.ownerName, EventsColors.TextSecondary)
                DetailRow(R.drawable.ic_location, "Location", c.location, EventsColors.TextSecondary)
                DetailRow(R.drawable.ic_group, "Max occupancy", "${c.maxOccupancy}", EventsColors.TextSecondary)
                DetailRow(R.drawable.ic_calendar, "Listed", formatDayTime(c.createdAt), EventsColors.TextSecondary)
            }
            if (c.description.isNotBlank()) Text(c.description, style = EventsType.BodySmall, color = EventsColors.TextSecondary)
            val reports by vm.reports.collectAsStateWithLifecycle()
            CampsiteReportsCard(reports) { vm.dismissReports() }
            AdminRatingsCard(ratings, c.rating, c.ratingCount, onRemoveRating = { removingRating = it.userId }, onRemoveReview = { removingReview = it.userId })
            if (s.message != null) Text(s.message!!, style = EventsType.BodySmall, color = EventsColors.Danger)
        }
        ActionBar {
            DangerButton("Remove campsite", { confirm = true }, Modifier.weight(1f).testTag("admin_remove"), icon = R.drawable.ic_delete, loading = s.removing)
        }
    }
    removingRating?.let { userId ->
        val r = ratings.firstOrNull { it.userId == userId }
        ConfirmDialog(
            R.drawable.ic_star, "Remove this rating?",
            "${r?.userName?.ifBlank { null } ?: "This guest"}'s ${r?.stars ?: ""}-star rating${if (r?.comment.isNullOrBlank()) "" else " and review"} will be deleted and the campsite's average worked out again. This can’t be undone.",
            "Remove rating", onConfirm = { removingRating = null; vm.removeRating(userId) }, onDismiss = { removingRating = null }, destructive = true,
        )
    }
    removingReview?.let { userId ->
        ConfirmDialog(
            R.drawable.ic_delete, "Remove this review?",
            "The written review will be deleted. The guest's stars stay, so the average doesn't change. This can’t be undone.",
            "Remove review", onConfirm = { removingReview = null; vm.removeReview(userId) }, onDismiss = { removingReview = null }, destructive = true,
        )
    }
    if (confirm) {
        ConfirmDialog(
            R.drawable.ic_delete, "Remove ${site?.name ?: "Campsite"}?",
            "This deletes the listing, its lottery, waitlist and images. Entrants on the waitlist lose their spot. This can’t be undone.",
            "Remove", onConfirm = { confirm = false; vm.remove() }, onDismiss = { confirm = false }, destructive = true,
        )
    }
}

private val sharing = SharingStarted.WhileSubscribed(5_000)

/** State of the admin campsite browser. */
data class AdminCampsitesState(
    val loading: Boolean = true,
    val query: String = "",
    val items: List<CampsiteWithLottery> = emptyList(),
    val total: Int = 0,
    val now: Long = 0L,
    val flags: Map<String, Int> = emptyMap(),
)

/** Browse and search every campsite (US 03.04.01). */
class AdminCampsitesViewModel(container: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")

    /** Browser state. */
    val state: StateFlow<AdminCampsitesState> = combine(
        container.campsites.observeCampsites(), container.lotteries.observeLotteries(), query, nowTicker(container.clock), container.social.observeReports(),
    ) { sites, lotteries, q, now, reports ->
        val byCampsite = lotteries.groupBy { it.campsiteId }
        // By campsite (name, place, tags) or by the owner's name.
        val words = q.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val byOwner = if (words.isEmpty()) emptyList() else sites.filter { site -> words.all { it in site.ownerName.lowercase() } }.sortedBy { it.name.lowercase() }
        val matches = (CampsiteSearch.search(sites, q) + byOwner).distinctBy { it.id }
        AdminCampsitesState(
            false, q, matches.map { CampsiteWithLottery(it, currentLottery(byCampsite[it.id].orEmpty(), now)) }, sites.size, now,
            reports.groupingBy { it.campsiteId }.eachCount(),
        )
    }.stateIn(viewModelScope, sharing, AdminCampsitesState())

    /** Filters by owner or campsite name. */
    fun onQueryChange(q: String) { query.value = q }
}

/** State of the admin campsite detail screen. */
data class AdminCampsiteState(val removing: Boolean = false, val removed: Boolean = false, val message: String? = null)

/** Removes a campsite with its lotteries and waiting lists (US 03.01.01). */
class AdminCampsiteViewModel(private val container: AppContainer, private val campsiteId: String) : ViewModel() {
    private val _state = MutableStateFlow(AdminCampsiteState())

    /** Reports about this campsite. */
    val reports: StateFlow<List<Report>> = container.social.observeReports()
        .let { f -> kotlinx.coroutines.flow.flow { f.collect { list -> emit(list.filter { it.campsiteId == campsiteId }) } } }
        .stateIn(viewModelScope, sharing, emptyList())

    /** Every rating of this campsite, newest first, with its review if it has one. */
    val ratings: StateFlow<List<Rating>> = container.social.observeRatings(campsiteId).stateIn(viewModelScope, sharing, emptyList())

    /** Removes a rating that breaks the app policy, review included (US 03.09.01). */
    fun removeRating(userId: String) = moderate { container.social.removeRating(campsiteId, userId) }

    /** Removes just the written review of a rating that breaks the app policy (US 03.10.01). */
    fun removeReview(userId: String) = moderate { container.social.removeReview(campsiteId, userId) }

    private fun moderate(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { _state.value = _state.value.copy(message = null) }
                .onFailure { _state.value = _state.value.copy(message = it.userMessage()) }
        }
    }

    /** Clears this campsite's reports. */
    fun dismissReports() { viewModelScope.launch { runCatching { container.social.dismissReports(campsiteId) } } }

    /** Removal progress. */
    val state: StateFlow<AdminCampsiteState> = _state.asStateFlow()

    /** The campsite (null once removed). */
    val campsite = container.campsites.observeCampsite(campsiteId).stateIn(viewModelScope, sharing, null)

    /** Removes the campsite, then reports [AdminCampsiteState.removed]. */
    fun remove() {
        _state.value = AdminCampsiteState(removing = true)
        viewModelScope.launch {
            runCatching { container.campsites.deleteCampsite(campsiteId); runCatching { container.social.dismissReports(campsiteId) } }
                .onSuccess { _state.value = AdminCampsiteState(removed = true) }
                .onFailure { _state.value = AdminCampsiteState(message = it.userMessage()) }
        }
    }
}

/**
 * Every rating of a campsite for the admin (US 03.09.01, US 03.10.01): who gave it, the stars,
 * the review, and buttons to remove the review or the whole rating.
 */
@Composable
fun AdminRatingsCard(ratings: List<Rating>, average: Double, count: Int, onRemoveRating: (Rating) -> Unit, onRemoveReview: (Rating) -> Unit) {
    DuskCard(Modifier.fillMaxWidth().testTag("admin_ratings"), spacing = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ratings & reviews", style = EventsType.TitleSmall, color = EventsColors.Text, modifier = Modifier.weight(1f))
            if (count > 0) RatingLine(average, count, "· $count")
        }
        if (ratings.isEmpty()) {
            Text("No ratings yet.", style = EventsType.BodySmall, color = EventsColors.TextSecondary)
            return@DuskCard
        }
        ratings.forEachIndexed { i, r ->
            if (i > 0) CardDivider()
            Column(Modifier.fillMaxWidth().testTag("admin_rating_${r.userId}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(r.userName.ifBlank { "Guest" }, style = EventsType.BodyStrong, color = EventsColors.Text, modifier = Modifier.weight(1f))
                    StarRow(r.stars, null, size = 14)
                }
                if (r.comment.isNotBlank()) Text(r.comment, style = EventsType.BodySmall, color = EventsColors.TextSecondary)
                Text(formatDay(r.createdAt), style = EventsType.Caption, color = EventsColors.TextTertiary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (r.comment.isNotBlank()) SecondaryButton("Remove review", { onRemoveReview(r) }, Modifier.weight(1f).testTag("admin_remove_review_${r.userId}"), size = ButtonSize.SMALL)
                    DangerButton("Remove rating", { onRemoveRating(r) }, Modifier.weight(1f).testTag("admin_remove_rating_${r.userId}"), size = ButtonSize.SMALL)
                }
            }
        }
    }
}
