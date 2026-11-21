/*
 * AvailabilityTest.kt (Rayner)
 *
 * Purpose: Compose UI (intent) tests for Availability.kt: price and stay lengths (US 02.01.04).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write UI (intent) tests for Availability & Price."
 * Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import android.Manifest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** The sample owner, Parks Lodge, and their campsites. */
@RunWith(AndroidJUnit4::class)
class AvailabilityTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 },
        ).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW))
    private val owner = SampleData.OWNER_ID

    /** Signs the sample owner (Parks Lodge) in on this phone and opens My Campsites. */
    private fun launchOwner(seed: suspend () -> Unit = {}) {
        runBlocking {
            store.saveProfile(SampleData.owner(NOW).copy(activeRole = Role.OWNER))
            store.linkDevice(owner, owner)
            seed()
        }
        val c = AppContainer.inMemory(owner, clock, store)
        compose.setContent {
            EventsTheme {
                EventsApp(c, showSplash = false, splash = {}, setup = {}, alerts = {},
                    entrant = {},
                    owner = { LocalAppSession.current.let { OwnerShell(it.container, it.requireProfile(), it.profileVm) } },
                    admin = {},
                )
            }
        }
        waitFor("owner_campsites")
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun tap(tag: String) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) }

    private fun field(tag: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)) or (hasSetTextAction() and hasTestTag(tag)))
    private fun site(id: String) = store.snapshot.campsites.getValue(id)

    /** Taps a stepper's − or + button. */
    private fun step(label: String) {
        compose.onNodeWithContentDescription(label).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
    }

    /** Opens one of My Campsites, scrolling the list to it first. */
    private fun openCampsite(name: String) {
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("owner_campsites").performScrollToNode(hasText(name)) }.isSuccess }
        compose.onNodeWithText(name).performClick()
        waitFor("manage_name")
    }

    // ---- Availability & price ----

    @Test fun availability_savesThePrice_andTheShortestStay() {
        launchOwner()
        openCampsite("Two Jack Lakeside")
        compose.onNodeWithTag("manage_availability").performScrollTo()
        tap("manage_availability")
        waitFor("avail_price")
        field("avail_price").performTextClearance()
        field("avail_price").performTextInput("80")
        step("Increase Shortest stay")
        tap("avail_save")
        waitFor("avail_saved")
        assertEquals(80, site("two-jack").pricePerNight)
        assertEquals(2, site("two-jack").minNights)
    }

    @Test fun availability_withNoPrice_saysSo_andSavesNothing() {
        launchOwner()
        openCampsite("Two Jack Lakeside")
        compose.onNodeWithTag("manage_availability").performScrollTo()
        tap("manage_availability")
        waitFor("avail_price")
        field("avail_price").performTextClearance()
        tap("avail_save")
        compose.onNodeWithText("Enter a price of 0 or more.").assertExists()
        compose.onNodeWithTag("avail_saved").assertDoesNotExist()
        assertEquals(65, site("two-jack").pricePerNight)
    }

    @Test fun availability_cantSetTheShortestStayAboveTheLongest() {
        launchOwner()
        openCampsite("Two Jack Lakeside")
        compose.onNodeWithTag("manage_availability").performScrollTo()
        tap("manage_availability")
        waitFor("avail_min")
        repeat(6) { step("Decrease Longest stay") }
        step("Increase Shortest stay")
        tap("avail_save")
        compose.onNodeWithText("The shortest stay can't be longer than the longest.").assertExists()
        assertEquals(1, site("two-jack").minNights)
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
