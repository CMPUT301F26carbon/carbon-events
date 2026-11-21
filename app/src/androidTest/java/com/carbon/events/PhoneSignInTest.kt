/*
 * PhoneSignInTest.kt (Rayner)
 *
 * Purpose: Compose UI (intent) tests for signing in with the phone itself, no email or
 * password: Welcome > Create an Account > Continue with this phone > Your details > Terms,
 * then opening the app again on the same phone (US 01.09.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * a UI (intent) test for Continue with this phone followed by opening the app again." Reviewed,
 * edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import android.Manifest
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A new person on a phone nobody has used, on an in-memory store. */
@RunWith(AndroidJUnit4::class)
class PhoneSignInTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 },
        ).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW))
    private val auth = InMemoryAuth(null)
    private val phone = "pixel-8-test"

    /** Each change of [launches] is a fresh app start on the same phone, with the same saved data. */
    private var launches by mutableIntStateOf(0)

    private fun launch() {
        compose.setContent {
            key(launches) {
                // A new start has new screens and view models; only the saved data and login carry over.
                val screens = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
                val c = remember { AppContainer.inMemory(phone, clock, store, auth) }
                CompositionLocalProvider(LocalViewModelStoreOwner provides screens) {
                    EventsTheme {
                        EventsApp(c, showSplash = false, splash = {},
                            setup = { SetupScreen(LocalAppSession.current.profileVm) }, alerts = {},
                            entrant = { LocalAppSession.current.let { EntrantShell(it.container, it.requireProfile(), it.profileVm, null, {}) } },
                            owner = {}, admin = {},
                        )
                    }
                }
            }
        }
        waitFor("welcome")
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    /** Taps a button wherever it sits on the screen. */
    private fun tap(tag: String) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) }

    private fun field(tag: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)) or (hasSetTextAction() and hasTestTag(tag)))

    private fun signUpWithThisPhone(name: String) {
        tap("welcome_create")
        tap("account_device")
        field("details_name").performTextInput(name)
        field("details_age").performTextInput("24")
        tap("details_continue")
        waitFor("terms_agree")
        tap("terms_agree")
        tap("terms_accept")
        waitFor("explore")
    }

    @Test fun continueWithThisPhone_asksForNoEmailOrPassword() {
        launch()
        tap("welcome_create")
        tap("account_device")
        compose.onNodeWithTag("details_name").assertExists()
        compose.onNodeWithTag("details_password").assertDoesNotExist()
        compose.onNodeWithTag("details_age").assertExists()
    }

    @Test fun continueWithThisPhone_createsTheProfileAndOpensExplore() {
        launch()
        signUpWithThisPhone("Maya Chen")
        compose.onNodeWithTag("explore").assertIsDisplayed()
        val profile = store.snapshot.profiles.values.single { it.name == "Maya Chen" }
        assertEquals(Role.ENTRANT, profile.activeRole)
        assertEquals("", profile.email)
        assertEquals("the phone is trusted without asking", profile.id, store.snapshot.devices.getValue(phone).userId)
    }

    @Test fun openingTheAppAgain_onTheSamePhone_goesStraightIn() {
        launch()
        signUpWithThisPhone("Maya Chen")
        launches++ // close and open the app
        waitFor("explore")
        compose.onNodeWithTag("welcome").assertDoesNotExist()
        compose.onNodeWithTag("nav_Profile").performClick()
        compose.onNodeWithText("Maya Chen").assertExists()
        assertEquals("no second profile is made", 1, store.snapshot.profiles.values.count { it.name == "Maya Chen" })
    }

    @Test fun detailsWithoutAName_staysOnYourDetails() {
        launch()
        tap("welcome_create")
        tap("account_device")
        field("details_age").performTextInput("24")
        tap("details_continue")
        compose.onNodeWithTag("terms_agree").assertDoesNotExist()
        assertTrue(store.snapshot.profiles.values.none { it.age == 24 })
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
