/*
 * OwnerCampsitesTest.kt (Rayner)
 *
 * Purpose: Compose UI (intent) tests for the owner's campsites: list my campsites
 * (US 02.03.01), view a campsite's details (US 02.01.04), update a campsite (US 02.01.03) and
 * the automatic draw once registration closes (US 02.05.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 6, 2026). Prompt: "Write
 * UI (intent) tests for the 16 half-way user stories that have no UI test yet." Reviewed, edited
 * and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The sample owner, Parks Lodge, managing their campsites on an in-memory store. */
@RunWith(AndroidJUnit4::class)
class OwnerCampsitesTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 }).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW))
    private val owner = SampleData.OWNER_ID
    private var drawScope: CoroutineScope? = null

    @After fun stopDraws() { drawScope?.cancel() }

    private fun launch(startDraws: Boolean = false) {
        runBlocking {
            store.saveProfile(SampleData.owner(NOW).copy(activeRole = Role.OWNER))
            store.linkDevice(owner, owner)
        }
        val c = AppContainer.inMemory(owner, clock, store)
        if (startDraws) drawScope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { c.drawScheduler.start(it) }
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

    private fun waitForText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun field(tag: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

    private fun openCampsite(name: String) {
        compose.onNodeWithTag("owner_campsites").performScrollToNode(hasText(name))
        compose.onNodeWithText(name).performClick()
        waitFor("manage_name")
        compose.waitForIdle() // let the screen transition finish
    }

    /** US 02.03.01: the dashboard lists every campsite the owner registered. */
    @Test fun owner_seesAListOfTheirCampsites() {
        launch()
        compose.onNodeWithText("My Campsites").assertIsDisplayed()
        SampleData.campsites(NOW).forEach { site ->
            compose.onNodeWithTag("owner_campsites").performScrollToNode(hasText(site.name))
            compose.onNodeWithText(site.name).assertIsDisplayed()
        }
    }

    /** US 02.01.04: opening a campsite shows its name, location, price, occupancy and lottery. */
    @Test fun owner_viewsTheDetailsOfACampsite() {
        launch()
        openCampsite("Two Jack Lakeside")
        compose.onNodeWithTag("manage_name").assertTextEquals("Two Jack Lakeside")
        compose.onNodeWithText("Banff, AB · \$65/night · max 6").assertIsDisplayed()
        compose.onNodeWithTag("manage_lottery").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("LOTTERY LIVE").assertExists()
        compose.onNodeWithTag("qr_code").assertExists()
    }

    /** US 02.01.03: editing a campsite saves the new details to the store and the screen. */
    @Test fun owner_updatesTheDetailsOfACampsite() {
        launch()
        openCampsite("Two Jack Lakeside")
        compose.onNodeWithTag("manage_edit").performClick()
        compose.onNodeWithText("Edit Campsite").assertIsDisplayed()
        field("form_name").performTextClearance()
        field("form_name").performTextInput("Two Jack Main")
        field("form_location").performTextClearance()
        field("form_location").performTextInput("Canmore, AB")
        compose.onNodeWithTag("form_next").performClick()
        compose.onNodeWithText("Save changes").performClick()
        waitFor("manage_name")
        compose.onNodeWithTag("manage_name").assertTextEquals("Two Jack Main")
        val site = store.snapshot.campsites.getValue("two-jack")
        assertEquals("Two Jack Main", site.name)
        assertEquals("Canmore, AB", site.location)
        assertEquals(65, site.pricePerNight)
    }

    /** US 02.05.01: once registration closes, winners are drawn without the owner doing anything. */
    @Test fun lottery_drawsWinnersAutomaticallyAfterRegistrationEnds() {
        // The sample Lake Louise lottery closes two minutes after NOW; open the app after that.
        clock.advanceBy(3 * MINUTE)
        launch(startDraws = true)
        compose.waitUntil(10_000) { store.snapshot.lotteries.getValue("lot-lake-louise").status == LotteryStatus.DRAWN }
        val winners = store.snapshot.entries.filter { it.lotteryId == "lot-lake-louise" && it.status == EntryStatus.WON }
        assertEquals(3, winners.size)
        openCampsite("Lake Louise Tent Sites")
        compose.onNodeWithText("WINNERS DRAWN").assertIsDisplayed()
        compose.onAllNodesWithTag("draw_now").assertCountEquals(0)
    }

    private companion object {
        /** Fixed time shared with the realistic fixture. */
        const val NOW = 1_792_162_800_000L
        const val MINUTE = 60_000L
    }
}
