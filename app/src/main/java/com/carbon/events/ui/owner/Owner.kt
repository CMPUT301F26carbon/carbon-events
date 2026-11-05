/*
 * Owner.kt (Rayner)
 *
 * Owner side: my campsites, register / edit, the promo QR, running lotteries (create, edit,
 * booking period, waitlist cap, waitlist, winners) and bookings made by lottery winners.
 *
 * Outstanding issues: the draw only runs while some device has the app open (no Cloud
 * Function yet).
 */
package com.carbon.events

import androidx.compose.foundation.layout.ColumnScope

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Owner route names. */
object OwnerRoutes {
    const val CAMPSITES = "owner/campsites"
    const val LOTTERIES = "owner/lotteries"
    const val BOOKINGS = "owner/bookings"
    const val PROFILE = "owner/profile"
    const val PROFILE_EDIT = "owner/profile/edit"
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
            }
        }
    }
}

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
                GlassIconButton(R.drawable.ic_bell, "Messages", onBell, Modifier.testTag("owner_bell"))
            }
        }
        item { SectionTitle("My Campsites") { PrimaryButton("Add", onAdd, Modifier.testTag("owner_add"), icon = R.drawable.ic_add, size = ButtonSize.SMALL) } }
        if (state.loading) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = EventsColors.Coral) } }
        if (!state.loading && state.items.isEmpty()) {
            item { DuskCard { EmptyState(R.drawable.ic_tent, "List your first campsite", "Register a campsite to get a promo QR code and run a fair lottery.") { PrimaryButton("Register campsite", onAdd) } } }
        }
        items(state.items, key = { it.campsite.id }) { item ->
            CampsiteListCard(item.campsite, { onOpen(item.campsite.id) }, thumb = 72.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

/**
 * Register or edit a campsite (Figma O-EDIT).
 *
 * @param campsiteId null to register a new campsite.
 * @param onSaved called with the campsite ID after saving.
 */
@Composable
fun CampsiteFormScreen(container: AppContainer, profile: UserProfile, campsiteId: String?, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val vm = containerViewModel(container, key = "form-${campsiteId ?: "new"}") { CampsiteFormViewModel(it, campsiteId) }
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showMap by remember { mutableStateOf(false) }
    var customTag by remember { mutableStateOf("") }
    var addingTag by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val data = compressImageToBase64(context, uri)
            photoError = data == null
            if (data != null) vm.update { it.copy(imageBase64 = data) }
        }
    }
    androidx.activity.compose.BackHandler { if (!vm.back()) onBack() }
    Column(Modifier.fillMaxSize().imePadding()) {
        if (s.step == 2) {
            BackTopBar("Availability & Price", { vm.back() }, Modifier.statusBarsPadding())
            AvailabilityStep(s, vm)
            ActionBar {
                PrimaryButton(
                    if (campsiteId == null) "Publish listing" else "Save changes", { vm.save(profile, onSaved) },
                    Modifier.weight(1f).testTag("form_save"), icon = R.drawable.ic_check, loading = s.saving,
                )
            }
            return@Column
        }
        BackTopBar(if (campsiteId == null) "New Campsite" else "Edit Campsite", onBack, Modifier.statusBarsPadding(), icon = R.drawable.ic_close)
        if (s.loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = EventsColors.Coral) }; return@Column }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PageMargin).padding(top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Photo upload (Figma "Photo Upload": 104dp Dusk card with a 10% outline).
            val preview = remember(s.imageBase64) { decodeBase64Image(s.imageBase64) }
            Box(
                Modifier.fillMaxWidth().height(104.dp).clip(EventsShapes.Large).background(EventsColors.Surface)
                    .border(1.dp, Color(0x1AFFFFFF), EventsShapes.Large)
                    .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.testTag("form_photo"),
                contentAlignment = Alignment.Center,
            ) {
                if (preview != null) {
                    Image(preview, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { EventsIcon(R.drawable.ic_upload, size = 20.dp, tint = EventsColors.OnPrimaryContainer) }
                        Text("Add a promo photo", style = EventsType.BodyStrong, color = EventsColors.Primary)
                        Text("Optional · JPG or PNG up to 5 MB", style = EventsType.Caption, color = EventsColors.TextSecondary)
                    }
                }
            }
            if (photoError) Text("Couldn’t read that photo. Try another one.", style = EventsType.Caption, color = EventsColors.Danger)

            EventsTextField(s.name, { v -> vm.update(Field.NAME) { it.copy(name = v) } }, "Campsite name", Modifier.testTag("form_name"), "e.g. Bow River Sites", s.errors[Field.NAME])
            EventsTextField(s.location, { v -> vm.update(Field.LOCATION) { it.copy(location = v) } }, "Location", Modifier.testTag("form_location"), "e.g. Banff, AB", s.errors[Field.LOCATION], R.drawable.ic_location)
            // Figma O-EDIT: a map preview under the location; tap it to set or move the pin.
            Box(
                Modifier.fillMaxWidth().height(84.dp).clip(EventsShapes.Medium).clickable { showMap = true }.testTag("form_map"),
            ) {
                if (s.latitude != null && s.longitude != null) MapPreview(s.latitude!!, s.longitude!!, s.location.ifBlank { "Campsite" }, height = 84.dp, onClick = { showMap = true })
                else Box(Modifier.fillMaxSize().background(EventsColors.SurfaceSoft), contentAlignment = Alignment.Center) {
                    MetaItem(R.drawable.ic_map, "Tap to set the location on the map", EventsColors.TextSecondary)
                }
            }

            Stepper(s.maxOccupancy, { v -> vm.update(Field.OCCUPANCY) { it.copy(maxOccupancy = v) } }, 1, 50, "Max occupancy", supporting = "Guests per site")
            s.errors[Field.OCCUPANCY]?.let { Text(it, style = EventsType.Caption, color = EventsColors.Danger) }
            EventsTextField(s.amenities, { v -> vm.update { it.copy(amenities = v) } }, "Amenities", Modifier.testTag("form_amenities"), "Fire pits, potable water, flush toilets…", singleLine = false, minLines = 3)

            Text("Tags", style = EventsType.LabelSmall, color = EventsColors.TextSoft)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (SuggestedTags + s.tags.filter { it !in SuggestedTags }).forEach { tag ->
                    val on = tag in s.tags
                    EventsChip(tag, if (on) ChipStyle.CORAL else ChipStyle.GLASS, { vm.toggleTag(tag) }, icon = if (on) vibeIcon(tag) else null)
                }
                EventsChip("Add tag", ChipStyle.GLASS, { addingTag = true }, Modifier.testTag("form_add_tag"), R.drawable.ic_add)
            }
            if (addingTag) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EventsTextField(customTag, { customTag = it }, "Your own tag", Modifier.weight(1f), "e.g. Canoe launch")
                    SecondaryButton("Add", { vm.addCustomTag(customTag); customTag = ""; addingTag = false }, icon = R.drawable.ic_add)
                }
            }
            if (s.message != null) Text(s.message!!, style = EventsType.BodySmall, color = EventsColors.Danger)
        }
        ActionBar {
            PrimaryButton("Next: availability & price", vm::next, Modifier.weight(1f).testTag("form_next"), icon = R.drawable.ic_check)
        }
    }
    if (showMap) {
        MapPickerDialog(s.latitude?.let { lat -> s.longitude?.let { lat to it } }, onConfirm = { lat, lng -> vm.setPin(lat, lng); showMap = false }, onDismiss = { showMap = false })
    }
}

/**
 * Step 2 of 2 (Figma O-AVAILABILITY): price per night, cancellation & refunds, and the rules
 * guests accept when they book.
 */
@Composable
private fun ColumnScope.AvailabilityStep(s: CampsiteFormState, vm: CampsiteFormViewModel) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PageMargin).padding(top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusTag("Step 2 of 2", TagTone.BLUE)
            Text("${s.name.ifBlank { "Your campsite" }} · up to ${s.maxOccupancy} guests", style = EventsType.Caption, color = EventsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        EventsTextField(s.price, { v -> vm.update(Field.PRICE) { it.copy(price = v.filter(Char::isDigit)) } }, "Price per night (CAD)", Modifier.testTag("form_price"), "65", s.errors[Field.PRICE], R.drawable.ic_money, KeyboardType.Number)
        Text("Cancellation & refunds", style = EventsType.LabelSmall, color = EventsColors.TextSoft)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CancellationPolicy.entries.forEach { p ->
                EventsChip(p.label, if (s.cancellation == p) ChipStyle.CORAL else ChipStyle.GLASS, { vm.update { it.copy(cancellation = p) } }, Modifier.testTag("policy_${p.name.lowercase()}"))
            }
        }
        MetaItem(R.drawable.ic_calendar, s.cancellation.summary, EventsColors.TextSecondary)
        EventsTextField(s.rules, { v -> vm.update { it.copy(rules = v) } }, "Campsite rules", Modifier.testTag("form_rules"), "e.g. Quiet hours 10 PM – 7 AM, pets on a leash", singleLine = false, minLines = 3, supporting = "Guests accept these when they book.")
        if (s.message != null) Text(s.message, style = EventsType.BodySmall, color = EventsColors.Danger)
    }
}

