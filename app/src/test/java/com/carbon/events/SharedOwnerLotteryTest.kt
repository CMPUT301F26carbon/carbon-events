/*
 * SharedOwnerLotteryTest.kt (Suvir)
 *
 * Purpose: Unit tests for listing campsites with other campsite owners in one lottery
 * (US 02.04.08): an owner publishing a lottery invites another owner's campsite; that owner
 * accepts (their campsite's lottery joins the group with the same settings) or declines; each
 * side is told; entrants then join once for both campsites; and invitations that are someone
 * else's, already answered or for a closed waitlist can't be accepted.
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharedOwnerLotteryTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = ViewModelStore()
    private val now = TestData.NOW
    private val day = TestData.DAY
    private val clock = FixedClock(now)

    /** Parks Lodge runs Two Jack; Jasper Outfitters runs Whistlers, a different owner. */
    private val twoJack = TestData.campsite("two-jack", "Two Jack Lakeside", owner = "parks")
    private val whistlers = TestData.campsite("whistlers", "Whistlers Hollow", "Jasper, AB", owner = "jasper").copy(ownerName = "Jasper Outfitters")
    private lateinit var store: InMemoryEventsStore

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = InMemoryEventsStore(EventsSnapshot(
            profiles = mapOf("parks" to UserProfile("parks", "Parks Lodge", "p@example.com"), "jasper" to UserProfile("jasper", "Jasper Outfitters", "j@example.com")),
            campsites = mapOf(twoJack.id to twoJack, whistlers.id to whistlers),
        ))
    }

    @After fun tearDown() {
        models.clear()
        Dispatchers.resetMain()
    }

    private fun <T : ViewModel> track(vm: T): T = vm.also { models.put(System.identityHashCode(vm).toString(), vm) }
    private fun TestScope.observe(flow: Flow<*>) { backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} } }
    private fun container(user: String) = AppContainer.inMemory(user, clock, store)

    private val published = Lottery(
        "two-jack/may-2027", "two-jack", "Two Jack Lakeside", "parks", now - day, now + 7 * day, winnerCount = 5, waitlistCap = 40,
        bookingStartDay = now / day + 30, bookingEndDay = now / day + 60, groupId = "grp-1",
    )

    private fun invite() = LotteryInvites.forCampsites(published, "grp-1", "Parks Lodge", twoJack, listOf(whistlers), now).single()

    /** Parks Lodge publishes a lottery for Two Jack and invites Whistlers, through the real form. */
    private fun TestScope.publishWithInvite(): String {
        val vm = track(CreateLotteryViewModel(container("parks"), "two-jack"))
        observe(vm.state)
        vm.setStart(now - 60_000); vm.setEnd(now + 7 * day); vm.setBooking(now / day + 30, now / day + 60); vm.setWinners(5)
        vm.setInviteQuery("jasper")
        assertEquals("other owners' campsites are found by owner", listOf("whistlers"), LotteryInvites.searchOthers(vm.state.value.others, "parks", "jasper").map { it.id })
        vm.toggleInvite("whistlers")
        var id: String? = null
        vm.publish { id = it }
        return id!!
    }

    // ---- The rules ----

    @Test fun invitations_copyTheLotterysSettings_andSkipYourOwnCampsites() {
        val sent = LotteryInvites.forCampsites(published, "grp-1", "Parks Lodge", twoJack, listOf(whistlers, twoJack, whistlers), now)
        val inv = sent.single()
        assertEquals("whistlers", inv.campsiteId)
        assertEquals("jasper", inv.toOwnerId)
        assertEquals("two-jack", inv.fromCampsiteId)
        assertEquals(published.registrationEnd, inv.registrationEnd)
        assertEquals(published.bookingStartDay, inv.bookingStartDay)
        assertEquals(5, inv.winnerCount)
        assertEquals(40, inv.waitlistCap)
        assertEquals(InviteStatus.PENDING, inv.status)
    }

    @Test fun accepting_addsALotteryRunByTheInvitedOwner_inTheSameGroup() {
        val lot = LotteryInvites.lotteryFor(invite())
        assertEquals("whistlers", lot.campsiteId)
        assertEquals("jasper", lot.ownerId)
        assertEquals("grp-1", lot.groupId)
        assertEquals(published.registrationStart, lot.registrationStart)
        assertEquals(published.bookingEndDay, lot.bookingEndDay)
        assertEquals(LotteryStatus.OPEN, lot.status)
    }

    @Test fun checkAccept_refusesWhatCantBeAccepted() {
        val inv = invite()
        assertNull(LotteryInvites.checkAccept(inv, "jasper", whistlers, now))
        assertEquals("This invitation is for another owner.", LotteryInvites.checkAccept(inv, "parks", whistlers, now))
        assertEquals("This invitation has already been answered.", LotteryInvites.checkAccept(inv.copy(status = InviteStatus.DECLINED), "jasper", whistlers, now))
        assertEquals("That campsite is no longer yours.", LotteryInvites.checkAccept(inv, "jasper", null, now))
        assertEquals("This lottery's waitlist has already closed.", LotteryInvites.checkAccept(inv, "jasper", whistlers, published.registrationEnd))
    }

    @Test fun searchOthers_findsOtherOwnersCampsitesByNamePlaceOrOwner() {
        val all = listOf(twoJack, whistlers)
        assertEquals(listOf("whistlers"), LotteryInvites.searchOthers(all, "parks", "whistlers").map { it.id })
        assertEquals(listOf("whistlers"), LotteryInvites.searchOthers(all, "parks", "Jasper").map { it.id })
        assertTrue("your own campsites aren't offered", LotteryInvites.searchOthers(all, "parks", "two jack").isEmpty())
        assertTrue("nothing until you type", LotteryInvites.searchOthers(all, "parks", "  ").isEmpty())
    }

    // ---- Through the app ----

    @Test fun publishing_sendsTheInvitation_andTellsTheOtherOwner() = runTest(dispatcher) {
        val lotId = publishWithInvite()
        val mine = store.observeLottery(lotId).first()!!
        assertTrue("an invitation makes it a group", mine.groupId.isNotBlank())
        val inv = store.observeInvitesTo("jasper").first().single()
        assertEquals(mine.groupId, inv.groupId)
        assertEquals(InviteStatus.PENDING, inv.status)
        assertEquals("Parks Lodge", inv.fromOwnerName)
        assertEquals(listOf(inv), store.observeInvitesFrom("parks").first())
        val notice = store.observeNotifications("jasper").first().single()
        assertEquals("Parks Lodge invited Whistlers Hollow to a shared lottery", notice.title)
        assertTrue("Whistlers has no lottery until its owner accepts", store.observeLotteriesForCampsite("whistlers").first().isEmpty())
    }

    @Test fun accepting_joinsTheGroup_andEntrantsJoinBothCampsitesAtOnce() = runTest(dispatcher) {
        val lotId = publishWithInvite()
        val vm = track(OwnerLotteriesViewModel(container("jasper")))
        observe(vm.state)
        val inv = vm.state.value.invitesIn.single()
        vm.answer(inv, accept = true)
        assertNull(vm.state.value.message)

        val theirs = store.observeLotteriesForCampsite("whistlers").first().single()
        val mine = store.observeLottery(lotId).first()!!
        assertEquals("jasper", theirs.ownerId)
        assertEquals(mine.groupId, theirs.groupId)
        assertEquals(mine.registrationEnd, theirs.registrationEnd)
        assertEquals(mine.winnerCount, theirs.winnerCount)
        assertEquals(InviteStatus.ACCEPTED, store.observeInvitesTo("jasper").first().single().status)
        assertEquals(theirs.id, store.observeInvitesTo("jasper").first().single().lotteryId)
        assertEquals("the inviting owner is told", "Whistlers Hollow joined your lottery", store.observeNotifications("parks").first().single().title)
        assertTrue("it shows in Jasper's lotteries", vm.state.value.lotteries.any { it.id == theirs.id })

        // An entrant joins from Two Jack's page and is on both campsites' waitlists.
        LotteryGroups.joinAll(store, mine, "maya", "Maya Chen", now)
        assertEquals(setOf(mine.id, theirs.id), store.observeEntriesForEntrant("maya").first().map { it.lotteryId }.toSet())
        assertEquals(listOf("Two Jack Lakeside", "Whistlers Hollow"), LotteryGroups.members(mine, store.observeLotteries().first()).map { it.campsiteName })
    }

    @Test fun declining_addsNothing_andTellsTheInvitingOwner() = runTest(dispatcher) {
        publishWithInvite()
        val vm = track(OwnerLotteriesViewModel(container("jasper")))
        observe(vm.state)
        vm.answer(vm.state.value.invitesIn.single(), accept = false)
        assertEquals(InviteStatus.DECLINED, store.observeInvitesTo("jasper").first().single().status)
        assertTrue(store.observeLotteriesForCampsite("whistlers").first().isEmpty())
        assertEquals("Whistlers Hollow declined your lottery", store.observeNotifications("parks").first().single().title)
    }

    @Test fun anInvitationCanOnlyBeAnsweredOnce_andOnlyByItsOwner() = runTest(dispatcher) {
        publishWithInvite()
        val inv = store.observeInvitesTo("jasper").first().single()
        try { store.answerInvite(inv.id, "parks", accept = true, now); fail("the inviting owner can't accept for them") } catch (e: LotteryEditException) { assertNotNull(e.message) }
        assertNotNull(store.answerInvite(inv.id, "jasper", accept = true, now))
        try { store.answerInvite(inv.id, "jasper", accept = true, now); fail("it can't be accepted twice") } catch (e: LotteryEditException) {
            assertEquals("This invitation has already been answered.", e.message)
        }
        assertEquals(1, store.observeLotteriesForCampsite("whistlers").first().size)
    }

    @Test fun accepting_isRefused_whenTheCampsiteAlreadyOffersThoseNights() = runTest(dispatcher) {
        publishWithInvite()
        val inv = store.observeInvitesTo("jasper").first().single()
        // Whistlers already has its own lottery for some of the same nights.
        store.createLottery(Lottery("", "whistlers", "Whistlers Hollow", "jasper", now, now + day, 2,
            bookingStartDay = inv.bookingStartDay!! + 5, bookingEndDay = inv.bookingStartDay!! + 9))
        val vm = track(OwnerLotteriesViewModel(container("jasper")))
        observe(vm.state)
        vm.answer(inv, accept = true)
        assertTrue(vm.state.value.message.orEmpty().startsWith("Whistlers Hollow already has a lottery for stays"))
        assertEquals(InviteStatus.PENDING, store.observeInvitesTo("jasper").first().single().status)
        assertEquals("only its own lottery", 1, store.observeLotteriesForCampsite("whistlers").first().size)
    }

    @Test fun afterTheWaitlistCloses_itCanNoLongerBeAccepted() = runTest(dispatcher) {
        publishWithInvite()
        val inv = store.observeInvitesTo("jasper").first().single()
        val vm = track(OwnerLotteriesViewModel(AppContainer.inMemory("jasper", FixedClock(inv.registrationEnd + 1), store)))
        observe(vm.state)
        vm.answer(inv, accept = true)
        assertEquals("This lottery's waitlist has already closed.", vm.state.value.message)
        assertTrue(store.observeLotteriesForCampsite("whistlers").first().isEmpty())
    }
}
