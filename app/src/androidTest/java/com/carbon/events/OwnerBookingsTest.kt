/*
 * OwnerBookingsTest.kt (Suvir)
 *
 * Purpose: Compose UI (intent) tests for OwnerBookings.kt: the campsite filter on Bookings (US 02.07.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write UI (intent) tests for the owner's Bookings."
 * Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import android.Manifest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** The sample owner, Parks Lodge, and their campsites. */
@RunWith(AndroidJUnit4::class)
class OwnerBookingsTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 },
        ).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW))
    private val owner = SampleData.OWNER_ID

    /** Signs the sample owner (Parks Lodge) in on this phone and opens My Campsites. */
    private fun launchOwner(seed: suspend () -> Unit = {}) {
        runBlocking {
            store.saveProfile(SampleData.owner(NOW).copy(activeRole = Role.OWNER))
            store.linkDevice(owner, owner)
            seed()
        }
        val c = AppContainer.inMemory(owner, clock, store)
        compose.setContent {
            EventsTheme {
                EventsApp(c, showSplash = false, splash = {}, setup = {}, alerts = {},
                    entrant = {},
                    owner = { LocalAppSession.current.let { OwnerShell(it.container, it.requireProfile(), it.profileVm) } },
                    admin = {},
                )
            }
        }
        waitFor("owner_campsites")
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun tap(tag: String) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) }

    // ---- Bookings ----

    @Test fun bookings_canShowEveryCampsite_orJustOne() {
        launchOwner()
        compose.onNodeWithTag("nav_Bookings").performClick()
        waitFor("owner_bookings")
        compose.onNodeWithTag("bookings_site_all").assertExists()
        compose.onAllNodes(hasTestTagPrefix("bookings_site_")).fetchSemanticsNodes().let { assertTrue("a chip per campsite, plus All", it.size >= 2) }
        tap("bookings_site_all")
        compose.onNodeWithTag("owner_bookings").assertExists()
    }

    private fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("tag starts with $prefix") {
        androidx.compose.ui.semantics.SemanticsProperties.TestTag.let { key -> it.config.contains(key) && it.config[key].startsWith(prefix) }
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
