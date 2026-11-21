/*
 * Inputs.kt (Aditya)
 *
 * Inputs from our Figma: text fields, steppers, segmented controls, the search bar, the
 * date picker field and the date-range field used for booking periods and stays.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Stepper: 36dp circles (#3E3A8A minus, #B9A9FF plus), SemiBold 16 value, 14dp gaps.
 *
 * @param icon an optional tile on the left (Figma "Waitlist cap" row).
 * @param typeTag when set, the number can also be typed with the number keyboard (Figma
 *   O-LOTTERY-NEW-WINNERS-KB, O-LOTTERY-NEW-CAP-KB); the field gets this test tag. A typed
 *   number is kept between [min] and [max], and clearing the field means [min].
 */
@Composable
fun Stepper(
    value: Int,
    onChange: (Int) -> Unit,
    min: Int,
    max: Int,
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    @DrawableRes icon: Int? = null,
    typeTag: String? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (icon != null) IconTile(icon)
        Column(Modifier.weight(1f)) {
            Text(label, style = EventsType.BodyStrong, color = EventsColors.Text)
            if (supporting != null) Text(supporting, style = EventsType.BodySmall, color = EventsColors.TextSoft)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StepButton(R.drawable.ic_remove, "Decrease $label", value > min, EventsColors.PrimaryContainer, EventsColors.Text) { onChange(value - 1) }
            if (typeTag == null) {
                Text("$value", style = EventsType.TitleSmall, color = EventsColors.Text, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
            } else {
                // What's in the box while typing; it follows taps on − and + too.
                var text by remember { mutableStateOf("$value") }
                LaunchedEffect(value) { if ((text.toIntOrNull() ?: min) != value) text = "$value" }
                BasicTextField(
                    text,
                    { typed ->
                        val digits = typed.filter(Char::isDigit).take(9)
                        val n = digits.toIntOrNull()
                        // Out of range snaps to the nearest allowed number; an empty box stays empty while typing.
                        text = if (n == null) "" else "${n.coerceIn(min, max)}"
                        onChange((n ?: min).coerceIn(min, max))
                    },
                    Modifier.width(44.dp).testTag(typeTag).semantics { contentDescription = label },
                    textStyle = EventsType.TitleSmall.copy(color = EventsColors.Text, textAlign = TextAlign.Center),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    cursorBrush = SolidColor(EventsColors.Coral),
                )
            }
            StepButton(R.drawable.ic_add, "Increase $label", value < max, EventsColors.Primary, EventsColors.OnPrimary) { onChange(value + 1) }
        }
    }
}

@Composable
private fun StepButton(@DrawableRes icon: Int, description: String, enabled: Boolean, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (enabled) bg else bg.copy(alpha = 0.35f))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { EventsIcon(icon, tint = fg.copy(alpha = if (enabled) 1f else 0.5f), size = 18.dp, contentDescription = description) }
}

/**
 * Field (Figma "Field" component): SemiBold 12 label at 78% white, then a 52dp input
 * (#37347F, radius 16, 8% white outline) with Regular 14 text and an optional 20dp icon.
 */
@Composable
fun EventsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isPassword: Boolean = false,
    readOnly: Boolean = false,
    supporting: String? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = EventsType.LabelSmall, color = EventsColors.TextSoft)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = EventsType.Body.copy(color = EventsColors.Text),
            cursorBrush = SolidColor(EventsColors.Coral),
            keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType),
            visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (singleLine) 52.dp else 96.dp)
                        .clip(EventsShapes.Medium)
                        .background(if (readOnly) EventsColors.Surface else EventsColors.SurfaceSoft)
                        .border(1.dp, if (error != null) EventsColors.Danger else Color(0x14FFFFFF), EventsShapes.Medium)
                        .padding(start = 16.dp, end = 14.dp, top = if (singleLine) 0.dp else 14.dp, bottom = if (singleLine) 0.dp else 14.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (leadingIcon != null) EventsIcon(leadingIcon, tint = EventsColors.TextSecondary, size = 20.dp)
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) Text(placeholder, style = EventsType.Body, color = EventsColors.TextTertiary)
                        inner()
                    }
                }
            },
        )
        if (error != null) Text(error, style = EventsType.Caption, color = EventsColors.Danger, modifier = Modifier.padding(start = 4.dp))
        else if (supporting != null) Text(supporting, style = EventsType.Caption, color = EventsColors.TextTertiary, modifier = Modifier.padding(start = 4.dp))
    }
}

