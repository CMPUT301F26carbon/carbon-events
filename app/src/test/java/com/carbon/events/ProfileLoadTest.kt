/*
 * ProfileLoadTest.kt (Tausif)
 *
 * Purpose: Unit tests for a profile that can't be loaded (ProfileViewModel in
 * ProfileViewModel.kt): the app says so and offers to try again instead of showing sign-up, which
 * would make a second profile; trying again opens the profile, and the person stays signed in.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 25, 2026). Prompt: "Write
 * tests for a profile that fails to load." Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Adam Kent opening the app on his trusted phone while his profile can't be read. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileLoadTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = ViewModelStore()
    private val store = InMemoryEventsStore()
    private val auth = InMemoryAuth("adam")
    private var offline = true

    /** The store, except that reading the signed-in profile fails while [offline]. */
    private val profiles = object : ProfileRepository by store {
        override fun observeProfileForAccount(authUid: String): Flow<UserProfile?> =
            if (offline) flow { throw ProfileLoadException() } else store.observeProfileForAccount(authUid)
    }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }

    private suspend fun TestScope.open(): ProfileViewModel {
        store.saveProfile(UserProfile("adam", "Adam Kent", "adam@example.com", createdAt = TestData.NOW))
        store.trustDevice(TrustedDevice("adam", "adam", "Pixel 8", TestData.NOW))
        val c = AppContainer("adam", "Pixel 8", profiles, store, store, store, FixedClock(TestData.NOW), usingServer = false, auth = auth, social = store)
        val vm = ProfileViewModel(c).also { models.put("adam", it) }
        backgroundScope.launch { vm.state.collect {} }
        return vm
    }

    @Test fun aProfileThatCantBeRead_saysSo_insteadOfShowingSignUp() = runTest(dispatcher) {
        val vm = open()
        assertTrue(vm.state.value is ProfileState.Failed)
    }

    @Test fun aFailedLoad_keepsThePersonSignedIn() = runTest(dispatcher) {
        open()
        assertNotNull(auth.observeAccount().first())
    }

    @Test fun tryingAgain_opensTheProfile() = runTest(dispatcher) {
        val vm = open()
        offline = false
        vm.retryLoad()
        assertEquals("Adam Kent", (vm.state.value as ProfileState.Ready).profile.name)
    }

    @Test fun signingOutFromTheError_goesToWelcome() = runTest(dispatcher) {
        val vm = open()
        vm.signOut()
        assertEquals(ProfileState.Missing(null), vm.state.value)
    }
}
