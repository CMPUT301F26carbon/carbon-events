/*
 * AdminOwnerSearchTest.kt (Tausif)
 *
 * Purpose: Compose UI (intent) tests for searching the admin console's campsite list by the
 * owner's name, as its search box offers ("Search campsites or owners"; US 03.04.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for the admin searching campsites by owner."
 * Reviewed, edited and run by Team Carbon.
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
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The admin console's Campsites tab with two owners' campsites. */
@RunWith(AndroidJUnit4::class)
class AdminOwnerSearchTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 }).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW))
    private val admin = "admin-phone"

    private fun launchAdmin(tab: String, screen: String, seed: suspend () -> Unit = {}) {
        runBlocking {
            store.saveProfile(SampleData.owner(NOW).copy(activeRole = Role.OWNER))
            store.saveProfile(UserProfile(admin, "Alex Admin", "admin@carbon.ca", activeRole = Role.ADMIN, isAdmin = true))
            store.linkDevice(admin, admin)
            seed()
        }
        val c = AppContainer.inMemory(admin, clock, store)
        compose.setContent {
            EventsTheme {
                EventsApp(c, showSplash = false, splash = {}, setup = {}, alerts = {},
                    entrant = {}, owner = {},
                    admin = { LocalAppSession.current.let { AdminShell(it.container, it.requireProfile(), it.profileVm) } },
                )
            }
        }
        waitFor("admin_campsites")
        if (tab != "Campsites") compose.onNodeWithTag("nav_$tab").performClick()
        waitFor(screen)
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun tap(tag: String) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) }
    private fun tapText(text: String) { compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick) }

    private fun launch() = launchAdmin("Campsites", "admin_campsites") {
        store.createCampsite(Campsite("", "owner-2", "Rocky Ridge Camping", "Elk Meadow", "Jasper, AB", maxOccupancy = 4, createdAt = NOW))
    }

    private fun search(text: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("admin_campsites"))).performTextInput(text)

    @Test fun search_byOwnerName_findsThatOwnersCampsites() {
        launch()
        search("rocky ridge")
        compose.onNodeWithText("Elk Meadow").assertExists()
        compose.onNodeWithTag("campsite_card_two-jack").assertDoesNotExist()
    }

    @Test fun search_byPartOfAnOwnerName_findsAllTheirCampsites() {
        launch()
        search("parks")
        store.snapshot.campsites.values.filter { it.ownerName == "Parks Lodge" }.forEach {
            compose.onNodeWithTag("campsite_card_${it.id}").assertExists()
        }
        compose.onNodeWithText("Elk Meadow").assertDoesNotExist()
    }

    @Test fun search_byCampsiteName_stillWorks_andListsEachCampsiteOnce() {
        launch()
        search("elk")
        compose.onAllNodesWithText("Elk Meadow").assertCountEquals(1)
        compose.onNodeWithTag("campsite_card_two-jack").assertDoesNotExist()
    }

    @Test fun search_matchingNeitherCampsiteNorOwner_findsNothing() {
        launch()
        search("zzzz")
        compose.onNodeWithText("No campsites").assertExists()
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
