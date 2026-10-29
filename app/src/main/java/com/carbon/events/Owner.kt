/*
 * Owner.kt (Rayner)
 *
 * Owner side: my campsites, register / edit, the promo QR, and running lotteries
 * (create, waitlist, winners).
 */
package com.carbon.events

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
    const val INBOX = "owner/inbox"
    const val PROFILE = "owner/profile"
    const val PROFILE_EDIT = "owner/profile/edit"
    const val NEW = "owner/new"
    const val EDIT = "owner/edit/{id}"
    const val PUBLISHED = "owner/published/{id}"
    const val MANAGE = "owner/campsite/{id}"
    const val NEW_LOTTERY = "owner/lottery/new?campsiteId={campsiteId}"
    const val WAITLIST = "owner/waitlist/{id}"

    fun edit(id: String) = "owner/edit/$id"
    fun published(id: String) = "owner/published/$id"
    fun manage(id: String) = "owner/campsite/$id"
    fun newLottery(campsiteId: String? = null) = "owner/lottery/new?campsiteId=${campsiteId.orEmpty()}"
    fun waitlist(id: String) = "owner/waitlist/$id"
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
                        OwnerCampsitesScreen(container, profile, onAdd = { nav.navigate(OwnerRoutes.NEW) }, onOpen = { nav.navigate(OwnerRoutes.manage(it)) }, onInbox = { nav.goToTab(OwnerRoutes.INBOX) })
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
                            onBookings = { nav.goToTab(OwnerRoutes.BOOKINGS) }, onNotify = { nav.goToTab(OwnerRoutes.INBOX) },
                        )
                    }
                    composable(OwnerRoutes.NEW_LOTTERY, arguments = listOf(navArgument("campsiteId") { type = NavType.StringType; defaultValue = "" })) {
                        val preset = it.arguments?.getString("campsiteId")?.takeIf { s -> s.isNotBlank() }
                        CreateLotteryScreen(container, preset, onBack = { nav.popBackStack() }, onPublished = { lotId ->
                            nav.navigate(OwnerRoutes.waitlist(lotId)) { popUpTo(OwnerRoutes.NEW_LOTTERY) { inclusive = true } }
                        })
                    }
                    composable(OwnerRoutes.LOTTERIES) {
                        OwnerLotteriesScreen(container, onOpen = { nav.navigate(OwnerRoutes.waitlist(it)) }, onNew = { nav.navigate(OwnerRoutes.newLottery()) }, onInbox = { nav.goToTab(OwnerRoutes.INBOX) })
                    }
                    composable(OwnerRoutes.WAITLIST, arguments = idArg) {
                        WaitlistScreen(container, it.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
                    }
                    composable(OwnerRoutes.BOOKINGS) {
                        TabPlaceholder("Bookings", R.drawable.ic_event_check, "No bookings yet", "Reservations from lottery winners will be listed here, with booked dates per campsite.")
                    }
                    composable(OwnerRoutes.INBOX) {
                        TabPlaceholder("Inbox", R.drawable.ic_chat, "No messages yet", "Chats with guests and the notifications you send will appear here.")
                    }
                    composable(OwnerRoutes.PROFILE) { ProfileScreen(profile, profileVm, onEdit = { nav.navigate(OwnerRoutes.PROFILE_EDIT) }) }
                    composable(OwnerRoutes.PROFILE_EDIT) { EditProfileScreen(profile, profileVm, onDone = { nav.popBackStack() }) }
                }
            }
        }
    }
}

