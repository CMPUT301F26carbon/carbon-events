/*
 * CampsiteForm.kt (Rayner, Aditya)
 *
 * Registering and editing a campsite: details, map pin, photo, tags, rules and price
 * (US 02.01.01, US 02.01.02, US 02.01.03).
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.foundation.layout.ColumnScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
