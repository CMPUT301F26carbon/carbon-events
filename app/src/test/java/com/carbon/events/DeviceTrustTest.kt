/*
 * DeviceTrustTest.kt (Rayner)
 *
 * Purpose: Unit tests for trusting a phone at sign-up and sign-in: one phone belongs to one
 * profile at a time, and the newest sign-up or "Trust this device" replaces the old record
 * (US 01.09.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * tests for trusting a phone that another account already trusted." Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** One phone, several people: who the phone is trusted for after each sign-up or sign-in. */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceTrustTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = ViewModelStore()
    private lateinit var store: InMemoryEventsStore
    private lateinit var auth: InMemoryAuth
    private lateinit var container: AppContainer

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = InMemoryEventsStore()
        auth = InMemoryAuth(null)
        container = AppContainer.inMemory("phone", FixedClock(TestData.NOW), store, auth)
    }

    @After fun tearDown() {
        models.clear()
        Dispatchers.resetMain()
    }

    private fun <T : ViewModel> track(vm: T): T = vm.also { models.put(System.identityHashCode(vm).toString(), vm) }

    /** Welcome > Continue with this phone > Your details > Terms. */
    private fun phoneOnlySignUp(name: String): AccountViewModel {
        val vm = track(AccountViewModel(container, null))
        vm.useThisPhone()
        vm.onName(name); vm.onAge("30"); vm.submitDetails()
        vm.onAgreed(true); vm.acceptTerms()
        return vm
    }

    /** Welcome > Continue with email > Your details > Terms > Trust this device? */
    private fun emailSignUp(name: String, email: String, role: Role, trust: Boolean): AccountViewModel {
        val vm = track(AccountViewModel(container, null))
        vm.useEmail()
        vm.onName(name); vm.onEmail(email); vm.onPassword("campfire8"); vm.onAge("40"); vm.onRole(role)
        vm.submitDetails(); vm.onAgreed(true); vm.acceptTerms(); vm.chooseTrust(trust)
        return vm
    }

    private fun profile(name: String) = store.snapshot.profiles.values.first { it.name == name }
    private suspend fun phoneBelongsTo() = store.observeUserIdForDevice("phone").first()

    @Test fun phoneOnlySignUp_trustsThisPhoneWithoutAsking() = runTest(dispatcher) {
        val vm = phoneOnlySignUp("Maya Chen")
        assertNull(vm.state.value.message)
        assertEquals(profile("Maya Chen").id, phoneBelongsTo())
    }

    @Test fun everyPhoneOnlySignUp_getsItsOwnLogin() = runTest(dispatcher) {
        val first = auth.signInWithDevice()
        auth.signOut()
        val second = auth.signInWithDevice()
        assertNotEquals(first.uid, second.uid)
    }

    @Test fun secondPhoneOnlySignUp_onTheSamePhone_takesThePhoneOver() = runTest(dispatcher) {
        phoneOnlySignUp("Maya Chen")
        auth.signOut()
        val vm = phoneOnlySignUp("Liam Okafor")
        assertNull(vm.state.value.message)
        assertNotEquals(profile("Maya Chen").authUid, profile("Liam Okafor").authUid)
        assertEquals(profile("Liam Okafor").id, phoneBelongsTo())
        assertEquals("one phone has one record", 1, store.snapshot.devices.size)
    }

    @Test fun emailSignUp_onAPhoneSomeoneElseTrusted_takesThePhoneOver() = runTest(dispatcher) {
        phoneOnlySignUp("Maya Chen")
        auth.signOut()
        val vm = emailSignUp("Parks Lodge", "hello@parkslodge.ca", Role.OWNER, trust = true)
        assertNull(vm.state.value.message)
        assertEquals(Role.OWNER, profile("Parks Lodge").activeRole)
        assertEquals(profile("Parks Lodge").id, phoneBelongsTo())
        assertTrue(store.observeDevices(profile("Maya Chen").id).first().isEmpty())
    }

    @Test fun emailSignUp_notNow_leavesThePhoneWithWhoeverHadIt() = runTest(dispatcher) {
        phoneOnlySignUp("Maya Chen")
        auth.signOut()
        emailSignUp("Parks Lodge", "hello@parkslodge.ca", Role.OWNER, trust = false)
        assertEquals(profile("Maya Chen").id, phoneBelongsTo())
    }

    @Test fun signInWithRememberMe_onAPhoneSomeoneElseTrusted_takesThePhoneOver() = runTest(dispatcher) {
        emailSignUp("Parks Lodge", "hello@parkslodge.ca", Role.OWNER, trust = false)
        auth.signOut()
        phoneOnlySignUp("Maya Chen")
        auth.signOut()
        val vm = track(AccountViewModel(container, null))
        vm.go(AccountStep.SIGN_IN)
        vm.onEmail("hello@parkslodge.ca"); vm.onPassword("campfire8"); vm.onRemember(true); vm.signIn()
        assertNull(vm.state.value.message)
        assertEquals(profile("Parks Lodge").id, phoneBelongsTo())
        assertEquals(1, store.snapshot.devices.size)
    }

    @Test fun trustingAPhoneAgain_replacesTheRecordInsteadOfAddingOne() = runTest(dispatcher) {
        store.trustDevice(TrustedDevice("phone", "maya-chen-x9k2", "Pixel 8", 1L))
        store.trustDevice(TrustedDevice("phone", "maya-chen-x9k2", "Pixel 8 Pro", 2L))
        val devices = store.observeDevices("maya-chen-x9k2").first()
        assertEquals(1, devices.size)
        assertEquals("Pixel 8 Pro", devices.single().model)
    }
}
