/*
 * StartupRoutingTest.kt (Tausif)
 *
 * Purpose: checks signed-out startup routing to Welcome through the production app wrapper,
 * that a build without Firebase starts signed out, and that turning the phone or tablet (which
 * recreates the activity) keeps the app open instead of going back to the splash.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Covers signed-out routing through the same wrapper used by the configured production app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StartupRoutingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun configuredAppWrapper_displaysWelcomeForSignedOutUser() {
        val c = AppContainer.inMemory("new-phone", FixedClock(0), auth = InMemoryAuth(null))
        compose.setContent {
            EventsTheme {
                OfflineDemoFrame(enabled = false) {
                    EventsApp(c, showSplash = false,
                        splash = { Text("Loading") },
                        setup = { SetupScreen(LocalAppSession.current.profileVm) },
                        alerts = {}, entrant = {}, owner = {}, admin = {},
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag("welcome").assertExists()
    }

    /** Without google-services.json the app runs offline; nobody is signed in until they sign up. */
    @Test fun buildWithoutFirebase_startsSignedOut() {
        val c = AppContainer.create(ApplicationProvider.getApplicationContext())
        assertFalse(c.usingServer)
        assertNull(runBlocking { c.auth.observeAccount().first() })
    }

    @Test fun buildWithoutFirebase_opensOnWelcome() {
        val c = AppContainer.create(ApplicationProvider.getApplicationContext())
        compose.setContent {
            EventsTheme {
                OfflineDemoFrame(enabled = !c.usingServer) {
                    EventsApp(c, showSplash = false,
                        splash = { Text("Loading") },
                        setup = { SetupScreen(LocalAppSession.current.profileVm) },
                        alerts = {}, entrant = {}, owner = {}, admin = {},
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag("welcome").assertExists()
    }

    /** Turning a tablet recreates the activity; it must come back on the app, not the loading splash. */
    @Test fun rotating_keepsTheApp_insteadOfShowingTheSplashAgain() {
        val adam = UserProfile("adam-kent", "Adam Kent", "adam@example.com", authUid = "uid-1", createdAt = TestData.NOW)
        val store = InMemoryEventsStore(EventsSnapshot(
            profiles = mapOf(adam.id to adam),
            devices = mapOf("tablet" to TrustedDevice("tablet", adam.id, "Tablet", TestData.NOW)),
        ))
        val c = AppContainer.inMemory("tablet", FixedClock(TestData.NOW), store, InMemoryAuth(adam.authUid))
        val rotation = StateRestorationTester(compose)
        rotation.setContent {
            EventsTheme {
                EventsApp(c, showSplash = true,
                    splash = { Text("Loading", Modifier.testTag("test_splash")) },
                    setup = {}, alerts = {},
                    entrant = { Text("Explore", Modifier.testTag("test_entrant")) },
                    owner = {}, admin = {},
                )
            }
        }
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithTag("test_entrant").assertExists()

        // Hold the clock so a splash shown after the rotation can't time out before we look.
        compose.mainClock.autoAdvance = false
        rotation.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("test_splash").assertDoesNotExist()
        compose.onNodeWithTag("test_entrant").assertExists()
    }
}
