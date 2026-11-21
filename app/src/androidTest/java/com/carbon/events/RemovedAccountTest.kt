/*
 * RemovedAccountTest.kt (Tausif)
 *
 * Purpose: Compose UI (intent) tests for what someone an admin removed sees (US 03.02.01,
 * US 03.07.01): opening the app shows only Your account was removed, and the phone they used
 * can't be used to sign up again.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for what a person an admin removed sees."
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

/** Maya, removed by an admin, on the phone she used. */
@RunWith(AndroidJUnit4::class)
class RemovedAccountTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 },
        ).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW))

    /** Opens the whole app on [phone] with [auth] as its login. */
    private fun launchApp(phone: String, auth: InMemoryAuth) {
        val c = AppContainer.inMemory(phone, clock, store, auth)
        compose.setContent {
            EventsTheme {
                EventsApp(c, showSplash = false, splash = {},
                    setup = { SetupScreen(LocalAppSession.current.profileVm) }, alerts = {},
                    entrant = { LocalAppSession.current.let { EntrantShell(it.container, it.requireProfile(), it.profileVm, null, {}) } },
                    owner = {}, admin = {},
                )
            }
        }
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun tap(tag: String) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) }
    private fun tapText(text: String) { compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick) }

    private val maya = UserProfile("sample-entrant-0", "Maya Chen", "maya@example.com", authUid = "uid-maya")
    private val phone = "maya-phone"

    private fun removeMaya() = runBlocking {
        store.saveProfile(maya)
        store.trustDevice(TrustedDevice(phone, maya.id, "Pixel 8", NOW))
        store.removeProfile(maya, NOW)
    }

    private fun field(tag: String) = compose.onNode(hasSetTextAction() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))))

    @Test fun openingTheApp_showsOnlyAccountRemoved() {
        removeMaya()
        launchApp(phone, InMemoryAuth("uid-maya"))
        waitFor("blocked")
        compose.onNodeWithText("Your account was removed").assertExists()
        compose.onNodeWithText("An administrator removed Maya's account", substring = true).assertExists()
        compose.onNodeWithTag("explore").assertDoesNotExist()
        compose.onNodeWithTag("nav_Profile").assertDoesNotExist()
    }

    @Test fun signingOut_goesBackToWelcome() {
        removeMaya()
        launchApp(phone, InMemoryAuth("uid-maya"))
        waitFor("blocked")
        tapText("Sign out")
        waitFor("welcome")
    }

    @Test fun continueWithThisPhone_onTheirPhone_isRefused() {
        removeMaya()
        launchApp(phone, InMemoryAuth(null))
        waitFor("welcome")
        tap("welcome_create")
        tap("account_device")
        field("details_name").performTextInput("Maya C")
        field("details_age").performTextInput("30")
        tap("details_continue")
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(PHONE_REMOVED_MESSAGE)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithTag("terms_agree").assertDoesNotExist()
        assertEquals(1, store.snapshot.profiles.values.count { it.name.startsWith("Maya") })
    }

    @Test fun anotherPhone_isNotAffected() {
        removeMaya()
        launchApp("someone-elses-phone", InMemoryAuth(null))
        waitFor("welcome")
        tap("welcome_create")
        tap("account_device")
        field("details_name").performTextInput("Jordan Lee")
        field("details_age").performTextInput("30")
        tap("details_continue")
        waitFor("terms_agree")
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