/** "Your Campsite Is Live!" with the generated promo QR code (Figma O-PUBLISHED, US 02.02.01). */
@Composable
fun PublishedScreen(container: AppContainer, campsiteId: String, onCreateLottery: () -> Unit, onDone: () -> Unit) {
    val vm = containerViewModel(container, key = "published-$campsiteId") { ManageCampsiteViewModel(it, campsiteId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val site = state.campsite
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        // Hero 220dp; the check badge overlaps its bottom edge by 34dp.
        Box(Modifier.fillMaxWidth().height(220.dp)) { CampsiteImage(campsiteId, site?.imageBase64) }
        Column(
            Modifier.offset(y = (-34).dp).fillMaxWidth().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(68.dp).clip(CircleShape).background(EventsColors.Green).border(4.dp, EventsColors.Surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) { EventsIcon(R.drawable.ic_check, tint = EventsColors.OnPrimary, size = 34.dp) }
            Text("Your Campsite Is Live!", style = EventsType.Headline, color = EventsColors.Text, textAlign = TextAlign.Center, modifier = Modifier.testTag("published_title"))
            if (site != null) Text("${site.name} · ${site.location} · $${site.pricePerNight}/night", style = EventsType.BodySmall, color = EventsColors.TextSoft, textAlign = TextAlign.Center)
            DuskCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    QrCodeImage(QrCodes.payloadFor(campsiteId), 112.dp, Modifier.testTag("qr_code"), withLogo = true)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Unique promo QR", style = EventsType.BodyStrong, color = EventsColors.Text)
                        Text("Generated for this campsite. Scanning it opens the listing and its lottery.", style = EventsType.Caption, color = EventsColors.TextSecondary)
                        QrDownloadButton { saveQrToPhotos(context, QrCodes.payloadFor(campsiteId), "${site?.name ?: campsiteId} QR") }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Create a lottery", onCreateLottery, Modifier.fillMaxWidth().testTag("published_lottery"), icon = R.drawable.ic_ticket)
                Box(
                    Modifier.fillMaxWidth().height(52.dp).clip(EventsShapes.Pill).clickable(onClick = onDone).testTag("published_done"),
                    contentAlignment = Alignment.Center,
                ) { Text("Done", style = EventsType.Label, color = EventsColors.Primary) }
            }
        }
    }
}

/** Figma "download" button: 44dp #3E3A8A circle; saves the promo QR to Photos. */
@Composable
private fun QrDownloadButton(save: () -> Boolean) {
    val context = LocalContext.current
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(EventsColors.PrimaryContainer)
            .clickable {
                val saved = save()
                Toast.makeText(context, if (saved) "QR code saved to Photos" else "Couldn’t save the QR code", Toast.LENGTH_SHORT).show()
            }.testTag("download_qr"),
        contentAlignment = Alignment.Center,
    ) { EventsIcon(R.drawable.ic_download, tint = EventsColors.Primary, contentDescription = "Download QR code") }
}

/**
 * View my campsite (Figma O-MANAGE): details, promo QR code, lottery and bookings
 * (US 02.01.04, US 02.02.01, US 02.04.06, US 02.06.01).
 *
 * @param onEditLottery opens the lottery form in edit mode for the given lottery ID.
 * @param onBookings opens this campsite's bookings.
 */
@Composable
fun ManageCampsiteScreen(
    container: AppContainer,
    campsiteId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onCreateLottery: () -> Unit,
    onWaitlist: (String) -> Unit,
    onBookings: () -> Unit = {},
    onEditLottery: (String) -> Unit = {},
    onNotify: (String) -> Unit = {},
    onAvailability: () -> Unit = {},
) {
    val vm = containerViewModel(container, key = "manage-$campsiteId") { ManageCampsiteViewModel(it, campsiteId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val site = state.campsite
    val context = LocalContext.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    if (state.loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = EventsColors.Coral) }; return }
    if (site == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) { BackTopBar(null, onBack); EmptyState(R.drawable.ic_info, "Campsite removed", "This campsite is no longer listed.") }
        return
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(210.dp)) { CampsiteImage(site.id, site.imageBase64) }
            // The sheet overlaps the hero by 30dp (hero 210, sheet starts at 180 in Figma).
            Column(
                Modifier
                    .offset(y = (-30).dp)
                    .fillMaxWidth()
                    .heightIn(min = screenHeight - 180.dp)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(EventsColors.Background)
                    .padding(start = PageMargin, end = PageMargin, top = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val lot = state.lottery
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(site.name, style = EventsType.Headline, color = EventsColors.Text, modifier = Modifier.weight(1f).testTag("manage_name"))
                    when {
                        lot == null -> StatusTag("No lottery", TagTone.BLUE)
                        lot.status == LotteryStatus.DRAWN -> StatusTag("Winners drawn", TagTone.CORAL)
                        lot.isRegistrationOpen(state.now) -> StatusTag("Lottery live", TagTone.GREEN)
                        else -> LotteryStatusTag(lot, state.now)
                    }
                }
                MetaItem(R.drawable.ic_location, "${site.location} · $${site.pricePerNight}/night · max ${site.maxOccupancy}")
                // Promo QR card (Figma "Card": QR 94dp on white, text column, download button).
                DuskCard(Modifier.fillMaxWidth(), padding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        QrCodeImage(QrCodes.payloadFor(site.id), 94.dp, Modifier.testTag("qr_code"), withLogo = true)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Promo QR code", style = EventsType.BodyStrong, color = EventsColors.Text)
                            Text("Print it at the trailhead - scanning opens this campsite.", style = EventsType.BodySmall, color = EventsColors.TextSecondary)
                            QrDownloadButton { saveQrToPhotos(context, QrCodes.payloadFor(site.id), "${site.name} QR") }
                        }
                    }
                }
                // Lottery card (Figma "Lottery": radius 16, #3E3A8A, 8dp progress bar).
                DuskCard(
                    Modifier.fillMaxWidth().testTag("manage_lottery"), padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    color = EventsColors.PrimaryContainer, shape = RoundedCornerShape(16.dp), spacing = 8.dp,
                ) {
                    if (lot == null) {
                        Text("No lottery yet", style = EventsType.BodyStrong, color = EventsColors.Text)
                        Text("Create one so campers can join the waitlist. Winners are drawn automatically.", style = EventsType.BodySmall, color = EventsColors.OnPrimaryContainer)
                        PrimaryButton("Create lottery", onCreateLottery, Modifier.fillMaxWidth().testTag("manage_create_lottery"), icon = R.drawable.ic_add, size = ButtonSize.SMALL)
                    } else {
                        Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Lottery · ${formatRange(lot.registrationStart, lot.registrationEnd)}", style = EventsType.BodyStrong, color = EventsColors.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (lot.status != LotteryStatus.DRAWN) Box(
                                Modifier.height(28.dp).clip(EventsShapes.Pill).clickable { onEditLottery(lot.id) }.padding(horizontal = 12.dp).testTag("manage_edit_lottery"),
                                contentAlignment = Alignment.Center,
                            ) { Text("Edit", style = EventsType.LabelSmall, color = EventsColors.Primary) }
                            Box(
                                Modifier.height(28.dp).clip(EventsShapes.Pill).clickable(onClick = onCreateLottery).padding(horizontal = 18.dp).testTag("manage_create_lottery"),
                                contentAlignment = Alignment.Center,
                            ) { Text("New", style = EventsType.LabelSmall, color = EventsColors.Primary) }
                        }
                        DetailRow(R.drawable.ic_trophy, if (lot.status == LotteryStatus.DRAWN) "Drawn" else "Auto-draw", "${formatDay(lot.drawnAt ?: lot.registrationEnd)} · ${lot.winnerCount} winners")
                        DetailRow(R.drawable.ic_group, "Waitlist", lot.waitlistCap?.let { "${lot.entrantCount} / $it" } ?: "${lot.entrantCount} joined")
                        if (lot.hasBookingPeriod) DetailRow(R.drawable.ic_calendar, "Stays", formatStay(lot.bookingStartDay!!, lot.bookingEndDay!!))
                        lot.waitlistCap?.takeIf { it > 0 }?.let { cap ->
                            val fraction = (lot.entrantCount.toFloat() / cap).coerceIn(0f, 1f)
                            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(EventsColors.Background)) {
                                Box(Modifier.fillMaxWidth(fraction).height(8.dp).clip(RoundedCornerShape(4.dp)).background(EventsColors.Primary))
                            }
                        }
                        if (lot.isDrawDue(state.now)) DrawNowButton(state.drawing) { vm.drawNow() }
                    }
                }
                // Quick actions (Figma: three 60dp tiles, radius 16, Dusk).
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(R.drawable.ic_group, "Waitlist · ${lot?.entrantCount ?: 0}", Modifier.weight(1f).testTag("manage_waitlist"), enabled = lot != null) { lot?.let { onWaitlist(it.id) } }
                    QuickAction(R.drawable.ic_event_check, "Booked · ${state.bookedCount}", Modifier.weight(1f).testTag("manage_bookings"), onClick = onBookings)
                    QuickAction(R.drawable.ic_send, "Notify", Modifier.weight(1f).testTag("manage_notify"), enabled = lot != null && lot.entrantCount > 0) { lot?.let { onNotify(it.id) } }
                }
                DuskCard(Modifier.fillMaxWidth().testTag("manage_availability"), onClick = onAvailability, padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconTile(R.drawable.ic_money)
                        Column(Modifier.weight(1f)) {
                            Text("Availability & price", style = EventsType.BodyStrong, color = EventsColors.Text)
                            Text("$${site.pricePerNight}/night · ${site.minNights}–${site.maxNights} nights · in ${site.checkInTime}, out ${site.checkOutTime}", style = EventsType.BodySmall, color = EventsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        EventsIcon(R.drawable.ic_chevron_right, tint = EventsColors.TextSecondary, size = 20.dp)
                    }
                }
                if (site.ratingCount > 0) MetaItem(R.drawable.ic_star, "Rated ${"%.1f".format(site.rating)} by ${site.ratingCount} guest${if (site.ratingCount == 1) "" else "s"}", iconTint = EventsColors.Star)
                if (site.hasMapLocation) MapPreview(site.latitude!!, site.longitude!!, site.location)
            }
        }
        Row(Modifier.statusBarsPadding().padding(horizontal = PageMargin, vertical = 8.dp)) {
            GlassIconButton(R.drawable.ic_arrow_back, "Back", onBack)
            Spacer(Modifier.weight(1f))
            GlassIconButton(R.drawable.ic_edit, "Edit campsite", onEdit, Modifier.testTag("manage_edit"))
        }
    }
}

