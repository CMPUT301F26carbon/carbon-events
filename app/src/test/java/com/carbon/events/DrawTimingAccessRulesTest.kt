/*
 * DrawTimingAccessRulesTest.kt (Rayner)
 *
 * Purpose: When a lottery can be drawn and joined, against the security rules on the local
 * Firebase emulators, never the production project (enabled only when FIRESTORE_EMULATOR and
 * AUTH_EMULATOR are set): the draw goes by the server's clock, so a phone with its clock set ahead
 * can't draw early, a drawn lottery can't be reopened, and nobody joins after the draw.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 25, 2026). Prompt: "Write
 * emulator tests for when a lottery can be drawn and joined." Reviewed, edited and run by Team
 * Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
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

/** Parks Lodge running lotteries, and Maya trying to join them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class DrawTimingAccessRulesTest {
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

    private fun Who.lotteryDoc(lotteryId: String) = db.collection("campsites").document(Paths.campsiteOf(lotteryId)).collection("lotteries").document(Paths.keyOf(lotteryId))

    @Test fun aPhoneWithItsClockAhead_cantDrawBeforeRegistrationCloses() {
        assumeTrue(System.getenv("FIRESTORE_EMULATOR") != null && System.getenv("AUTH_EMULATOR") != null)
        val now = System.currentTimeMillis()
        val owner = Who("Parks Lodge").signUp(Role.OWNER, "dev-owner-$tag")
        val site = call { owner.store.createCampsite(Campsite("", owner.id, "Parks Lodge", "Bow River $tag", "Banff, AB", maxOccupancy = 6, createdAt = now)) }
        val lotteryId = call { owner.store.createLottery(Lottery("", site, "Bow River $tag", owner.id, now - 1_000, now + 3_600_000L, 2)) }
        val early = attempt { owner.store.runDrawIfDue(lotteryId, now + 7_200_000L) }
        step("drawing with the phone's clock two hours ahead is refused", denied(early), early.exceptionOrNull()?.message ?: "allowed!")
        val direct = attempt { owner.lotteryDoc(lotteryId).update("status", "drawn").await() }
        step("marking it drawn directly is refused too", denied(direct), direct.exceptionOrNull()?.message ?: "allowed!")
        val lottery = call { owner.store.observeLottery(lotteryId).first { it != null }!! }
        step("the lottery is still open", lottery.status == LotteryStatus.OPEN)
    }

    @Test fun aDrawnLottery_staysDrawn_andNobodyJoinsAfterTheDraw() {
        assumeTrue(System.getenv("FIRESTORE_EMULATOR") != null && System.getenv("AUTH_EMULATOR") != null)
        val now = System.currentTimeMillis()
        val owner = Who("Parks Lodge").signUp(Role.OWNER, "dev-owner-$tag")
        val maya = Who("Maya Chen").signUp()
        val sam = Who("Sam Park").signUp()
        val site = call { owner.store.createCampsite(Campsite("", owner.id, "Parks Lodge", "Bow River $tag", "Banff, AB", maxOccupancy = 6, createdAt = now)) }
        val closes = now + 4_000L
        val lotteryId = call { owner.store.createLottery(Lottery("", site, "Bow River $tag", owner.id, now - 1_000, closes, 2)) }
        call { maya.store.joinWaitlist(lotteryId, maya.id, maya.name, now) }
        while (System.currentTimeMillis() < closes + 1_500) Thread.sleep(250)
        val drawn = attempt { owner.store.runDrawIfDue(lotteryId, System.currentTimeMillis()) }
        step("the owner draws once registration has closed", drawn.getOrNull() == true, drawn.exceptionOrNull()?.message ?: "")
        val reopen = attempt { owner.lotteryDoc(lotteryId).update("status", "open").await() }
        step("a drawn lottery can't be reopened", denied(reopen), reopen.exceptionOrNull()?.message ?: "allowed!")
        val later = Timestamp(java.util.Date(System.currentTimeMillis() + 3_600_000L))
        val extended = attempt { owner.lotteryDoc(lotteryId).update("closesAt", later).await() }
        step("moving the closing time ahead after the draw is saved", extended.isSuccess, extended.exceptionOrNull()?.message ?: "")
        val late = attempt {
            val entry = WaitlistEntry(lotteryId, sam.id, sam.name, System.currentTimeMillis(), campsiteId = site, campsiteName = "Bow River $tag", ownerId = owner.id)
            val ref = sam.lotteryDoc(lotteryId)
            sam.db.batch().set(ref.collection("entrants").document(sam.id), entry.toMap()).update(ref, "entrantCount", FieldValue.increment(1)).commit().await()
        }
        step("but nobody can join once winners are drawn", denied(late), late.exceptionOrNull()?.message ?: "allowed!")
    }
}
