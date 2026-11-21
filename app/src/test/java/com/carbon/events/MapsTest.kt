/*
 * MapsTest.kt (Aditya)
 *
 * Purpose: Unit tests for Maps.kt: the OpenStreetMap view draws one tent pin per campsite at its
 * coordinates, starts where it's told, and the small preview opens the full map when tapped
 * (US 01.01.05, US 02.01.02).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * a test file for each source file that has none." Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The map widgets, drawn off-screen; no tiles are downloaded. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MapsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val twoJack = MapPin("two-jack", 51.2316, -115.4983, "Two Jack Lakeside", "Banff, AB")
    private val louise = MapPin("lake-louise", 51.4194, -116.1795, "Lake Louise Tent Sites")

    private fun mapView(): MapView {
        fun find(v: View): MapView? = when (v) {
            is MapView -> v
            is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { find(v.getChildAt(it)) }
            else -> null
        }
        compose.waitForIdle()
        return requireNotNull(find(compose.activity.window.decorView)) { "no map on screen" }
    }

    private fun markers() = mapView().overlays.filterIsInstance<Marker>()

    @Test fun pin_snippetIsOptional() {
        assertEquals("", louise.snippet)
        assertEquals("Banff, AB", twoJack.snippet)
    }

    @Test fun map_drawsOnePinPerCampsite_atItsCoordinates() {
        compose.setContent { OsmMap(51.3, -115.8, 9.0, listOf(twoJack, louise), Modifier.size(300.dp)) }
        val pins = markers()
        assertEquals(2, pins.size)
        assertEquals(listOf("Two Jack Lakeside", "Lake Louise Tent Sites"), pins.map { it.title })
        assertEquals(51.2316, pins[0].position.latitude, 1e-6)
        assertEquals(-115.4983, pins[0].position.longitude, 1e-6)
        assertEquals("Banff, AB", pins[0].snippet)
    }

    @Test fun map_startsAtTheGivenCentreAndZoom() {
        compose.setContent { OsmMap(51.3, -115.8, 9.0, emptyList(), Modifier.size(300.dp)) }
        val map = mapView()
        assertEquals(9.0, map.zoomLevelDouble, 1e-6)
        assertTrue(markers().isEmpty())
    }

    @Test fun map_withATapHandler_listensForTaps() {
        compose.setContent { OsmMap(51.3, -115.8, 9.0, emptyList(), Modifier.size(300.dp), onTap = { _, _ -> }) }
        assertEquals(1, mapView().overlays.filterIsInstance<MapEventsOverlay>().size)
    }

    @Test fun map_withoutATapHandler_hasNoTapLayer() {
        compose.setContent { OsmMap(51.3, -115.8, 9.0, listOf(twoJack), Modifier.size(300.dp)) }
        assertTrue(mapView().overlays.filterIsInstance<MapEventsOverlay>().isEmpty())
    }

    @Test fun preview_showsOnePinForThePlace() {
        compose.setContent { OsmPreview(51.2316, -115.4983, "Banff, AB", Modifier.size(200.dp)) }
        val pin = markers().single()
        assertEquals("Banff, AB", pin.title)
        assertEquals(51.2316, pin.position.latitude, 1e-6)
    }

    @Test fun preview_runsOnClickWhenTapped() {
        var opened = 0
        compose.setContent { OsmPreview(51.2316, -115.4983, "Banff, AB", Modifier.size(200.dp).testTag("preview")) { opened++ } }
        compose.onNodeWithTag("preview").performClick()
        assertEquals(1, opened)
    }
}
