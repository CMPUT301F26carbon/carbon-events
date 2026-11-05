/*
 * Admin.kt (Tausif)
 *
 * Admin console: browse every campsite and remove one after confirming, and browse every
 * entrant and owner profile.
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
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
    const val PROFILE_EDIT = "admin/profile/edit"
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
    var filter by remember { mutableIntStateOf(0) }
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

/** Admin console: browse every entrant and owner profile (Figma A-PROFILES, US 03.05.01). */
@Composable
fun AdminProfilesScreen(container: AppContainer, onOpen: (String) -> Unit = {}) {
    val vm = containerViewModel(container) { AdminProfilesViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var removing by remember { mutableStateOf<AdminProfileRow?>(null) }
    val moderation = containerViewModel(container, key = "moderate-list") { ProfileModerationViewModel(it) }
    val shown = if (tab == 0) s.entrants else s.owners
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().testTag("admin_profiles"),
        contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(44.dp).clip(EventsShapes.Medium).background(EventsColors.SkyTop.copy(alpha = 0.6f)).border(1.dp, EventsColors.TintStroke, EventsShapes.Medium),
                    contentAlignment = Alignment.Center,
                ) { EventsIcon(R.drawable.ic_group, size = 22.dp) }
                Column(Modifier.weight(1f)) {
                    Text("Admin console", style = EventsType.Caption, color = EventsColors.TextSoft)
                    Text("Profiles", style = EventsType.Headline, color = EventsColors.Text)
                }
                StatusTag("${s.total} total", TagTone.BLUE)
            }
        }
        item { SearchBar(s.query, vm::onQueryChange, placeholder = "Search names or emails…") }
        item { SegmentedControl(listOf("Entrants · ${s.entrants.size}", "Owners · ${s.owners.size}"), tab, { tab = it }, tagPrefix = "profiles_tab_") }
        if (!s.loading && shown.isEmpty()) item {
            DuskCard { EmptyState(R.drawable.ic_person, if (s.query.isBlank()) "No profiles yet" else "No matches", if (s.query.isBlank()) "Profiles appear when people open the app." else "Nothing matches that search.") }
        }
        if (shown.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().clip(EventsShapes.Large).background(EventsColors.Surface).padding(6.dp)) {
                shown.forEach { row ->
                    val p = row.profile
                    Row(
                        Modifier.fillMaxWidth().clip(EventsShapes.Medium).clickable { onOpen(p.id) }.padding(horizontal = 10.dp, vertical = 10.dp).testTag("profile_${p.id}"),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ProfileAvatar(p, 40.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(p.name.ifBlank { "Unnamed" }, style = EventsType.BodyStrong, color = EventsColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (tab == 1) "Owner · ${row.campsites} campsite${if (row.campsites == 1) "" else "s"}" else p.email.ifBlank { "No email" },
                                style = EventsType.BodySmall, color = EventsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (p.blocked) StatusTag("Blocked", TagTone.RED)
                        // Figma A-PROFILES: a delete button on each row.
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(EventsColors.DangerContainer).clickable { removing = row }.testTag("profile_remove_${p.id}"),
                            contentAlignment = Alignment.Center,
                        ) { EventsIcon(R.drawable.ic_delete, size = 18.dp, tint = EventsColors.Danger, contentDescription = "Remove ${p.name}") }
                    }
                }
            }
        }
    }
    removing?.let { row ->
        ConfirmDialog(
            R.drawable.ic_delete, "Remove ${row.profile.name}?",
            "This deletes their profile, inbox and trusted devices" + (if (row.campsites > 0) ", and their ${row.campsites} campsite${if (row.campsites == 1) "" else "s"}" else "") + ". To keep a blocked record instead, open the profile.",
            "Remove", onConfirm = { removing = null; moderation.remove(row.profile) {} }, onDismiss = { removing = null }, destructive = true,
        )
    }
}

