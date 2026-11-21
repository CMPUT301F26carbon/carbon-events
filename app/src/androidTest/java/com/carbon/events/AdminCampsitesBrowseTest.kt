/*
 * AdminCampsitesBrowseTest.kt (Tausif)
 *
 * Purpose: Compose UI (intent) tests for the admin console's campsite browser: every campsite is
 * listed with its owner and lottery, the list can be searched, and a row
 * opens the campsite's review screen (US 03.04.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for the admin browsing campsites." Reviewed, edited and run by Team Carbon.
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

/** An admin looking through the sample campsites on an in-memory store. */
@RunWith(AndroidJUnit4::class)
class AdminCampsitesBrowseTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 }).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW))
    private val admin = "admin-phone"

    private fun launch() {
        runBlocking {
            store.saveProfile(UserProfile(admin, "Tausif Admin", "admin@example.com", activeRole = Role.ADMIN, isAdmin = true))
            store.linkDevice(admin, admin)
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
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("campsite_card_two-jack")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun searchField() = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("admin_campsites")))
    private fun row(id: String) = compose.onNodeWithTag("campsite_card_$id")

    @Test fun browser_listsEveryCampsiteWithTheTotal() {
        launch()
        val all = store.snapshot.campsites.keys
        compose.onNodeWithText("${all.size} TOTAL", ignoreCase = true).assertExists()
        all.forEach { row(it).assertExists() }
    }

    @Test fun eachRow_showsTheOwnerAndTheLottery() {
        launch()
        compose.onNode(hasText("Parks Lodge · ", substring = true) and hasText("waitlisted", substring = true) and hasAnyAncestor(hasTestTag("campsite_card_two-jack")), useUnmergedTree = true).assertExists()
        compose.onNode(hasText("Parks Lodge · no lottery") and hasAnyAncestor(hasTestTag("campsite_card_waterfowl-lakes")), useUnmergedTree = true).assertExists()
    }

    @Test fun search_byCampsiteName_narrowsTheList() {
        launch()
        searchField().performTextInput("moraine")
        row("moraine-lake").assertExists()
        row("two-jack").assertDoesNotExist()
    }

    @Test fun search_withNoMatch_saysSo() {
        launch()
        searchField().performTextInput("zzzz")
        compose.onNodeWithText("No campsites").assertExists()
    }

    @Test fun tappingARow_opensTheCampsiteReview() {
        launch()
        row("lake-louise").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("admin_campsite_name").assertTextEquals("Lake Louise Tent Sites")
        compose.onNodeWithText("Lake Louise, AB").assertExists()
        compose.onNodeWithText("Parks Lodge").assertExists()
        assertTrue("browsing removes nothing", store.snapshot.campsites.containsKey("lake-louise"))
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
