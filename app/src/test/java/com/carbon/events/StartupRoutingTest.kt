/*
 * StartupRoutingTest.kt (Tausif)
 */
package com.carbon.events

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
}
