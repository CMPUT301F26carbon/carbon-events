/*
 * CampsiteFormTest.kt (Rayner)
 *
 * Purpose: Compose UI (intent) tests for CampsiteForm.kt: the campsite rules on the form (US 02.01.03).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write UI (intent) tests for the campsite form."
 * Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import android.Manifest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** The sample owner, Parks Lodge, and their campsites. */
@RunWith(AndroidJUnit4::class)
class CampsiteFormTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 },
        ).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW))
    private val owner = SampleData.OWNER_ID

    /** Signs the sample owner (Parks Lodge) in on this phone and opens My Campsites. */
    private fun launchOwner(seed: suspend () -> Unit = {}) {
        runBlocking {
            store.saveProfile(SampleData.owner(NOW).copy(activeRole = Role.OWNER))
            store.linkDevice(owner, owner)
            seed()
        }
        val c = AppContainer.inMemory(owner, clock, store)
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

    private fun tap(tag: String) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) }

    private fun field(tag: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)) or (hasSetTextAction() and hasTestTag(tag)))
    private fun site(id: String) = store.snapshot.campsites.getValue(id)

    /** Opens one of My Campsites, scrolling the list to it first. */
    private fun openCampsite(name: String) {
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("owner_campsites").performScrollToNode(hasText(name)) }.isSuccess }
        compose.onNodeWithText(name).performClick()
        waitFor("manage_name")
    }

    // ---- After publishing a campsite ----

    @Test fun campsiteRules_areSavedFromTheForm() {
        launchOwner()
        openCampsite("Two Jack Lakeside")
        compose.onNodeWithTag("manage_edit").performClick()
        tap("form_next")
        waitFor("form_rules")
        field("form_rules").performScrollTo().performTextInput("Quiet hours 10 PM to 7 AM")
        compose.onNodeWithText("Save changes").performClick()
        waitFor("manage_name")
        assertTrue(site("two-jack").rules.contains("Quiet hours 10 PM to 7 AM"))
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