/** My campsites (Figma O-HOME, US 02.03.01). */
@Composable
fun OwnerCampsitesScreen(container: AppContainer, profile: UserProfile, onAdd: () -> Unit, onOpen: (String) -> Unit, onInbox: () -> Unit = {}) {
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
                Spacer(Modifier.width(10.dp))
                GlassIconButton(R.drawable.ic_bell, "Inbox", onInbox)
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
    Column(Modifier.fillMaxSize().imePadding()) {
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
            if (s.latitude != null && s.longitude != null) MapPreview(s.latitude!!, s.longitude!!, s.location.ifBlank { "Campsite" }, height = 84.dp)
            SecondaryButton(if (s.latitude == null) "Set on map" else "Adjust pin", { showMap = true }, Modifier.fillMaxWidth(), icon = R.drawable.ic_map, size = ButtonSize.SMALL)

            Stepper(s.maxOccupancy, { v -> vm.update(Field.OCCUPANCY) { it.copy(maxOccupancy = v) } }, 1, 50, "Max occupancy", supporting = "Guests per site")
            s.errors[Field.OCCUPANCY]?.let { Text(it, style = EventsType.Caption, color = EventsColors.Danger) }
            EventsTextField(s.price, { v -> vm.update(Field.PRICE) { it.copy(price = v.filter(Char::isDigit)) } }, "Price per night (CAD)", Modifier.testTag("form_price"), "65", s.errors[Field.PRICE], R.drawable.ic_money, KeyboardType.Number)
            EventsTextField(s.amenities, { v -> vm.update { it.copy(amenities = v) } }, "Amenities", Modifier.testTag("form_amenities"), "Describe what’s on site: fire pits, water, toilets…", singleLine = false, minLines = 3)
            EventsTextField(s.description, { v -> vm.update { it.copy(description = v) } }, "Description", placeholder = "What makes this campsite special?", singleLine = false, minLines = 3)

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
            PrimaryButton(
                if (campsiteId == null) "Publish listing" else "Save changes", { vm.save(profile, onSaved) },
                Modifier.weight(1f).testTag("form_save"), icon = R.drawable.ic_check, loading = s.saving,
            )
        }
    }
    if (showMap) {
        MapPickerDialog(s.latitude?.let { lat -> s.longitude?.let { lat to it } }, onConfirm = { lat, lng -> vm.setPin(lat, lng); showMap = false }, onDismiss = { showMap = false })
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

/** View my campsite (Figma O-MANAGE): details, promo QR code and lottery (US 02.01.04, US 02.02.01). */
@Composable
fun ManageCampsiteScreen(
    container: AppContainer,
    campsiteId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onCreateLottery: () -> Unit,
    onWaitlist: (String) -> Unit,
    onBookings: () -> Unit = {},
    onNotify: () -> Unit = {},
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
                            Text("Lottery · ${formatRange(lot.registrationStart, lot.registrationEnd)}", style = EventsType.BodyStrong, color = EventsColors.Text, modifier = Modifier.weight(1f))
                            Box(
                                Modifier.height(28.dp).clip(EventsShapes.Pill).clickable(onClick = onCreateLottery).padding(horizontal = 18.dp).testTag("manage_create_lottery"),
                                contentAlignment = Alignment.Center,
                            ) { Text("New", style = EventsType.LabelSmall, color = EventsColors.Primary) }
                        }
                        DetailRow(R.drawable.ic_trophy, if (lot.status == LotteryStatus.DRAWN) "Drawn" else "Auto-draw", "${formatDay(lot.drawnAt ?: lot.registrationEnd)} · ${lot.winnerCount} winners")
                        DetailRow(R.drawable.ic_group, "Waitlist", lot.waitlistCap?.let { "${lot.entrantCount} / $it" } ?: "${lot.entrantCount} joined")
                        lot.waitlistCap?.takeIf { it > 0 }?.let { cap ->
                            val fraction = (lot.entrantCount.toFloat() / cap).coerceIn(0f, 1f)
                            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(EventsColors.Background)) {
                                Box(Modifier.fillMaxWidth(fraction).height(8.dp).clip(RoundedCornerShape(4.dp)).background(EventsColors.Primary))
                            }
                        }
                    }
                }
                // Quick actions (Figma: three 60dp tiles, radius 16, Dusk).
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(R.drawable.ic_group, "Waitlist · ${lot?.entrantCount ?: 0}", Modifier.weight(1f).testTag("manage_waitlist"), enabled = lot != null) { lot?.let { onWaitlist(it.id) } }
                    QuickAction(R.drawable.ic_event_check, "Booked · 0", Modifier.weight(1f), onClick = onBookings)
                    QuickAction(R.drawable.ic_send, "Notify", Modifier.weight(1f), onClick = onNotify)
                }
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

private const val MIN = 60_000L
private const val DAY = 24 * 60 * MIN

/**
 * New lottery form (Figma O-LOTTERY-NEW).
 *
 * @param presetCampsiteId campsite chosen before opening, or null to pick here.
 * @param onPublished called with the new lottery's ID.
 */
