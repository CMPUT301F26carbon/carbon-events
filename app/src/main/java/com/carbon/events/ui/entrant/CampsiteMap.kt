/*
 * CampsiteMap.kt (Ansh)
 *
 * The full-screen map of campsites with a card for the one tapped.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map

/**
 * Every campsite with a pin on a full-screen map (Figma E-MAP). Tapping a pin's label opens it.
 */
@Composable
fun CampsiteMapScreen(container: AppContainer, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val sites by container.campsites.observeCampsites().collectAsStateWithLifecycle(emptyList())
    val here = LocalDeviceLocation.current
    val pinned = sites.filter { it.hasMapLocation }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(Modifier.fillMaxSize().testTag("campsite_map")) {
        val center = here?.let { it.latitude to it.longitude }
            ?: pinned.firstOrNull()?.let { it.latitude!! to it.longitude!! }
            ?: (53.5461 to -113.4938)
        OsmMap(
            center.first, center.second, 6.0,
            pinned.map { MapPin(it.id, it.latitude!!, it.longitude!!, it.name, "$${it.pricePerNight}/night · tap again to open") },
            Modifier.fillMaxSize(), onPinTap = { selected = it },
        )
        Row(Modifier.statusBarsPadding().padding(horizontal = PageMargin, vertical = 8.dp)) { GlassIconButton(R.drawable.ic_arrow_back, "Back", onBack) }
        // Figma E-MAP: the tapped campsite in a card at the bottom, with directions.
        pinned.firstOrNull { it.id == selected }?.let { site ->
            DuskCard(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(PageMargin).testTag("map_card"),
                onClick = { onOpen(site.id) }, spacing = 10.dp,
            ) {
                Text(site.name, style = EventsType.Title, color = EventsColors.Text)
                Text(site.location, style = EventsType.BodySmall, color = EventsColors.TextSecondary)
                PrimaryButton("Get directions", {
                    val uri = android.net.Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${site.latitude},${site.longitude}")
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) }
                }, Modifier.fillMaxWidth().testTag("map_directions"), icon = R.drawable.ic_location)
            }
        }
    }
}
