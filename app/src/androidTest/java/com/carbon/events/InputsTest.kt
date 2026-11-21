/*
 * InputsTest.kt (Aditya)
 *
 * Purpose: Compose UI tests for Inputs.kt: the stepper keeps to its range and can be typed in, the
 * text field shows its error, and the segmented control picks one option.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write UI tests for the inputs in Inputs.kt."
 * Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InputsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun stepper_staysWithinItsRange() {
        var value by mutableIntStateOf(1)
        compose.setContent { EventsTheme { Stepper(value, { value = it }, 1, 3, "Guests") } }
        compose.onNodeWithContentDescription("Decrease Guests").performClick()
        assertEquals("can't go below the minimum", 1, value)
        repeat(4) { compose.onNodeWithContentDescription("Increase Guests").performClick() }
        assertEquals("can't go above the maximum", 3, value)
    }

    @Test fun stepper_canBeTyped_andSnapsToItsRange() {
        var value by mutableIntStateOf(5)
        compose.setContent { EventsTheme { Stepper(value, { value = it }, 1, 99, "Winners", typeTag = "winners_input") } }
        compose.onNodeWithTag("winners_input").performTextReplacement("42")
        assertEquals(42, value)
        compose.onNodeWithTag("winners_input").performTextReplacement("150")
        assertEquals(99, value)
    }

    @Test fun textField_showsItsLabelAndError() {
        var text by mutableStateOf("")
        compose.setContent { EventsTheme { EventsTextField(text, { text = it }, "Email", Modifier.testTag("email"), error = "Enter a valid email") } }
        compose.onNodeWithText("Email").assertExists()
        compose.onNodeWithText("Enter a valid email").assertExists()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("email"))).performTextInput("adam@example.com")
        assertEquals("adam@example.com", text)
    }

    @Test fun segmentedControl_picksTheOptionTapped() {
        var picked by mutableIntStateOf(0)
        compose.setContent { EventsTheme { SegmentedControl(listOf("User", "Campsite Owner"), picked, { picked = it }, tagPrefix = "role_") } }
        compose.onNodeWithTag("role_1").performClick()
        assertEquals(1, picked)
    }

    @Test fun searchBar_passesOnWhatIsTyped() {
        var query by mutableStateOf("")
        compose.setContent { EventsTheme { SearchBar(query, { query = it }, Modifier.testTag("search")) } }
        compose.onNode(hasSetTextAction()).performTextInput("lake")
        assertEquals("lake", query)
    }
}