@Composable
private fun QuickAction(icon: Int, label: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        modifier.height(60.dp).clip(EventsShapes.Medium).background(EventsColors.Surface).clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        EventsIcon(icon, size = 20.dp, tint = if (enabled) EventsColors.OnPrimaryContainer else EventsColors.TextTertiary)
        Text(label, style = EventsType.LabelSmall, color = if (enabled) EventsColors.Text else EventsColors.TextTertiary, maxLines = 1)
    }
}

/** "Draw winners now" for a lottery whose registration closed while this phone was away. */
@Composable
private fun DrawNowButton(working: Boolean, onClick: () -> Unit) {
    Text("Registration closed. Winners are drawn on your phone, so draw them now.", style = EventsType.BodySmall, color = EventsColors.OnPrimaryContainer)
    PrimaryButton("Draw winners now", onClick, Modifier.fillMaxWidth().testTag("draw_now"), icon = R.drawable.ic_trophy, size = ButtonSize.SMALL, loading = working)
}

/** State of Availability & price. */
data class AvailabilityState(
    val loading: Boolean = true,
    val campsite: Campsite? = null,
    val price: String = "",
    val maxGuests: Int = 4,
    val minNights: Int = 1,
    val maxNights: Int = 7,
    val checkIn: String = "3:00 PM",
    val checkOut: String = "11:00 AM",
    val saving: Boolean = false,
    val saved: Boolean = false,
    val message: String? = null,
)

/** Edits a campsite's price, guests, stay length and check-in / check-out times. */
class AvailabilityViewModel(private val container: AppContainer, campsiteId: String) : ViewModel() {
    private val _state = MutableStateFlow(AvailabilityState())

    /** Form state. */
    val state: StateFlow<AvailabilityState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val site = container.campsites.observeCampsite(campsiteId).first()
            _state.value = if (site == null) AvailabilityState(loading = false, message = "This campsite no longer exists.") else AvailabilityState(
                false, site, site.pricePerNight.toString(), site.maxOccupancy, site.minNights, site.maxNights, site.checkInTime, site.checkOutTime,
            )
        }
    }

    /** Applies a change to the form. */
    fun update(change: (AvailabilityState) -> AvailabilityState) { _state.value = change(_state.value).copy(saved = false, message = null) }

    /** Saves the changes. */
    fun save() {
        val s = _state.value
        val site = s.campsite ?: return
        val price = s.price.trim().toIntOrNull()
        val problem = when {
            price == null || price < 0 -> "Enter a price of 0 or more."
            s.minNights > s.maxNights -> "The shortest stay can't be longer than the longest."
            else -> null
        }
        if (problem != null) { _state.value = s.copy(message = problem); return }
        _state.value = s.copy(saving = true)
        viewModelScope.launch {
            val updated = site.copy(
                pricePerNight = price!!, maxOccupancy = s.maxGuests, minNights = s.minNights, maxNights = s.maxNights,
                checkInTime = s.checkIn.trim().ifBlank { "3:00 PM" }, checkOutTime = s.checkOut.trim().ifBlank { "11:00 AM" },
            )
            runCatching { container.campsites.updateCampsite(updated) }
                .onSuccess { _state.value = _state.value.copy(saving = false, saved = true, campsite = updated) }
                .onFailure { _state.value = _state.value.copy(saving = false, message = it.userMessage()) }
        }
    }
}

/** Availability & price (Figma O-AVAILABILITY). */
@Composable
fun AvailabilityScreen(container: AppContainer, campsiteId: String, onBack: () -> Unit) {
    val vm = containerViewModel(container, key = "availability-$campsiteId") { AvailabilityViewModel(it, campsiteId) }
    val s by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().imePadding()) {
        BackTopBar("Availability & Price", onBack, Modifier.statusBarsPadding())
        if (s.loading) return@Column
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PageMargin).padding(top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(s.campsite?.name.orEmpty(), style = EventsType.Title, color = EventsColors.Text)
            EventsTextField(s.price, { v -> vm.update { it.copy(price = v.filter(Char::isDigit)) } }, "Price per night (CAD)", Modifier.testTag("avail_price"), "65", leadingIcon = R.drawable.ic_money, keyboardType = KeyboardType.Number, supporting = "Guests pay this plus 5% GST at check-in.")
            DuskCard(Modifier.fillMaxWidth(), spacing = 14.dp) {
                Stepper(s.maxGuests, { v -> vm.update { it.copy(maxGuests = v) } }, 1, CampsiteValidator.MAX_OCCUPANCY, "Max guests", supporting = "Per reservation")
                Stepper(s.minNights, { v -> vm.update { it.copy(minNights = v) } }, 1, BookingPolicy.MAX_NIGHTS, "Shortest stay", supporting = "Nights", modifier = Modifier.testTag("avail_min"))
                Stepper(s.maxNights, { v -> vm.update { it.copy(maxNights = v) } }, 1, BookingPolicy.MAX_NIGHTS, "Longest stay", supporting = "Nights", modifier = Modifier.testTag("avail_max"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EventsTextField(s.checkIn, { v -> vm.update { it.copy(checkIn = v) } }, "Check-in from", Modifier.weight(1f), "3:00 PM", leadingIcon = R.drawable.ic_clock)
                EventsTextField(s.checkOut, { v -> vm.update { it.copy(checkOut = v) } }, "Check-out by", Modifier.weight(1f), "11:00 AM", leadingIcon = R.drawable.ic_clock)
            }
            Text("The nights winners can pick are set on each lottery (booking period).", style = EventsType.BodySmall, color = EventsColors.TextSecondary)
            s.message?.let { Text(it, style = EventsType.BodySmall, color = EventsColors.Danger) }
            if (s.saved) Text("Saved", style = EventsType.BodyStrong, color = EventsColors.Green, modifier = Modifier.testTag("avail_saved"))
        }
        ActionBar { PrimaryButton("Save", vm::save, Modifier.weight(1f).testTag("avail_save"), icon = R.drawable.ic_check, loading = s.saving) }
    }
}