@Composable
fun CreateLotteryScreen(container: AppContainer, presetCampsiteId: String?, onBack: () -> Unit, onPublished: (String) -> Unit) {
    val vm = containerViewModel(container, key = "newlottery-${presetCampsiteId.orEmpty()}") { CreateLotteryViewModel(it, presetCampsiteId) }
    val s by vm.state.collectAsStateWithLifecycle()
    val now = { container.clock.now() }
    Column(Modifier.fillMaxSize().imePadding()) {
        BackTopBar("New Lottery", onBack, Modifier.statusBarsPadding())
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PageMargin).padding(top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Campsites in this lottery", style = EventsType.LabelSmall, color = EventsColors.TextSoft)
            if (s.campsites.isEmpty()) {
                Text("You don’t have any campsites yet. Register one first.", style = EventsType.BodySmall, color = EventsColors.TextSecondary)
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.campsites.forEach { site ->
                        val on = site.id == s.selectedId
                        EventsChip(site.name, if (on) ChipStyle.CORAL else ChipStyle.GLASS, { vm.select(site.id) }, Modifier.testTag("lottery_site_${site.id}"), if (on) R.drawable.ic_check else R.drawable.ic_tent)
                    }
                }
            }
            // Figma O-LOTTERY-NEW: fields sit straight on the sky, no cards.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DateTimeField(
                    "Waitlist opens (registration)", s.registrationStart, vm::setStart, error = s.errors[Field.REG_START], testTag = "start",
                    presets = listOf(TimePreset("Now") { now() }, TimePreset("Tomorrow") { now() + DAY }),
                )
                DateTimeField(
                    "Waitlist closes · lottery draw (auto)", s.registrationEnd, vm::setEnd, error = s.errors[Field.REG_END], testTag = "end",
                    presets = listOf(
                        TimePreset("In 2 min (demo)") { now() + 2 * MIN }, TimePreset("In 3 days") { now() + 3 * DAY },
                        TimePreset("In 1 week") { now() + 7 * DAY }, TimePreset("In 2 weeks") { now() + 14 * DAY },
                    ),
                )
            }
            Text("Winners are drawn automatically when the waitlist closes.", style = EventsType.BodySmall, color = EventsColors.TextSecondary)
            Stepper(s.winners, vm::setWinners, 1, 99, "Number of winners", supporting = "Drawn from the waitlist")
            s.errors[Field.WINNERS]?.let { Text(it, style = EventsType.Caption, color = EventsColors.Danger) }
            if (s.message != null) Text(s.message!!, style = EventsType.BodySmall, color = EventsColors.Danger, modifier = Modifier.testTag("lottery_message"))
        }
        ActionBar {
            PrimaryButton("Publish lottery", { vm.publish(onPublished) }, Modifier.weight(1f).testTag("lottery_publish"), loading = s.saving)
        }
    }
}

