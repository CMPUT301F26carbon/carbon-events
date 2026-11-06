/*
 * AdminProfilesTest.kt (Tausif)
 *
 * Purpose: Compose UI (intent) tests for an admin browsing every entrant and owner profile and
 * opening one (US 03.05.01).
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
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** An admin on an in-memory store holding the sample owner and three entrants. */
@RunWith(AndroidJUnit4::class)
class AdminProfilesTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 }).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW))
    private val admin = "admin-phone"
    private val entrants = listOf(
        UserProfile("sample-entrant-0", "Maya Chen", "maya.chen@example.com", "+1 780 555 0110"),
        UserProfile("sample-entrant-1", "Liam Okafor", "liam.okafor@example.com", "+1 587 555 0121"),
        UserProfile("sample-entrant-2", "Sofia Rossi", "sofia.rossi@example.com"),
    )

    private fun launch() {
        runBlocking {
            entrants.forEach { store.saveProfile(it) }
            store.saveProfile(SampleData.owner(NOW).copy(activeRole = Role.OWNER))
            store.saveProfile(UserProfile(admin, "Alex Admin", "admin@carbon.ca", activeRole = Role.ADMIN, isAdmin = true))
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
        waitFor("admin_campsites")
        compose.onNodeWithTag("nav_Profiles").performClick()
        waitFor("admin_profiles")
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    /** US 03.05.01: the admin sees every entrant profile with its email. */
    @Test fun admin_browsesEntrantProfiles() {
        launch()
        compose.onNodeWithTag("admin_profiles").assertIsDisplayed()
        compose.onNodeWithText("Entrants · 3").assertExists()
        entrants.forEach { p ->
            compose.onNodeWithTag("admin_profiles").performScrollToNode(hasTestTag("profile_${p.id}"))
            compose.onNodeWithTag("profile_${p.id}").assertIsDisplayed()
            compose.onNodeWithText(p.name).assertExists()
            compose.onNodeWithText(p.email).assertExists()
        }
    }

    /** US 03.05.01: the owners tab lists campsite owners with how many campsites they run. */
    @Test fun admin_browsesOwnerProfiles() {
        launch()
        compose.onNodeWithTag("profiles_tab_1").performClick()
        compose.onNodeWithTag("profile_${SampleData.OWNER_ID}").assertIsDisplayed()
        compose.onNodeWithText("Parks Lodge").assertExists()
        compose.onNodeWithText("Owner · 6 campsites").assertExists()
        compose.onAllNodesWithTag("profile_sample-entrant-0").assertCountEquals(0)
    }

    /** US 03.05.01: opening a profile shows that person's contact details. */
    @Test fun admin_opensAProfileToSeeItsDetails() {
        launch()
        compose.onNodeWithTag("profile_sample-entrant-0").performClick()
        waitFor("admin_profile_detail")
        compose.onNodeWithText("Entrant Profile").assertIsDisplayed()
        compose.onNodeWithText("Maya Chen").assertIsDisplayed()
        compose.onNodeWithText("maya.chen@example.com").assertIsDisplayed()
        compose.onNodeWithText("+1 780 555 0110").assertIsDisplayed()
    }

    private companion object {
        /** Fixed time shared with the realistic fixture. */
        const val NOW = 1_792_162_800_000L
    }
}
