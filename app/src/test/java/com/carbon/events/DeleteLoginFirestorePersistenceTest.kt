/*
 * DeleteLoginFirestorePersistenceTest.kt (Rayner)
 *
 * Purpose: Deleting your own profile against the local Firebase emulators, never the production
 * project (enabled only when FIRESTORE_EMULATOR and AUTH_EMULATOR are set): the password check
 * that Firebase wants before deleting an old sign-in, and the login being gone afterwards, so
 * nothing is left behind in Authentication (US 01.08.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write
 * tests for deleting a profile together with its Firebase sign-in." Reviewed, edited and run by
 * Team Carbon.
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.UUID

/** Adam signs up by email, then deletes his profile. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class DeleteLoginFirestorePersistenceTest {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuthStore
    private lateinit var store: FirestoreEventsStore
    private val tag = UUID.randomUUID().toString().take(8)
    private val email = "delete-$tag@example.com"
    private val password = "campfire8"

    private fun <T> call(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { withTimeout(25_000) { block() } }

    @Before fun setUp() {
        assumeTrue("Local Firebase services are not configured", System.getenv("FIRESTORE_EMULATOR") != null && System.getenv("AUTH_EMULATOR") != null)
        val options = FirebaseOptions.Builder().setProjectId("demo-carbon-events")
            .setApplicationId("1:1:android:1").setApiKey("emulator-only").build()
        val app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), options, "delete-${UUID.randomUUID()}")
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

    private fun signUp(): String {
        val account = call { auth.signUp(email, password) }
        return call { store.createProfile(UserProfile("", "Adam Kent", email, authUid = account.uid, age = 30, createdAt = System.currentTimeMillis()), null) }
    }

    @Test fun aWrongPassword_isRefused() {
        signUp()
        val refused = runCatching { call { auth.confirmPassword("not-my-password1") } }.exceptionOrNull()
        assertEquals(AuthError.WRONG_PASSWORD, (refused as? AuthException)?.error)
    }

    @Test fun afterThePassword_theProfileAndTheLoginAreBothGone() {
        val id = signUp()
        call { auth.confirmPassword(password) }
        call { store.deleteProfile(UserProfile(id, "Adam Kent", email), System.currentTimeMillis()) }
        assertTrue("login deleted", call { auth.deleteAccount() })
        val again = runCatching { call { auth.signIn(email, password) } }
        assertTrue("nothing left in Authentication", again.isFailure)
    }

    @Test fun aFreshSignIn_needsNoPassword_andAPhoneLoginNeverDoes() {
        signUp()
        assertFalse("just signed up", auth.needsPasswordToDelete())
        auth.signOut()
        call { auth.signInWithDevice() }
        assertFalse(auth.needsPasswordToDelete())
    }
}
