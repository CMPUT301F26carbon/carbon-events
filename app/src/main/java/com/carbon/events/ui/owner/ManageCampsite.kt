/*
 * ManageCampsite.kt (Rayner, Aditya)
 *
 * One campsite for its owner: the screen after publishing it, the promo QR code, and the
 * manage page with its lottery and quick actions (US 02.01.04, US 02.02.01).
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.launch

private val sharing = SharingStarted.WhileSubscribed(5_000)

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
internal fun DrawNowButton(working: Boolean, onClick: () -> Unit) {
    Text("Registration closed. Winners are drawn on your phone, so draw them now.", style = EventsType.BodySmall, color = EventsColors.OnPrimaryContainer)
    PrimaryButton("Draw winners now", onClick, Modifier.fillMaxWidth().testTag("draw_now"), icon = R.drawable.ic_trophy, size = ButtonSize.SMALL, loading = working)
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
        container.lotteries.observeReservationsForOwner(container.userId).map { list -> list.filter { it.campsiteId == campsiteId } },
        nowTicker(container.clock, 15_000),
        drawing,
    ) { site, lotteries, reservations, now, d ->
        ManageState(false, site, currentLottery(lotteries, now), now, reservations.count { it.isActive(dayOf(now)) }, d)
    }
        .stateIn(viewModelScope, sharing, ManageState())

    /** Draws the current lottery's winners now (registration has closed). */
    fun drawNow() {
        val lot = state.value.lottery ?: return
        drawing.value = true
        viewModelScope.launch { runCatching { container.lotteries.runDrawIfDue(lot.id, container.clock.now()) }; drawing.value = false }
    }
}
