/*
 * TrustedPhoneAccessRulesTest.kt (Rayner)
 *
 * Purpose: Who can read trusted-phone records, against the security rules on the local Firebase
 * emulators, never the production project (enabled only when FIRESTORE_EMULATOR and AUTH_EMULATOR
 * are set): a phone can look up its own record, people can list their own phones, but nobody can
 * list everyone's phones or someone else's.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 25, 2026). Prompt: "Write
 * emulator tests for who can read trusted-phone records." Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.UUID

/** Two people, each with a trusted phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class TrustedPhoneAccessRulesTest {
    private val tag = UUID.randomUUID().toString().take(6)
    private val pw = "campfire8"
    private fun step(name: String, ok: Boolean, detail: String = "") = assertTrue(if (detail.isBlank()) name else "$name — $detail", ok)
    private fun <T> call(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { withTimeout(30_000) { block() } }
    private fun <T> attempt(block: suspend () -> T): Result<T> = runCatching { call(block) }

    inner class Who(val name: String) {
        val db: FirebaseFirestore; val auth: FirebaseAuthStore; val store: FirestoreEventsStore
        lateinit var id: String
        val email = "${name.lowercase().replace(" ", "")}-$tag@example.com"
        init {
            val o = FirebaseOptions.Builder().setProjectId("demo-carbon-events").setApplicationId("1:1:android:1").setApiKey("emulator-only").build()
            val app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), o, "flow-${UUID.randomUUID()}")
            db = FirebaseFirestore.getInstance(app)
            val (h, p) = System.getenv("FIRESTORE_EMULATOR")!!.split(":"); db.useEmulator(h, p.toInt())
            db.firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
            val fa = FirebaseAuth.getInstance(app); val (ah, ap) = System.getenv("AUTH_EMULATOR")!!.split(":"); fa.useEmulator(ah, ap.toInt())
            auth = FirebaseAuthStore(fa); store = FirestoreEventsStore(db)
        }
        fun signUp(role: Role = Role.ENTRANT, device: String? = null): Who {
            val acc = call { auth.signUp(email, pw) }
            id = call { store.createProfile(UserProfile("", name, email, activeRole = role, authUid = acc.uid, createdAt = System.currentTimeMillis()),
                device?.let { TrustedDevice(it, "", "Pixel", System.currentTimeMillis()) }) }
            return this
        }
    }

    private fun denied(r: Result<*>) = (r.exceptionOrNull() as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED

    @Test fun peopleSeeTheirOwnPhones_butNobodyCanBrowseEveryonesPhones() {
        assumeTrue(System.getenv("FIRESTORE_EMULATOR") != null && System.getenv("AUTH_EMULATOR") != null)
        val maya = Who("Maya Chen").signUp(device = "dev-maya-$tag")
        val sam = Who("Sam Park").signUp(device = "dev-sam-$tag")
        val own = attempt { maya.store.trustedUserIdNow("dev-maya-$tag") }
        step("a phone looks up its own record", own.getOrNull() == maya.id, own.exceptionOrNull()?.message ?: "")
        val mine = attempt { maya.store.observeDevices(maya.id).first { it.isNotEmpty() } }
        step("Maya lists her own phones", mine.getOrNull()?.map { it.deviceId } == listOf("dev-maya-$tag"), mine.exceptionOrNull()?.message ?: "")
        val everyone = attempt { maya.db.collection("devices").get().await() }
        step("nobody can list every phone", denied(everyone), everyone.exceptionOrNull()?.message ?: "allowed!")
        val samsPhones = attempt { maya.db.collection("devices").whereEqualTo("userId", sam.id).get().await() }
        step("Maya can't list Sam's phones", denied(samsPhones), samsPhones.exceptionOrNull()?.message ?: "allowed!")
    }
}
