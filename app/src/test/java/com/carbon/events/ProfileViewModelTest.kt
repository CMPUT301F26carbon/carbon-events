/*
 * ProfileViewModelTest.kt (Tausif)
 *
 * Purpose: Unit tests for ProfileViewModel.kt: editing the profile and its checks
 * (US 01.07.03), switching the role an admin views the app as (US 03.11.01), and the
 * notifications switch (US 01.04.03).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write
 * tests for the profile view model." Reviewed, edited and run by Team Carbon.
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = ViewModelStore()
    private val store = InMemoryEventsStore()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }

    /** [profile] signed in on a phone with the same ID, so it opens straight in. */
    private suspend fun TestScope.open(profile: UserProfile): ProfileViewModel {
        store.saveProfile(profile); store.trustDevice(TrustedDevice(profile.id, profile.id, "Pixel 8", TestData.NOW))
        val vm = ProfileViewModel(AppContainer.inMemory(profile.id, FixedClock(TestData.NOW), store)).also { models.put(profile.id, it) }
        backgroundScope.launch { vm.state.collect {} }
        assertTrue(vm.state.value is ProfileState.Ready)
        return vm
    }

    private fun saved(id: String) = store.snapshot.profiles.getValue(id)
    private val adam = UserProfile("adam", "Adam Kent", "adam@example.com", createdAt = TestData.NOW)

    @Test fun editing_savesTheNewDetails_andSaysSo() = runTest(dispatcher) {
        val vm = open(adam)
        vm.startEditing(adam)
        vm.onNameChange("Adam K."); vm.onPhoneChange("+1 780 555 0142"); vm.onPhoto("abc")
        var done = false
        vm.save { done = true }
        assertTrue(done)
        assertTrue(vm.saved.value)
        assertEquals("Adam K.", saved("adam").name)
        assertEquals("+1 780 555 0142", saved("adam").phone)
        assertEquals("abc", saved("adam").photoBase64)
        vm.savedShown()
        assertFalse(vm.saved.value)
    }

    @Test fun editing_withProblems_savesNothing() = runTest(dispatcher) {
        val vm = open(adam)
        vm.startEditing(adam)
        vm.onNameChange(" "); vm.onEmailChange("nope"); vm.onPhoneChange("abc")
        vm.save()
        assertEquals(setOf(Field.NAME, Field.EMAIL, Field.PHONE), vm.form.value.errors.keys)
        assertEquals("Adam Kent", saved("adam").name)
        vm.onNameChange("Adam")
        assertFalse("typing clears that error", Field.NAME in vm.form.value.errors)
    }

    @Test fun aPhoneOnlyProfile_canKeepNoEmail() = runTest(dispatcher) {
        val maya = UserProfile("maya", "Maya Chen", "", createdAt = TestData.NOW)
        val vm = open(maya)
        vm.startEditing(maya)
        vm.onNameChange("Maya C.")
        vm.save()
        assertEquals("Maya C.", saved("maya").name)
    }

    @Test fun anAdmin_canViewTheAppAsAnOwner_butAnEntrantCant() = runTest(dispatcher) {
        val admin = UserProfile("alex", "Alex Admin", "alex@example.com", activeRole = Role.ADMIN, isAdmin = true)
        open(admin).switchRole(Role.OWNER)
        assertEquals(Role.OWNER, saved("alex").activeRole)
        open(adam).switchRole(Role.OWNER)
        assertEquals(Role.ENTRANT, saved("adam").activeRole)
    }

    @Test fun notificationsSwitch_savesTheChoice() = runTest(dispatcher) {
        val vm = open(adam)
        vm.savePrefs(adam.prefs.copy(optOut = true))
        assertTrue(saved("adam").prefs.optOut)
    }
}
