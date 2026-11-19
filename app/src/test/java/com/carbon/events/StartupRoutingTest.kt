/*
 * StartupRoutingTest.kt (Tausif)
 *
 * Purpose: checks signed-out startup routing to Welcome through the production app wrapper,
 * and that a build without Firebase starts signed out.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.material3.Text
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
}