private const val MIN = 60_000L
private const val DAY = 24 * 60 * MIN

/**
 * New / edit lottery form (Figma O-LOTTERY-NEW, O-LOTTERY-EDIT, O-LOTTERY-NEW-BOOK).
 * Sets the campsite (US 02.04.01), registration period (US 02.04.03), booking period
 * (US 02.04.04), number of winners (US 02.04.05) and an optional waitlist cap (US 02.04.07);
 * in edit mode it updates the published lottery (US 02.04.06).
 *
 * @param presetCampsiteId campsite chosen before opening, or null to pick here.
 * @param onPublished called with the lottery's ID after saving.
 * @param lotteryId the lottery to edit, or null to create a new one.
 */
@Composable
fun CreateLotteryScreen(container: AppContainer, presetCampsiteId: String?, onBack: () -> Unit, onPublished: (String) -> Unit, lotteryId: String? = null) {
    val vm = containerViewModel(container, key = "lottery-${lotteryId ?: "new"}-${presetCampsiteId.orEmpty()}") { CreateLotteryViewModel(it, presetCampsiteId, lotteryId) }
    val s by vm.state.collectAsStateWithLifecycle()
    val now = { container.clock.now() }
    val startLocked = s.editing && s.original?.let { now() >= it.registrationStart } == true
    Column(Modifier.fillMaxSize().imePadding()) {
        BackTopBar(if (s.editing) "Edit Lottery" else "New Lottery", onBack, Modifier.statusBarsPadding())
        if (s.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = EventsColors.Coral) }
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PageMargin).padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Campsite in this lottery", style = EventsType.LabelSmall, color = EventsColors.TextSoft)
            if (s.campsites.isEmpty()) {
                Text("You don’t have any campsites yet. Register one first.", style = EventsType.BodySmall, color = EventsColors.TextSecondary)
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // A published lottery stays on its campsite, so edit mode shows only that one.
                    s.campsites.filter { !s.editing || it.id == s.selectedId }.forEach { site ->
                        val on = site.id == s.selectedId
                        EventsChip(
                            site.name, if (on) ChipStyle.CORAL else ChipStyle.GLASS, { if (!s.editing) vm.select(site.id) },
                            Modifier.testTag("lottery_site_${site.id}"), if (on) R.drawable.ic_check else R.drawable.ic_tent,
                        )
                    }
                }
            }
            // Figma O-LOTTERY-NEW: fields sit straight on the sky, no cards.
            if (startLocked) {
                MetaItem(R.drawable.ic_clock, "Opened ${formatDayTime(s.registrationStart ?: 0L)} · start is locked")
            } else {
                DateTimeField(
                    "Waitlist opens (registration)", s.registrationStart, vm::setStart, error = s.errors[Field.REG_START], testTag = "start",
                    presets = listOf(TimePreset("Now") { now() }, TimePreset("Tomorrow") { now() + DAY }),
                )
            }
            DateTimeField(
                "Waitlist closes · lottery draw (auto)", s.registrationEnd, vm::setEnd, error = s.errors[Field.REG_END], testTag = "end",
                presets = listOf(
                    TimePreset("In 1 hour") { now() + 60 * MIN }, TimePreset("In 3 days") { now() + 3 * DAY },
                    TimePreset("In 1 week") { now() + 7 * DAY }, TimePreset("In 2 weeks") { now() + 14 * DAY },
                ),
            )
            val firstStayDay = dayOf(s.registrationEnd ?: now())
            DateRangeField(
                "Booking period · nights winners can book", s.bookingStartDay, s.bookingEndDay, vm::setBooking,
                error = s.errors[Field.BOOK_START] ?: s.errors[Field.BOOK_END], placeholder = "Pick stay dates", testTag = "booking",
                initialDay = firstStayDay, isSelectable = { it >= firstStayDay },
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("1 week" to 7L, "2 weeks" to 14L, "1 month" to 30L).forEach { (label, nights) ->
                    // Stays start the day after the draw.
                    EventsChip(label, ChipStyle.GLASS, { vm.setBooking(firstStayDay + 1, firstStayDay + 1 + nights) }, Modifier.testTag("preset_booking_$label"))
                }
            }
            Text(
                "Winners are drawn automatically when the waitlist closes, then book nights inside the booking period.",
                style = EventsType.BodySmall, color = EventsColors.TextSecondary,
            )
            Stepper(s.winners, vm::setWinners, 1, 99, "Number of winners", supporting = "Drawn from the waitlist")
            s.errors[Field.WINNERS]?.let { Text(it, style = EventsType.Caption, color = EventsColors.Danger) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(R.drawable.ic_group)
                Column(Modifier.weight(1f)) {
                    Text("Waitlist cap", style = EventsType.BodyStrong, color = EventsColors.Text)
                    Text(
                        if (s.cap == null) "No limit · anyone can join" else "Max entrants allowed to join",
                        style = EventsType.BodySmall, color = EventsColors.TextSecondary,
                    )
                }
                if (s.cap == null) SecondaryButton("Set cap", vm::enableCap, Modifier.testTag("lottery_cap_on"), size = ButtonSize.SMALL)
                else TextLinkButton("No limit", vm::disableCap, Modifier.testTag("lottery_cap_off"))
            }
            s.cap?.let { cap -> Stepper(cap, vm::setCap, 1, 500, "Maximum on the waitlist", Modifier.testTag("lottery_cap")) }
            s.errors[Field.CAP]?.let { Text(it, style = EventsType.Caption, color = EventsColors.Danger) }
            if (s.message != null) Text(s.message!!, style = EventsType.BodySmall, color = EventsColors.Danger, modifier = Modifier.testTag("lottery_message"))
        }
        ActionBar {
            PrimaryButton(
                if (s.editing) "Save changes" else "Publish lottery", { vm.publish(onPublished) },
                Modifier.weight(1f).testTag("lottery_publish"), loading = s.saving,
            )
        }
    }
}

/** All lotteries the owner runs (Figma O-LOTTERIES). */
@Composable
fun OwnerLotteriesScreen(container: AppContainer, onOpen: (String) -> Unit, onNew: () -> Unit) {
    val vm = containerViewModel(container) { OwnerLotteriesViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val (drawn, live) = s.lotteries.partition { it.status == LotteryStatus.DRAWN }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Lotteries", style = EventsType.DisplaySmall, color = EventsColors.Coral, modifier = Modifier.weight(1f))
            }
        }
        if (!s.loading && s.lotteries.isEmpty()) item { DuskCard { EmptyState(R.drawable.ic_ticket, "No lotteries yet", "Create a lottery for one of your campsites.") } }
        if (live.isNotEmpty()) item { SectionTitle("Live Now · ${live.size}") }
        items(live, key = { it.id }) { lot -> OwnerLotteryCard(lot, s.campsites[lot.campsiteId], s.now, onOpen) }
        if (drawn.isNotEmpty()) item { SectionTitle("Recently Drawn", Modifier.padding(top = 4.dp)) }
        items(drawn, key = { it.id }) { lot -> OwnerLotteryCard(lot, s.campsites[lot.campsiteId], s.now, onOpen) }
        item { PrimaryButton("New lottery", onNew, Modifier.fillMaxWidth().padding(top = 4.dp).testTag("owner_new_lottery"), icon = R.drawable.ic_add) }
    }
}

/** Figma lottery row: campsite card with a LIVE / DRAWN tag and the close date and count. */
@Composable
private fun OwnerLotteryCard(lot: Lottery, site: Campsite?, now: Long, onOpen: (String) -> Unit) {
    val campsite = site ?: Campsite(id = lot.campsiteId, name = lot.campsiteName, location = "", ownerId = lot.ownerId, ownerName = "", maxOccupancy = 0)
    CampsiteListCard(campsite, { onOpen(lot.id) }, Modifier.testTag("owner_lottery_${lot.id}"), thumb = 72.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                lot.status == LotteryStatus.DRAWN -> StatusTag("Drawn", TagTone.BLUE)
                lot.isRegistrationOpen(now) -> StatusTag("Live", TagTone.GREEN)
                else -> LotteryStatusTag(lot, now)
            }
            Text(
                if (lot.status == LotteryStatus.DRAWN) "${formatDay(lot.drawnAt ?: lot.registrationEnd)} · ${lot.winnerCount} winners notified"
                else "Closes ${formatDay(lot.registrationEnd)} · ${lot.waitlistCap?.let { "${lot.entrantCount} / $it" } ?: "${lot.entrantCount}"} joined",
                style = EventsType.Caption, color = EventsColors.TextSecondary, maxLines = 1,
            )
        }
    }
}

