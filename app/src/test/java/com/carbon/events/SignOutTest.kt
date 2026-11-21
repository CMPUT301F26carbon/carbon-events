/*
 * SignOutTest.kt (Rayner)
 *
 * Purpose: Unit tests for Profile > Sign out (Figma E-SIGNOUT, US 01.09.01): signing out stops
 * trusting this phone and signs it out, the person's other phones stay trusted, the next launch
 * opens on Welcome, and with no connection signing out doesn't hang.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write
 * tests that signing out removes this phone from the trusted devices." Reviewed, edited and run by
 * Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Adam Kent, trusted on his Pixel and his tablet, signs out on the Pixel. */
@OptIn(ExperimentalCoroutinesApi::class)
class SignOutTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = ViewModelStore()
    private val adam = UserProfile("adam-kent", "Adam Kent", "adam@example.com", authUid = "uid-1", createdAt = TestData.NOW)
    private val store = InMemoryEventsStore(EventsSnapshot(
        profiles = mapOf(adam.id to adam),
        devices = listOf("pixel", "tablet").associateWith { TrustedDevice(it, adam.id, "Pixel 8", TestData.NOW) },
    ))
    private val auth = InMemoryAuth(adam.authUid)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }

    private fun TestScope.open(profiles: ProfileRepository = store): ProfileViewModel {
        val c = AppContainer("pixel", "Pixel 8", profiles, store, store, store, FixedClock(TestData.NOW), usingServer = false, auth = auth, social = store)
        val vm = ProfileViewModel(c).also { models.put(System.identityHashCode(it).toString(), it) }
        backgroundScope.launch { vm.state.collect {} }
        return vm
    }

    @Test fun signingOut_removesThisPhoneFromTrustedDevices_andSignsOut() = runTest(dispatcher) {
        val vm = open()
        assertTrue(vm.state.value is ProfileState.Ready)
        vm.signOut()
        assertNull("this phone is no longer trusted", store.snapshot.devices["pixel"])
        assertNull(auth.observeAccount().first())
        assertEquals(ProfileState.Missing(null), vm.state.value)
    }

    @Test fun signingOut_leavesYourOtherPhonesTrusted() = runTest(dispatcher) {
        open().signOut()
        assertEquals(listOf("tablet"), store.observeDevices(adam.id).first().map { it.deviceId })
    }

    @Test fun afterSigningOut_theNextLaunchOpensOnWelcome() = runTest(dispatcher) {
        open().signOut()
        val next = open()
        assertEquals(ProfileState.Missing(null), next.state.value)
    }

    @Test fun withNoConnection_signingOutDoesntHang() = runTest(dispatcher) {
        val offline = object : ProfileRepository by store {
            override suspend fun removeDevice(deviceId: String) = awaitCancellation()
        }
        val vm = open(offline)
        vm.signOut()
        advanceTimeBy(30_000)
        assertNull("signed out even without the server", auth.observeAccount().first())
    }
}