/** All lotteries the owner runs (Figma O-LOTTERIES). */
@Composable
fun OwnerLotteriesScreen(container: AppContainer, onOpen: (String) -> Unit, onNew: () -> Unit, onInbox: () -> Unit = {}) {
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
                GlassIconButton(R.drawable.ic_bell, "Inbox", onInbox)
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

/** A lottery's waiting list and winners (Figma O-WAITLIST, US 02.09.01, US 02.05.01). */
@Composable
fun WaitlistScreen(container: AppContainer, lotteryId: String, onBack: () -> Unit) {
    val vm = containerViewModel(container, key = "waitlist-$lotteryId") { WaitlistViewModel(it, lotteryId) }
    val s by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    val lot = s.lottery
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackTopBar(lot?.let { "Waitlist · ${it.campsiteName}" } ?: "Waitlist", onBack)
        if (s.loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = EventsColors.Coral) }; return@Column }
        if (lot == null) { EmptyState(R.drawable.ic_info, "Lottery not found", "It may have been removed."); return@Column }
        val winners = s.entries.filter { it.status == EntryStatus.WON }
        val shown = if (tab == 0) s.entries else winners
        LazyColumn(contentPadding = PaddingValues(start = PageMargin, end = PageMargin, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                }
            }
            item { SegmentedControl(listOf("Waitlist · ${s.entries.size}", "Winners · ${winners.size}"), tab, { tab = it }) }
            if (shown.isEmpty()) item {
                DuskCard {
                    EmptyState(
                        R.drawable.ic_group,
                        if (tab == 0) "No one has joined yet" else if (lot.status == LotteryStatus.DRAWN) "No winners" else "Not drawn yet",
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
                                Text("Joined ${formatDayTime(e.joinedAt)}", style = EventsType.BodySmall, color = EventsColors.TextSecondary, maxLines = 1)
                            }
                            when (e.status) {
                                EntryStatus.WAITING -> StatusTag("Waiting", TagTone.BLUE)
                                EntryStatus.WON -> StatusTag("Winner", TagTone.GREEN)
                                EntryStatus.LOST -> StatusTag("Not selected", TagTone.GREY)
                            }
                        }
                    }
                }
            }
        }
    }
}

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
        container.campsites.observeCampsitesByOwner(container.deviceId),
        container.lotteries.observeLotteriesByOwner(container.deviceId),
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
                price = site.pricePerNight.toString(), imageBase64 = site.imageBase64,
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
            _state.value = s.copy(errors = errors)
            return
        }
        val base = original ?: Campsite(
            id = "", ownerId = container.deviceId, ownerName = owner.name, name = "", location = "", maxOccupancy = 1,
            createdAt = container.clock.now(),
        )
        val site = base.copy(
            ownerName = owner.name, name = s.name.trim(), location = s.location.trim(), latitude = s.latitude, longitude = s.longitude,
            maxOccupancy = s.maxOccupancy, amenities = s.amenities.trim(), description = s.description.trim(), tags = s.tags,
            pricePerNight = s.priceValue, imageBase64 = s.imageBase64,
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
data class ManageState(val loading: Boolean = true, val campsite: Campsite? = null, val lottery: Lottery? = null, val now: Long = 0L)

/** One of my campsites with its QR code and current lottery (US 02.01.04, US 02.02.01). */
class ManageCampsiteViewModel(container: AppContainer, campsiteId: String) : ViewModel() {
    /** Manage screen state. */
    val state: StateFlow<ManageState> = combine(
        container.campsites.observeCampsite(campsiteId),
        container.lotteries.observeLotteriesForCampsite(campsiteId),
        nowTicker(container.clock),
    ) { site, lotteries, now -> ManageState(false, site, currentLottery(lotteries), now) }
        .stateIn(viewModelScope, sharing, ManageState())
}

/** State of the New Lottery form. */
data class CreateLotteryState(
    val campsites: List<Campsite> = emptyList(),
    val selectedId: String? = null,
    val registrationStart: Long? = null,
    val registrationEnd: Long? = null,
    val winners: Int = 4,
    val errors: Map<Field, String> = emptyMap(),
    val saving: Boolean = false,
    val message: String? = null,
)

/**
 * Creates a lottery for one campsite (US 02.04.01) with a registration period
 * (US 02.04.03) and number of winners (US 02.04.05).
 *
 * @param presetCampsiteId campsite chosen before opening the form, if any.
 */
class CreateLotteryViewModel(private val container: AppContainer, presetCampsiteId: String?) : ViewModel() {
    private val _state = MutableStateFlow(CreateLotteryState(selectedId = presetCampsiteId))

    /** Form state. */
    val state: StateFlow<CreateLotteryState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.campsites.observeCampsitesByOwner(container.deviceId).collect { sites ->
                _state.value = _state.value.copy(campsites = sites, selectedId = _state.value.selectedId ?: sites.firstOrNull()?.id)
            }
        }
    }

    /** Chooses which campsite the lottery is for. */
    fun select(id: String) { _state.value = _state.value.copy(selectedId = id) }

    /** Sets when the waiting list opens. */
    fun setStart(millis: Long) { _state.value = _state.value.copy(registrationStart = millis, errors = _state.value.errors - Field.REG_START - Field.REG_END) }

    /** Sets when the waiting list closes (the draw runs then). */
    fun setEnd(millis: Long) { _state.value = _state.value.copy(registrationEnd = millis, errors = _state.value.errors - Field.REG_END) }

    /** Sets the number of winners. */
    fun setWinners(n: Int) { _state.value = _state.value.copy(winners = n, errors = _state.value.errors - Field.WINNERS) }

    /**
     * Validates and publishes the lottery.
     * @param onPublished called with the new lottery's ID.
     */
    fun publish(onPublished: (String) -> Unit) {
        val s = _state.value
        val site = s.campsites.firstOrNull { it.id == s.selectedId }
        val errors = LotteryValidator.validate(s.registrationStart, s.registrationEnd, s.winners, container.clock.now())
        if (site == null || errors.isNotEmpty()) {
            _state.value = s.copy(errors = errors, message = if (site == null) "Register a campsite first." else null)
            return
        }
        val lottery = Lottery(
            id = "", campsiteId = site.id, campsiteName = site.name, ownerId = container.deviceId,
            registrationStart = s.registrationStart!!, registrationEnd = s.registrationEnd!!, winnerCount = s.winners,
        )
        _state.value = s.copy(saving = true, message = null)
        viewModelScope.launch {
            runCatching { container.lotteries.createLottery(lottery) }
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
        container.lotteries.observeLotteriesByOwner(container.deviceId), nowTicker(container.clock),
        container.campsites.observeCampsitesByOwner(container.deviceId),
    ) { list, now, sites -> OwnerLotteriesState(false, list, now, sites.associateBy { it.id }) }.stateIn(viewModelScope, sharing, OwnerLotteriesState())
}

/** State of the waiting-list screen. */
data class WaitlistState(val loading: Boolean = true, val lottery: Lottery? = null, val entries: List<WaitlistEntry> = emptyList(), val now: Long = 0L)

/** A lottery's waiting list and, after the draw, its winners (US 02.09.01, US 02.05.01). */
class WaitlistViewModel(container: AppContainer, lotteryId: String) : ViewModel() {
    /** Waiting-list state. */
    val state: StateFlow<WaitlistState> = combine(
        container.lotteries.observeLottery(lotteryId),
        container.lotteries.observeEntries(lotteryId),
        nowTicker(container.clock, 15_000),
    ) { lot, entries, now -> WaitlistState(false, lot, entries, now) }.stateIn(viewModelScope, sharing, WaitlistState())
}
