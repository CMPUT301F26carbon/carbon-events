/*
 * ChatTest.kt (Tausif)
 *
 * Purpose: Compose UI (intent) tests for Chat.kt: a winner messages their host from Bookings, and the message is sent (US 01.14.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write UI (intent) tests for a conversation."
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

/** Liam Okafor, a Johnston Canyon winner. */
@RunWith(AndroidJUnit4::class)
class ChatTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 },
        ).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW))

    /** Signs [profile] in on this phone and opens the entrant app on Explore. */
    private fun launchEntrant(profile: UserProfile, seed: suspend () -> Unit = {}) {
        runBlocking {
            store.saveProfile(profile)
            store.linkDevice(profile.id, profile.id)
            seed()
        }
        val c = AppContainer.inMemory(profile.id, clock, store)
        compose.setContent {
            EventsTheme {
                EventsApp(c, showSplash = false, splash = {}, setup = {}, alerts = {},
                    entrant = { LocalAppSession.current.let { EntrantShell(it.container, it.requireProfile(), it.profileVm, null, {}) } },
                    owner = {}, admin = {},
                )
            }
        }
        waitFor("explore")
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun tap(tag: String) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) }
    private fun tapText(text: String) { compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick) }

    private fun field(tag: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)) or (hasSetTextAction() and hasTestTag(tag)))

    /** Liam Okafor, a Johnston Canyon winner whose stay is in progress (sample data). */
    private val liam = UserProfile("sample-entrant-1", "Liam Okafor", "liam@example.com")
    /** Maya Chen, waiting on the Two Jack and Lake Louise lists, with no bookings. */
    private val maya = UserProfile("sample-entrant-0", "Maya Chen", "maya@example.com")

    private fun openBookings() {
        compose.onNodeWithTag("nav_Bookings").performClick()
        waitFor("entrant_bookings")
    }

    @Test fun aWinner_messagesTheirHost() {
        launchEntrant(liam)
        openBookings()
        compose.onNodeWithTag("entrant_bookings").performScrollToNode(hasTestTag("chat_lot-johnston"))
        tap("chat_lot-johnston")
        waitFor("chat_screen")
        compose.onNodeWithTag("chat_read_only").assertDoesNotExist()
        field("chat_input").performTextInput("Is there firewood?")
        tap("chat_send")
        compose.waitUntil(5_000) { store.snapshot.messages.any { it.text == "Is there firewood?" && it.senderId == liam.id } }
    }

    @Test fun anEmptyMessage_cantBeSent() {
        launchEntrant(liam)
        openBookings()
        compose.onNodeWithTag("entrant_bookings").performScrollToNode(hasTestTag("chat_lot-johnston"))
        tap("chat_lot-johnston")
        waitFor("chat_send")
        compose.onNodeWithTag("chat_send").assertIsNotEnabled()
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
