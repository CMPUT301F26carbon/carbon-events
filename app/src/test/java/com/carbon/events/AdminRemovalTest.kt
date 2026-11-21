/*
 * AdminRemovalTest.kt (Tausif)
 *
 * Purpose: Unit tests for an admin removing someone (US 03.02.01, US 03.07.01) for good: their
 * campsites, lotteries, waitlist spots and inbox go, a removed record stays on their login so
 * they can't use the app again, and the phones they used can't be used to sign up again. An
 * admin can restore the account.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 18, 2026). Prompt: "Write
 * tests for an admin removing someone so they can't use the app again." Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** The sample data: Maya is on two waitlists and won a stay; Parks Lodge owns every campsite. */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminRemovalTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = ViewModelStore()
    private val now = TestData.NOW
    private val maya = UserProfile("sample-entrant-0", "Maya Chen", "maya@example.com", "+1 780 555 0110", authUid = "uid-maya", photoBase64 = "abc")
    private lateinit var store: InMemoryEventsStore

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = InMemoryEventsStore(SampleData.snapshot(now))
        runBlocking {
            store.saveProfile(maya)
            store.saveProfile(SampleData.owner(now))
            store.trustDevice(TrustedDevice("maya-phone", maya.id, "Pixel 8", now))
            store.trustDevice(TrustedDevice("maya-tablet", maya.id, "Pixel Tablet", now))
        }
    }

    @After fun tearDown() {
        models.clear()
        Dispatchers.resetMain()
    }

    private fun <T : ViewModel> track(vm: T): T = vm.also { models.put(System.identityHashCode(vm).toString(), vm) }
    private fun removeMaya() = runBlocking { store.removeProfile(maya, now) }
    private fun record(id: String = maya.id) = store.snapshot.profiles[id]

    @Test fun removing_keepsARemovedRecordOnTheirLogin() {
        removeMaya()
        val r = record()!!
        assertTrue(r.removed)
        assertTrue(r.blocked)
        assertEquals("Maya Chen", r.name)
        assertEquals("uid-maya", r.authUid)
        assertEquals("", r.phone)
        assertNull(r.photoBase64)
    }

    @Test fun removing_takesThemOffWaitlists_andGivesBackWhatTheyWon() = runTest {
        assertTrue(store.snapshot.entries.any { it.entrantId == maya.id && it.status == EntryStatus.WAITING })
        removeMaya()
        assertTrue(store.snapshot.entries.none { it.entrantId == maya.id && it.status == EntryStatus.WAITING })
        assertTrue(store.snapshot.entries.none { it.entrantId == maya.id && it.status == EntryStatus.WON && (it.checkOutDay == null || it.checkOutDay!! >= dayOf(now)) })
    }

    @Test fun removing_clearsTheirInbox() = runTest {
        store.warn(maya, "Please keep messages polite.", now)
        removeMaya()
        assertTrue(store.snapshot.notifications.none { it.recipientId == maya.id })
    }

    @Test fun removingAnOwner_deletesTheirCampsitesAndLotteries() = runTest {
        store.removeProfile(SampleData.owner(now), now)
        assertTrue(store.snapshot.campsites.values.none { it.ownerId == SampleData.OWNER_ID })
        assertTrue(store.snapshot.lotteries.values.none { it.ownerId == SampleData.OWNER_ID })
        assertTrue(record(SampleData.OWNER_ID)!!.removed)
    }

    @Test fun removing_blocksEveryPhoneTheyUsed() = runTest {
        removeMaya()
        assertTrue(store.isPhoneBlocked("maya-phone"))
        assertTrue(store.isPhoneBlocked("maya-tablet"))
        assertFalse(store.isPhoneBlocked("someone-elses-phone"))
    }

    @Test fun aBlockedPhone_cantBeTrustedByAnyone() = runTest {
        removeMaya()
        assertTrue(runCatching { store.trustDevice(TrustedDevice("maya-phone", "sample-entrant-1", "Pixel 8", now)) }.isFailure)
        assertEquals(maya.id, store.observeUserIdForDevice("maya-phone").first())
    }

    @Test fun theRemovedPerson_signingInAgain_findsTheirBlockedRecord() = runTest {
        removeMaya()
        val found = store.observeProfileForAccount("uid-maya").first()!!
        assertTrue(found.blocked && found.removed)
    }

    @Test fun phoneOnlySignUp_onARemovedPersonsPhone_isRefused() = runTest(dispatcher) {
        removeMaya()
        val auth = InMemoryAuth(null)
        val vm = track(AccountViewModel(AppContainer.inMemory("maya-phone", FixedClock(now), store, auth), null))
        vm.useThisPhone()
        vm.onName("Maya C"); vm.onAge("30"); vm.submitDetails()
        assertEquals(PHONE_REMOVED_MESSAGE, vm.state.value.message)
        assertNotEquals(AccountStep.TERMS, vm.state.value.step)
        assertNull(auth.observeAccount().first())
        assertEquals(1, store.snapshot.profiles.values.count { it.name.startsWith("Maya") })
    }

    @Test fun emailSignUp_onARemovedPersonsPhone_isRefused() = runTest(dispatcher) {
        removeMaya()
        val auth = InMemoryAuth(null)
        val vm = track(AccountViewModel(AppContainer.inMemory("maya-phone", FixedClock(now), store, auth), null))
        vm.useEmail()
        vm.onName("New Name"); vm.onEmail("new.name@example.com"); vm.onPassword("campfire8"); vm.onAge("30"); vm.submitDetails()
        assertEquals(PHONE_REMOVED_MESSAGE, vm.state.value.message)
        assertNull(auth.observeAccount().first())
        assertTrue(store.snapshot.profiles.values.none { it.name == "New Name" })
    }

    @Test fun signUpOnAnyOtherPhone_stillWorks() = runTest(dispatcher) {
        removeMaya()
        val vm = track(AccountViewModel(AppContainer.inMemory("fresh-phone", FixedClock(now), store, InMemoryAuth(null)), null))
        vm.useThisPhone()
        vm.onName("Jordan Lee"); vm.onAge("30"); vm.submitDetails()
        vm.onAgreed(true); vm.acceptTerms()
        assertNull(vm.state.value.message)
        assertTrue(store.snapshot.profiles.values.any { it.name == "Jordan Lee" })
    }

    @Test fun restoring_letsThemAndTheirPhonesBackIn() = runTest {
        removeMaya()
        store.restoreProfile(record()!!)
        val r = record()!!
        assertFalse(r.removed)
        assertFalse(r.blocked)
        assertFalse(store.isPhoneBlocked("maya-phone"))
        store.trustDevice(TrustedDevice("maya-phone", maya.id, "Pixel 8", now))
    }

    @Test fun deletingYourOwnProfile_stillDeletesEverything() = runTest {
        store.deleteProfile(maya, now)
        assertNull(record())
        assertFalse(store.isPhoneBlocked("maya-phone"))
        assertNull(store.observeUserIdForDevice("maya-phone").first())
    }
}
