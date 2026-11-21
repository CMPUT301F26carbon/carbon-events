/*
 * Location.kt (Aditya)
 *
 * The phone's approximate location, for "Lotteries Near Me" and distances on campsites, plus
 * the permission explainer shown before Android asks (Figma E-PERMISSION-LOCATION). The
 * location never leaves the phone.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Geographic coordinates in decimal degrees, used only for local distance/radius calculations.
 * @property latitude north/south coordinate, conventionally -90 through 90.
 * @property longitude east/west coordinate, conventionally -180 through 180.
 */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** Great-circle distance between two points, in kilometres (haversine). */
fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
    val r = 6371.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(h))
}

/** Distance from [here] to [site], or null if either location is unknown. */
fun distanceKm(here: GeoPoint?, site: Campsite): Double? =
    if (here == null || !site.hasMapLocation) null else distanceKm(here, GeoPoint(site.latitude!!, site.longitude!!))

/** "850 m", "12 km", "1,240 km". */
fun formatKm(km: Double): String = when {
    km < 1 -> "${(km * 1000).toInt() / 50 * 50} m"
    km < 10 -> String.format(java.util.Locale.CANADA, "%.1f km", km)
    else -> String.format(java.util.Locale.CANADA, "%,d km", Math.round(km))
}

/**
 * Sorts [items] nearest first by [site]'s distance from [here]; items without a pin go last.
 * With no location, the order is unchanged.
 */
fun <T> nearestFirst(items: List<T>, here: GeoPoint?, site: (T) -> Campsite?): List<T> =
    if (here == null) items else items.sortedBy { item -> site(item)?.let { distanceKm(here, it) } ?: Double.MAX_VALUE }

/**
 * "Lotteries Near Me" on Explore: once the phone's location is known, only lotteries within
 * [withinKm], nearest first. If none are that close, every lottery is shown nearest first so the
 * list is never empty; without a location, the list stays as it is.
 */
fun <T> nearMe(items: List<T>, here: GeoPoint?, withinKm: Double = NEAR_ME_LOTTERIES_KM, site: (T) -> Campsite?): List<T> {
    val sorted = nearestFirst(items, here, site)
    if (here == null) return sorted
    val close = sorted.filter { item -> site(item)?.let { distanceKm(here, it) }?.let { it <= withinKm } == true }
    return close.ifEmpty { sorted }
}

/** How far "Lotteries Near Me" reaches, in kilometres. */
const val NEAR_ME_LOTTERIES_KM = 100.0

/** The phone's last known location while the app runs (null until allowed and found). */
object DeviceLocation {
    private val current = MutableStateFlow<GeoPoint?>(null)

    /** The latest location, or null. */
    val location: StateFlow<GeoPoint?> = current.asStateFlow()

    /** Whether the person allowed approximate location. */
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Sets a location directly (tests and previews). */
    fun set(point: GeoPoint?) { current.value = point }

    /** Reads the last known location, then asks for a fresh one, if permission was given. */
    @SuppressLint("MissingPermission")
    fun refresh(context: Context) {
        if (!hasPermission(context)) return
        val lm = context.getSystemService(LocationManager::class.java) ?: return
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        runCatching {
            providers.mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }.maxByOrNull { it.time }
                ?.let { current.value = GeoPoint(it.latitude, it.longitude) }
        }
        val provider = providers.firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return
        runCatching {
            LocationManagerCompat.getCurrentLocation(
                lm, provider, null as androidx.core.os.CancellationSignal?, ContextCompat.getMainExecutor(context),
                androidx.core.util.Consumer<android.location.Location?> { loc -> if (loc != null) current.value = GeoPoint(loc.latitude, loc.longitude) },
            )
        }
    }
}

/** The phone's location for the screen being drawn (null when unknown or not allowed). */
val LocalDeviceLocation = compositionLocalOf<GeoPoint?> { null }

/**
 * Returns a function that asks Android for approximate location and then refreshes
 * [DeviceLocation].
 */
@Composable
fun rememberLocationRequest(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) DeviceLocation.refresh(context) }
    return { if (DeviceLocation.hasPermission(context)) DeviceLocation.refresh(context) else launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
}
