/*
 * DeleteProfileLoginTest.kt (Rayner)
 *
 * Purpose: Unit tests for deleting your own profile (US 01.08.01): the sign-in login is deleted
 * with it, so nothing is left behind in Firebase Authentication. When Firebase wants a fresh
 * sign-in first, the password is asked before anything is deleted.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write
 * tests for deleting a profile together with its Firebase sign-in." Reviewed, edited and run by
 * Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Adam Kent deletes his own profile on his phone. */
@OptIn(ExperimentalCoroutinesApi::class)
class DeleteProfileLoginTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = ViewModelStore()
    private val store = InMemoryEventsStore()
    private val auth = InMemoryAuth(null)
    private val container = AppContainer.inMemory("pixel", FixedClock(TestData.NOW), store, auth)
    private val email = "adam@example.com"
    private val password = "campfire8"

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }

    /** Signs up by email and opens Profile. */
    private suspend fun TestScope.signedUp(): ProfileViewModel {
        val account = auth.signUp(email, password)
        store.createProfile(UserProfile("", "Adam Kent", email, authUid = account.uid, createdAt = TestData.NOW), TrustedDevice("pixel", "", "Pixel 8", TestData.NOW))
        val vm = ProfileViewModel(container).also { models.put("profile", it) }
        backgroundScope.launch { vm.state.collect {} }
        assertTrue(vm.state.value is ProfileState.Ready)
        return vm
    }

    private fun profileCount() = store.snapshot.profiles.size
    private suspend fun loginStillWorks() = runCatching { auth.signIn(email, password) }.isSuccess

    @Test fun deleting_removesTheSignInLoginToo() = runTest(dispatcher) {
        val vm = signedUp()
        vm.deleteProfile()
        assertEquals(0, profileCount())
        assertNull("signed out", auth.observeAccount().first())
        assertFalse("nothing left in Authentication", loginStillWorks())
    }

    @Test fun aFreshSignIn_needsNoPassword() = runTest(dispatcher) {
        val vm = signedUp()
        assertFalse(vm.needsPasswordToDelete)
    }

    @Test fun anOldSignIn_asksForThePassword_beforeDeletingAnything() = runTest(dispatcher) {
        val vm = signedUp()
        auth.requireFreshSignIn()
        assertTrue(vm.needsPasswordToDelete)
        vm.deleteProfile()
        assertEquals("nothing is deleted yet", 1, profileCount())
        assertEquals("Enter your password to delete your profile.", vm.form.value.message)
        assertNotNull("still signed in", auth.observeAccount().first())
    }

    @Test fun aWrongPassword_deletesNothing() = runTest(dispatcher) {
        val vm = signedUp()
        auth.requireFreshSignIn()
        vm.deleteProfile("not-my-password1")
        assertEquals(1, profileCount())
        assertEquals(AuthError.WRONG_PASSWORD.message, vm.form.value.message)
        assertFalse(vm.form.value.saving)
        assertTrue(loginStillWorks())
    }

    @Test fun theRightPassword_deletesTheProfileAndTheLogin() = runTest(dispatcher) {
        val vm = signedUp()
        auth.requireFreshSignIn()
        vm.deleteProfile(password)
        assertEquals(0, profileCount())
        assertNull(auth.observeAccount().first())
        assertFalse("nothing left in Authentication", loginStillWorks())
    }

    @Test fun withoutTheFix_anOldSignInWouldLeaveTheLoginBehind() = runTest {
        // What Firebase does on its own: an old sign-in can't delete the login.
        val account = auth.signUp(email, password)
        auth.requireFreshSignIn()
        assertFalse(auth.deleteAccount())
        assertNotNull(account)
        assertTrue("so the password is asked first", loginStillWorks())
    }

    @Test fun aPhoneOnlyProfile_isDeletedWithoutAPassword() = runTest(dispatcher) {
        val account = auth.signInWithDevice()
        store.createProfile(UserProfile("", "Maya Chen", "", authUid = account.uid, createdAt = TestData.NOW), TrustedDevice("pixel", "", "Pixel 8", TestData.NOW))
        val vm = ProfileViewModel(container).also { models.put("profile", it) }
        backgroundScope.launch { vm.state.collect {} }
        auth.requireFreshSignIn()
        assertFalse("a phone-only login has no password to ask", vm.needsPasswordToDelete)
        vm.deleteProfile()
        assertEquals(0, profileCount())
        assertNull(auth.observeAccount().first())
    }
}
