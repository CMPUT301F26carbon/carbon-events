/*
 * PoliciesTest.kt (Ansh)
 *
 * Purpose: Compose UI (intent) tests for reading the app's policies and guidelines:
 * Profile > Policies & guidelines lists each policy, and tapping one opens its sections
 * (US 01.11.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for seeing the app policy and guidelines." Reviewed, edited and run by Team Carbon.
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

/** An entrant opening the policies from their profile, on an in-memory store. */
@RunWith(AndroidJUnit4::class)
class PoliciesTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 },
        ).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW))
    private val entrant = "test-phone"

    private fun openPolicies() {
        runBlocking {
            store.saveProfile(UserProfile(entrant, "Adam Kent", "adam@example.com"))
            store.linkDevice(entrant, entrant)
        }
        val c = AppContainer.inMemory(entrant, FixedClock(NOW), store)
        compose.setContent {
            EventsTheme {
                EventsApp(c, showSplash = false, splash = {}, setup = {}, alerts = {},
                    entrant = { LocalAppSession.current.let { EntrantShell(it.container, it.requireProfile(), it.profileVm, null, {}) } },
                    owner = {}, admin = {},
                )
            }
        }
        waitFor("explore")
        compose.onNodeWithTag("nav_Profile").performClick()
        waitFor("profile_policies")
        compose.onNodeWithTag("profile_policies").performSemanticsAction(SemanticsActions.OnClick)
        waitFor("policy_Terms of Service")
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    @Test fun profile_opensPoliciesAndGuidelines() {
        openPolicies()
        compose.onNodeWithText("Policies & Guidelines").assertExists()
        POLICIES.forEach { compose.onNodeWithTag("policy_${it.title}").assertExists() }
        compose.onNodeWithText("Last updated October 2026").assertExists()
    }

    @Test fun policies_startClosed() {
        openPolicies()
        compose.onNodeWithText("Fair draws").assertDoesNotExist()
    }

    @Test fun tappingAPolicy_showsEverySection() {
        openPolicies()
        compose.onNodeWithText("Terms of Service").performClick()
        POLICIES.first { it.title == "Terms of Service" }.sections.forEach { (title, body) ->
            compose.onNodeWithText(title).assertExists()
            compose.onNodeWithText(body).assertExists()
        }
    }

    @Test fun campsiteGuidelines_canBeRead() {
        openPolicies()
        compose.onNodeWithText("Campsite Guidelines").performScrollTo().performClick()
        val sections = POLICIES.first { it.title == "Campsite Guidelines" }.sections
        compose.onNodeWithText(sections.first().first).assertExists()
    }

    @Test fun tappingAnOpenPolicy_closesIt() {
        openPolicies()
        compose.onNodeWithText("Privacy Policy").performClick()
        compose.onNodeWithText("What we keep").assertExists()
        compose.onNodeWithText("Privacy Policy").performClick()
        compose.onNodeWithText("What we keep").assertDoesNotExist()
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
