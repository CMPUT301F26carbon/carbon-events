/*
 * NotifyWaitlistTest.kt (Tausif)
 *
 * Purpose: Compose UI (intent) tests for NotifyWaitlist.kt: an owner's message goes to the chosen part of the waitlist and shows in the sent log (US 02.10.01, US 02.10.02).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write UI (intent) tests for notifying a waitlist."
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

/** The sample owner, Parks Lodge. */
@RunWith(AndroidJUnit4::class)
class NotifyWaitlistTest {
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
    private fun tapText(text: String) { compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick) }

    private fun field(tag: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)) or (hasSetTextAction() and hasTestTag(tag)))

    private fun openNotify() {
        launchOwner()
        compose.onNodeWithTag("owner_campsites").performScrollToNode(hasText("Two Jack Lakeside"))
        compose.onNodeWithText("Two Jack Lakeside").performClick()
        waitFor("manage_notify")
        tap("manage_notify")
        waitFor("notify_send")
    }

    @Test fun aMessage_isSent_andLogged() {
        openNotify()
        field("notify_text").performTextClearance()
        field("notify_text").performTextInput("Gates open at 2 pm.")
        tap("notify_send")
        waitFor("notify_result")
        compose.waitUntil(5_000) { runBlocking { store.observeAnnouncements("lot-two-jack").first() }.any { it.text == "Gates open at 2 pm." } }
    }

    @Test fun anEmptyMessage_isNotSent_andSaysWhy() {
        openNotify()
        field("notify_text").performTextClearance()
        tap("notify_send")
        compose.onNodeWithText("Write a message first.").assertExists()
        assertTrue(runBlocking { store.observeAnnouncements("lot-two-jack").first() }.isEmpty())
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
