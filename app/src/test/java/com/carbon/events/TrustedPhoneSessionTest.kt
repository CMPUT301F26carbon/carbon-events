/*
 * TrustedPhoneSessionTest.kt (Rayner)
 *
 * Purpose: Unit tests for staying signed in on a trusted phone (US 01.09.01): a phone whose trust
 * was removed opens on Welcome even if it still has an old copy saying it is trusted; "Log out of
 * all devices" first stops trusting every phone, then signs out every phone, open ones included;
 * and finishing sign-up goes straight into the app.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write
 * tests for removing a trusted phone, logging out of all devices and finishing sign-up." Reviewed,
 * edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Adam Kent, signed in on one or more phones. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrustedPhoneSessionTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = ViewModelStore()
    private val clock = FixedClock(TestData.NOW)
    private val adam = UserProfile("adam-kent", "Adam Kent", "adam@example.com", authUid = "uid-1", createdAt = TestData.NOW)
    private lateinit var store: InMemoryEventsStore

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }

    private fun <T : ViewModel> track(vm: T): T = vm.also { models.put(System.identityHashCode(vm).toString(), vm) }

    private fun device(id: String) = TrustedDevice(id, adam.id, "Pixel 8", TestData.NOW)

    private fun storeWith(vararg trusted: String) =
        InMemoryEventsStore(EventsSnapshot(profiles = mapOf(adam.id to adam), devices = trusted.associateWith { device(it) })).also { store = it }

    /** One phone: [deviceId], signed in as Adam when [signedIn]. */
    private inner class Phone(val deviceId: String, signedIn: Boolean = true, profiles: ProfileRepository = store) {
        val auth = InMemoryAuth(if (signedIn) adam.authUid else null)
        val container = AppContainer(deviceId, "Pixel 8", profiles, store, store, store, clock, usingServer = false, auth = auth, social = store)
        lateinit var vm: ProfileViewModel

        /** Opens the app on this phone (a fresh launch). */
        fun open(scope: TestScope): Phone {
            vm = track(ProfileViewModel(container))
            scope.backgroundScope.launch { vm.state.collect {} }
            return this
        }

        val signedIn: Boolean get() = vm.state.value is ProfileState.Ready
        suspend fun account() = auth.observeAccount().first()
    }

    // ---- Removing a trusted phone ----

    @Test fun phoneWhoseTrustWasRemoved_opensSignedOut_onWelcome() = runTest(dispatcher) {
        storeWith()
        val phone = Phone("pixel").open(this)
        assertNull(phone.account())
        assertEquals(ProfileState.Missing(null), phone.vm.state.value)
    }

    @Test fun phoneWithAnOldCopySayingItIsTrusted_stillOpensOnWelcome() = runTest(dispatcher) {
        storeWith()
        // The phone's saved copy still says it is Adam's; the server says it is no one's.
        val stale = object : ProfileRepository by store {
            override fun observeUserIdForDevice(deviceId: String): Flow<String?> = flowOf(adam.id)
        }
        val phone = Phone("pixel", profiles = stale).open(this)
        assertNull("a removed phone must not stay signed in", phone.account())
        assertEquals(ProfileState.Missing(null), phone.vm.state.value)
    }

    @Test fun trustedPhone_opensSignedIn() = runTest(dispatcher) {
        storeWith("pixel")
        val phone = Phone("pixel").open(this)
        assertTrue(phone.signedIn)
    }

    @Test fun removingThisPhone_keepsYouInNow_andTheNextLaunchOpensOnWelcome() = runTest(dispatcher) {
        storeWith("pixel")
        val phone = Phone("pixel").open(this)
        phone.vm.removeDevice(device("pixel"))
        assertTrue("still signed in until the app is opened again", phone.signedIn)
        phone.open(this)
        assertNull(phone.account())
        assertEquals(ProfileState.Missing(null), phone.vm.state.value)
    }

    @Test fun removingAnotherPhone_signsThatPhoneOutWhileItIsOpen() = runTest(dispatcher) {
        storeWith("pixel", "tablet")
        val pixel = Phone("pixel").open(this)
        val tablet = Phone("tablet").open(this)
        pixel.vm.removeDevice(device("tablet"))
        assertTrue(pixel.signedIn)
        assertNull(tablet.account())
    }

    // ---- Log out of all devices ----

    @Test fun logOutOfAllDevices_removesEveryTrustedPhone_thenSignsEveryPhoneOut() = runTest(dispatcher) {
        storeWith("pixel", "tablet")
        val pixel = Phone("pixel").open(this)
        val tablet = Phone("tablet").open(this)
        // A laptop emulator signed in without "Remember me": open, but not trusted.
        val laptop = Phone("laptop", signedIn = false).open(this)
        laptop.auth.signUp(adam.email, "campfire8")
        assertTrue(laptop.signedIn)
        var trustedWhenSignedOut: List<TrustedDevice>? = null
        backgroundScope.launch {
            pixel.auth.observeAccount().first { it == null }
            trustedWhenSignedOut = store.observeDevices(adam.id).first()
        }

        pixel.vm.logOutEverywhere()

        assertEquals("trusted phones are removed before signing out", emptyList<TrustedDevice>(), trustedWhenSignedOut)
        assertTrue(store.observeDevices(adam.id).first().isEmpty())
        assertNull("this phone", pixel.account())
        assertNull("a trusted phone that is open", tablet.account())
        assertNull("an open phone that was never trusted", laptop.account())
    }

    @Test fun logOutOfAllDevices_reachesAPhoneThatRemovedItselfEarlier() = runTest(dispatcher) {
        storeWith("pixel", "tablet")
        val pixel = Phone("pixel").open(this)
        val tablet = Phone("tablet").open(this)
        pixel.vm.removeDevice(device("pixel"))
        assertTrue(pixel.signedIn)
        tablet.vm.logOutEverywhere()
        assertNull(tablet.account())
        assertNull(pixel.account())
    }

    @Test fun afterLoggingOutEverywhere_signingBackIn_staysSignedIn() = runTest(dispatcher) {
        storeWith("pixel")
        val pixel = Phone("pixel", signedIn = false).open(this)
        pixel.auth.signUp(adam.email, "campfire8")
        pixel.vm.logOutEverywhere()
        assertNull(pixel.account())
        pixel.auth.signIn(adam.email, "campfire8")
        assertTrue("signing in again isn't undone by the earlier log out", pixel.signedIn)
    }

    @Test fun logOutOfAllDevices_thatCantRemoveThePhones_signsNobodyOut() = runTest(dispatcher) {
        storeWith("pixel", "tablet")
        val failing = object : ProfileRepository by store {
            override suspend fun removeAllDevices(userId: String) = throw AuthException(AuthError.NETWORK)
        }
        val pixel = Phone("pixel", profiles = failing).open(this)
        val tablet = Phone("tablet").open(this)
        pixel.vm.logOutEverywhere()
        assertNotNull(pixel.account())
        assertNotNull(tablet.account())
        assertEquals(2, store.observeDevices(adam.id).first().size)
        assertEquals(AuthError.NETWORK.message, pixel.vm.logOutError.value)
    }

    @Test fun logOutOfAllDevices_withNoConnection_saysSo_insteadOfHanging() = runTest(dispatcher) {
        storeWith("pixel")
        val offline = object : ProfileRepository by store {
            override suspend fun removeAllDevices(userId: String) = awaitCancellation()
        }
        val pixel = Phone("pixel", profiles = offline).open(this)
        pixel.vm.logOutEverywhere()
        advanceTimeBy(30_000)
        assertEquals(AuthError.NETWORK.message, pixel.vm.logOutError.value)
        assertNotNull(pixel.account())
    }

    // ---- Finishing sign-up ----

    private fun TestScope.signUp(role: Role, trust: Boolean): Phone {
        store = InMemoryEventsStore()
        val phone = Phone("pixel", signedIn = false).open(this)
        val vm = track(AccountViewModel(phone.container, null))
        vm.useEmail()
        vm.onName("Parks Lodge"); vm.onEmail("hello@parkslodge.ca"); vm.onPassword("campfire8"); vm.onAge("40"); vm.onRole(role)
        vm.submitDetails(); vm.onAgreed(true); vm.acceptTerms(); vm.chooseTrust(trust)
        assertNull(vm.state.value.message)
        return phone
    }

    @Test fun finishingSignUp_asAnOwner_goesStraightToTheOwnerApp() = runTest(dispatcher) {
        val phone = signUp(Role.OWNER, trust = true)
        val ready = phone.vm.state.value as? ProfileState.Ready
        assertNotNull("signed in after sign-up, not sent back to Welcome", ready)
        assertEquals(Role.OWNER, ready!!.profile.activeRole)
        assertNotNull(phone.account())
    }

    @Test fun finishingSignUp_withNotNow_stillGoesStraightIn() = runTest(dispatcher) {
        val phone = signUp(Role.ENTRANT, trust = false)
        val ready = phone.vm.state.value as? ProfileState.Ready
        assertNotNull(ready)
        assertEquals(Role.ENTRANT, ready!!.profile.activeRole)
    }

    @Test fun finishingPhoneOnlySignUp_goesStraightIn() = runTest(dispatcher) {
        store = InMemoryEventsStore()
        val phone = Phone("pixel", signedIn = false).open(this)
        val vm = track(AccountViewModel(phone.container, null))
        vm.useThisPhone(); vm.onName("Maya Chen"); vm.onAge("30"); vm.submitDetails()
        vm.onAgreed(true); vm.acceptTerms()
        assertTrue(phone.signedIn)
    }
}