/**
 * A lottery's waiting list and winners with their booked nights (Figma O-WAITLIST,
 * US 02.09.01, US 02.05.01, US 02.06.01).
 *
 * @param onEdit opens the lottery form in edit mode (US 02.04.06); hidden after the draw.
 */
@Composable
fun WaitlistScreen(
    container: AppContainer,
    lotteryId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit = {},
    onNotify: (String) -> Unit = {},
    onChat: (Chat) -> Unit = {},
    ownerName: String = "",
) {
    val vm = containerViewModel(container, key = "waitlist-$lotteryId") { WaitlistViewModel(it, lotteryId) }
    val s by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    val lot = s.lottery
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackTopBar(lot?.let { "Waitlist · ${it.campsiteName}" } ?: "Waitlist", onBack) {
            if (lot != null && lot.status != LotteryStatus.DRAWN) GlassIconButton(R.drawable.ic_edit, "Edit lottery", { onEdit(lot.id) }, Modifier.testTag("waitlist_edit"))
        }
        if (s.loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = EventsColors.Coral) }; return@Column }
        if (lot == null) { EmptyState(R.drawable.ic_info, "Lottery not found", "It may have been removed."); return@Column }
        val winners = s.entries.filter { it.status == EntryStatus.WON }
        // Figma O-WAITLIST: two tabs, Waitlist (everyone else) and Winners.
        val waitlist = s.entries.filter { it.status != EntryStatus.WON }
        val shown = if (tab == 0) waitlist else winners
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                DuskCard(Modifier.fillMaxWidth().testTag("waitlist_summary"), padding = PaddingValues(14.dp), spacing = 8.dp) {
                    Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        val cap = lot.waitlistCap
                        Text(if (cap != null) "${s.entries.size} of $cap spots filled" else "${s.entries.size} joined", style = EventsType.BodyStrong, color = EventsColors.Text, modifier = Modifier.weight(1f))
                        if (lot.status == LotteryStatus.DRAWN) StatusTag("Drawn", TagTone.GREY)
                        else if (lot.isRegistrationOpen(s.now)) StatusTag("Open", TagTone.GREEN)
                        else LotteryStatusTag(lot, s.now)
                    }
                    lot.waitlistCap?.takeIf { it > 0 }?.let { cap ->
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(EventsColors.PrimaryContainer)) {
                            Box(Modifier.fillMaxWidth((s.entries.size.toFloat() / cap).coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(EventsColors.Primary))
                        }
                    }
                    MetaItem(R.drawable.ic_calendar, "Open ${formatRange(lot.registrationStart, lot.registrationEnd)}")
                    MetaItem(R.drawable.ic_trophy, "${if (lot.status == LotteryStatus.DRAWN) "Drawn" else "Auto-draw"} ${formatDayTime(lot.drawnAt ?: lot.registrationEnd)} · ${lot.winnerCount} winners")
                    if (lot.hasBookingPeriod) MetaItem(R.drawable.ic_event_check, "Stays ${formatStay(lot.bookingStartDay!!, lot.bookingEndDay!!)}")
                    if (lot.isDrawDue(s.now)) DrawNowButton(s.drawing) { vm.drawNow() }
                    if (lot.spotsFree > 0) Text("${lot.spotsFree} spot${if (lot.spotsFree == 1) "" else "s"} given back; offering ${if (lot.spotsFree == 1) "it" else "them"} to the next entrants…", style = EventsType.BodySmall, color = EventsColors.Amber)
                }
            }
            item {
                SegmentedControl(listOf("Waitlist · ${waitlist.size}", "Winners · ${winners.size}"), tab, { tab = it })
            }
            if (shown.isEmpty()) item {
                DuskCard {
                    EmptyState(
                        R.drawable.ic_group,
                        if (tab == 0) (if (s.entries.isEmpty()) "No one has joined yet" else "Everyone was drawn") else if (lot.status != LotteryStatus.DRAWN) "Not drawn yet" else "No winners",
                        if (tab == 0) "Share your promo QR code so campers can find this lottery." else "Winners are drawn automatically on ${formatDayTime(lot.registrationEnd)}.",
                    )
                }
            }
            if (shown.isNotEmpty()) item {
                Column(Modifier.fillMaxWidth().clip(EventsShapes.Large).background(EventsColors.Surface).padding(6.dp)) {
                    shown.forEach { e ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp).testTag("entry_${e.entrantId}"),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val tone = avatarTones[(e.entrantId.hashCode() and 0x7fffffff) % avatarTones.size]
                            Avatar(e.entrantName.split(" ").take(2).joinToString("") { it.take(1).uppercase() }, size = 40.dp, bg = tone.first, fg = tone.second)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(e.entrantName, style = EventsType.BodyStrong, color = EventsColors.Text, maxLines = 1)
                                Text(
                                    // Figma: "Party of 4 · joined Oct 2" (party size is known once they book).
                                    listOfNotNull(
                                        e.guests?.let { "Party of $it" },
                                        if (e.isBooked) "booked ${formatStayShort(e.checkInDay!!, e.checkOutDay!!).substringBefore(" ·")}"
                                        else if (e.status == EntryStatus.WON) "hasn’t picked dates yet" else "joined ${formatDay(e.joinedAt)}",
                                    ).joinToString(" · ").replaceFirstChar { it.uppercase() },
                                    style = EventsType.BodySmall, color = EventsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            // Individual messages are only for entrants selected in this lottery.
                            if (e.status == EntryStatus.WON) RoundIconButton(
                                R.drawable.ic_chat, "Message ${e.entrantName}", EventsColors.PrimaryContainer, EventsColors.Text, Modifier.testTag("waitlist_chat_${e.entrantId}"),
                            ) { onChat(Chat(Chat.idFor(lot.campsiteId, e.entrantId), lot.campsiteId, lot.campsiteName, lot.ownerId, ownerName, e.entrantId, e.entrantName, lotteryKey = Paths.keyOf(lot.id))) }
                            when (e.status) {
                                EntryStatus.WAITING -> StatusTag("Waiting", TagTone.BLUE)
                                EntryStatus.WON -> StatusTag("Winner", TagTone.GREEN)
                                EntryStatus.LOST -> StatusTag("Not selected", TagTone.GREY)
                                EntryStatus.DECLINED -> StatusTag("Declined", TagTone.AMBER)
                                EntryStatus.CANCELLED -> StatusTag("Cancelled", TagTone.AMBER)
                                EntryStatus.REJECTED -> StatusTag("Rejected", TagTone.RED)
                            }
                        }
                    }
                }
            }
        }
        // Figma O-WAITLIST: "Notify waitlist" across the bottom.
        if (s.entries.isNotEmpty()) ActionBar {
            PrimaryButton("Notify waitlist", { onNotify(lot.id) }, Modifier.weight(1f).testTag("waitlist_notify"), icon = R.drawable.ic_send)
        }
    }
}

/**
 * Reservations at my campsites (Figma O-BOOKINGS): a strip of booked dates (US 02.06.01) and
 * the active (US 02.07.01) and past (US 02.07.02) reservations, optionally for one campsite.
 *
 * @param campsiteId show one campsite only (opened from Manage), or null for the Bookings tab.
 * @param onBack shown as a back button when opened from a campsite.
 */
