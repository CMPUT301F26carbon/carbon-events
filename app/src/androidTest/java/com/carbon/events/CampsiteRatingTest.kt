/*
 * CampsiteRatingTest.kt (Ansh)
 *
 * Purpose: Compose UI (intent) tests for rating a campsite from its details screen: a guest who
 * booked a stay picks one to five stars, the rating is saved and the campsite's average moves;
 * someone who never stayed is told they can't rate yet (US 01.13.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for giving a campsite a rating." Reviewed, edited and run by Team Carbon.
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

/** Johnston Canyon in the sample data: its lottery was drawn and two winners booked stays. */
@RunWith(AndroidJUnit4::class)
class CampsiteRatingTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 },
        ).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW))

    /** A winner whose stay at Johnston Canyon has ended. */
    private val guest = "sample-entrant-0"
    private val site = "johnston-canyon"

    private fun launch(userId: String, name: String) {
        runBlocking {
            store.saveProfile(UserProfile(userId, name, "guest@example.com"))
            store.linkDevice(userId, userId)
        }
        val c = AppContainer.inMemory(userId, clock, store)
        compose.setContent {
            EventsTheme {
                EventsApp(c, showSplash = false, splash = {}, setup = {}, alerts = {},
                    entrant = { LocalAppSession.current.let { EntrantShell(it.container, it.requireProfile(), it.profileVm, null, {}) } },
                    owner = {}, admin = {},
                )
            }
        }
        waitFor("explore")
        compose.onNodeWithTag("explore_search").performClick()
        compose.onNodeWithTag("search_field").performTextInput("johnston")
        compose.onNodeWithTag("campsite_card_$site").performClick()
        waitFor("details_rate")
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun tap(tag: String) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) }
    private fun myRating(userId: String = guest) = store.snapshot.ratings.firstOrNull { it.campsiteId == site && it.userId == userId }

    @Test fun guestWhoStayed_opensTheRatingSheet() {
        launch(guest, "Maya Chen")
        tap("details_rate")
        compose.onNodeWithTag("rate_dialog").assertExists()
        compose.onNodeWithText("Rate Johnston Canyon").assertExists()
        compose.onNodeWithText("Tap a star to rate").assertExists()
        compose.onNodeWithTag("rate_submit").assertIsNotEnabled()
    }

    @Test fun pickingStars_andSubmitting_savesTheRating() {
        val before = store.snapshot.campsites.getValue(site)
        launch(guest, "Maya Chen")
        tap("details_rate")
        tap("star_4")
        compose.onNodeWithText("4 of 5 stars · tap a star to change").assertExists()
        tap("rate_submit")
        compose.waitUntil(10_000) { myRating() != null }
        assertEquals(4, myRating()!!.stars)
        assertEquals("Maya Chen", myRating()!!.userName)
        assertEquals(before.ratingCount + 1, store.snapshot.campsites.getValue(site).ratingCount)
        compose.onNodeWithTag("rate_dialog").assertDoesNotExist()
    }

    @Test fun ratingAgain_changesTheRatingInsteadOfAddingOne() {
        val before = store.snapshot.campsites.getValue(site).ratingCount
        launch(guest, "Maya Chen")
        tap("details_rate"); tap("star_2"); tap("rate_submit")
        compose.waitUntil(10_000) { myRating()?.stars == 2 }
        compose.onNodeWithText("Edit rating").assertExists()
        tap("details_rate"); tap("star_5"); tap("rate_submit")
        compose.waitUntil(10_000) { myRating()?.stars == 5 }
        assertEquals(1, store.snapshot.ratings.count { it.campsiteId == site && it.userId == guest })
        assertEquals(before + 1, store.snapshot.campsites.getValue(site).ratingCount)
    }

    @Test fun notNow_savesNothing() {
        launch(guest, "Maya Chen")
        tap("details_rate")
        tap("star_3")
        compose.onNodeWithText("Not now").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("rate_dialog").assertDoesNotExist()
        assertNull(myRating())
    }

    @Test fun someoneWhoNeverStayed_isToldTheyCannotRateYet() {
        launch("new-entrant", "Adam Kent")
        tap("details_rate")
        compose.onNodeWithText("You can rate Johnston Canyon after you've stayed there.").assertExists()
        compose.onNodeWithTag("rate_dialog").assertDoesNotExist()
        assertNull(myRating("new-entrant"))
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
