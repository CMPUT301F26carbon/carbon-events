/*
 * Receipts.kt (Suvir)
 *
 * Reservation codes and the CSV receipts guests and owners can download.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** A short code guests show at check-in, e.g. "EV-NOV26-7K3Q". */
fun reservationCode(lotteryId: String, entrantId: String): String {
    val key = Paths.keyOf(lotteryId).split('-').let { p -> (p.getOrNull(0).orEmpty().take(3) + p.getOrNull(1).orEmpty().takeLast(2)) }.uppercase()
    val chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    var h = (lotteryId + entrantId).hashCode().toLong() and 0x7fffffff
    val tail = buildString { repeat(4) { append(chars[(h % chars.length).toInt()]); h /= chars.length } }
    return "EV-${key.ifBlank { "LOT" }}-$tail"
}

// ---- Downloadable receipts (US 01.05.02, 01.07.02) ----

/** Builds the CSV receipts guests can download for a booking or a cancellation. */
object ReceiptCsv {
    /** What a receipt confirms. */
    enum class Kind(val title: String) {
        RESERVATION("Reservation confirmation"),
        CANCELLATION("Cancellation confirmation"),
        REJECTION("Reservation rejected by owner"),
    }

    /** The kind of receipt for [r]'s current status. */
    fun kindOf(r: Reservation): Kind = when (r.status) {
        EntryStatus.CANCELLED -> Kind.CANCELLATION
        EntryStatus.REJECTED -> Kind.REJECTION
        else -> Kind.RESERVATION
    }

    /**
     * A two-column "Field,Value" CSV describing [r]: the code, campsite, guest, dates, guests,
     * price with GST, and (when cancelled or rejected) when and why.
     */
    fun build(r: Reservation, code: String, site: Campsite?): String {
        val kind = kindOf(r)
        val rows = buildList {
            add("Receipt" to kind.title)
            add("Reservation code" to code)
            add("Campsite" to r.campsiteName)
            site?.let { add("Location" to it.location); add("Host" to it.ownerName) }
            add("Guest" to r.entrantName)
            add("Check-in" to (isoDay(r.checkInDay) + (site?.checkInTime?.let { " $it" } ?: "")))
            add("Check-out" to (isoDay(r.checkOutDay) + (site?.checkOutTime?.let { " $it" } ?: "")))
            add("Nights" to "${r.nights}")
            add("Guests" to "${r.guests}")
            if (site != null) {
                val price = StayPrice(r.nights, site.pricePerNight)
                add("Price per night (CAD)" to "${price.perNight}")
                add("Subtotal (CAD)" to "${price.subtotal}")
                add("GST 5% (CAD)" to "${price.tax}")
                add((if (kind == Kind.RESERVATION) "Total due at check-in (CAD)" else "Amount charged (CAD)") to if (kind == Kind.RESERVATION) "${price.total}" else "${cancellationCharge(r, site)}")
                if (kind == Kind.CANCELLATION) add("Cancellation policy" to site.cancellation.label)
            }
            add("Booked at" to isoTime(r.bookedAt))
            r.endedAt?.let { add((if (kind == Kind.CANCELLATION) "Cancelled at" else "Rejected at") to isoTime(it)) }
            if (kind == Kind.REJECTION) add("Reason" to r.note.ifBlank { "No reason given" })
        }
        return (listOf("Field" to "Value") + rows).joinToString("\n", postfix = "\n") { (k, v) -> "${cell(k)},${cell(v)}" }
    }

    /** A safe file name like "events-reservation-EV-TWO12-AB3C.csv". */
    fun fileName(r: Reservation, code: String): String =
        "events-${kindOf(r).name.lowercase()}-${code.replace(Regex("[^A-Za-z0-9-]"), "")}.csv"

    /** Quotes a CSV cell when it holds a comma, quote or line break. */
    fun cell(v: String): String = if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    private fun isoDay(day: Long) = java.time.LocalDate.ofEpochDay(day).toString()
    private fun isoTime(ms: Long) = java.time.Instant.ofEpochMilli(ms).toString()
}

/**
 * "Download CSV" button: asks Android where to save (no storage permission needed) and writes
 * [csv] there.
 */
@Composable
fun CsvDownloadButton(label: String, fileName: String, csv: () -> String, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var saved by remember { mutableStateOf<Boolean?>(null) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) saved = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(csv().toByteArray()) } != null
        }.getOrDefault(false)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SecondaryButton(label, { launcher.launch(fileName) }, Modifier.fillMaxWidth().testTag("download_csv"), icon = R.drawable.ic_download, size = ButtonSize.SMALL)
        saved?.let { ok ->
            Text(if (ok) "Saved $fileName" else "Couldn't save the file.", style = EventsType.Caption, color = if (ok) EventsColors.Green else EventsColors.Danger, modifier = Modifier.testTag("csv_result"))
        }
    }
}
