/*
 * CampsiteRemovalFirestorePersistenceTest.kt (Suvir)
 *
 * Purpose: An owner deleting their own campsite against the security rules on the local Firebase
 * emulators, never the production project (enabled only when FIRESTORE_EMULATOR and AUTH_EMULATOR
 * are set): a guest with an upcoming, rated stay and someone not selected; the owner deletes the
 * campsite with its lottery, waitlist and ratings; the guest is told and the other entrant isn't;
 * nobody else can delete it.
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

/** An owner, a guest with an upcoming stay, someone not selected, and a stranger. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class CampsiteRemovalFirestorePersistenceTest {
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

    private fun bypassPatch(path: String, json: String) {
        val (h, p) = System.getenv("FIRESTORE_EMULATOR")!!.split(":")
        val name = "projects/demo-carbon-events/databases/(default)/documents/$path"
        val fields = json.substringAfter("\"fields\":").removeSuffix("}")
        val body = """{"writes":[{"update":{"name":"$name","fields":$fields},"updateMask":{"fieldPaths":["isAdmin"]}}]}"""
        val c = URL("http://$h:$p/v1/projects/demo-carbon-events/databases/(default)/documents:commit").openConnection(java.net.Proxy.NO_PROXY) as HttpURLConnection
        c.requestMethod = "POST"; c.setRequestProperty("Authorization", "Bearer owner"); c.setRequestProperty("Content-Type", "application/json"); c.doOutput = true
        c.outputStream.use { it.write(body.toByteArray()) }
        check(c.responseCode == 200) { "bypass ${c.responseCode} ${c.errorStream?.bufferedReader()?.readText()}" }
    }

    private fun denied(r: Result<*>) = (r.exceptionOrNull() as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED

    @Test fun anOwnerDeletesTheirCampsite_andItsGuestsAreTold() {
        assumeTrue(System.getenv("FIRESTORE_EMULATOR") != null && System.getenv("AUTH_EMULATOR") != null)
        val now = System.currentTimeMillis(); val today = dayOf(now)
        val owner = Who("Parks Lodge").signUp(Role.OWNER, "dev-owner-$tag")
        val site = call { owner.store.createCampsite(Campsite("", owner.id, owner.name, "Bow River $tag", "Banff, AB", maxOccupancy = 6, createdAt = now)) }
        val lotId = call { owner.store.createLottery(Lottery("", site, "Bow River $tag", owner.id, now - 1_000, now + 8_000, winnerCount = 1, bookingStartDay = today + 2, bookingEndDay = today + 30)) }
        val maya = Who("Maya Chen").signUp(device = "dev-maya-$tag")
        val adam = Who("Adam Kent").signUp(device = "dev-adam-$tag")
        listOf(maya, adam).forEach { w -> call { w.store.joinWaitlist(lotId, w.id, w.name, System.currentTimeMillis()) } }
        Thread.sleep(maxOf(0, now + 9_000 - System.currentTimeMillis()))
        call { owner.store.runDrawIfDue(lotId, System.currentTimeMillis()) }
        val entries = call { owner.store.observeEntries(lotId).first { l -> l.none { it.status == EntryStatus.WAITING } } }
        val guest = listOf(maya, adam).single { w -> entries.single { it.entrantId == w.id }.status == EntryStatus.WON }
        val other = listOf(maya, adam).single { it !== guest }
        call { guest.store.bookDates(lotId, guest.id, today + 3, today + 5, System.currentTimeMillis(), 2, true) }
        call { guest.store.rate(Rating(site, guest.id, guest.name, 5, "Lovely.", lotId, System.currentTimeMillis())) }

        val stranger = Who("Hinton Camps").signUp(Role.OWNER, "dev-hinton-$tag")
        step("another owner can't delete it", attempt { stranger.store.deleteCampsite(site, System.currentTimeMillis()) }.isFailure)

        val deleted = attempt { owner.store.deleteCampsite(site, System.currentTimeMillis()) }
        step("the owner deletes their campsite, ratings and all", deleted.isSuccess, deleted.exceptionOrNull()?.message ?: "")
        step("it's gone", call { owner.store.observeCampsite(site).first() } == null)
        val told = call { guest.store.observeNotifications(guest.id).first { l -> l.any { it.type == NotificationType.REMOVED } } }
        step("the guest with an upcoming stay is told", told.any { it.type == NotificationType.REMOVED && "is cancelled" in it.body })
        step("someone not selected isn't", call { other.store.observeNotifications(other.id).first() }.none { it.type == NotificationType.REMOVED })
        step("the guest's booking is gone", call { guest.store.observeReservationsForEntrant(guest.id).first() }.isEmpty())
    }
}
