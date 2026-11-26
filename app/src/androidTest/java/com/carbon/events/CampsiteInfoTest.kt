/*
 * CampsiteInfoTest.kt (Ansh)
 *
 * Purpose: Compose UI (intent) tests for CampsiteInfo.kt and the details page around it: before
 * joining, guests see the description and amenities together, the tags and host, when they can
 * stay, the rules and cancellation policy (US 01.01.05), and other guests' reviews with their
 * stars; a guest writes a review with their stars (US 01.13.02); and a guest from an earlier
 * lottery can still rate after a new one opens.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 25, 2026). Prompt: "Write
 * UI (intent) tests for the campsite details sections and reviews." Reviewed, edited and
 * run by Team Carbon.
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

/** Johnston Canyon in the sample data: its lottery was drawn and sample-entrant-0's stay has ended. */
@RunWith(AndroidJUnit4::class)
class CampsiteInfoTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 },
        ).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW))
    private val site = "johnston-canyon"
    private val guest = "sample-entrant-0"

    private fun open(userId: String = guest, name: String = "Maya Chen", campsite: String = site) {
        val c = AppContainer.inMemory(userId, clock, store)
        compose.setContent { EventsTheme { CampsiteDetailsScreen(c, UserProfile(userId, name, "$userId@example.com"), campsite, onBack = {}, onMyLotteries = {}) } }
        waitFor("details_name")
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun tap(tag: String) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) }
    private fun review() = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("rate_review")))
    private fun shows(text: String, substring: Boolean = false) = compose.onNode(hasText(text, substring = substring)).performScrollTo().assertExists()

    // ---- What the campsite offers (US 01.01.05) ----

    @Test fun descriptionAndAmenities_areBothShown() {
        open()
        shows("Steps from the canyon trail and its waterfalls.")
        compose.onNodeWithTag("details_amenities").performScrollTo()
        shows("Firewood, potable water")
    }

    @Test fun tagsAndHost_areShown() {
        open()
        compose.onNodeWithTag("details_tags").performScrollTo()
        shows("FOREST"); shows("FIRE PITS")
        shows("Hosted by Parks Lodge")
    }

    @Test fun whenGuestsCanStay_isShownBeforeJoining() {
        runBlocking { store.updateCampsite(store.snapshot.campsites.getValue(site).copy(minNights = 2, maxNights = 5, checkInTime = "2:00 PM", checkOutTime = "11:00 AM")) }
        open(userId = "new-entrant", name = "Adam Kent")
        compose.onNodeWithTag("details_stay").performScrollTo()
        shows("2 – 5")
        shows("2:00 PM · 11:00 AM")
        shows("$36 per night")
    }

    @Test fun rulesAndCancellationPolicy_areShown() {
        runBlocking { store.updateCampsite(store.snapshot.campsites.getValue(site).copy(rules = "No generators. Dogs on a leash.", cancellation = CancellationPolicy.STRICT)) }
        open()
        compose.onNodeWithTag("details_rules").performScrollTo()
        shows("No generators"); shows("Dogs on a leash")
        compose.onNodeWithTag("details_policy").performScrollTo()
        shows("Cancellation · Strict")
        shows(CancellationPolicy.STRICT.summary, substring = true)
    }

    // ---- Reviews (US 01.13.02) ----

    @Test fun otherGuestsReviews_areShownWithTheirStars() {
        runBlocking { store.rate(Rating(site, "sample-entrant-1", "Liam Ortiz", 4, "Loved the waterfall hike.", "lot-johnston", NOW)) }
        open(userId = "new-entrant", name = "Adam Kent")
        compose.onNodeWithTag("reviews").performScrollTo()
        shows("Loved the waterfall hike.")
        compose.onNodeWithContentDescription("4 of 5 stars").assertExists()
    }

    @Test fun aGuestWhoStayed_writesAReviewWithTheirStars() {
        open()
        tap("details_rate")
        tap("star_5")
        review().performTextInput("Quiet sites and a clean washroom.")
        tap("rate_submit")
        compose.waitUntil(10_000) { store.snapshot.ratings.any { it.userId == guest && it.campsiteId == site } }
        val mine = store.snapshot.ratings.single { it.userId == guest && it.campsiteId == site }
        assertEquals(5, mine.stars)
        assertEquals("Quiet sites and a clean washroom.", mine.comment)
    }

    @Test fun editingARating_keepsTheReviewAlreadyWritten() {
        runBlocking { store.rate(Rating(site, guest, "Maya Chen", 3, "Busy weekend.", "lot-johnston", NOW)) }
        open()
        tap("details_rate")
        review().assert(hasText("Busy weekend.", substring = true))
    }

    @Test fun aGuestFromAnEarlierLottery_canStillRate_afterANewOneOpens() {
        runBlocking {
            store.createLottery(Lottery("", site, "Johnston Canyon", SampleData.OWNER_ID, NOW - DAY, NOW + 5 * DAY, 3, bookingStartDay = dayOf(NOW) + 10, bookingEndDay = dayOf(NOW) + 20))
        }
        open()
        tap("details_rate")
        waitFor("rate_dialog")
        compose.onNodeWithText("You can rate Johnston Canyon after you've stayed there.").assertDoesNotExist()
    }

    private companion object {
        const val NOW = 1_792_162_800_000L
        const val DAY = 86_400_000L
    }
}
