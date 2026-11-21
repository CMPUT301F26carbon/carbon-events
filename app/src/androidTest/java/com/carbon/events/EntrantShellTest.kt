/*
 * EntrantShellTest.kt (Ansh)
 *
 * Purpose: Compose UI (intent) tests for EntrantShell.kt: every tab of the entrant's bottom bar opens its screen.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write UI (intent) tests for the entrant's bottom-nav shell."
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

/** An entrant on the sample data. */
@RunWith(AndroidJUnit4::class)
class EntrantShellTest {
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

    /** Liam Okafor, a Johnston Canyon winner whose stay is in progress (sample data). */
    private val liam = UserProfile("sample-entrant-1", "Liam Okafor", "liam@example.com")
    /** Maya Chen, waiting on the Two Jack and Lake Louise lists, with no bookings. */
    private val maya = UserProfile("sample-entrant-0", "Maya Chen", "maya@example.com")

    private fun openBookings() {
        compose.onNodeWithTag("nav_Bookings").performClick()
        waitFor("entrant_bookings")
    }

    @Test fun everyTab_opensItsScreen() {
        launchEntrant(maya)
        compose.onNodeWithTag("nav_Lotteries").performClick()
        waitFor("my_lottery_lot-two-jack")
        compose.onNodeWithTag("nav_Bookings").performClick()
        waitFor("entrant_bookings")
        compose.onNodeWithTag("nav_Profile").performClick()
        waitFor("profile_name")
        compose.onNodeWithTag("nav_Explore").performClick()
        waitFor("explore")
        // Scan is full screen, without the bar, so it is opened last.
        compose.onNodeWithTag("nav_Scan").performClick()
        waitFor("scan_screen")
    }

    @Test fun lotteries_listsEveryWaitlistJoined() {
        launchEntrant(maya)
        compose.onNodeWithTag("nav_Lotteries").performClick()
        waitFor("my_lottery_lot-two-jack")
        waitFor("my_lottery_lot-lake-louise")
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
