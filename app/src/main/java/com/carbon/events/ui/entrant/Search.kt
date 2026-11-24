/*
 * Search.kt (Ansh)
 *
 * Keyword search and the Filters sheet: location, dates, guests, vibe and amenities
 * (US 01.01.01 to 01.01.03).
 *
 * Outstanding issues: none known.
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** How far "Use my current location" looks around the phone. */
private const val NEAR_ME_KM = 50

private val sharing = SharingStarted.WhileSubscribed(5_000)

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
    var pickLocation by rememberSaveable { mutableStateOf(false) }
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
