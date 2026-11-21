/*
 * BarsTest.kt (Aditya)
 *
 * Purpose: Compose UI tests for Bars.kt: the bottom nav bar, the top bar's Back, and the confirm
 * dialog's two buttons.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write UI tests for the bars and dialog in Bars.kt."
 * Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BarsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun navBar_opensTheTabTapped() {
        val items = listOf(NavItem("explore", "Explore", R.drawable.ic_explore), NavItem("profile", "Profile", R.drawable.ic_person))
        var route by mutableStateOf("explore")
        compose.setContent { EventsTheme { EventsNavBar(items, route) { route = it.route } } }
        compose.onNodeWithTag("nav_Profile").performClick()
        assertEquals("profile", route)
    }

    @Test fun topBar_showsItsTitle_andGoesBack() {
        var back = 0
        compose.setContent { EventsTheme { BackTopBar("Trusted devices", { back++ }) } }
        compose.onNodeWithText("Trusted devices").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, back)
    }

    @Test fun confirmDialog_confirmsOrCancels() {
        val said = mutableListOf<String>()
        compose.setContent { EventsTheme { ConfirmDialog(R.drawable.ic_logout, "Sign out?", "You'll need your password.", "Sign out", { said += "yes" }, { said += "no" }) } }
        compose.onNodeWithText("Sign out?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Sign out").performClick()
        assertEquals(listOf("no", "yes"), said)
    }
}