@Composable
fun OwnerBookingsScreen(
    container: AppContainer,
    profile: UserProfile? = null,
    campsiteId: String? = null,
    onBack: (() -> Unit)? = null,
    onChat: (Chat) -> Unit = {},
) {
    val vm = containerViewModel(container, key = "owner-bookings-${campsiteId.orEmpty()}") { OwnerBookingsViewModel(it, campsiteId) }
    val s by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<Reservation?>(null) }
    var startReject by remember { mutableStateOf(false) }
    val shown = s.reservations.filter { if (s.siteFilter == null) true else it.campsiteId == s.siteFilter }
    val ended = s.ended.filter { if (s.siteFilter == null) true else it.campsiteId == s.siteFilter }
    val (active, past) = shown.partition { it.isActive(s.today) }
    val list = when (tab) { 0 -> active.sortedBy { it.checkInDay }; 1 -> past.sortedByDescending { it.checkInDay }; else -> ended }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
    if (onBack != null) BackTopBar(s.campsites.firstOrNull { it.id == campsiteId }?.let { "Bookings · ${it.name}" } ?: "Bookings", onBack)
    LazyColumn(
        Modifier.fillMaxSize().testTag("owner_bookings"),
        contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack == null) item {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Bookings", style = EventsType.DisplaySmall, color = EventsColors.Coral, modifier = Modifier.weight(1f))
            }
        }
        if (campsiteId == null && s.campsites.size > 1) item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EventsChip("All", if (s.siteFilter == null) ChipStyle.CORAL else ChipStyle.GLASS, { vm.filter(null) }, Modifier.testTag("bookings_site_all"))
                s.campsites.forEach { site ->
                    EventsChip(site.name, if (s.siteFilter == site.id) ChipStyle.CORAL else ChipStyle.GLASS, { vm.filter(site.id) }, Modifier.testTag("bookings_site_${site.id}"))
                }
            }
        }
        item { BookedDatesStrip(s.today, shown) }
        item { SegmentedControl(listOf("Active · ${active.size}", "Past · ${past.size}", "Cancelled · ${ended.size}"), tab, { tab = it }, tagPrefix = "bookings_tab_") }
        if (!s.loading && list.isEmpty()) item {
            DuskCard {
                EmptyState(
                    R.drawable.ic_event_check,
                    when (tab) { 0 -> "No active bookings"; 1 -> "No past bookings"; else -> "Nothing cancelled" },
                    when (tab) {
                        0 -> "When lottery winners pick their nights, their stays appear here."
                        1 -> "Finished stays will be listed here."
                        else -> "Reservations guests cancel or you reject show up here."
                    },
                )
            }
        }
        if (list.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().clip(EventsShapes.Large).background(EventsColors.Surface).padding(6.dp)) {
                list.forEach { r ->
                    val site = s.campsites.firstOrNull { it.id == r.campsiteId }
                    GuestReservationRow(
                        r, showCampsite = s.siteFilter == null && s.campsites.size > 1, today = s.today, onClick = { open = r },
                        onChat = { onChat(Chat(Chat.idFor(r.campsiteId, r.entrantId), r.campsiteId, r.campsiteName, r.ownerId, site?.ownerName ?: profile?.name.orEmpty(), r.entrantId, r.entrantName, lotteryKey = Paths.keyOf(r.lotteryId))) },
                        onReject = if (r.status == EntryStatus.WON && r.isActive(s.today)) ({ open = r; startReject = true }) else null,
                    )
                }
            }
        }
    }
    }
    open?.let { r ->
        val site = s.campsites.firstOrNull { it.id == r.campsiteId }
        GuestReservationDialog(
            r, site, s.today, s.working, startRejecting = startReject, onDismiss = { open = null; startReject = false },
            onMessage = {
                open = null
                onChat(Chat(Chat.idFor(r.campsiteId, r.entrantId), r.campsiteId, r.campsiteName, r.ownerId, site?.ownerName ?: profile?.name.orEmpty(), r.entrantId, r.entrantName, lotteryKey = Paths.keyOf(r.lotteryId)))
            },
            onReject = { reason -> vm.reject(r, reason); open = null; startReject = false },
        )
    }
}

/** A guest's reservation for the owner: details, message the guest, or reject with a reason (refund note). */
@Composable
private fun GuestReservationDialog(r: Reservation, site: Campsite?, today: Long, working: Boolean, startRejecting: Boolean = false, onDismiss: () -> Unit, onMessage: () -> Unit, onReject: (String) -> Unit) {
    var rejecting by remember { mutableStateOf(startRejecting) }
    var reason by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        DuskCard(Modifier.fillMaxWidth().testTag("guest_dialog"), padding = PaddingValues(20.dp), spacing = 10.dp) {
            Text(r.entrantName.ifBlank { "Guest" }, style = EventsType.Title, color = EventsColors.Text)
            ReservationTag(r, today)
            DetailRow(R.drawable.ic_tent, "Campsite", r.campsiteName)
            DetailRow(R.drawable.ic_calendar, "Stay", formatStay(r.checkInDay, r.checkOutDay))
            DetailRow(R.drawable.ic_group, "Guests", "${r.guests}")
            if (site != null) DetailRow(R.drawable.ic_money, "Pays at check-in", "$${StayPrice(r.nights, site.pricePerNight).total}")
            if (r.status == EntryStatus.REJECTED) Text("Rejected: ${r.note.ifBlank { "no reason given" }}", style = EventsType.BodySmall, color = EventsColors.Danger)
            if (rejecting) {
                EventsTextField(reason, { if (it.length <= 200) reason = it }, "Reason (sent to the guest)", Modifier.testTag("reject_reason"), "e.g. The site is closed for repairs that week", singleLine = false, minLines = 2)
                Text("Nothing has been paid, so there's nothing to refund. The spot is offered to the next entrant.", style = EventsType.Caption, color = EventsColors.TextTertiary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Back", { rejecting = false }, Modifier.weight(1f), size = ButtonSize.SMALL)
                    DangerButton("Reject", { onReject(reason.trim()) }, Modifier.weight(1f).testTag("reject_confirm"), size = ButtonSize.SMALL, loading = working)
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Message", onMessage, Modifier.weight(1f).testTag("guest_message"), icon = R.drawable.ic_chat, size = ButtonSize.SMALL)
                if (r.status == EntryStatus.WON && r.isActive(today)) DangerButton("Reject", { rejecting = true }, Modifier.weight(1f).testTag("guest_reject"), size = ButtonSize.SMALL)
            }
        }
    }
}

/** Figma "Booked dates" card: the next two weeks with a dot on every booked night. */
@Composable
private fun BookedDatesStrip(today: Long, reservations: List<Reservation>) {
    val booked = remember(reservations) { reservations.flatMap { it.nightDays() }.toSet() }
    val days = (today until today + 14).toList()
    DuskCard(Modifier.fillMaxWidth().testTag("booked_dates"), padding = PaddingValues(14.dp), spacing = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Booked dates · ${monthLabel(today)}", style = EventsType.BodyStrong, color = EventsColors.Text, modifier = Modifier.weight(1f))
            Text("${days.count { it in booked }} of 14 nights", style = EventsType.Caption, color = EventsColors.TextSecondary)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach { d ->
                val date = java.time.LocalDate.ofEpochDay(d)
                val on = d in booked
                Column(
                    Modifier.width(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (d == today) EventsColors.PrimaryContainer else Color.Transparent).padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.CANADA), style = EventsType.Caption, color = EventsColors.TextSecondary)
                    Text("${date.dayOfMonth}", style = EventsType.BodyStrong, color = EventsColors.Text)
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (on) EventsColors.Coral else Color.Transparent))
                }
            }
        }
    }
}

private fun monthLabel(day: Long): String =
    java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.CANADA).format(java.time.LocalDate.ofEpochDay(day))

/** One guest's stay (Figma O-BOOKINGS row): avatar, name, dates · guests, and message / reject buttons. */
@Composable
private fun GuestReservationRow(r: Reservation, showCampsite: Boolean, today: Long, onClick: () -> Unit, onChat: () -> Unit = {}, onReject: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(EventsShapes.Medium).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 12.dp).testTag("reservation_${r.lotteryId}_${r.entrantId}"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val tone = avatarTones[(r.entrantId.hashCode() and 0x7fffffff) % avatarTones.size]
        Avatar(initialsOf(r.entrantName), size = 40.dp, bg = tone.first, fg = tone.second)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(r.entrantName.ifBlank { "Guest" }, style = EventsType.BodyStrong, color = EventsColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${formatStayShort(r.checkInDay, r.checkOutDay).substringBefore(" ·")} · ${r.guests} guest${if (r.guests == 1) "" else "s"}",
                style = EventsType.BodySmall, color = EventsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (showCampsite) Text(r.campsiteName, style = EventsType.Caption, color = EventsColors.TextTertiary, maxLines = 1)
            if (r.status != EntryStatus.WON) ReservationTag(r, today)
        }
        RoundIconButton(R.drawable.ic_chat, "Message ${r.entrantName}", EventsColors.PrimaryContainer, EventsColors.Text, Modifier.testTag("guest_chat_${r.entrantId}"), onChat)
        if (onReject != null) RoundIconButton(R.drawable.ic_close, "Reject", EventsColors.DangerContainer, EventsColors.Danger, Modifier.testTag("guest_reject_${r.entrantId}"), onReject)
    }
}