/**
 * One person's profile for an admin (Figma A-ENTRANT-DETAIL / A-PROFILE-DETAIL, US 03.05.01):
 * contact details, activity numbers, and their bookings or campsites.
 *
 * @param userId the person to show.
 */
@Composable
fun AdminProfileDetailScreen(container: AppContainer, userId: String, onBack: () -> Unit) {
    val vm = containerViewModel(container, key = "admin-profile-$userId") { AdminProfileDetailViewModel(it, userId) }
    val s by vm.state.collectAsStateWithLifecycle()
    val p = s.profile
    val owner = s.campsites.isNotEmpty() || p?.activeRole == Role.OWNER
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackTopBar(if (owner) "Owner Profile" else "Entrant Profile", onBack)
        if (s.loading) return@Column
        if (p == null) { EmptyState(R.drawable.ic_info, "Profile not found", "This profile no longer exists."); return@Column }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                DuskCard(Modifier.fillMaxWidth().testTag("admin_profile_detail"), spacing = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProfileAvatar(p, 56.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(p.name, style = EventsType.Title, color = EventsColors.Text, maxLines = 1)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusTag(if (p.isAdmin) "Admin" else if (owner) "Owner" else "Entrant", TagTone.BLUE)
                                if (p.blocked) StatusTag("Blocked", TagTone.RED)
                                if (p.warnings > 0) StatusTag("${p.warnings} warning${if (p.warnings == 1) "" else "s"}", TagTone.AMBER)
                            }
                        }
                    }
                    MetaItem(R.drawable.ic_mail, p.email.ifBlank { "No email" })
                    MetaItem(R.drawable.ic_phone, p.phone.ifBlank { "No phone added" })
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (owner) {
                        InfoTile(R.drawable.ic_tent, "${s.campsites.size}", "Campsites", Modifier.weight(1f))
                        InfoTile(R.drawable.ic_ticket, "${s.liveLotteries}", "Live lotteries", Modifier.weight(1f))
                    } else {
                        InfoTile(R.drawable.ic_ticket, "${s.lotteriesJoined}", "Lotteries joined", Modifier.weight(1f))
                        InfoTile(R.drawable.ic_trophy, "${s.wins}", "Wins", Modifier.weight(1f))
                    }
                    InfoTile(R.drawable.ic_calendar, if (p.createdAt > 0) monthYear(p.createdAt) else "—", "Joined", Modifier.weight(1f))
                }
            }
            if (owner) {
                item { SectionTitle("Campsites") }
                if (s.campsites.isEmpty()) item { DuskCard { EmptyState(R.drawable.ic_tent, "No campsites", "This owner hasn't listed a campsite yet.") } }
                else item {
                    Column(Modifier.fillMaxWidth().clip(EventsShapes.Large).background(EventsColors.Surface).padding(6.dp)) {
                        s.campsites.forEach { c -> ProfileListRow(c.id, c.imageBase64, c.name, c.location) }
                    }
                }
            } else {
                item { SectionTitle("Recent bookings") }
                if (s.bookings.isEmpty()) item { DuskCard { EmptyState(R.drawable.ic_event_check, "No bookings", "Stays booked after winning a lottery show up here.") } }
                else item {
                    Column(Modifier.fillMaxWidth().clip(EventsShapes.Large).background(EventsColors.Surface).padding(6.dp)) {
                        s.bookings.forEach { r -> ProfileListRow(r.campsiteId, null, r.campsiteName, formatStayShort(r.checkInDay, r.checkOutDay)) }
                    }
                }
            }
        }
        if (!p.isAdmin) ProfileModerationBar(container, p, owner, s.campsites.size, onRemoved = onBack)
    }
}

@Composable
private fun ProfileListRow(campsiteId: String, image: String?, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))) { CampsiteImage(campsiteId, image) }
        Column(Modifier.weight(1f)) {
            Text(title, style = EventsType.BodyStrong, color = EventsColors.Text, maxLines = 1)
            Text(subtitle, style = EventsType.BodySmall, color = EventsColors.TextSecondary, maxLines = 1)
        }
    }
}

