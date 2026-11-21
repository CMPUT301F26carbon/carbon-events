/*
 * NotificationOptOutTest.kt (Rayner)
 *
 * Purpose: Compose UI (intent) tests for opting out of notifications with the switch on Profile:
 * off stops lottery results and owner messages from popping up, on brings them back, and the
 * choice is saved on the profile (US 01.10.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for an entrant opting out of notifications."
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

/** An entrant using the Notifications switch on their profile. */
@RunWith(AndroidJUnit4::class)
class NotificationOptOutTest {
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

    private val adam = UserProfile("test-phone", "Adam Kent", "adam@example.com")

    private fun openProfile() {
        launchApp(adam, "explore")
        compose.onNodeWithTag("nav_Profile").performClick()
        waitFor("profile_notifications_switch")
    }

    private fun prefs() = store.snapshot.profiles.getValue(adam.id).prefs
    private fun flipSwitch() { compose.onNodeWithTag("profile_notifications_switch").performClick() }

    @Test fun notifications_startOn() {
        openProfile()
        compose.onNodeWithTag("profile_notifications_switch").assertIsOn()
        assertFalse(prefs().optOut)
    }

    @Test fun switchingOff_optsOutOfResultsAndMessages() {
        openProfile()
        flipSwitch()
        compose.waitUntil(10_000) { prefs().optOut }
        compose.onNodeWithTag("profile_notifications_switch").assertIsOff()
        assertFalse(prefs().allows(NotificationType.LOTTERY_WON))
        assertFalse(prefs().allows(NotificationType.LOTTERY_LOST))
        assertFalse(prefs().allows(NotificationType.MESSAGE))
        assertFalse(prefs().allows(NotificationType.OPENED))
    }

    @Test fun optedOut_stillGetsAdminWarnings() {
        openProfile()
        flipSwitch()
        compose.waitUntil(10_000) { prefs().optOut }
        assertTrue(prefs().allows(NotificationType.WARNING))
    }

    @Test fun optedOut_resultsStillReachTheInAppInbox() {
        openProfile()
        flipSwitch()
        compose.waitUntil(10_000) { prefs().optOut }
        runBlocking {
            store.joinWaitlist("lot-lake-louise", adam.id, adam.name, NOW)
            store.runDrawIfDue("lot-lake-louise", NOW + 5 * 60_000)
        }
        assertTrue(store.snapshot.notifications.any { it.recipientId == adam.id })
    }

    @Test fun switchingBackOn_optsIn() {
        openProfile()
        flipSwitch()
        compose.waitUntil(10_000) { prefs().optOut }
        flipSwitch()
        compose.waitUntil(10_000) { !prefs().optOut }
        compose.onNodeWithTag("profile_notifications_switch").assertIsOn()
        assertTrue(prefs().allows(NotificationType.LOTTERY_WON))
    }

    @Test fun theChoice_isStillThereNextTime() {
        launchApp(adam.copy(prefs = NotificationPrefs(optOut = true)), "explore")
        compose.onNodeWithTag("nav_Profile").performClick()
        waitFor("profile_notifications_switch")
        compose.onNodeWithTag("profile_notifications_switch").assertIsOff()
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