@Composable
private fun RoundIconButton(icon: Int, label: String, bg: Color, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.size(34.dp).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        EventsIcon(icon, size = 16.dp, tint = tint, contentDescription = label)
    }
}

private fun initialsOf(name: String) = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }

/** Avatar fills and initials colours used on the Figma waitlist rows. */
private val avatarTones = listOf(
    EventsColors.CoralContainer to EventsColors.Coral,
    EventsColors.PrimaryContainer to EventsColors.Primary,
    EventsColors.GreenContainer to EventsColors.Green,
    EventsColors.AmberContainer to EventsColors.Amber,
)

private val sharing = SharingStarted.WhileSubscribed(5_000)

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
        OwnerCampsitesState(false, sites.map { CampsiteWithLottery(it, currentLottery(byCampsite[it.id].orEmpty())) }, now)
    }.stateIn(viewModelScope, sharing, OwnerCampsitesState())
}

/** State of the campsite registration / edit form. */
data class CampsiteFormState(
    val loading: Boolean = false,
    val name: String = "",
    val location: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val maxOccupancy: Int = 4,
    val amenities: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val price: String = "",
    val imageBase64: String? = null,
    val rules: String = "",
    val cancellation: CancellationPolicy = CancellationPolicy.FLEXIBLE,
    val step: Int = 1,
    val errors: Map<Field, String> = emptyMap(),
    val saving: Boolean = false,
    val message: String? = null,
) {
    /** Price as a number (blank counts as free). */
    val priceValue: Int get() = price.trim().toIntOrNull() ?: if (price.isBlank()) 0 else -1
}

/** Tag suggestions offered on the form (from the Figma "Add tags" sheet). */
val SuggestedTags = listOf("Lakeside", "Mountain", "Forest", "Riverside", "Pet friendly", "Fire pits", "Accessible", "Quiet hours", "Family friendly", "Wi-Fi")

/**
 * Registers a new campsite or edits an existing one.
 *
 * @param campsiteId the campsite to edit, or null to register a new one.
 */
class CampsiteFormViewModel(private val container: AppContainer, private val campsiteId: String?) : ViewModel() {
    private val _state = MutableStateFlow(CampsiteFormState(loading = campsiteId != null))

    /** Form state. */
    val state: StateFlow<CampsiteFormState> = _state.asStateFlow()

    private var original: Campsite? = null

    init {
        if (campsiteId != null) viewModelScope.launch {
            val site = container.campsites.observeCampsite(campsiteId).first()
            original = site
            _state.value = if (site == null) CampsiteFormState(message = "This campsite no longer exists.") else CampsiteFormState(
                name = site.name, location = site.location, latitude = site.latitude, longitude = site.longitude,
                maxOccupancy = site.maxOccupancy, amenities = site.amenities, description = site.description, tags = site.tags,
                price = site.pricePerNight.toString(), imageBase64 = site.imageBase64, rules = site.rules, cancellation = site.cancellation,
            )
        }
    }

    /** True when editing an existing campsite. */
    val isEditing: Boolean get() = campsiteId != null

    /** Applies [change] to the form and clears errors for the touched [fields]. */
    fun update(vararg fields: Field, change: (CampsiteFormState) -> CampsiteFormState) {
        _state.value = change(_state.value).let { it.copy(errors = it.errors - fields.toSet()) }
    }

    /** Adds or removes a tag. */
    fun toggleTag(tag: String) = update { s -> s.copy(tags = if (tag in s.tags) s.tags - tag else s.tags + tag) }

    /** Adds a custom tag typed by the owner. */
    fun addCustomTag(tag: String) {
        val t = tag.trim().replaceFirstChar { it.uppercase() }
        if (t.isNotEmpty() && t !in _state.value.tags) update { it.copy(tags = it.tags + t) }
    }

    /** Sets the map pin. */
    fun setPin(lat: Double, lng: Double) = update(Field.LOCATION) { it.copy(latitude = lat, longitude = lng) }

    /** Step 1's "Next: availability & price": checks the details, then shows step 2 (Figma O-AVAILABILITY). */
    fun next() {
        val s = _state.value
        val errors = CampsiteValidator.validate(s.name, s.location, s.maxOccupancy, 0) - Field.PRICE
        _state.value = if (errors.isNotEmpty()) s.copy(errors = errors) else s.copy(step = 2, message = null)
    }

    /** Back from step 2 to step 1; false when already on step 1. */
    fun back(): Boolean {
        if (_state.value.step == 2) { _state.value = _state.value.copy(step = 1, message = null); return true }
        return false
    }

    /**
     * Validates and saves.
     *
     * @param owner the owner's profile (for the owner name shown on the listing).
     * @param onSaved called with the campsite ID after saving.
     */
    fun save(owner: UserProfile, onSaved: (String) -> Unit) {
        val s = _state.value
        val errors = CampsiteValidator.validate(s.name, s.location, s.maxOccupancy, s.priceValue)
        if (errors.isNotEmpty()) {
            _state.value = s.copy(errors = errors, step = if (errors.keys.any { it != Field.PRICE }) 1 else 2)
            return
        }
        val base = original ?: Campsite(
            id = "", ownerId = container.userId, ownerName = owner.name, name = "", location = "", maxOccupancy = 1,
            createdAt = container.clock.now(),
        )
        val site = base.copy(
            ownerName = owner.name, name = s.name.trim(), location = s.location.trim(), latitude = s.latitude, longitude = s.longitude,
            maxOccupancy = s.maxOccupancy, amenities = s.amenities.trim(), description = s.description.trim(), tags = s.tags,
            pricePerNight = s.priceValue, imageBase64 = s.imageBase64, rules = s.rules.trim(), cancellation = s.cancellation,
        )
        _state.value = s.copy(saving = true, message = null)
        viewModelScope.launch {
            runCatching { if (original == null) container.campsites.createCampsite(site) else site.id.also { container.campsites.updateCampsite(site) } }
                .onSuccess { id -> _state.value = _state.value.copy(saving = false); onSaved(id) }
                .onFailure { _state.value = _state.value.copy(saving = false, message = it.userMessage()) }
        }
    }
}

/** State of the Manage Campsite screen. */
data class ManageState(val loading: Boolean = true, val campsite: Campsite? = null, val lottery: Lottery? = null, val now: Long = 0L, val bookedCount: Int = 0, val drawing: Boolean = false)

/** One of my campsites with its QR code and current lottery (US 02.01.04, US 02.02.01). */
class ManageCampsiteViewModel(private val container: AppContainer, campsiteId: String) : ViewModel() {
    private val drawing = MutableStateFlow(false)

    /** Manage screen state. */
    val state: StateFlow<ManageState> = combine(
        container.campsites.observeCampsite(campsiteId),
        container.lotteries.observeLotteriesForCampsite(campsiteId),
        container.lotteries.observeReservationsForCampsite(campsiteId),
        nowTicker(container.clock, 15_000),
        drawing,
    ) { site, lotteries, reservations, now, d ->
        ManageState(false, site, currentLottery(lotteries), now, reservations.count { it.isActive(dayOf(now)) }, d)
    }
        .stateIn(viewModelScope, sharing, ManageState())

    /** Draws the current lottery's winners now (registration has closed). */
    fun drawNow() {
        val lot = state.value.lottery ?: return
        drawing.value = true
        viewModelScope.launch { runCatching { container.lotteries.runDrawIfDue(lot.id, container.clock.now()) }; drawing.value = false }
    }
}

/** State of the New / Edit Lottery form. */
data class CreateLotteryState(
    val loading: Boolean = false,
    val editing: Boolean = false,
    val original: Lottery? = null,
    val campsites: List<Campsite> = emptyList(),
    val selectedId: String? = null,
    val registrationStart: Long? = null,
    val registrationEnd: Long? = null,
    val bookingStartDay: Long? = null,
    val bookingEndDay: Long? = null,
    val winners: Int = 4,
    val cap: Int? = null,
    val errors: Map<Field, String> = emptyMap(),
    val saving: Boolean = false,
    val message: String? = null,
)

/**
 * Creates a lottery for one campsite (US 02.04.01) with a registration period (US 02.04.03),
 * booking period (US 02.04.04), number of winners (US 02.04.05) and optional waitlist cap
 * (US 02.04.07), or edits a published one (US 02.04.06).
 *
 * @param presetCampsiteId campsite chosen before opening the form, if any.
 * @param lotteryId lottery to edit, or null to create a new one.
 */
class CreateLotteryViewModel(private val container: AppContainer, presetCampsiteId: String?, lotteryId: String? = null) : ViewModel() {
    private val _state = MutableStateFlow(CreateLotteryState(selectedId = presetCampsiteId, editing = lotteryId != null, loading = lotteryId != null))

