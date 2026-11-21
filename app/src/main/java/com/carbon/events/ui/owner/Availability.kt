/*
 * Availability.kt (Rayner)
 *
 * Availability & Price: price per night, shortest and longest stay, check-in and
 * check-out times.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
