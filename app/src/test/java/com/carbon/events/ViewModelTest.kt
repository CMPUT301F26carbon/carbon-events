/*
 * ViewModelTest.kt
 *
 * Purpose: unit tests for the control classes (view models): first-launch profile and
 * edits (US 01.09.01, US 01.07.03), search (US 01.01.01), details join / leave
 * (US 01.01.06, US 01.02.01, US 01.02.02), campsite registration (US 02.01.01,
 * US 02.01.02), new lottery (US 02.04.01, US 02.04.03, US 02.04.05) and admin removal
 * (US 03.01.01). They run against the in-memory store with a fixed clock.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import com.carbon.events.TestData.DAY
import com.carbon.events.TestData.NOW
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {
    private val clock = FixedClock(NOW)
    private lateinit var store: InMemoryEventsStore
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        store = InMemoryEventsStore(SampleData.snapshot(NOW))
        container = AppContainer.inMemory("device-adam", clock, store)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Keeps a StateFlow subscribed for the test's duration. */
    private fun TestScope.keep(flow: kotlinx.coroutines.flow.Flow<*>) = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }

    @Test
    fun profile_firstLaunchCreatesDeviceProfile() = runTest {
        val vm = ProfileViewModel(container)
        keep(vm.state)
        assertEquals(ProfileState.Missing, vm.state.value)
        vm.onNameChange("Adam Kent"); vm.onEmailChange("adam@example.com")
        var saved = false
        vm.save { saved = true }
        assertTrue(saved)
        val profile = store.observeProfile("device-adam").first()
        assertEquals("Adam Kent", profile?.name)
        assertEquals(Role.ENTRANT, profile?.activeRole)
    }

    @Test
    fun profile_invalidFormShowsErrorsAndDoesNotSave() = runTest {
        val vm = ProfileViewModel(container)
        vm.onNameChange(""); vm.onEmailChange("bad")
        vm.save()
        assertTrue(Field.NAME in vm.form.value.errors && Field.EMAIL in vm.form.value.errors)
        assertNull(store.observeProfile("device-adam").first())
    }

    @Test
    fun profile_editUpdatesDetailsAndKeepsRole() = runTest {
        store.saveProfile(UserProfile("device-adam", "Adam Kent", "adam@example.com", activeRole = Role.OWNER))
        val vm = ProfileViewModel(container)
        keep(vm.state)
        vm.startEditing(store.observeProfile("device-adam").first()!!)
        vm.onPhoneChange("+1 780 555 0142")
        vm.save()
        val p = store.observeProfile("device-adam").first()!!
        assertEquals("+1 780 555 0142", p.phone)
        assertEquals(Role.OWNER, p.activeRole)
    }

    @Test
    fun profile_switchRoleRespectsAdminFlag() = runTest {
        store.saveProfile(UserProfile("device-adam", "Adam Kent", "adam@example.com"))
        val vm = ProfileViewModel(container)
        keep(vm.state)
        vm.switchRole(Role.ADMIN)
        assertEquals(Role.ENTRANT, store.observeProfile("device-adam").first()?.activeRole)
        vm.switchRole(Role.OWNER)
        assertEquals(Role.OWNER, store.observeProfile("device-adam").first()?.activeRole)
    }

    @Test
    fun search_filtersAsUserTypes() = runTest {
        val vm = SearchViewModel(container)
        keep(vm.state)
        assertEquals(6, vm.state.value.results.size)
        vm.onQueryChange("louise")
        // Name match first, then Moraine Lake (located in Lake Louise).
        assertEquals(listOf("lake-louise", "moraine-lake"), vm.state.value.results.map { it.id })
    }

    @Test
    fun details_joinThenLeaveWaitlist() = runTest {
        val vm = CampsiteDetailsViewModel(container, "two-jack")
        keep(vm.state)
        assertEquals(LotteryAction.CAN_JOIN, vm.state.value.action)
        vm.join("Adam Kent")
        assertEquals(LotteryAction.JOINED, vm.state.value.action)
        assertEquals(7, vm.state.value.lottery?.entrantCount)
        vm.leave()
        assertEquals(LotteryAction.CAN_JOIN, vm.state.value.action)
        assertNull(vm.state.value.message)
    }

    @Test
    fun details_upcomingLotteryCannotBeJoined() = runTest {
        val vm = CampsiteDetailsViewModel(container, "moraine-lake")
        keep(vm.state)
        assertEquals(LotteryAction.UPCOMING, vm.state.value.action)
    }

    @Test
    fun lotteryAction_coversDrawOutcomes() {
        val drawn = store.snapshot.lotteries.getValue("lot-johnston")
        val won = store.snapshot.entries.first { it.lotteryId == drawn.id && it.status.name == "WON" }
        assertEquals(LotteryAction.WON, lotteryAction(drawn, won, NOW))
        assertEquals(LotteryAction.ENDED, lotteryAction(drawn, null, NOW))
        assertEquals(LotteryAction.NONE, lotteryAction(null, null, NOW))
    }

    @Test
    fun campsiteForm_validatesThenCreates() = runTest {
        val vm = CampsiteFormViewModel(container, null)
        val owner = UserProfile("device-adam", "Adam Kent", "adam@example.com")
        var newId: String? = null
        vm.save(owner) { newId = it }
        assertTrue(Field.NAME in vm.state.value.errors)
        vm.update(Field.NAME) { it.copy(name = "Bow River Sites") }
        vm.update(Field.LOCATION) { it.copy(location = "Banff, AB") }
        vm.toggleTag("Lakeside"); vm.addCustomTag("canoe launch")
        vm.save(owner) { newId = it }
        val site = store.snapshot.campsites.getValue(newId!!)
        assertEquals("device-adam", site.ownerId)
        assertEquals(listOf("Lakeside", "Canoe launch"), site.tags)
    }

    @Test
    fun campsiteForm_editUpdatesExisting() = runTest {
        val vm = CampsiteFormViewModel(container, "two-jack")
        vm.update(Field.NAME) { it.copy(name = "Two Jack Lakeside (North)") }
        vm.save(SampleData.owner(NOW)) {}
        assertEquals("Two Jack Lakeside (North)", store.snapshot.campsites.getValue("two-jack").name)
    }

    @Test
    fun createLottery_validatesAndPublishes() = runTest {
        val owner = AppContainer.inMemory(SampleData.OWNER_ID, clock, store)
        val vm = CreateLotteryViewModel(owner, "tunnel-mountain")
        vm.publish {}
        assertNotNull(vm.state.value.errors[Field.REG_START])
        vm.setStart(NOW); vm.setEnd(NOW + 7 * DAY); vm.setWinners(5)
        var id: String? = null
        vm.publish { id = it }
        val lot = store.snapshot.lotteries.getValue(id!!)
        assertEquals("tunnel-mountain", lot.campsiteId)
        assertEquals(5, lot.winnerCount)
        assertEquals(NOW + 7 * DAY, lot.registrationEnd)
    }

    @Test
    fun admin_removeCampsite() = runTest {
        val vm = AdminCampsiteViewModel(container, "two-jack")
        vm.remove()
        assertTrue(vm.state.value.removed)
        assertTrue("two-jack" !in store.snapshot.campsites)
    }
}
