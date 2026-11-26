/*
 * StayPeriodsFirestorePersistenceTest.kt (Vaibhav)
 *
 * Purpose: One campsite's lotteries never offering the same nights, on the local Firebase
 * emulators, never the production project (enabled only when FIRESTORE_EMULATOR and AUTH_EMULATOR
 * are set): a second lottery whose stays overlap the first is refused, one that starts as the
 * first ends is fine, and an edit can't move its stays into the other's.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 25, 2026). Prompt: "Write emulator tests for keeping lottery stay periods apart."
 * Reviewed, edited and run by Team Carbon.
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

/** An owner with one campsite scheduling two lotteries. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class StayPeriodsFirestorePersistenceTest {
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

    @Test fun aCampsitesLotteries_cantOfferTheSameNights() {
        assumeTrue(System.getenv("FIRESTORE_EMULATOR") != null && System.getenv("AUTH_EMULATOR") != null)
        val now = System.currentTimeMillis()
        val today = dayOf(now)
        val owner = Who("Parks Lodge").signUp(Role.OWNER, "dev-owner-$tag")
        val site = call { owner.store.createCampsite(Campsite("", owner.id, "Parks Lodge", "Bow River $tag", "Banff, AB", maxOccupancy = 6, createdAt = now)) }
        fun lottery(from: Long, until: Long) = Lottery("", site, "Bow River $tag", owner.id, now - 1_000, now + 86_400_000L, 2, bookingStartDay = from, bookingEndDay = until)
        val first = attempt { owner.store.createLottery(lottery(today + 10, today + 20)) }
        step("the first lottery is saved", first.isSuccess, first.exceptionOrNull()?.message ?: "")
        val overlapping = attempt { owner.store.createLottery(lottery(today + 15, today + 25)) }
        step("a second lottery sharing nights is refused", overlapping.exceptionOrNull() is LotteryEditException, overlapping.exceptionOrNull()?.toString() ?: "allowed!")
        val after = attempt { owner.store.createLottery(lottery(today + 20, today + 30)) }
        step("one starting as the first ends is fine", after.isSuccess, after.exceptionOrNull()?.message ?: "")
        val moved = attempt {
            val second = owner.store.observeLottery(after.getOrThrow()).first { it != null }!!
            owner.store.updateLottery(second.copy(bookingStartDay = today + 18), System.currentTimeMillis())
        }
        step("an edit can't move its stays into the other lottery's", moved.exceptionOrNull() is LotteryEditException, moved.exceptionOrNull()?.toString() ?: "allowed!")
        val saved = call { owner.store.observeLotteriesForCampsite(site).first { it.size == 2 } }
        step("only the two lotteries that don't overlap are saved", saved.map { it.bookingStartDay }.toSet() == setOf(today + 10, today + 20))
    }
}
