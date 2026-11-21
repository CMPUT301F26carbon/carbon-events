/*
 * AdminTest.kt (Tausif)
 *
 * Purpose: Compose UI (intent) tests for the admin screens in Admin.kt and Moderation.kt that no other test covers: the Flagged filter on Campsites, reading and dismissing the reports about a campsite (US 03.01.01), and sending a profile a warning (US 03.04.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write UI (intent) tests for every part of the admin screens that isn't tested yet."
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** An admin on the sample data. */
@RunWith(AndroidJUnit4::class)
class AdminTest {
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

    private fun field(tag: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)) or (hasSetTextAction() and hasTestTag(tag)))
    private val maya = UserProfile("sample-entrant-0", "Maya Chen", "maya.chen@example.com")

    private suspend fun reportsAbout(vararg campsites: Pair<String, String>) =
        campsites.forEachIndexed { i, (id, name) -> store.report(Report("", id, name, "reporter-$i", "Reporter $i", REPORT_REASONS[i % REPORT_REASONS.size], NOW + i)) }

    // ---- Flagged campsites ----

    @Test fun flagged_showsOnlyReportedCampsites() {
        launchAdmin("Campsites", "admin_campsites") {
            reportsAbout("two-jack" to "Two Jack Lakeside", "two-jack" to "Two Jack Lakeside", "johnston-canyon" to "Johnston Canyon")
        }
        waitFor("admin_flagged")
        compose.onNodeWithText("Flagged · 2").assertExists()
        tap("admin_flagged")
        waitFor("campsite_card_two-jack")
        compose.onNodeWithTag("campsite_card_johnston-canyon").assertExists()
        compose.onNodeWithTag("campsite_card_tunnel-mountain").assertDoesNotExist()
        compose.onNodeWithText("2 flags", ignoreCase = true).assertExists()
        compose.onNodeWithText("1 flag", ignoreCase = true).assertExists()
    }

    @Test fun reportsOnACampsite_areShown_andCanBeDismissed() {
        launchAdmin("Campsites", "admin_campsites") { reportsAbout("two-jack" to "Two Jack Lakeside", "two-jack" to "Two Jack Lakeside") }
        compose.onNodeWithTag("campsite_card_two-jack").performScrollTo().performClick()
        waitFor("campsite_reports")
        compose.onNodeWithText(REPORT_REASONS[0], substring = true).assertExists()
        compose.onNodeWithTag("reports_dismiss").performScrollTo()
        tap("reports_dismiss")
        compose.waitUntil(5_000) { runBlocking { store.observeReports().first() }.none { it.campsiteId == "two-jack" } }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("campsite_reports")).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
    }

    // ---- Warnings ----

    private fun openMaya() {
        tap("profile_${maya.id}")
        waitFor("moderate_warn")
    }

    @Test fun warning_isSent_counted_andConfirmed() {
        launchAdmin("Profiles", "admin_profiles") { store.saveProfile(maya) }
        openMaya()
        tap("moderate_warn")
        waitFor("warn_text")
        field("warn_text").performTextClearance()
        field("warn_text").performTextInput("Please keep reviews respectful.")
        tap("warn_send")
        waitFor("moderation_message")
        compose.waitUntil(5_000) { store.snapshot.profiles.getValue(maya.id).warnings == 1 }
        val note = store.snapshot.notifications.single { it.recipientId == maya.id && it.type == NotificationType.WARNING }
        assertEquals("Please keep reviews respectful.", note.body)
    }

    @Test fun aWarning_startsWithASuggestedMessage_butCantBeSentEmpty() {
        launchAdmin("Profiles", "admin_profiles") { store.saveProfile(maya) }
        openMaya()
        tap("moderate_warn")
        waitFor("warn_send")
        compose.onNodeWithTag("warn_send").assertIsEnabled() // starts with a suggested message
        field("warn_text").performTextClearance()
        compose.onNodeWithTag("warn_send").assertIsNotEnabled()
        field("warn_text").performTextInput("   ")
        compose.onNodeWithTag("warn_send").assertIsNotEnabled()
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
