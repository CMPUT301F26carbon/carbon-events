/*
 * CampsiteSummaryTest.kt (Ansh)
 *
 * Purpose: Compose UI (intent) tests for what an entrant sees about a campsite: the summary on
 * each search result (US 01.01.04) and the full details after tapping one (US 01.01.05).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for the campsite summary in search results and the campsite details screen."
 * Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** An entrant browsing the sample Banff campsites on an in-memory store. */
@RunWith(AndroidJUnit4::class)
class CampsiteSummaryTest {
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

    private fun launch() {
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
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("explore")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun search(text: String) {
        compose.onNodeWithTag("explore_search").performClick()
        compose.onNodeWithTag("search_field").performTextInput(text)
    }

    /** A text inside one campsite's result card. */
    private fun inCard(id: String, text: String) =
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("campsite_card_$id")), useUnmergedTree = true)

    private fun openDetails(id: String) {
        compose.onNodeWithTag("explore").performScrollToNode(hasTestTag("campsite_card_$id"))
        compose.onNodeWithTag("campsite_card_$id").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("details_name")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    @Test fun searchResult_showsNameLocationOccupancyAndRating() {
        launch()
        search("two jack")
        inCard("two-jack", "Two Jack Lakeside").assertExists()
        inCard("two-jack", "Banff, AB").assertExists()
        inCard("two-jack", "Max 6").assertExists()
        inCard("two-jack", "4.8").assertExists()
        inCard("two-jack", "(126)").assertExists()
    }

    @Test fun searchResults_eachShowTheirOwnSummary() {
        launch()
        search("lake louise")
        inCard("lake-louise", "Lake Louise Tent Sites").assertExists()
        inCard("lake-louise", "Max 4").assertExists()
        inCard("moraine-lake", "Moraine Lake Sites").assertExists()
        inCard("moraine-lake", "4.9").assertExists()
        compose.onNodeWithTag("campsite_card_two-jack").assertDoesNotExist()
    }

    @Test fun campsiteWithoutRatings_saysNew() {
        runBlocking {
            store.createCampsite(Campsite("", SampleData.OWNER_ID, "Parks Lodge", "Bow Valley Meadows", "Canmore, AB", maxOccupancy = 5, pricePerNight = 40, createdAt = NOW))
        }
        launch()
        search("bow valley")
        compose.onNodeWithText("Bow Valley Meadows").assertExists()
        compose.onNodeWithText("Max 5").assertExists()
        compose.onNodeWithText("New").assertExists()
    }

    @Test fun tappingAResult_opensThatCampsitesDetails() {
        launch()
        search("two jack")
        compose.onNodeWithTag("campsite_card_two-jack").performClick()
        compose.onNodeWithTag("details_name").assertTextEquals("Two Jack Lakeside")
    }

    @Test fun details_showLocationDescriptionOccupancyPriceAndRating() {
        launch()
        openDetails("two-jack")
        compose.onNodeWithTag("details_name").assertTextEquals("Two Jack Lakeside")
        compose.onNodeWithText("Banff, AB").assertExists()
        compose.onNodeWithText("Lakeside sites under Mount Rundle", substring = true).assertExists()
        compose.onNodeWithText("6 guests").assertExists()
        compose.onNodeWithText("Max occupancy").assertExists()
        compose.onNodeWithText("$65").assertExists()
        compose.onNodeWithText("per night").assertExists()
        compose.onNodeWithText("· 126 ratings").assertExists()
    }

    @Test fun details_showTheLotteryAndTheMap() {
        launch()
        openDetails("two-jack")
        compose.onNodeWithTag("lottery_card").assertExists()
        compose.onNodeWithText("Join lottery").assertExists()
        compose.onNodeWithText("Stays from").assertExists()
        compose.onNodeWithTag("details_map").assertExists()
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