/**
 * Segmented control (Figma "Segmented"): 50dp frosted pill with 4dp padding; the selected
 * segment is a Dusk (#2D2A6E) pill, labels SemiBold 12.
 */
@Composable
fun SegmentedControl(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, tagPrefix: String = "tab_") {
    Row(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(EventsShapes.Pill)
            .background(EventsColors.Tint)
            .border(1.dp, EventsColors.TintStroke, EventsShapes.Pill)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { i, label ->
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(EventsShapes.Pill)
                    .background(if (i == selected) EventsColors.Surface else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .then(Modifier.semanticsTag("$tagPrefix$i")),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = EventsType.LabelSmall, color = if (i == selected) EventsColors.Text else EventsColors.TextSoft, maxLines = 1) }
        }
    }
}

/** Shorthand for a test tag (keeps call sites short). */
fun Modifier.semanticsTag(tag: String): Modifier = this.testTag(tag)

/**
 * Frosted search bar (56dp, white 20% fill, 28% outline, 22dp icon, Regular 14 text).
 * With [onClick] it is a button (Explore); otherwise an editable field (Search).
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search campsites…",
    onClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    onFilters: (() -> Unit)? = null,
    filtersOn: Boolean = false,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(EventsShapes.Pill)
            .background(EventsColors.Tint)
            .border(1.dp, EventsColors.TintStroke, EventsShapes.Pill)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Search", onClick = onClick) else Modifier)
            .padding(start = 18.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EventsIcon(R.drawable.ic_search, size = 22.dp)
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(placeholder, style = EventsType.Body, color = EventsColors.TextSoft, maxLines = 1)
            if (onClick == null) {
                BasicTextField(
                    value = query, onValueChange = onQueryChange, singleLine = true,
                    textStyle = EventsType.Body.copy(color = EventsColors.Text), cursorBrush = SolidColor(EventsColors.Coral),
                    modifier = Modifier.fillMaxWidth().testTag("search_field").then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                )
            }
        }
        if (onClick == null && query.isNotEmpty()) {
            Box(Modifier.size(28.dp).clip(CircleShape).clickable(onClickLabel = "Clear search") { onQueryChange("") }, contentAlignment = Alignment.Center) {
                EventsIcon(R.drawable.ic_close, size = 18.dp, tint = EventsColors.TextSecondary, contentDescription = "Clear search")
            }
        }
        // Figma: the filters button sits inside the search bar.
        if (onFilters != null) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(if (filtersOn) EventsColors.Coral.copy(alpha = 0.35f) else EventsColors.Tint)
                    .clickable(onClickLabel = "Filters", onClick = onFilters).testTag("search_filters"),
                contentAlignment = Alignment.Center,
            ) { EventsIcon(R.drawable.ic_tune, size = 18.dp, tint = EventsColors.Coral, contentDescription = "Filters") }
        }
    }
}

/** A quick-pick option shown under a [DateTimeField]. */
data class TimePreset(val label: String, val millis: () -> Long)

