/*
 * FirestoreEventsStoreTest.kt (Vaibhav)
 *
 * Purpose: Tests for FirestoreEventsStore.kt against the security rules on the local Firebase
 * emulators, never the production project (enabled only when FIRESTORE_EMULATOR and
 * AUTH_EMULATOR are set): an owner's campsite keeps every detail through the server
 * (US 02.01.01, US 02.01.03), shows in their list, can be edited, and goes when deleted. The class name ends in FirestorePersistenceTest so the
 * emulator job in CI runs it with the other emulator tests.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write
 * emulator tests for the Firestore store." Reviewed, edited and run by Team Carbon.
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.UUID

/** An owner, Parks Lodge, and their campsite on the emulators. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class FirestoreEventsStoreFirestorePersistenceTest {
    private lateinit var store: FirestoreEventsStore
    private lateinit var auth: FirebaseAuthStore
    private val tag = UUID.randomUUID().toString().take(8)

    private fun <T> call(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { withTimeout(25_000) { block() } }

    @Before fun setUp() {
        assumeTrue("Local Firebase services are not configured", System.getenv("FIRESTORE_EMULATOR") != null && System.getenv("AUTH_EMULATOR") != null)
        val options = FirebaseOptions.Builder().setProjectId("demo-carbon-events").setApplicationId("1:1:android:1").setApiKey("emulator-only").build()
        val app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), options, "store-${UUID.randomUUID()}")
        val db = FirebaseFirestore.getInstance(app)
        val (host, port) = requireNotNull(System.getenv("FIRESTORE_EMULATOR")).split(":")
        db.useEmulator(host, port.toInt())
        db.firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        val firebaseAuth = FirebaseAuth.getInstance(app)
        val (authHost, authPort) = requireNotNull(System.getenv("AUTH_EMULATOR")).split(":")
        firebaseAuth.useEmulator(authHost, authPort.toInt())
        auth = FirebaseAuthStore(firebaseAuth)
        store = FirestoreEventsStore(db)
    }

    private fun owner(): String {
        val account = call { auth.signUp("owner-$tag@example.com", "campfire8") }
        return call { store.createProfile(UserProfile("", "Parks Lodge", "owner-$tag@example.com", activeRole = Role.OWNER, authUid = account.uid, age = 40, createdAt = System.currentTimeMillis()), null) }
    }

    private fun site(ownerId: String) = Campsite(
        "", ownerId, "Parks Lodge", "Bow River Sites $tag", "Banff, AB", 51.17, -115.57, 6, "Fire pits, potable water",
        "Riverside sites.", listOf("Riverside", "Pet friendly"), 72, createdAt = System.currentTimeMillis(),
        checkInTime = "3:00 PM", checkOutTime = "11:00 AM", minNights = 2, maxNights = 5, rules = "Quiet hours 10 PM – 7 AM",
    )

    @Test fun aCampsite_keepsEveryDetail_throughTheServer() {
        val ownerId = owner()
        val id = call { store.createCampsite(site(ownerId)) }
        val back = call { store.observeCampsite(id).first() }!!
        assertEquals(site(ownerId).copy(id = id, createdAt = back.createdAt), back)
    }

    @Test fun anOwnersCampsites_listTheirs_andADeletedOneGoes() {
        val ownerId = owner()
        val id = call { store.createCampsite(site(ownerId)) }
        assertEquals(listOf(id), call { store.observeCampsitesByOwner(ownerId).first() }.map { it.id })
        call { store.deleteCampsite(id) }
        assertNull(call { store.observeCampsite(id).first() })
    }

    @Test fun editingACampsite_savesTheChanges() {
        val ownerId = owner()
        val id = call { store.createCampsite(site(ownerId)) }
        val saved = call { store.observeCampsite(id).first() }!!
        call { store.updateCampsite(saved.copy(pricePerNight = 80, rules = "Pets on a leash", maxNights = 7)) }
        val back = call { store.observeCampsite(id).first() }!!
        assertEquals(80, back.pricePerNight)
        assertEquals("Pets on a leash", back.rules)
        assertEquals(7, back.maxNights)
    }
}
