/*
 * RatingModerationTest.kt (Suvir)
 *
 * Purpose: Unit tests for admins removing ratings and reviews that break the app policy
 * (US 03.09.01, US 03.10.01): removing a rating takes it out of the campsite's average, removing
 * a review keeps the stars, and the admin's campsite screen does both.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RatingModerationTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = ViewModelStore()
    private val clock = FixedClock(TestData.NOW)
    private lateinit var store: InMemoryEventsStore

    private fun rating(user: String, stars: Int, comment: String = "") = Rating("two-jack", user, "Guest $user", stars, comment, "two-jack/may-2026", TestData.NOW)

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = InMemoryEventsStore(EventsSnapshot(campsites = mapOf("two-jack" to TestData.campsite())))
    }

    @After fun tearDown() {
        models.clear()
        Dispatchers.resetMain()
    }

    private fun <T : ViewModel> track(vm: T): T = vm.also { models.put(System.identityHashCode(vm).toString(), vm) }
    private fun TestScope.observe(flow: Flow<*>) { backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} } }
    private fun site() = store.snapshot.campsites.getValue("two-jack")

    // ---- The average without a rating ----

    @Test fun summary_isTheRoundedAverageOfTheStarsThatStay() {
        assertEquals(4.5 to 2, RatingPolicy.summary(listOf(5, 4)))
        assertEquals(3.3 to 3, RatingPolicy.summary(listOf(5, 4, 1)))
        assertEquals(5.0 to 1, RatingPolicy.summary(listOf(5)))
    }

    @Test fun summary_noRatingsLeftIsZero() = assertEquals(0.0 to 0, RatingPolicy.summary(emptyList()))

    // ---- The store ----

    @Test fun removingARating_takesItOutOfTheAverage() = runTest(dispatcher) {
        store.rate(rating("a", 5)); store.rate(rating("b", 4)); store.rate(rating("c", 1, "Awful."))
        assertEquals(3, site().ratingCount)
        store.removeRating("two-jack", "c")
        assertEquals(listOf("a", "b"), store.snapshot.ratings.map { it.userId }.sorted())
        assertEquals(4.5, site().rating, 0.0)
        assertEquals(2, site().ratingCount)
    }

    @Test fun removingAReview_keepsTheStars() = runTest(dispatcher) {
        store.rate(rating("a", 5)); store.rate(rating("b", 2, "Insulting text here."))
        val before = site()
        store.removeReview("two-jack", "b")
        val left = store.snapshot.ratings.single { it.userId == "b" }
        assertEquals("", left.comment)
        assertEquals(2, left.stars)
        assertEquals(before.rating, site().rating, 0.0)
        assertEquals(before.ratingCount, site().ratingCount)
    }

    @Test fun removingARatingThatIsGone_changesNothing() = runTest(dispatcher) {
        store.rate(rating("a", 4))
        store.removeRating("two-jack", "nobody")
        assertEquals(1, site().ratingCount)
        assertEquals(4.0, site().rating, 0.0)
    }

    // ---- The admin's campsite screen ----

    @Test fun adminScreen_listsRatings_andRemovesThem() = runTest(dispatcher) {
        store.rate(rating("a", 5, "Lovely.")); store.rate(rating("b", 1, "Spam spam spam."))
        val vm = track(AdminCampsiteViewModel(AppContainer.inMemory("admin-phone", clock, store), "two-jack"))
        observe(vm.ratings)
        assertEquals(2, vm.ratings.value.size)

        vm.removeReview("b")
        assertEquals("", vm.ratings.value.single { it.userId == "b" }.comment)
        vm.removeRating("b")
        assertEquals(listOf("a"), vm.ratings.value.map { it.userId })
        assertEquals(1, site().ratingCount)
        assertNull(vm.state.value.message)
    }
}
