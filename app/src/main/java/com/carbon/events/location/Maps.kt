/*
 * Maps.kt (Aditya)
 *
 * OpenStreetMap maps (osmdroid): free, no API key and no billing. Used for the campsite map
 * preview, the owner's location picker and the full campsite map.
 *
 * Outstanding issues: map tiles need a network connection the first time an area is
 * viewed; without one the map area stays blank.
 */
package com.carbon.events

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

/**
 * A labelled marker on [OsmMap]. Coordinates are decimal degrees.
 * @property id stable marker identity passed to selection callbacks.
 * @property latitude north/south position.
 * @property longitude east/west position.
 * @property title display name of the campsite.
 * @property snippet optional supporting text in the marker popup.
 */
data class MapPin(val id: String, val latitude: Double, val longitude: Double, val title: String, val snippet: String = "")

private var osmReady = false

/** One-time osmdroid setup: identify the app to the tile servers and cache tiles in app storage. */
private fun setUpOsm(context: Context) {
    if (osmReady) return
    val config = Configuration.getInstance()
    config.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    config.userAgentValue = context.packageName
    config.osmdroidBasePath = java.io.File(context.cacheDir, "osmdroid")
    config.osmdroidTileCache = java.io.File(context.cacheDir, "osmdroid/tiles")
    osmReady = true
}

/**
 * An OpenStreetMap map.
 *
 * @param interactive pan and zoom with fingers; false for small previews.
 * @param onTap called with the tapped latitude and longitude (location picker).
 * @param onPinOpen called when a pin's label is tapped a second time.
 */
@Composable
fun OsmMap(
    centerLat: Double,
    centerLng: Double,
    zoom: Double,
    pins: List<MapPin>,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    onTap: ((Double, Double) -> Unit)? = null,
    onPinOpen: ((String) -> Unit)? = null,
    onPinTap: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val map = remember {
        setUpOsm(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(interactive)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(zoom)
            controller.setCenter(org.osmdroid.util.GeoPoint(centerLat, centerLng))
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        map.onResume()
        onDispose { lifecycle.removeObserver(observer); map.onPause(); map.onDetach() }
    }
    Box(modifier) {
        AndroidView({ map }, Modifier.fillMaxSize()) { view ->
            view.overlays.clear()
            if (onTap != null) view.overlays.add(
                MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: org.osmdroid.util.GeoPoint): Boolean { onTap(p.latitude, p.longitude); return true }
                    override fun longPressHelper(p: org.osmdroid.util.GeoPoint): Boolean = false
                }),
            )
            pins.forEach { pin ->
                view.overlays.add(
                    Marker(view).apply {
                        position = org.osmdroid.util.GeoPoint(pin.latitude, pin.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = pin.title
                        snippet = pin.snippet
                        icon = tentPin(context)
                        setOnMarkerClickListener { m, _ ->
                            when {
                                onPinTap != null -> onPinTap(pin.id)
                                m.isInfoWindowShown && onPinOpen != null -> onPinOpen(pin.id)
                                else -> m.showInfoWindow()
                            }
                            true
                        }
                    },
                )
            }
            view.invalidate()
        }
        // Previews don't pan; touches go to the screen around them instead.
        if (!interactive) Box(Modifier.matchParentSize().pointerInput(Unit) {
            awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
        })
    }
}

/** A still map preview that runs [onClick] when tapped. */
@Composable
fun OsmPreview(lat: Double, lng: Double, label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Box(modifier) {
        OsmMap(lat, lng, 11.0, listOf(MapPin("here", lat, lng, label)), Modifier.fillMaxSize(), interactive = false)
        if (onClick != null) Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

/** Figma's map pin: the tent icon in a lilac circle with a soft ring. */
private fun tentPin(context: Context): android.graphics.drawable.Drawable {
    val d = context.resources.displayMetrics.density
    val size = (40 * d).toInt()
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    p.color = 0x55B9A8FF; c.drawCircle(size / 2f, size / 2f, size / 2f, p)
    p.color = 0xFFB9A8FF.toInt(); c.drawCircle(size / 2f, size / 2f, size / 2f - 4 * d, p)
    androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_tent)?.mutate()?.let { tent ->
        tent.setTint(0xFF2D2A6E.toInt())
        val inset = (11 * d).toInt(); tent.setBounds(inset, inset, size - inset, size - inset); tent.draw(c)
    }
    return android.graphics.drawable.BitmapDrawable(context.resources, bmp)
}
