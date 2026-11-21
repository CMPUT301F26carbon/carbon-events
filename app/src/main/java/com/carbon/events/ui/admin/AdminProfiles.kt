/*
 * AdminProfiles.kt (Tausif)
 *
 * Admin console: browsing every entrant and owner profile and opening one.
 *
 * Outstanding issues: none known.
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

private val sharing = SharingStarted.WhileSubscribed(5_000)

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
                        if (p.removed) StatusTag("Removed", TagTone.RED) else if (p.blocked) StatusTag("Blocked", TagTone.RED)
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
            "This deletes their inbox and waitlist spots" + (if (row.campsites > 0) ", and their ${row.campsites} campsite${if (row.campsites == 1) "" else "s"}" else "") + ". They and the phones they used can't use Events again.",
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
                                if (p.removed) StatusTag("Removed", TagTone.RED) else if (p.blocked) StatusTag("Blocked", TagTone.RED)
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
