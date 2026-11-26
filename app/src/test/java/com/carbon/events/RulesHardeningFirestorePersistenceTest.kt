/*
 * RulesHardeningFirestorePersistenceTest.kt (Suvir)
 *
 * Purpose: The security rules against made-up rating averages (C1) and owners picking winners by
 * hand (C5), on the local Firebase emulators, never the production project (enabled only when
 * FIRESTORE_EMULATOR and AUTH_EMULATOR are set). A guest's rating moves the average only by their
 * own stars; an owner's phone can still draw, re-offer a given-back spot and reject a reservation,
 * but can't mark someone a winner outside those steps or change a guest's booked nights.
 *
 * Outstanding issues: the rules can't check that a draw was random; that needs a server function.
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
import com.google.firebase.firestore.DocumentReference

/** An owner with a lottery, entrants, and a guest who stayed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class RulesHardeningFirestorePersistenceTest {
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

    private fun entry(db: com.google.firebase.firestore.FirebaseFirestore, lotteryId: String, userId: String): DocumentReference =
        db.collection("campsites").document(Paths.campsiteOf(lotteryId)).collection("lotteries").document(Paths.keyOf(lotteryId)).collection("entrants").document(userId)

    /** An owner's campsite with a lottery that closes in 8 s; [names] join it. */
    private fun lotteryWith(vararg names: String, winners: Int = 1): Triple<Who, String, List<Who>> {
        val now = System.currentTimeMillis(); val today = dayOf(now)
        val owner = Who("Parks Lodge").signUp(Role.OWNER, "dev-owner-$tag")
        val site = call { owner.store.createCampsite(Campsite("", owner.id, owner.name, "Bow River $tag", "Banff, AB", maxOccupancy = 6, createdAt = now)) }
        val lotId = call { owner.store.createLottery(Lottery("", site, "Bow River $tag", owner.id, now - 1_000, now + 8_000, winnerCount = winners, bookingStartDay = today + 2, bookingEndDay = today + 30)) }
        val people = names.map { n -> Who(n).signUp(device = "dev-${n.substringBefore(' ').lowercase()}-$tag").also { w -> call { w.store.joinWaitlist(lotId, w.id, w.name, System.currentTimeMillis()) } } }
        Thread.sleep(maxOf(0, now + 9_000 - System.currentTimeMillis()))
        return Triple(owner, lotId, people)
    }

    @Test fun anOwner_cantPickWinnersByHand_butTheAppsStepsStillWork() {
        assumeTrue(System.getenv("FIRESTORE_EMULATOR") != null && System.getenv("AUTH_EMULATOR") != null)
        val (owner, lotId, people) = lotteryWith("Maya Chen", "Adam Kent", "Lee Park", winners = 1)
        step("before the draw, the owner can't mark someone a winner",
            denied(attempt { entry(owner.db, lotId, people[0].id).update("status", "won").await() }))
        step("the owner's phone draws", call { owner.store.runDrawIfDue(lotId, System.currentTimeMillis()) })
        val entries = call { owner.store.observeEntries(lotId).first { l -> l.none { it.status == EntryStatus.WAITING } } }
        val winner = entries.single { it.status == EntryStatus.WON }
        val loser = entries.first { it.status == EntryStatus.LOST }
        step("after the draw, the owner can't make someone not selected a winner",
            denied(attempt { entry(owner.db, lotId, loser.entrantId).update(mapOf("status" to "won")).await() }))
        step("nor change a winner's booked nights",
            denied(attempt { entry(owner.db, lotId, winner.entrantId).update(mapOf("checkIn" to "2030-01-01", "checkOut" to "2030-01-05")).await() }))
        val rejected = attempt { owner.store.rejectReservation(lotId, winner.entrantId, "Fire ban", System.currentTimeMillis()) }
        step("the owner can reject a reservation", rejected.isSuccess, rejected.exceptionOrNull()?.message ?: "")
        val filled = attempt { owner.store.fillFreeSpots(lotId, System.currentTimeMillis()) }
        step("and the given-back spot goes to someone not selected", filled.getOrNull() == 1, filled.exceptionOrNull()?.message ?: "filled ${filled.getOrNull()}")
        step("but only one: with no spots free, nobody else can be made a winner", denied(attempt {
            val still = call { owner.store.observeEntries(lotId).first() }.first { it.status == EntryStatus.LOST }
            entry(owner.db, lotId, still.entrantId).update(mapOf("status" to "won")).await()
        }))
    }

    @Test fun aGuest_cantMakeUpTheRatingAverage() {
        assumeTrue(System.getenv("FIRESTORE_EMULATOR") != null && System.getenv("AUTH_EMULATOR") != null)
        val (owner, lotId, people) = lotteryWith("Maya Chen")
        val maya = people.single()
        call { owner.store.runDrawIfDue(lotId, System.currentTimeMillis()) }
        call { maya.store.observeEntriesForEntrant(maya.id).first { l -> l.any { it.status == EntryStatus.WON } } }
        val today = dayOf(System.currentTimeMillis())
        call { maya.store.bookDates(lotId, maya.id, today + 3, today + 5, System.currentTimeMillis(), 2, true) }
        val site = Paths.campsiteOf(lotId)
        val rated = attempt { maya.store.rate(Rating(site, maya.id, maya.name, 2, "Windy.", lotId, System.currentTimeMillis())) }
        step("rating moves the average by her own stars", rated.isSuccess, rated.exceptionOrNull()?.message ?: "")
        val campsite = maya.db.collection("campsites").document(site)
        step("she can't set a made-up average", denied(attempt { campsite.update(mapOf("rating" to 5.0)).await() }))
        step("nor a made-up count", denied(attempt { campsite.update(mapOf("rating" to 2.0, "ratingCount" to 1000)).await() }))
        val changed = attempt { maya.store.rate(Rating(site, maya.id, maya.name, 4, "Calmer the next day.", lotId, System.currentTimeMillis())) }
        step("changing her rating moves it honestly", changed.isSuccess, changed.exceptionOrNull()?.message ?: "")
        val after = call { maya.store.observeCampsite(site).first { it?.rating == 4.0 } }!!
        step("still one rating, now 4 stars", after.ratingCount == 1)
    }
}
