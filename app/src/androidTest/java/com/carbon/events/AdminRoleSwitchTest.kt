/*
 * AdminRoleSwitchTest.kt (Ansh)
 *
 * Purpose: Compose UI (intent) tests for an admin using their one profile as an entrant or a
 * campsite owner: Account > View the app as switches the whole app, and back again; people
 * who aren't admins can't switch (US 03.11.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for an admin acting as an entrant or campsite owner with their admin profile."
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

/** The "View the app as" control on Profile. */
@RunWith(AndroidJUnit4::class)
class AdminRoleSwitchTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 },
        ).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW))

    /** Signs [profile] in on this phone and opens the whole app, whichever role it is using. */
    private fun launchApp(profile: UserProfile, firstScreen: String, seed: suspend () -> Unit = {}) {
        runBlocking {
            store.saveProfile(profile)
            store.linkDevice(profile.id, profile.id)
            seed()
        }
        val c = AppContainer.inMemory(profile.id, clock, store)
        compose.setContent {
            EventsTheme {
                EventsApp(c, showSplash = false, splash = {},
                    setup = { SetupScreen(LocalAppSession.current.profileVm) }, alerts = {},
                    entrant = { LocalAppSession.current.let { EntrantShell(it.container, it.requireProfile(), it.profileVm, null, {}) } },
                    owner = { LocalAppSession.current.let { OwnerShell(it.container, it.requireProfile(), it.profileVm) } },
                    admin = { LocalAppSession.current.let { AdminShell(it.container, it.requireProfile(), it.profileVm) } },
                )
            }
        }
        waitFor(firstScreen)
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun tap(tag: String) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) }
    private fun tapText(text: String) { compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick) }

    private val alex = UserProfile("admin-phone", "Alex Admin", "admin@carbon.ca", activeRole = Role.ADMIN, isAdmin = true)

    private fun openAdminAccount() {
        launchApp(alex, "admin_campsites")
        compose.onNodeWithTag("nav_Account").performClick()
        waitFor("role_control")
    }

    private fun role() = store.snapshot.profiles.getValue(alex.id).activeRole

    @Test fun admin_canPickAnyOfTheThreeRoles() {
        openAdminAccount()
        compose.onNodeWithText("View the app as").assertExists()
        listOf("role_entrant", "role_owner").forEach { compose.onNodeWithTag(it).assertIsEnabled() }
    }

    @Test fun admin_switchesToEntrant_andGetsTheEntrantApp() {
        openAdminAccount()
        tap("role_entrant")
        waitFor("explore")
        assertEquals(Role.ENTRANT, role())
        assertTrue("still the same admin profile", store.snapshot.profiles.getValue(alex.id).isAdmin)
        assertEquals(1, store.snapshot.profiles.values.count { it.name == "Alex Admin" })
    }

    @Test fun admin_asEntrant_canJoinALottery() {
        openAdminAccount()
        tap("role_entrant")
        waitFor("explore")
        compose.onNodeWithTag("explore_search").performClick()
        compose.onNodeWithTag("search_field").performTextInput("two jack")
        compose.onNodeWithTag("campsite_card_two-jack").performClick()
        waitFor("lottery_action")
        tap("lottery_action")
        compose.waitUntil(10_000) { store.snapshot.entries.any { it.lotteryId == "lot-two-jack" && it.entrantId == alex.id } }
    }

    @Test fun admin_switchesToOwner_andGetsMyCampsites() {
        openAdminAccount()
        tap("role_owner")
        waitFor("owner_campsites")
        assertEquals(Role.OWNER, role())
    }

    @Test fun admin_asOwner_canSwitchBackToAdmin() {
        openAdminAccount()
        tap("role_owner")
        waitFor("owner_campsites")
        compose.onNodeWithTag("nav_Profile").performClick()
        waitFor("role_control")
        tap("role_admin")
        waitFor("admin_campsites")
        assertEquals(Role.ADMIN, role())
    }

    @Test fun entrant_cannotSwitchRoles() {
        launchApp(UserProfile("test-phone", "Adam Kent", "adam@example.com"), "explore")
        compose.onNodeWithTag("nav_Profile").performClick()
        waitFor("role_control")
        compose.onNodeWithTag("role_owner").assertIsNotEnabled()
        compose.onNodeWithTag("role_admin").assertIsNotEnabled()
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
