/*
 * SignInSessionFirestorePersistenceTest.kt (Rayner)
 *
 * Purpose: Staying signed in, checked against the security rules on the local Firebase emulators,
 * never the production project (enabled only when FIRESTORE_EMULATOR and AUTH_EMULATOR are set):
 * the profile the app watches during sign-up is found once sign-up finishes, a phone's trust is
 * read from the server, and "Log out of all devices" removes every trusted phone and then marks
 * the profile so every open phone signs out (US 01.09.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write
 * tests for removing a trusted phone, logging out of all devices and finishing sign-up." Reviewed,
 * edited and run by Team Carbon.
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.UUID

/** One person signing up and then logging out of all their phones. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class SignInSessionFirestorePersistenceTest {
    private val tag = UUID.randomUUID().toString().take(8)
    private val password = "campfire8"

    private fun <T> call(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { withTimeout(25_000) { block() } }

    /** One phone with its own Firebase connection. */
    private class Phone {
        val db: FirebaseFirestore
        val auth: FirebaseAuthStore
        val store: FirestoreEventsStore

        init {
            val options = FirebaseOptions.Builder().setProjectId("demo-carbon-events")
                .setApplicationId("1:1:android:1").setApiKey("emulator-only").build()
            val app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), options, "session-${UUID.randomUUID()}")
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
    }

    @Before fun setUp() {
        assumeTrue("Local Firebase services are not configured", System.getenv("FIRESTORE_EMULATOR") != null && System.getenv("AUTH_EMULATOR") != null)
    }

    private fun person(name: String, uid: String, email: String, role: Role = Role.ENTRANT) =
        UserProfile("", name, email, activeRole = role, authUid = uid, age = 30, createdAt = System.currentTimeMillis())

    private fun trust(deviceId: String) = TrustedDevice(deviceId, "", "Google Pixel 8", System.currentTimeMillis())

    @Test fun profileWatchedDuringSignUp_isFoundOnceSignUpFinishes_andStaysFound() = runBlocking(Dispatchers.IO) {
        repeat(3) { round ->
            val phone = Phone()
            val email = "watch-$round-$tag@example.com"
            val account = withTimeout(25_000) { phone.auth.signUp(email, password) }
            // The app watches for the profile from Your details on, before it exists.
            var latest: UserProfile? = null
            val watching = launch { phone.store.observeProfileForAccount(account.uid).collect { latest = it } }
            delay(300)
            val id = withTimeout(25_000) { phone.store.createProfile(person("Parks Lodge", account.uid, email, Role.OWNER), trust("phone-$round-$tag")) }
            withTimeoutOrNull(10_000) { while (latest?.id != id) delay(100) }
            delay(2_000)
            watching.cancel()
            assertEquals("round $round: signed in after sign-up, not sent back to Welcome", id, latest?.id)
            assertEquals(Role.OWNER, latest?.activeRole)
        }
    }

    @Test fun phoneTrust_isReadFromTheServer() {
        val phone = Phone()
        val account = call { phone.auth.signUp("trust-$tag@example.com", password) }
        val id = call { phone.store.createProfile(person("Adam Kent", account.uid, "trust-$tag@example.com"), trust("pixel-$tag")) }
        assertEquals(id, call { phone.store.trustedUserIdNow("pixel-$tag") })
        call { phone.store.removeDevice("pixel-$tag") }
        assertNull(call { phone.store.trustedUserIdNow("pixel-$tag") })
        assertNull("a phone nobody trusted", call { phone.store.trustedUserIdNow("never-$tag") })
    }

    @Test fun logOutOfAllDevices_removesEveryTrustedPhone_thenMarksTheProfile() {
        val pixel = Phone()
        val email = "everywhere-$tag@example.com"
        val account = call { pixel.auth.signUp(email, password) }
        val id = call { pixel.store.createProfile(person("Adam Kent", account.uid, email), trust("pixel-$tag")) }
        call { pixel.store.trustDevice(TrustedDevice("tablet-$tag", id, "Galaxy Tab", System.currentTimeMillis())) }
        val before = call { pixel.store.observeProfile(id).first() }!!.signedOutAt

        call { pixel.store.removeAllDevices(id) }
        call { pixel.store.signOutEverywhere(id, System.currentTimeMillis()) }

        assertTrue(call { pixel.db.collection(Paths.DEVICES).whereEqualTo("userId", id).get(Source.SERVER).await() }.isEmpty)
        val marked = call { pixel.db.collection(Paths.USERS).document(id).get(Source.SERVER).await() }.toProfile().signedOutAt
        assertTrue("the profile is marked so open phones sign out", marked > before)

        // Editing the profile later keeps the mark.
        val profile = call { pixel.store.observeProfile(id).first() }!!
        call { pixel.store.saveProfile(profile.copy(phone = "+1 780 555 0142")) }
        assertEquals(marked, call { pixel.db.collection(Paths.USERS).document(id).get(Source.SERVER).await() }.toProfile().signedOutAt)
    }

    @Test fun nobodyElse_canLogYouOutEverywhere() {
        val adam = Phone()
        val adamAccount = call { adam.auth.signUp("adam-$tag@example.com", password) }
        val adamId = call { adam.store.createProfile(person("Adam Kent", adamAccount.uid, "adam-$tag@example.com"), null) }
        val maya = Phone()
        val mayaAccount = call { maya.auth.signUp("maya-$tag@example.com", password) }
        call { maya.store.createProfile(person("Maya Chen", mayaAccount.uid, "maya-$tag@example.com"), null) }

        val refused = runCatching { call { maya.store.signOutEverywhere(adamId, System.currentTimeMillis()) } }
        assertTrue("Maya can't mark Adam's profile", refused.isFailure)
    }
}
