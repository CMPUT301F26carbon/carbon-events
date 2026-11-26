/*
 * NameChangeFirestorePersistenceTest.kt (Suvir)
 *
 * Purpose: A new name reaching the copies other screens show (B12) against the security rules on the
 * local Firebase emulators, never the production project (enabled only when FIRESTORE_EMULATOR and
 * AUTH_EMULATOR are set): a guest's new name goes on their waitlist entry and conversation, an
 * owner's on their campsite and conversation, and nobody can put a name that isn't theirs there.
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
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.tasks.await

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class NameChangeFirestorePersistenceTest {
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

    @Test fun newNames_reachTheCopies_andOnlyRealNamesAreAllowed() {
        assumeTrue(System.getenv("FIRESTORE_EMULATOR") != null && System.getenv("AUTH_EMULATOR") != null)
        val now = System.currentTimeMillis()
        val owner = Who("Parks Lodge").signUp(Role.OWNER, "dev-owner-$tag")
        val site = call { owner.store.createCampsite(Campsite("", owner.id, owner.name, "Bow River $tag", "Banff, AB", maxOccupancy = 6, createdAt = now)) }
        val lotId = call { owner.store.createLottery(Lottery("", site, "Bow River $tag", owner.id, now - 1_000, now + 3_600_000, winnerCount = 1)) }
        val maya = Who("Maya Chen").signUp(device = "dev-maya-$tag")
        call { maya.store.joinWaitlist(lotId, maya.id, maya.name, System.currentTimeMillis()) }
        val lottery = call { owner.store.observeLottery(lotId).first() }!!
        call { owner.store.sendAnnouncement(lottery, Audience.ALL, "Welcome!", System.currentTimeMillis()) }
        val chatId = Chat.idFor(site, maya.id)

        val entry = maya.db.collection("campsites").document(site).collection("lotteries").document(Paths.keyOf(lotId)).collection("entrants").document(maya.id)
        step("nobody can put a made-up name on their entry", denied(attempt { entry.update("userName", "Somebody Else").await() }))

        val profile = call { maya.store.observeProfile(maya.id).first() }!!
        call { maya.store.saveProfile(profile.copy(name = "Maya Chen-Lee")) }
        val renamed = attempt { maya.store.renameEverywhere(maya.id, "Maya Chen-Lee") }
        step("the guest's new name goes everywhere", renamed.isSuccess, renamed.exceptionOrNull()?.message ?: "")
        step("on her waitlist entry", call { owner.store.observeEntries(lotId).first() }.single().entrantName == "Maya Chen-Lee")
        step("and in her conversation", call { owner.store.observeChat(chatId).first { it?.entrantName == "Maya Chen-Lee" } } != null)

        val ownerProfile = call { owner.store.observeProfile(owner.id).first() }!!
        call { owner.store.saveProfile(ownerProfile.copy(name = "Parks Lodge & Co")) }
        call { owner.store.renameEverywhere(owner.id, "Parks Lodge & Co") }
        step("the owner's new name is on their campsite", call { maya.store.observeCampsite(site).first { it?.ownerName == "Parks Lodge & Co" } } != null)
        step("and in the conversation", call { maya.store.observeChat(chatId).first { it?.ownerName == "Parks Lodge & Co" } } != null)
        step("a guest can't rename the owner in their conversation",
            denied(attempt { maya.db.collection("chats").document(chatId).update("ownerName", "Someone").await() }))
    }
}
