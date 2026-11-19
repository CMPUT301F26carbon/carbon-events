/*
 * TrustedDeviceFirestorePersistenceTest.kt (Rayner)
 *
 * Purpose: Sign-up and "Trust this device" against the security rules on the local Firebase
 * emulators, never the production project (enabled only when FIRESTORE_EMULATOR and
 * AUTH_EMULATOR are set): a phone another account trusted, two phone-only sign-ups on one
 * phone, and a login whose earlier profile is gone (US 01.09.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * tests for trusting a phone that another account already trusted." Reviewed, edited and run by Team Carbon.
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

/** Who a phone is trusted for, checked on the server after each sign-up. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class TrustedDeviceFirestorePersistenceTest {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuthStore
    private lateinit var store: FirestoreEventsStore
    private val tag = UUID.randomUUID().toString().take(8)
    private val phone = "phone-$tag"
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

    private fun trust() = TrustedDevice(phone, "", "Google Pixel 8", System.currentTimeMillis())
    private fun person(name: String, uid: String, email: String = "", role: Role = Role.ENTRANT) =
        UserProfile("", name, email, activeRole = role, authUid = uid, age = 30, createdAt = System.currentTimeMillis())
    private fun phoneBelongsTo(): String? =
        call { db.collection(Paths.DEVICES).document(phone).get(Source.SERVER).await().getString("userId") }

    @Test fun emailSignUp_canTrustAPhoneAnotherAccountTrusted() {
        val first = call { auth.signUp("first-$tag@example.com", password) }
        val firstId = call { store.createProfile(person("Maya Chen", first.uid, "first-$tag@example.com"), trust()) }
        assertEquals(firstId, phoneBelongsTo())
        auth.signOut()

        val second = call { auth.signUp("second-$tag@example.com", password) }
        val secondId = call { store.createProfile(person("Parks Lodge", second.uid, "second-$tag@example.com", Role.OWNER), trust()) }
        assertEquals(secondId, phoneBelongsTo())
    }

    @Test fun phoneOnlySignUp_twiceOnOnePhone_bothSucceed() {
        val first = call { auth.signInWithDevice() }
        call { store.createProfile(person("Maya Chen", first.uid), trust()) }
        // The first person's phone login is still here when the second person starts.
        val second = call { auth.signInWithDevice() }
        assertNotEquals("the second person needs their own login", first.uid, second.uid)
        val secondId = call { store.createProfile(person("Liam Okafor", second.uid), trust()) }
        assertEquals(secondId, phoneBelongsTo())
    }

    @Test fun signInWithRememberMe_canTakeBackAPhoneAnotherAccountTrusted() {
        val owner = call { auth.signUp("owner-$tag@example.com", password) }
        val ownerId = call { store.createProfile(person("Parks Lodge", owner.uid, "owner-$tag@example.com", Role.OWNER), trust()) }
        auth.signOut()
        val entrant = call { auth.signInWithDevice() }
        call { store.createProfile(person("Maya Chen", entrant.uid), trust()) }
        auth.signOut()

        call { auth.signIn("owner-$tag@example.com", password) }
        call { store.trustDevice(TrustedDevice(phone, ownerId, "Google Pixel 8", System.currentTimeMillis())) }
        assertEquals(ownerId, phoneBelongsTo())
        assertEquals(1, call { store.observeDevices(ownerId).first() }.size)
    }

    @Test fun sameAccount_canTrustItsPhoneAgain() {
        val account = call { auth.signUp("again-$tag@example.com", password) }
        val id = call { store.createProfile(person("Sofia Rossi", account.uid, "again-$tag@example.com"), trust()) }
        call { store.trustDevice(TrustedDevice(phone, id, "Google Pixel 8 Pro", System.currentTimeMillis())) }
        assertEquals(id, phoneBelongsTo())
        assertEquals("Google Pixel 8 Pro", call { store.observeDevices(id).first() }.single().model)
    }

    @Test fun loginWhoseProfileIsGone_canSignUpAgain() {
        val account = call { auth.signUp("back-$tag@example.com", password) }
        val oldId = call { store.createProfile(person("Parks Lodge", account.uid, "back-$tag@example.com", Role.OWNER), trust()) }
        // The profile is removed but the login and its old link stay behind.
        call { db.collection(Paths.USERS).document(oldId).delete().await() }

        val newId = call { store.createProfile(person("Parks Lodge", account.uid, "back-$tag@example.com", Role.OWNER), trust()) }
        assertNotEquals(oldId, newId)
        assertEquals(newId, phoneBelongsTo())
        assertEquals(newId, call { db.collection(Paths.ACCOUNTS).document(account.uid).get(Source.SERVER).await().getString("userId") })
    }

    @Test fun finishingSignUpTwice_keepsTheOneProfile() {
        val account = call { auth.signUp("twice-$tag@example.com", password) }
        val id = call { store.createProfile(person("Noah Patel", account.uid, "twice-$tag@example.com"), null) }
        assertEquals(id, call { store.createProfile(person("Noah Patel", account.uid, "twice-$tag@example.com"), trust()) })
        assertEquals(id, phoneBelongsTo())
    }
}
