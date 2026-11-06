/*
 * ControllerTest.kt (Rayner)
 *
 * Purpose: tests form validation and the screen controllers (ViewModels) through the real
 * repository interfaces, including sign-up (US 01.09.01).
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Exercises form validation and screen controllers through real repository interfaces. */
@OptIn(ExperimentalCoroutinesApi::class)
class ControllerTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = ViewModelStore()
    private val clock = FixedClock(TestData.NOW)
    private lateinit var store: InMemoryEventsStore

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = InMemoryEventsStore()
    }

    @After fun tearDown() {
        models.clear() // Cancel viewModelScope timers and subscriptions before resetting Main.
        Dispatchers.resetMain()
    }

    private fun <T : ViewModel> track(vm: T): T = vm.also { models.put(System.identityHashCode(vm).toString(), vm) }
    private fun TestScope.observe(flow: Flow<*>) { backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} } }
    private fun container(user: String = "phone", auth: AuthRepository = InMemoryAuth(user)) = AppContainer.inMemory(user, clock, store, auth)

    private fun details(vm: AccountViewModel) {
        vm.go(AccountStep.DETAILS)
        vm.onName("Maya Chen"); vm.onEmail("maya@example.com")
        vm.onPassword("campfire8"); vm.onAge("21"); vm.submitDetails()
    }

    @Test fun signup_createsProfileOnlyAfterTermsAndTrustChoice() = runTest(dispatcher) {
        val auth = InMemoryAuth(null)
        val vm = track(AccountViewModel(container(auth = auth), null))
        details(vm)
        assertEquals(AccountStep.TERMS, vm.state.value.step)
        assertTrue(store.snapshot.profiles.isEmpty())
        vm.acceptTerms()
        assertEquals(AccountStep.TERMS, vm.state.value.step)
        vm.onAgreed(true); vm.acceptTerms(); vm.chooseTrust(true)
        val id = store.observeUserIdForDevice("phone").first()!!
        assertEquals("Maya Chen", store.observeProfile(id).first()!!.name)
        assertEquals(auth.observeAccount().first()!!.uid, store.observeProfile(id).first()!!.authUid)
    }

    @Test fun signup_withoutTrustDoesNotLinkPhone() = runTest(dispatcher) {
        val vm = track(AccountViewModel(container(auth = InMemoryAuth(null)), null))
        details(vm); vm.onAgreed(true); vm.acceptTerms(); vm.chooseTrust(false)
        assertEquals(1, store.snapshot.profiles.size)
        assertTrue(store.snapshot.devices.isEmpty())
    }

    @Test fun signup_invalidDetailsHaveNoAccountOrProfileSideEffects() = runTest(dispatcher) {
        val auth = InMemoryAuth(null)
        val vm = track(AccountViewModel(container(auth = auth), null))
        vm.submitDetails()
        assertTrue(vm.state.value.errors.keys.containsAll(listOf(Field.NAME, Field.EMAIL, Field.PASSWORD, Field.AGE)))
        assertNull(auth.observeAccount().first())
        assertTrue(store.snapshot.profiles.isEmpty())
    }

    @Test fun signup_decliningTermsSignsOutAndReturnsToWelcome() = runTest(dispatcher) {
        val auth = InMemoryAuth(null)
        val vm = track(AccountViewModel(container(auth = auth), null))
        details(vm); vm.declineTerms()
        assertEquals(AccountStep.WELCOME, vm.state.value.step)
        assertNull(auth.observeAccount().first())
    }

    @Test fun signin_wrongPasswordDoesNotTrustDevice() = runTest(dispatcher) {
        val auth = InMemoryAuth(null)
        auth.signUp("maya@example.com", "campfire8"); auth.signOut()
        val vm = track(AccountViewModel(container(auth = auth), null))
        vm.onEmail("maya@example.com"); vm.onPassword("wrong123"); vm.signIn()
        assertNotNull(vm.state.value.message)
        assertTrue(store.snapshot.devices.isEmpty())
    }

    @Test fun campsiteForm_editsExistingListingWithoutDuplicatingIt() = runTest(dispatcher) {
        store = InMemoryEventsStore(EventsSnapshot(campsites = mapOf("two-jack" to TestData.campsite())))
        val vm = track(CampsiteFormViewModel(container("owner-1"), "two-jack"))
        observe(vm.state)
        vm.update(Field.NAME) { it.copy(name = "Two Jack North") }
        var saved: String? = null
        vm.save(UserProfile("owner-1", "Parks Lodge", "owner@example.com")) { saved = it }
        assertEquals("two-jack", saved)
        assertEquals(1, store.snapshot.campsites.size)
        assertEquals("Two Jack North", store.observeCampsite("two-jack").first()!!.name)
    }

    @Test fun profileEdit_savesNameAndPhoneWhileKeepingLoginEmail() = runTest(dispatcher) {
        val profile = UserProfile("phone", "Maya Chen", "maya@example.com")
        store.saveProfile(profile); store.linkDevice("phone", "phone")
        val vm = track(ProfileViewModel(container()))
        observe(vm.state)
        vm.startEditing(profile); vm.onNameChange("Maya C."); vm.onPhoneChange("+1 780 555 0142")
        vm.save()
        val saved = store.observeProfile("phone").first()!!
        assertEquals("Maya C.", saved.name)
        assertEquals("+1 780 555 0142", saved.phone)
        assertEquals(profile.email, saved.email)
    }

    @Test fun winnerMustAcceptRulesBeforeBooking() = runTest(dispatcher) {
        val lot = TestData.lottery().copy(status = LotteryStatus.DRAWN, bookingStartDay = 100, bookingEndDay = 114)
        val entry = WaitlistEntry(lot.id, "phone", "Maya Chen", clock.now(), status = EntryStatus.WON)
        store = InMemoryEventsStore(EventsSnapshot(campsites = mapOf("two-jack" to TestData.campsite()), lotteries = mapOf(lot.id to lot), entries = listOf(entry)))
        val vm = track(BookDatesViewModel(container(), lot.id))
        observe(vm.state)
        vm.pick(101, 104); vm.onGuests(2); vm.confirm()
        assertEquals("Accept the campsite rules to book.", vm.state.value.message)
        assertFalse(store.snapshot.entries.single().isBooked)
        vm.onAgreed(true); vm.confirm()
        assertTrue(store.snapshot.entries.single().isBooked)
        assertEquals(2, store.snapshot.entries.single().guests)
    }

    @Test fun adminSearch_matchesEmailAndKeepsRolePartitions() = runTest(dispatcher) {
        store.saveProfile(UserProfile("maya", "Maya Chen", "maya@example.com"))
        store.saveProfile(UserProfile("owner", "Parks Lodge", "owner@example.com", activeRole = Role.OWNER))
        val vm = track(AdminProfilesViewModel(container("admin")))
        observe(vm.state)
        assertEquals(1, vm.state.value.owners.size)
        vm.onQueryChange("MAYA@")
        assertEquals(listOf("maya"), vm.state.value.entrants.map { it.profile.id })
        assertTrue(vm.state.value.owners.isEmpty())
    }
}
