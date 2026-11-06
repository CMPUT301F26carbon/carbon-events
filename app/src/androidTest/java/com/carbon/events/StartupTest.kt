/*
 * StartupTest.kt (Aditya)
 *
 * Purpose: launches the real Activity and checks that a signed-out launch reaches Welcome.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Rule
import org.junit.Test

/** Launches the production Activity, including its theme, startup data and splash/setup routing. */
class StartupTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun signedOutLaunch_reachesWelcome() {
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag("welcome")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithTag("welcome").assertIsDisplayed()
    }
}
