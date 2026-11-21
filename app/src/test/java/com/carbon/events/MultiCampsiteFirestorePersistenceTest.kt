/*
 * MultiCampsiteFirestorePersistenceTest.kt (Aditya)
 *
 * Purpose: One lottery over several campsites against the security rules on the local Firebase
 * emulators, never the production project (enabled only when FIRESTORE_EMULATOR and
 * AUTH_EMULATOR are set): the owner publishes it, an entrant joins and leaves all of it
 * (US 02.04.02).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 17, 2026). Prompt: "Write
 * tests for one lottery over several campsites." Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.UUID

/** An owner's lottery over two campsites, and an entrant using it, on the server. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class MultiCampsiteFirestorePersistenceTest {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuthStore
    private lateinit var store: FirestoreEventsStore
    private val tag = UUID.randomUUID().toString().take(8)
    private val password = "campfire8"

    private fun <T> call(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { withTimeout(25_000) { block() } }

    @Before fun setUp() {
        assumeTrue("Local Firebase services are not configured", System.getenv("FIRESTORE_EMULATOR") != null && System.getenv("AUTH_EMULATOR") != null)
        val options = FirebaseOptions.Builder().setProjectId("demo-carbon-events")
            .setApplicationId("1:1:android:1").setApiKey("emulator-only").build()
        val app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), options, "test-${UUID.randomUUID()}")
        db = FirebaseFirestore.getInstance(app)
        val (host, port) = requireNotNull(System.getenv("FIRESTORE_EMULATOR")).split(":")
        db.useEmulator(host, port.toInt())
        db.firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        val firebaseAuth = FirebaseAuth.getInstance(app)
        val (authHost, authPort) = requireNotNull(System.getenv("AUTH_EMULATOR")).split(":")
        firebaseAuth.useEmulator(authHost, authPort.toInt())
        auth = FirebaseAuthStore(firebaseAuth)
        store = FirestoreEventsStore(db)
    }

    private fun person(name: String, uid: String, email: String, role: Role) =
        UserProfile("", name, email, activeRole = role, authUid = uid, age = 30, createdAt = System.currentTimeMillis())

    private class Published(val ownerId: String, val lotteries: List<Lottery>)

    /** Signs an owner up, registers two campsites and publishes one lottery over both. */
    private fun publishGroup(): Published {
        val account = call { auth.signUp("owner-$tag@example.com", password) }
        val ownerId = call { store.createProfile(person("Bow Valley $tag", account.uid, "owner-$tag@example.com", Role.OWNER), null) }
        val now = System.currentTimeMillis()
        val today = dayOf(now)
        val group = LotteryGroups.newGroupId(now)
        val lotteries = listOf("Elk Meadow", "Spray River").map { name ->
            val siteId = call { store.createCampsite(Campsite("", ownerId, "Bow Valley $tag", "$name $tag", "Canmore, AB", maxOccupancy = 4, createdAt = now)) }
            val lot = Lottery("", siteId, "$name $tag", ownerId, now - 60_000, now + 86_400_000, 1, bookingStartDay = today + 3, bookingEndDay = today + 10, groupId = group)
            lot.copy(id = call { store.createLottery(lot) })
        }
        auth.signOut()
        return Published(ownerId, lotteries)
    }

    private fun lottery(id: String) = call { store.observeLottery(id).first() }!!

    @Test fun owner_canPublishOneLotteryOverTwoCampsites() {
        val p = publishGroup()
        val saved = p.lotteries.map { lottery(it.id) }
        assertEquals(2, saved.size)
        assertTrue(saved.all { it.groupId.isNotBlank() && it.groupId == saved.first().groupId })
    }

    @Test fun entrant_joiningOne_joinsBoth_andLeavingOne_leavesBoth() {
        val p = publishGroup()
        val account = call { auth.signUp("entrant-$tag@example.com", password) }
        val me = call { store.createProfile(person("Maya Chen", account.uid, "entrant-$tag@example.com", Role.ENTRANT), null) }
        val now = System.currentTimeMillis()
        call { LotteryGroups.joinAll(store, p.lotteries.first(), me, "Maya Chen", now) }
        p.lotteries.forEach { assertEquals(1, lottery(it.id).entrantCount) }
        assertEquals(2, call { store.observeEntriesForEntrant(me).first() }.size)

        call { LotteryGroups.leaveAll(store, p.lotteries.last(), me, now) }
        p.lotteries.forEach { assertEquals(0, lottery(it.id).entrantCount) }
        assertTrue(call { store.observeEntriesForEntrant(me).first() }.isEmpty())
    }
}
