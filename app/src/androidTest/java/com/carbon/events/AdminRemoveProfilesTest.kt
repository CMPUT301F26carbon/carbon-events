/*
 * AdminRemoveProfilesTest.kt (Tausif)
 *
 * Purpose: Compose UI (intent) tests for the admin removing people for good: an entrant
 * profile (US 03.02.01) and a campsite owner who broke the rules, together with their campsites
 * (US 03.07.01). A removed record stays so they can't come back, and the admin can restore it.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for the admin removing profiles and campsite owners."
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

/** The admin console's Profiles tab over the sample data. */
@RunWith(AndroidJUnit4::class)
class AdminRemoveProfilesTest {
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

    private val maya = UserProfile("sample-entrant-0", "Maya Chen", "maya.chen@example.com", "+1 780 555 0110")
    private val liam = UserProfile("sample-entrant-1", "Liam Okafor", "liam.okafor@example.com")

    private fun launch() = launchAdmin("Profiles", "admin_profiles") { store.saveProfile(maya); store.saveProfile(liam) }

    private fun openProfile(id: String) {
        tap("profile_$id")
        waitFor("moderate_remove")
    }

    private fun profiles() = store.snapshot.profiles

    @Test fun removeEntrant_asksFirst() {
        launch()
        openProfile(maya.id)
        tap("moderate_remove")
        compose.onNodeWithTag("remove_dialog").assertExists()
        compose.onNodeWithText("Remove Maya Chen?").assertExists()
        assertTrue("nothing is removed before confirming", profiles().containsKey(maya.id))
    }

    @Test fun removeEntrant_cancel_keepsTheProfile() {
        launch()
        openProfile(maya.id)
        tap("moderate_remove")
        tapText("Cancel")
        compose.onNodeWithTag("remove_dialog").assertDoesNotExist()
        assertTrue(profiles().containsKey(maya.id))
    }

    @Test fun removeEntrant_saysTheyCantComeBack() {
        launch()
        openProfile(maya.id)
        tap("moderate_remove")
        compose.onNodeWithText("can't use Events again", substring = true).assertExists()
        compose.onNodeWithTag("remove_block").assertDoesNotExist() // every removal keeps them out
    }

    @Test fun removeEntrant_confirm_leavesARemovedRecord() {
        launch()
        openProfile(maya.id)
        tap("moderate_remove")
        tap("remove_confirm")
        compose.waitUntil(10_000) { profiles()[maya.id]?.removed == true }
        assertTrue(profiles().getValue(maya.id).blocked)
        assertFalse("the other entrant is untouched", profiles().getValue(liam.id).removed)
    }

    @Test fun aRemovedPerson_isTaggedRemoved_andCanBeRestored() {
        launch()
        openProfile(maya.id)
        tap("moderate_remove")
        tap("remove_confirm")
        compose.waitUntil(10_000) { profiles()[maya.id]?.removed == true }
        waitFor("admin_profiles")
        compose.onNode(hasText("REMOVED", ignoreCase = true) and hasAnyAncestor(hasTestTag("profile_${maya.id}")), useUnmergedTree = true).assertExists()
        tap("profile_${maya.id}")
        waitFor("moderate_block")
        compose.onNodeWithText("Restore").assertExists()
        tap("moderate_block")
        compose.waitUntil(10_000) { profiles()[maya.id]?.removed == false }
        assertFalse(profiles().getValue(maya.id).blocked)
    }

    @Test fun removeOwner_warnsAboutTheirCampsites() {
        launch()
        tap("profiles_tab_1")
        openProfile(SampleData.OWNER_ID)
        tap("moderate_remove")
        compose.onNodeWithText("Remove Parks Lodge?").assertExists()
        compose.onNodeWithText("their 6 campsites with every lottery and waiting list", substring = true).assertExists()
    }

    @Test fun removeOwner_confirm_deletesTheOwnerTheirCampsitesAndLotteries() {
        launch()
        tap("profiles_tab_1")
        openProfile(SampleData.OWNER_ID)
        tap("moderate_remove")
        tap("remove_confirm")
        compose.waitUntil(10_000) { profiles()[SampleData.OWNER_ID]?.removed == true }
        assertTrue(store.snapshot.campsites.values.none { it.ownerId == SampleData.OWNER_ID })
        assertTrue(store.snapshot.lotteries.values.none { it.ownerId == SampleData.OWNER_ID })
        assertTrue("entrants stay", profiles().containsKey(maya.id))
    }


    private companion object { const val NOW = 1_792_162_800_000L }
}
