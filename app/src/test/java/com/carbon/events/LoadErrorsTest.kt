/*
 * LoadErrorsTest.kt (Suvir)
 *
 * Purpose: Unit tests for live data that can't be loaded (B15): a read that fails is tried again
 * after 1, 2 and 4 seconds; if one of those works nothing is shown; if they all fail, the app says
 * it couldn't load everything (instead of an empty list passing for "nothing here") and carries on;
 * Try again clears that and starts the reads over.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LoadErrorsTest {
    @get:Rule val compose = createComposeRule()

    @Before fun setUp() = LoadErrors.reset()
    @After fun tearDown() = LoadErrors.reset()

    /** A read that fails [failures] times before it works. */
    private fun read(failures: Int): kotlinx.coroutines.flow.Flow<List<String>> {
        var tries = 0
        return flow { if (tries++ < failures) throw IllegalStateException("offline") else emit(listOf("Two Jack")) }
    }

    @Test fun aReadThatWorksOnRetry_showsItsData_andNoProblem() = runTest {
        assertEquals(listOf(listOf("Two Jack")), read(failures = 2).recoverLoad(emptyList()).toList())
        assertEquals("waited 1 s then 2 s", 3_000L, currentTime)
        assertNull(LoadErrors.problem.value)
    }

    @Test fun aReadThatKeepsFailing_saysSo_andCarriesOn() = runTest {
        assertEquals(listOf(emptyList<String>()), read(failures = 99).recoverLoad(emptyList(), describe = { "Couldn't reach the server." }).toList())
        assertEquals("1 s + 2 s + 4 s of retries", 7_000L, currentTime)
        assertEquals("Couldn't reach the server.", LoadErrors.problem.value)
    }

    @Test fun tryAgain_clearsTheProblem_andStartsReadsOver() {
        LoadErrors.report("Couldn't load everything.")
        val before = LoadErrors.attempt.value
        LoadErrors.tryAgain()
        assertNull(LoadErrors.problem.value)
        assertEquals(before + 1, LoadErrors.attempt.value)
    }

    @Test fun theBanner_showsWhileSomethingCouldntLoad_andTryAgainClearsIt() {
        compose.setContent { EventsTheme { LoadErrorFrame { Text("No bookings yet") } } }
        compose.onNodeWithTag("load_error_banner").assertDoesNotExist()
        LoadErrors.report("Couldn't reach the server. Check your connection and try again.")
        compose.onNodeWithText("Couldn't reach the server. Check your connection and try again.").assertExists()
        compose.onNodeWithTag("load_error_retry").performClick()
        compose.onNodeWithTag("load_error_banner").assertDoesNotExist()
    }
}
