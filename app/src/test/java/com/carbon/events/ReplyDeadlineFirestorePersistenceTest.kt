/*
 * ReplyDeadlineFirestorePersistenceTest.kt (Vaibhav)
 *
 * Purpose: The time a winner has to book, on the local Firebase emulators, never the production
 * project (enabled only when FIRESTORE_EMULATOR and AUTH_EMULATOR are set): the owner's phone gives
 * back the spot of a winner who didn't book within 48 hours, tells them, and offers the spot to
 * the next person, and a winner still inside the window keeps it.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 25, 2026). Prompt: "Write
 * emulator tests for the deadline a lottery winner has to book by." Reviewed, edited and run by
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

/** Parks Lodge running a lottery with one winner, who never books. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class ReplyDeadlineFirestorePersistenceTest {
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

    /** A lottery at a new campsite that closes in a few seconds, with [guests] on its waiting list. */
    private fun closedLottery(owner: Who, winners: Int, vararg guests: Who): String {
        val now = System.currentTimeMillis()
        val site = call { owner.store.createCampsite(Campsite("", owner.id, "Parks Lodge", "Bow River $tag", "Banff, AB", maxOccupancy = 6, createdAt = now)) }
        val closes = now + 4_000L
        val lotteryId = call { owner.store.createLottery(Lottery("", site, "Bow River $tag", owner.id, now - 1_000, closes, winners)) }
        guests.forEach { g -> call { g.store.joinWaitlist(lotteryId, g.id, g.name, System.currentTimeMillis()) } }
        while (System.currentTimeMillis() < closes + 1_500) Thread.sleep(250)
        return lotteryId
    }

    @Test fun aWinnerWhoDoesntBookIn48Hours_givesTheSpotToTheNextPerson() {
        assumeTrue(System.getenv("FIRESTORE_EMULATOR") != null && System.getenv("AUTH_EMULATOR") != null)
        val owner = Who("Parks Lodge").signUp(Role.OWNER, "dev-owner-$tag")
        val guests = listOf(Who("Maya Chen").signUp(), Who("Sam Park").signUp())
        val lotteryId = closedLottery(owner, 1, *guests.toTypedArray())
        call { owner.store.runDrawIfDue(lotteryId, System.currentTimeMillis()) }
        val drawn = call { owner.store.observeEntries(lotteryId).first { list -> list.none { it.status == EntryStatus.WAITING } } }
        val winner = guests.single { g -> drawn.single { it.entrantId == g.id }.status == EntryStatus.WON }
        val other = guests.single { it != winner }
        step("the winner's selection time is saved", drawn.single { it.entrantId == winner.id }.wonAt != null)

        val early = attempt { owner.store.expireUnbookedWinners(lotteryId, System.currentTimeMillis() + 47 * HOUR) }
        step("inside 48 hours the winner keeps the spot", early.getOrNull() == 0, early.exceptionOrNull()?.message ?: "${early.getOrNull()}")

        val late = attempt { owner.store.expireUnbookedWinners(lotteryId, System.currentTimeMillis() + 49 * HOUR) }
        step("after 48 hours the spot is given back", late.getOrNull() == 1, late.exceptionOrNull()?.message ?: "${late.getOrNull()}")
        val filled = attempt { owner.store.fillFreeSpots(lotteryId, System.currentTimeMillis()) }
        step("and offered to the next person", filled.getOrNull() == 1, filled.exceptionOrNull()?.message ?: "${filled.getOrNull()}")
        val after = call { owner.store.observeEntries(lotteryId).first { list -> list.any { it.entrantId == other.id && it.status == EntryStatus.WON } } }
        step("the first winner is marked declined", after.single { it.entrantId == winner.id }.status == EntryStatus.DECLINED)
        val told = call { winner.store.observeNotifications(winner.id).first { list -> list.any { it.title.contains("went to someone else") } } }
        step("and is told why", told.isNotEmpty())
    }

    private companion object { const val HOUR = 3_600_000L }
}
