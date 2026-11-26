/*
 * EmailChangeTest.kt (Tausif)
 *
 * Purpose: Unit tests for changing the sign-in email (ProfileViewModel in ProfileViewModel.kt,
 * US 01.07.03): the profile keeps the email the person still signs in with until the link Firebase
 * emails is opened, then follows it; an email that can't be used saves nothing and says why; a
 * phone-only profile saves its contact email straight away.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 25, 2026). Prompt: "Write
 * tests for changing the sign-in email." Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Adam Kent, who signs in with adam@example.com, moving to a new address. */
@OptIn(ExperimentalCoroutinesApi::class)
class EmailChangeTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = ViewModelStore()
    private val store = InMemoryEventsStore()
    private val auth = InMemoryAuth()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }

    /** Adam signed up with [email] and open on his trusted phone. */
    private suspend fun TestScope.open(email: String = "adam@example.com", login: AuthRepository = auth): ProfileViewModel {
        val account = auth.signUp(email, "campfire8")
        val adam = UserProfile("adam", "Adam Kent", email, authUid = account.uid, createdAt = TestData.NOW)
        store.saveProfile(adam); store.trustDevice(TrustedDevice("adam-phone", "adam", "Pixel 8", TestData.NOW))
        val vm = ProfileViewModel(AppContainer.inMemory("adam-phone", FixedClock(TestData.NOW), store, login)).also { models.put("adam", it) }
        backgroundScope.launch { vm.state.collect {} }
        assertTrue(vm.state.value is ProfileState.Ready)
        vm.startEditing(adam)
        return vm
    }

    private fun saved() = store.snapshot.profiles.getValue("adam")

    @Test fun anewEmail_waitsForItsLink_andThePersonKeepsSigningInWithTheOldOne() = runTest(dispatcher) {
        val vm = open()
        vm.onNameChange("Adam K."); vm.onEmailChange("adam.kent@example.com")
        vm.save()
        assertTrue(vm.saved.value)
        assertEquals("Adam K.", saved().name)
        assertEquals("adam@example.com", saved().email)
        assertEquals("adam.kent@example.com", vm.pendingEmail.value)
    }

    @Test fun openingTheLink_movesTheProfileToTheNewEmail() = runTest(dispatcher) {
        val vm = open()
        vm.onEmailChange("adam.kent@example.com")
        vm.save()
        auth.openEmailChangeLink()
        assertEquals("adam.kent@example.com", saved().email)
        assertNull(vm.pendingEmail.value)
    }

    @Test fun anEmailSomeoneElseUses_savesNothing_andSaysWhy() = runTest(dispatcher) {
        auth.signUp("sam@example.com", "campfire8")
        val vm = open()
        vm.onNameChange("Adam K."); vm.onEmailChange("sam@example.com")
        vm.save()
        assertEquals(AuthError.EMAIL_TAKEN.message, vm.form.value.message)
        assertEquals("Adam Kent", saved().name)
        assertEquals("adam@example.com", saved().email)
        assertNull(vm.pendingEmail.value)
    }

    @Test fun whenFirebaseWantsAFreshSignIn_itSaysToSignInAgain() = runTest(dispatcher) {
        val stale = object : AuthRepository by auth {
            override suspend fun changeEmail(newEmail: String): Boolean = throw AuthException(AuthError.SIGN_IN_AGAIN)
        }
        val vm = open(login = stale)
        vm.onEmailChange("adam.kent@example.com")
        vm.save()
        assertEquals(AuthError.SIGN_IN_AGAIN.message, vm.form.value.message)
        assertEquals("adam@example.com", saved().email)
    }

    @Test fun aProfileOutOfStepWithItsLogin_followsTheSignInEmail() = runTest(dispatcher) {
        val account = auth.signUp("adam@example.com", "campfire8")
        store.saveProfile(UserProfile("adam", "Adam Kent", "old@example.com", authUid = account.uid, createdAt = TestData.NOW))
        store.trustDevice(TrustedDevice("adam-phone", "adam", "Pixel 8", TestData.NOW))
        val vm = ProfileViewModel(AppContainer.inMemory("adam-phone", FixedClock(TestData.NOW), store, auth)).also { models.put("adam", it) }
        backgroundScope.launch { vm.state.collect {} }
        assertEquals("adam@example.com", saved().email)
    }

    @Test fun aPhoneOnlyProfile_savesItsContactEmailStraightAway() = runTest(dispatcher) {
        val maya = UserProfile("maya", "Maya Chen", "", createdAt = TestData.NOW)
        store.saveProfile(maya); store.trustDevice(TrustedDevice("maya", "maya", "Pixel 8", TestData.NOW))
        val vm = ProfileViewModel(AppContainer.inMemory("maya", FixedClock(TestData.NOW), store)).also { models.put("maya", it) }
        backgroundScope.launch { vm.state.collect {} }
        vm.startEditing(maya)
        vm.onEmailChange("maya@example.com")
        vm.save()
        assertEquals("maya@example.com", store.snapshot.profiles.getValue("maya").email)
        assertNull(vm.pendingEmail.value)
    }
}
