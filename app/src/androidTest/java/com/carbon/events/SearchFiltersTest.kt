/*
 * SearchFiltersTest.kt (Ansh)
 *
 * Purpose: Compose UI (intent) tests for the Filters sheet: filtering campsites by location and
 * number of guests (US 01.01.02) and the list of campsites that comes back (US 01.01.03).
 * Filtering by date is covered at the rule level in ExploreTest, which can fix the calendar.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for filtering campsites and the filtered list." Reviewed, edited and run by
 * Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import android.Manifest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** An entrant filtering the six sample campsites on an in-memory store. */
@RunWith(AndroidJUnit4::class)
class SearchFiltersTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 },
        ).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW))
    private val entrant = "test-phone"

    /** Opens Explore, then Search with the Filters sheet up. */
    private fun launchFilters() {
        runBlocking {
            store.saveProfile(UserProfile(entrant, "Adam Kent", "adam@example.com"))
            store.linkDevice(entrant, entrant)
        }
        val c = AppContainer.inMemory(entrant, clock, store)
        compose.setContent {
            EventsTheme {
                EventsApp(c, showSplash = false, splash = {}, setup = {}, alerts = {},
                    entrant = { LocalAppSession.current.let { EntrantShell(it.container, it.requireProfile(), it.profileVm, null, {}) } },
                    owner = {}, admin = {},
                )
            }
        }
        waitFor("explore")
        compose.onNodeWithTag("explore_search").performClick()
        waitFor("result_count")
        compose.onNodeWithContentDescription("Filters").performSemanticsAction(SemanticsActions.OnClick)
        waitFor("filters")
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun tap(tag: String) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) }
    private fun addGuest() { compose.onNodeWithContentDescription("Increase Guests").performSemanticsAction(SemanticsActions.OnClick) }

    private fun pickLocation(location: String) {
        compose.onNode(hasSetTextAction() and (hasTestTag("filter_location") or hasAnyAncestor(hasTestTag("filter_location")))).performClick()
        tap("filter_location_$location")
    }

    private fun apply() { tap("filters_apply"); compose.waitForIdle() }
    private fun card(id: String) = compose.onNodeWithTag("campsite_card_$id")

    @Test fun noFilters_listsEveryCampsite() {
        launchFilters()
        compose.onNodeWithText("Show 6 campsites").assertExists()
        apply()
        compose.onNodeWithTag("result_count").assertTextEquals("6 campsites")
    }

    @Test fun locationFilter_listsOnlyCampsitesThere() {
        launchFilters()
        pickLocation("Lake Louise, AB")
        compose.onNodeWithText("Show 2 campsites").assertExists()
        apply()
        compose.onNodeWithTag("result_count").assertTextEquals("2 campsites near Lake Louise, AB")
        card("lake-louise").assertExists()
        card("moraine-lake").assertExists()
        card("two-jack").assertDoesNotExist()
    }

    @Test fun guestsFilter_hidesCampsitesThatAreTooSmall() {
        launchFilters()
        repeat(4) { addGuest() } // 5 guests
        apply()
        compose.onNodeWithText("5 guests").assertExists()
        card("lake-louise").assertDoesNotExist() // max 4
        card("moraine-lake").assertDoesNotExist() // max 4
        card("two-jack").assertExists() // max 6
        card("tunnel-mountain").assertExists() // max 8
    }

    @Test fun locationAndGuestsTogether_mustBothMatch() {
        launchFilters()
        pickLocation("Banff, AB")
        repeat(6) { addGuest() } // 7 guests
        compose.onNodeWithText("Show 1 campsite").assertExists()
        apply()
        card("tunnel-mountain").assertExists()
        card("two-jack").assertDoesNotExist()
        card("johnston-canyon").assertDoesNotExist()
    }

    @Test fun filtersThatMatchNothing_sayNoCampsitesFound() {
        launchFilters()
        pickLocation("Lake Louise, AB")
        repeat(4) { addGuest() }
        compose.onNodeWithText("Show 0 campsites").assertExists()
        apply()
        compose.onNodeWithText("No campsites found").assertExists()
    }

    @Test fun reset_bringsEveryCampsiteBack() {
        launchFilters()
        pickLocation("Lake Louise, AB")
        tap("filters_reset")
        compose.onNodeWithText("Show 6 campsites").assertExists()
    }

    @Test fun filteredList_keepsTheSummaryOnEachCampsite() {
        launchFilters()
        pickLocation("Lake Louise, AB")
        apply()
        compose.onNode(hasText("Max 4") and hasAnyAncestor(hasTestTag("campsite_card_lake-louise")), useUnmergedTree = true).assertExists()
        compose.onNode(hasText("Lake Louise, AB") and hasAnyAncestor(hasTestTag("campsite_card_moraine-lake")), useUnmergedTree = true).assertExists()
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