/**
 * A tappable field showing a chosen date and time.
 *
 * @param value chosen time in epoch ms, or null.
 * @param presets quick choices shown as chips.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeField(
    label: String,
    value: Long?,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    presets: List<TimePreset> = emptyList(),
    testTag: String = label,
) {
    var showDate by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    Column(modifier.fillMaxWidth()) {
        Text(label, style = EventsType.LabelSmall, color = EventsColors.TextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(EventsShapes.Medium).background(EventsColors.SurfaceSoft)
                .border(1.dp, if (error != null) EventsColors.Danger else Color.Transparent, EventsShapes.Medium)
                .clickable { showDate = true }.padding(horizontal = 14.dp).testTag("field_$testTag"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EventsIcon(R.drawable.ic_calendar, tint = EventsColors.TextSecondary, size = 20.dp)
            Spacer(Modifier.width(10.dp))
            Text(value?.let { formatDayTime(it) } ?: "Pick date & time", style = EventsType.Body, color = if (value == null) EventsColors.TextTertiary else EventsColors.Text)
        }
        if (presets.isNotEmpty()) {
            Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { p -> EventsChip(p.label, ChipStyle.DUSK, { onChange(p.millis()) }, Modifier.testTag("preset_${testTag}_${p.label}")) }
            }
        }
        if (error != null) Text(error, style = EventsType.Caption, color = EventsColors.Danger, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
    }

    if (showDate) {
        val initial = value ?: System.currentTimeMillis()
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = Instant.ofEpochMilli(initial).atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton({
                    dateState.selectedDateMillis?.let { pickedDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                }) { Text("Next", color = EventsColors.Coral) }
            },
            dismissButton = { TextButton({ showDate = false }) { Text("Cancel", color = EventsColors.TextSecondary) } },
        ) { DatePicker(dateState) }
    }
    pickedDate?.let { date ->
        val start = value?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime() } ?: LocalTime.of(9, 0)
        val timeState = rememberTimePickerState(start.hour, start.minute, is24Hour = false)
        Dialog(onDismissRequest = { pickedDate = null }) {
            Column(Modifier.clip(EventsShapes.XLarge).background(EventsColors.Surface).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Pick a time", style = EventsType.Title, color = EventsColors.Text)
                Spacer(Modifier.height(12.dp))
                TimePicker(timeState)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton({ pickedDate = null }) { Text("Cancel", color = EventsColors.TextSecondary) }
                    TextButton({
                        onChange(date.atTime(timeState.hour, timeState.minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                        pickedDate = null
                    }) { Text("Done", color = EventsColors.Coral) }
                }
            }
        }
    }
}

/**
 * A tappable field that opens a calendar for picking a range of days, e.g. a booking period
 * (US 02.04.04) or a winner's stay. Days are day numbers ([LocalDate.toEpochDay]).
 *
 * @param startDay chosen first day, or null.
 * @param endDay chosen last (check-out) day, or null.
 * @param onChange called with the new start and end when the user confirms.
 * @param isSelectable which days may be tapped; others are greyed out.
 * @param initialDay day the calendar opens on when nothing is chosen yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeField(
    label: String,
    startDay: Long?,
    endDay: Long?,
    onChange: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    placeholder: String = "Pick dates",
    testTag: String = label,
    initialDay: Long? = null,
    isSelectable: (Long) -> Boolean = { true },
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Text(label, style = EventsType.LabelSmall, color = EventsColors.TextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(EventsShapes.Medium).background(EventsColors.SurfaceSoft)
                .border(1.dp, if (error != null) EventsColors.Danger else Color.Transparent, EventsShapes.Medium)
                .clickable(role = Role.Button) { open = true }.padding(horizontal = 14.dp).testTag("field_$testTag"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EventsIcon(R.drawable.ic_calendar, tint = EventsColors.TextSecondary, size = 20.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                if (startDay != null && endDay != null) formatStay(startDay, endDay) else placeholder,
                style = EventsType.Body, color = if (startDay == null) EventsColors.TextTertiary else EventsColors.Text,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (error != null) Text(error, style = EventsType.Caption, color = EventsColors.Danger, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
    }
    if (open) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = startDay?.times(DAY_MILLIS),
            initialSelectedEndDateMillis = endDay?.times(DAY_MILLIS),
            initialDisplayedMonthMillis = (startDay ?: initialDay)?.times(DAY_MILLIS),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = isSelectable(utcTimeMillis / DAY_MILLIS)
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val s = state.selectedStartDateMillis
                        val e = state.selectedEndDateMillis
                        if (s != null && e != null) onChange(s / DAY_MILLIS, e / DAY_MILLIS)
                        open = false
                    },
                    enabled = state.selectedEndDateMillis != null,
                ) { Text("Done", color = EventsColors.Coral) }
            },
            dismissButton = { TextButton({ open = false }) { Text("Cancel", color = EventsColors.TextSecondary) } },
        ) {
            DateRangePicker(state, Modifier.heightIn(max = 560.dp), title = null, headline = null, showModeToggle = false)
        }
    }
}