private fun monthYear(millis: Long): String =
    java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.CANADA)
        .format(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()))

/** Review one campsite and remove it after confirming (US 03.01.01). */
@Composable
private fun AdminCampsiteScreen(container: AppContainer, campsiteId: String, onBack: () -> Unit, onRemoved: (String) -> Unit) {
    val vm = containerViewModel(container, key = "admin-$campsiteId") { AdminCampsiteViewModel(it, campsiteId) }
    val site by vm.campsite.collectAsStateWithLifecycle()
    val s by vm.state.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
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
            if (s.message != null) Text(s.message!!, style = EventsType.BodySmall, color = EventsColors.Danger)
        }
        ActionBar {
            DangerButton("Remove campsite", { confirm = true }, Modifier.weight(1f).testTag("admin_remove"), icon = R.drawable.ic_delete, loading = s.removing)
        }
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
        val matches = CampsiteSearch.search(sites, q)
        AdminCampsitesState(
            false, q, matches.map { CampsiteWithLottery(it, currentLottery(byCampsite[it.id].orEmpty())) }, sites.size, now,
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

/** One profile in the admin browser, with how many campsites they own. */
data class AdminProfileRow(val profile: UserProfile, val campsites: Int)

/** State of the admin profile browser. */
data class AdminProfilesState(
    val loading: Boolean = true,
    val query: String = "",
    val entrants: List<AdminProfileRow> = emptyList(),
    val owners: List<AdminProfileRow> = emptyList(),
    val total: Int = 0,
)

/**
 * Browse every profile (US 03.05.01). Owners are profiles that own a campsite or use the
 * owner role; everyone else is listed as an entrant.
 */
class AdminProfilesViewModel(container: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")

    /** Browser state. */
    val state: StateFlow<AdminProfilesState> = combine(container.profiles.observeProfiles(), container.campsites.observeCampsites(), query) { profiles, sites, q ->
        val owned = sites.groupingBy { it.ownerId }.eachCount()
        val needle = q.trim().lowercase()
        val rows = profiles
            .filter { !it.isAdmin } // admins aren't entrants or owners
            .filter { needle.isEmpty() || it.name.lowercase().contains(needle) || it.email.lowercase().contains(needle) }
            .map { AdminProfileRow(it, owned[it.id] ?: 0) }
        val (owners, entrants) = rows.partition { it.campsites > 0 || it.profile.activeRole == Role.OWNER }
        AdminProfilesState(false, q, entrants, owners, profiles.count { !it.isAdmin })
    }.stateIn(viewModelScope, sharing, AdminProfilesState())

    /** Filters by name or email. */
    fun onQueryChange(q: String) { query.value = q }
}

/** State of the admin's view of one profile. */
data class AdminProfileDetailState(
    val loading: Boolean = true,
    val profile: UserProfile? = null,
    val lotteriesJoined: Int = 0,
    val wins: Int = 0,
    val bookings: List<Reservation> = emptyList(),
    val campsites: List<Campsite> = emptyList(),
    val liveLotteries: Int = 0,
)

/** One person's profile, activity and bookings or campsites, for admins (US 03.05.01). */
class AdminProfileDetailViewModel(container: AppContainer, userId: String) : ViewModel() {
    /** Detail state. */
    val state: StateFlow<AdminProfileDetailState> = combine(
        container.profiles.observeProfile(userId),
        container.lotteries.observeEntriesForEntrant(userId),
        container.lotteries.observeReservationsForEntrant(userId),
        container.campsites.observeCampsitesByOwner(userId),
        container.lotteries.observeLotteriesByOwner(userId),
    ) { profile, entries, bookings, sites, lotteries ->
        AdminProfileDetailState(
            false, profile, entries.size, entries.count { it.status == EntryStatus.WON },
            bookings.sortedByDescending { it.checkInDay }, sites, lotteries.count { it.status == LotteryStatus.OPEN },
        )
    }.stateIn(viewModelScope, sharing, AdminProfileDetailState())
}
