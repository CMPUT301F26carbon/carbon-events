/*
 * NameChangeTest.kt (Suvir)
 *
 * Purpose: Unit tests for changing your name in Edit Profile (US 01.07.03, B12): the new name also
 * shows on the copies other screens use, the owner name on your campsites, the guest name on your
 * waiting-list entries and your name in your conversations, and nobody else's are touched.
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NameChangeTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = ViewModelStore()
    private val now = TestData.NOW
    private val owner = UserProfile("parks", "Parks Lodge", "p@example.com", createdAt = now)
    private val maya = UserProfile("maya", "Maya Chen", "m@example.com", createdAt = now)
    private val site = TestData.campsite(owner = "parks").copy(ownerName = "Parks Lodge")
    private val other = TestData.campsite("whistlers", "Whistlers Hollow", owner = "jasper").copy(ownerName = "Jasper Outfitters")
    private val lot = TestData.lottery("two-jack/may-2027", owner = "parks")
    private val chat = Chat(Chat.idFor(site.id, "maya"), site.id, site.name, "parks", "Parks Lodge", "maya", "Maya Chen", lotteryKey = "may-2027")
    private val otherChat = Chat(Chat.idFor(other.id, "lee"), other.id, other.name, "jasper", "Jasper Outfitters", "lee", "Lee Park", lotteryKey = "may-2027")
    private lateinit var store: InMemoryEventsStore

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = InMemoryEventsStore(EventsSnapshot(
            profiles = mapOf(owner.id to owner, maya.id to maya),
            campsites = mapOf(site.id to site, other.id to other), lotteries = mapOf(lot.id to lot),
            entries = listOf(WaitlistEntry(lot.id, "maya", "Maya Chen", now), WaitlistEntry(lot.id, "lee", "Lee Park", now)),
            chats = mapOf(chat.id to chat, otherChat.id to otherChat),
        ))
    }

    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }

    /** [who] opens Edit Profile on their trusted phone and changes their name to [name]. */
    private suspend fun TestScope.rename(who: UserProfile, name: String) {
        store.trustDevice(TrustedDevice(who.id, who.id, "Pixel 8", now))
        val vm = ProfileViewModel(AppContainer.inMemory(who.id, FixedClock(now), store)).also { models.put(who.id, it) }
        backgroundScope.launch { vm.state.collect {} }
        vm.startEditing(who)
        vm.onNameChange(name)
        vm.save()
    }

    @Test fun aGuestsNewName_showsOnTheirEntries_andConversations() = runTest(dispatcher) {
        rename(maya, "Maya Chen-Lee")
        assertEquals("Maya Chen-Lee", store.snapshot.profiles.getValue("maya").name)
        assertEquals("Maya Chen-Lee", store.snapshot.entries.single { it.entrantId == "maya" }.entrantName)
        assertEquals("Maya Chen-Lee", store.snapshot.chats.getValue(chat.id).entrantName)
        assertEquals("nobody else's", "Lee Park", store.snapshot.entries.single { it.entrantId == "lee" }.entrantName)
    }

    @Test fun anOwnersNewName_showsOnTheirCampsites_andConversations() = runTest(dispatcher) {
        rename(owner, "Parks Lodge & Co")
        assertEquals("Parks Lodge & Co", store.snapshot.campsites.getValue(site.id).ownerName)
        assertEquals("Parks Lodge & Co", store.snapshot.chats.getValue(chat.id).ownerName)
        assertEquals("another owner's campsite keeps its owner", "Jasper Outfitters", store.snapshot.campsites.getValue(other.id).ownerName)
        assertEquals("Jasper Outfitters", store.snapshot.chats.getValue(otherChat.id).ownerName)
    }

    @Test fun savingWithoutChangingTheName_changesNothingElse() = runTest(dispatcher) {
        val before = store.snapshot
        rename(maya, "Maya Chen")
        assertEquals(before.entries, store.snapshot.entries)
        assertEquals(before.chats, store.snapshot.chats)
        assertTrue(store.snapshot.campsites == before.campsites)
    }
}
