/*
 * CampsiteRemovalTest.kt (Suvir)
 *
 * Purpose: Unit tests for removing a campsite (US 03.01.01, and its owner deleting it): everyone it
 * still mattered to is told first (people waiting for the draw, winners who haven't booked,
 * guests whose stay hasn't ended), nobody else is, and the owner can delete their campsite from
 * its page after seeing how many people it affects.
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CampsiteRemovalTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = ViewModelStore()
    private val now = TestData.NOW
    private val today = now / TestData.DAY
    private val site = TestData.campsite()
    private val lot = TestData.lottery("two-jack/may-2027")

    private fun entry(id: String, status: EntryStatus, inDay: Long? = null, outDay: Long? = null) =
        WaitlistEntry(lot.id, id, "Guest $id", now, status, checkInDay = inDay, checkOutDay = outDay, campsiteId = site.id)

    private val waiting = entry("waiting", EntryStatus.WAITING)
    private val upcoming = entry("upcoming", EntryStatus.WON, today + 3, today + 5)
    private val staying = entry("staying", EntryStatus.WON, today - 1, today + 1)
    private val unbooked = entry("unbooked", EntryStatus.WON)
    private val stayed = entry("stayed", EntryStatus.WON, today - 9, today - 7)
    private val lost = entry("lost", EntryStatus.LOST)
    private val cancelled = entry("cancelled", EntryStatus.CANCELLED, today + 3, today + 5)
    private val everyone = listOf(waiting, upcoming, staying, unbooked, stayed, lost, cancelled)

    private lateinit var store: InMemoryEventsStore

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = InMemoryEventsStore(EventsSnapshot(campsites = mapOf(site.id to site), lotteries = mapOf(lot.id to lot), entries = everyone))
    }

    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }

    private fun <T : ViewModel> track(vm: T): T = vm.also { models.put(System.identityHashCode(vm).toString(), vm) }
    private fun TestScope.observe(flow: Flow<*>) { backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} } }

    @Test fun notices_goToEveryoneItStillMatteredTo_andNobodyElse() {
        val told = CampsiteRemoval.notices(site, everyone, today, now)
        assertEquals(setOf("waiting", "upcoming", "staying", "unbooked"), told.map { it.recipientId }.toSet())
        assertTrue(told.all { it.type == NotificationType.REMOVED && it.title == "Two Jack Lakeside was removed" && it.lotteryId == lot.id })
        assertEquals("Its lottery is cancelled, so you're no longer on its waitlist.", told.single { it.recipientId == "waiting" }.body)
        assertTrue(told.single { it.recipientId == "upcoming" }.body.endsWith("is cancelled. Nothing is charged."))
        assertEquals(4, CampsiteRemoval.affected(site, everyone, today))
    }

    @Test fun deleting_tellsThem_thenRemovesEverything() = runTest(dispatcher) {
        store.deleteCampsite(site.id, now)
        assertNull(store.observeCampsite(site.id).first())
        assertTrue(store.observeLotteriesForCampsite(site.id).first().isEmpty())
        assertTrue(store.observeEntriesForEntrant("upcoming").first().isEmpty())
        assertEquals("Two Jack Lakeside was removed", store.observeNotifications("upcoming").first().single().title)
        assertTrue("someone whose stay is over isn't told", store.observeNotifications("stayed").first().isEmpty())
        assertTrue(store.observeNotifications("lost").first().isEmpty())
    }

    @Test fun theOwner_seesWhoIsAffected_thenDeletesTheirCampsite() = runTest(dispatcher) {
        val vm = track(ManageCampsiteViewModel(AppContainer.inMemory("owner-1", FixedClock(now), store), site.id))
        observe(vm.state)
        vm.askDelete()
        assertEquals(4, vm.state.value.confirmDelete)
        vm.cancelDelete()
        assertNull(vm.state.value.confirmDelete)
        assertEquals("nothing is deleted when they don't confirm", site, store.observeCampsite(site.id).first())
        vm.askDelete(); vm.delete()
        assertTrue(vm.state.value.deleted)
        assertNull(store.observeCampsite(site.id).first())
        assertEquals(4, listOf("waiting", "upcoming", "staying", "unbooked").count { store.snapshot.notifications.any { n -> n.recipientId == it } })
    }
}
