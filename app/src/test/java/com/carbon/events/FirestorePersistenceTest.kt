/*
 * FirestorePersistenceTest.kt (Vaibhav)
 */
package com.carbon.events

import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
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

/**
 * Lab 5 persistence contracts on local services, never the production Firebase project.
 * Enabled only with FIRESTORE_EMULATOR and AUTH_EMULATOR pointing at local emulators.
 * A separate Firebase client and server-only reads prevent cache state from faking persistence.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class FirestorePersistenceTest {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuthStore
    private lateinit var store: FirestoreEventsStore
    private val tag = UUID.randomUUID().toString().take(8)
    private val email = "owner-$tag@example.com"
    private val password = "campfire8"

    private fun <T> call(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { withTimeout(25_000) { block() } }

    private fun client(): Pair<FirebaseFirestore, FirebaseAuthStore> {
        val options = FirebaseOptions.Builder().setProjectId("demo-carbon-events")
            .setApplicationId("1:1:android:1").setApiKey("emulator-only").build()
        val app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), options, "test-${UUID.randomUUID()}")
        val firestore = FirebaseFirestore.getInstance(app)
        val (host, port) = requireNotNull(System.getenv("FIRESTORE_EMULATOR")).split(":")
        firestore.useEmulator(host, port.toInt())
        firestore.firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        val firebaseAuth = FirebaseAuth.getInstance(app)
        val (authHost, authPort) = requireNotNull(System.getenv("AUTH_EMULATOR")).split(":")
        firebaseAuth.useEmulator(authHost, authPort.toInt())
        return firestore to FirebaseAuthStore(firebaseAuth)
    }

    @Before fun setUp() {
        assumeTrue("Local Firebase services are not configured", System.getenv("FIRESTORE_EMULATOR") != null && System.getenv("AUTH_EMULATOR") != null)
        val client = client(); db = client.first; auth = client.second
        store = FirestoreEventsStore(db)
    }

    private fun owner(): String {
        val account = call { auth.signUp(email, password) }
        return call { store.createProfile(UserProfile("", "Parks Lodge", email, activeRole = Role.OWNER, authUid = account.uid, createdAt = System.currentTimeMillis()), null) }
    }

    @Test fun createEditDelete_remainCorrectOnANewClient() {
        val owner = owner()
        val id = call { store.createCampsite(Campsite("", owner, "Parks Lodge", "Bow River Sites", "Banff, AB", maxOccupancy = 6, pricePerNight = 65, createdAt = System.currentTimeMillis())) }
        val saved = call { db.collection(Paths.CAMPSITES).document(id).get(Source.SERVER).await().toCampsite() }
        call { store.updateCampsite(saved.copy(name = "Bow River North", maxOccupancy = 8)) }

        val (freshDb, freshAuth) = client()
        call { freshAuth.signIn(email, password) }
        val freshStore = FirestoreEventsStore(freshDb)
        val reloaded = call { freshStore.observeCampsite(id).first { it?.name == "Bow River North" } }!!
        assertEquals(8, reloaded.maxOccupancy)
        assertEquals(owner, reloaded.ownerId)

        call { store.deleteCampsite(id) }
        assertFalse(call { freshDb.collection(Paths.CAMPSITES).document(id).get(Source.SERVER).await().exists() })
        assertNull(call { freshStore.observeCampsite(id).first { it == null } })
    }

    @Test fun unrelatedEntrant_cannotDeleteOwnersListing() {
        val owner = owner()
        val id = call { store.createCampsite(Campsite("", owner, "Parks Lodge", "Bow River Sites", "Banff, AB", maxOccupancy = 6, createdAt = System.currentTimeMillis())) }
        auth.signOut()
        val account = call { auth.signUp("entrant-$tag@example.com", password) }
        call { store.createProfile(UserProfile("", "Maya Chen", "entrant-$tag@example.com", authUid = account.uid, createdAt = System.currentTimeMillis()), null) }
        try {
            call { db.collection(Paths.CAMPSITES).document(id).delete().await() }
            fail("Another user's campsite must not be deletable")
        } catch (e: FirebaseFirestoreException) {
            // A timeout or transport error must not count as a successful authorization test.
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
        assertTrue(call { db.collection(Paths.CAMPSITES).document(id).get(Source.SERVER).await().exists() })
    }
}
