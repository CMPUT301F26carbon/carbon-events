/*
 * GroupLotteryEditTest.kt (Suvir)
 *
 * Purpose: Unit tests for editing a lottery that covers several campsites (US 02.04.06 with
 * US 02.04.02 and US 02.04.08): a change to one of an owner's campsites in it goes to all of that
 * owner's campsites in it, so they keep the same periods; when the lottery is shared with another
 * owner's campsite, its dates are locked (they'd change for that owner too), while the winners and
 * cap still change for this owner's campsites and never the other owner's.
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupLotteryEditTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = ViewModelStore()
    private val now = TestData.NOW
    private val day = TestData.DAY
    private val today = now / day
    private val clock = FixedClock(now)

    private fun lottery(id: String, campsite: String, name: String, owner: String) = Lottery(
        id, campsite, name, owner, now + day, now + 5 * day, winnerCount = 3, waitlistCap = 20,
        bookingStartDay = today + 10, bookingEndDay = today + 20, groupId = "grp-1",
    )
    private val twoJack = lottery("two-jack/may", "two-jack", "Two Jack Lakeside", "parks")
    private val tunnel = lottery("tunnel/may", "tunnel", "Tunnel Mountain", "parks")
    private val whistlers = lottery("whistlers/may", "whistlers", "Whistlers Hollow", "jasper")

    private lateinit var store: InMemoryEventsStore

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }

    private fun with(vararg lotteries: Lottery) {
        store = InMemoryEventsStore(EventsSnapshot(
            campsites = mapOf(
                "two-jack" to TestData.campsite("two-jack", "Two Jack Lakeside", owner = "parks"),
                "tunnel" to TestData.campsite("tunnel", "Tunnel Mountain", owner = "parks"),
                "whistlers" to TestData.campsite("whistlers", "Whistlers Hollow", "Jasper, AB", owner = "jasper"),
            ),
            lotteries = lotteries.associateBy { it.id },
        ))
    }

    private fun <T : ViewModel> track(vm: T): T = vm.also { models.put(System.identityHashCode(vm).toString(), vm) }
    private fun TestScope.observe(flow: Flow<*>) { backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} } }
    private suspend fun lot(id: String) = store.observeLottery(id).first()!!
    private fun TestScope.editor(id: String) = track(CreateLotteryViewModel(AppContainer.inMemory("parks", clock, store), null, id)).also { observe(it.state) }

    @Test fun editingOneOfYourCampsites_changesTheWholeLottery() = runTest(dispatcher) {
        with(twoJack, tunnel)
        val vm = editor(twoJack.id)
        assertEquals(listOf("Tunnel Mountain"), vm.state.value.siblings.map { it.campsiteName })
        assertFalse(vm.state.value.sharedWithOthers)
        vm.setEnd(now + 7 * day); vm.setBooking(today + 12, today + 22); vm.setWinners(5)
        var saved: String? = null
        vm.publish { saved = it }
        assertEquals(twoJack.id, saved)
        for (id in listOf(twoJack.id, tunnel.id)) {
            val l = lot(id)
            assertEquals(now + 7 * day, l.registrationEnd)
            assertEquals(today + 12, l.bookingStartDay)
            assertEquals(5, l.winnerCount)
        }
    }

    @Test fun aLotterySharedWithAnotherOwner_keepsItsDates() = runTest(dispatcher) {
        with(twoJack, tunnel, whistlers)
        val vm = editor(twoJack.id)
        assertTrue(vm.state.value.sharedWithOthers)
        vm.setEnd(now + 7 * day)
        vm.publish { }
        assertEquals(LotteryGroups.SHARED_DATES_LOCKED, vm.state.value.message)
        assertEquals("nothing was saved", twoJack.registrationEnd, lot(twoJack.id).registrationEnd)
        assertEquals(twoJack.registrationEnd, lot(tunnel.id).registrationEnd)
    }

    @Test fun aLotterySharedWithAnotherOwner_stillTakesNewWinners_forYourCampsitesOnly() = runTest(dispatcher) {
        with(twoJack, tunnel, whistlers)
        val vm = editor(twoJack.id)
        vm.setWinners(6); vm.setCap(30)
        vm.publish { }
        assertNull(vm.state.value.message)
        assertEquals(6, lot(twoJack.id).winnerCount)
        assertEquals(6, lot(tunnel.id).winnerCount)
        assertEquals(30, lot(tunnel.id).waitlistCap)
        assertEquals("the other owner's campsite is theirs to change", 3, lot(whistlers.id).winnerCount)
    }

    @Test fun aLotteryOnItsOwn_isEditedAlone() = runTest(dispatcher) {
        val alone = twoJack.copy(groupId = "")
        with(alone, tunnel)
        val vm = editor(alone.id)
        assertTrue(vm.state.value.siblings.isEmpty())
        vm.setWinners(9)
        vm.publish { }
        assertEquals(9, lot(alone.id).winnerCount)
        assertEquals(3, lot(tunnel.id).winnerCount)
    }

    @Test fun ifOneCampsiteCantTakeTheChange_noneIsSaved() = runTest(dispatcher) {
        // Tunnel Mountain already has another lottery for the nights the edit would move into.
        with(twoJack, tunnel, Lottery("tunnel/june", "tunnel", "Tunnel Mountain", "parks", now, now + day, 2, bookingStartDay = today + 25, bookingEndDay = today + 30))
        val vm = editor(twoJack.id)
        vm.setBooking(today + 24, today + 28)
        vm.publish { }
        assertTrue(vm.state.value.message.orEmpty().startsWith("Tunnel Mountain already has a lottery for stays"))
        assertEquals(today + 10, lot(twoJack.id).bookingStartDay)
        assertEquals(today + 10, lot(tunnel.id).bookingStartDay)
    }
}