    /** Form state. */
    val state: StateFlow<CreateLotteryState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.campsites.observeCampsitesByOwner(container.userId).collect { sites ->
                _state.value = _state.value.copy(campsites = sites, selectedId = _state.value.selectedId ?: sites.firstOrNull()?.id)
            }
        }
        if (lotteryId != null) viewModelScope.launch {
            val lot = container.lotteries.observeLottery(lotteryId).first()
            _state.value = if (lot == null) {
                _state.value.copy(loading = false, message = "This lottery no longer exists.")
            } else {
                _state.value.copy(
                    loading = false, original = lot, selectedId = lot.campsiteId, registrationStart = lot.registrationStart,
                    registrationEnd = lot.registrationEnd, bookingStartDay = lot.bookingStartDay, bookingEndDay = lot.bookingEndDay,
                    winners = lot.winnerCount, cap = lot.waitlistCap,
                )
            }
        }
    }

    private fun edit(vararg cleared: Field, change: (CreateLotteryState) -> CreateLotteryState) {
        _state.value = change(_state.value).let { it.copy(errors = it.errors - cleared.toSet(), message = null) }
    }

    /** Chooses which campsite the lottery is for (new lotteries only). */
    fun select(id: String) = edit { it.copy(selectedId = id) }

    /** Sets when the waiting list opens. */
    fun setStart(millis: Long) = edit(Field.REG_START, Field.REG_END) { it.copy(registrationStart = millis) }

    /** Sets when the waiting list closes (the draw runs then). */
    fun setEnd(millis: Long) = edit(Field.REG_END, Field.BOOK_START) { it.copy(registrationEnd = millis) }

    /** Sets the booking period: first night and last check-out day (day numbers). */
    fun setBooking(startDay: Long, endDay: Long) = edit(Field.BOOK_START, Field.BOOK_END) { it.copy(bookingStartDay = startDay, bookingEndDay = endDay) }

    /** Sets the number of winners. */
    fun setWinners(n: Int) = edit(Field.WINNERS) { it.copy(winners = n) }

    /** Turns on a waitlist cap, starting at 50 or the number already on the list. */
    fun enableCap() = edit(Field.CAP) { it.copy(cap = maxOf(50, it.original?.entrantCount ?: 0)) }

    /** Removes the waitlist cap (anyone can join). */
    fun disableCap() = edit(Field.CAP) { it.copy(cap = null) }

    /** Sets the waitlist cap. */
    fun setCap(n: Int) = edit(Field.CAP) { it.copy(cap = n) }

    /**
     * Validates and publishes the new lottery, or saves the changes to an existing one.
     * @param onPublished called with the lottery's ID.
     */
    fun publish(onPublished: (String) -> Unit) {
        val s = _state.value
        val now = container.clock.now()
        val site = s.campsites.firstOrNull { it.id == s.selectedId }
        val errors = buildMap {
            putAll(LotteryValidator.validate(s.registrationStart, s.registrationEnd, s.winners, now))
            putAll(LotteryValidator.validateBooking(s.registrationEnd?.let(::dayOf), s.bookingStartDay, s.bookingEndDay))
            LotteryValidator.validateCap(s.cap)?.let { put(Field.CAP, it) }
        }
        if (site == null || errors.isNotEmpty()) {
            _state.value = s.copy(errors = errors, message = if (site == null) "Register a campsite first." else null)
            return
        }
        val base = s.original ?: Lottery(
            id = "", campsiteId = site.id, campsiteName = site.name, ownerId = container.userId,
            registrationStart = 0L, registrationEnd = 0L, winnerCount = 1,
        )
        val lottery = base.copy(
            registrationStart = s.registrationStart!!, registrationEnd = s.registrationEnd!!, winnerCount = s.winners,
            waitlistCap = s.cap, bookingStartDay = s.bookingStartDay, bookingEndDay = s.bookingEndDay,
        )
        _state.value = s.copy(saving = true, message = null)
        viewModelScope.launch {
            runCatching {
                if (s.original == null) container.lotteries.createLottery(lottery).also { id ->
                    // "Notify me when a lottery opens": followers hear about it now.
                    runCatching { container.social.notifyFollowers(lottery.copy(id = id), now) }
                }
                else lottery.id.also { container.lotteries.updateLottery(lottery, now) }
            }
                .onSuccess { _state.value = _state.value.copy(saving = false); onPublished(it) }
                .onFailure { _state.value = _state.value.copy(saving = false, message = it.userMessage()) }
        }
    }
}

/** State of the owner's lotteries list. */
data class OwnerLotteriesState(
    val loading: Boolean = true,
    val lotteries: List<Lottery> = emptyList(),
    val now: Long = 0L,
    val campsites: Map<String, Campsite> = emptyMap(),
)

/** All lotteries the owner runs. */
class OwnerLotteriesViewModel(container: AppContainer) : ViewModel() {
    /** Lotteries list state. */
    val state: StateFlow<OwnerLotteriesState> = combine(
        container.lotteries.observeLotteriesByOwner(container.userId), nowTicker(container.clock),
        container.campsites.observeCampsitesByOwner(container.userId),
    ) { list, now, sites -> OwnerLotteriesState(false, list, now, sites.associateBy { it.id }) }.stateIn(viewModelScope, sharing, OwnerLotteriesState())
}

/** State of the waiting-list screen. */
data class WaitlistState(val loading: Boolean = true, val lottery: Lottery? = null, val entries: List<WaitlistEntry> = emptyList(), val now: Long = 0L, val drawing: Boolean = false)

/** A lottery's waiting list and, after the draw, its winners (US 02.09.01, US 02.05.01). */
class WaitlistViewModel(private val container: AppContainer, private val lotteryId: String) : ViewModel() {
    private val drawing = MutableStateFlow(false)

    /** Waiting-list state. */
    val state: StateFlow<WaitlistState> = combine(
        container.lotteries.observeLottery(lotteryId),
        container.lotteries.observeEntries(lotteryId),
        nowTicker(container.clock, 15_000),
        drawing,
    ) { lot, entries, now, d -> WaitlistState(false, lot, entries, now, d) }.stateIn(viewModelScope, sharing, WaitlistState())

    /** Draws the winners now (registration has closed). */
    fun drawNow() {
        drawing.value = true
        viewModelScope.launch { runCatching { container.lotteries.runDrawIfDue(lotteryId, container.clock.now()) }; drawing.value = false }
    }
}

/** State of the owner's Bookings screen. */
data class OwnerBookingsState(
    val loading: Boolean = true,
    val reservations: List<Reservation> = emptyList(),
    val campsites: List<Campsite> = emptyList(),
    val siteFilter: String? = null,
    val today: Long = 0L,
    val ended: List<Reservation> = emptyList(),
    val working: Boolean = false,
)

/**
 * Reservations at the owner's campsites (US 02.06.01, US 02.07.01, US 02.07.02).
 *
 * @param campsiteId limit to one campsite, or null for all of them (filterable with chips).
 */
class OwnerBookingsViewModel(private val container: AppContainer, private val campsiteId: String? = null) : ViewModel() {
    private val filter = MutableStateFlow(campsiteId)
    private val working = MutableStateFlow(false)

    /** Bookings state. */
    val state: StateFlow<OwnerBookingsState> = combine(
        if (campsiteId != null) container.lotteries.observeReservationsForCampsite(campsiteId)
        else container.lotteries.observeReservationsForOwner(container.userId),
        container.campsites.observeCampsitesByOwner(container.userId),
        combine(filter, working) { f, w -> f to w },
        nowTicker(container.clock, 60_000),
        container.lotteries.observeEndedReservationsForOwner(container.userId),
    ) { list, sites, (f, w), now, ended ->
        OwnerBookingsState(false, list, sites, f, dayOf(now), if (campsiteId == null) ended else ended.filter { it.campsiteId == campsiteId }, w)
    }.stateIn(viewModelScope, sharing, OwnerBookingsState(siteFilter = campsiteId))

    /** Rejects a guest's reservation with [reason]; the guest is told and the spot reopens. */
    fun reject(r: Reservation, reason: String) {
        working.value = true
        viewModelScope.launch {
            runCatching { container.lotteries.rejectReservation(r.lotteryId, r.entrantId, reason, container.clock.now()) }
            working.value = false
        }
    }

    /** Shows one campsite's bookings, or all with null (ignored when opened for one campsite). */
    fun filter(id: String?) { if (campsiteId == null) filter.value = id }
}
