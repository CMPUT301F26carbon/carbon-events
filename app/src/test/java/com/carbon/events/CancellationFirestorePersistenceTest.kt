/*
 * CancellationFirestorePersistenceTest.kt (Vaibhav)
 *
 * Purpose: Cancelling a booked stay against the security rules on the local Firebase emulators,
 * never the production project (enabled only when FIRESTORE_EMULATOR and AUTH_EMULATOR are set):
 * the guest's phone files the confirmation in their notifications, and can file no other kind
 * (US 01.07.01, US 01.07.02).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 18, 2026). Prompt: "Write
 * tests for the cancellation confirmation notification." Reviewed, edited and run by Team Carbon.
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
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** A winner who booked a stay at an owner's campsite. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class CancellationFirestorePersistenceTest {
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

    @Test fun cancellingABookedStay_filesItsConfirmation() {
        assumeTrue(System.getenv("FIRESTORE_EMULATOR") != null && System.getenv("AUTH_EMULATOR") != null)
        val now = System.currentTimeMillis()
        val today = dayOf(now)
        val owner = Who("Parks Lodge").signUp(Role.OWNER, "dev-owner-$tag")
        val site = call { owner.store.createCampsite(Campsite("", owner.id, "Parks Lodge", "Bow River $tag", "Banff, AB", maxOccupancy = 6, createdAt = now)) }
        val lotId = call { owner.store.createLottery(Lottery("", site, "Bow River $tag", owner.id, now - 1_000, now + 8_000, winnerCount = 1, bookingStartDay = today + 2, bookingEndDay = today + 9)) }
        val maya = Who("Maya Chen").signUp(device = "dev-maya-$tag")
        call { maya.store.joinWaitlist(lotId, maya.id, maya.name, System.currentTimeMillis()) }
        Thread.sleep(maxOf(0, now + 9_000 - System.currentTimeMillis()))
        step("the owner's phone draws", call { owner.store.runDrawIfDue(lotId, System.currentTimeMillis()) })
        call { maya.store.observeEntriesForEntrant(maya.id).first { l -> l.any { it.status == EntryStatus.WON } } }
        call { maya.store.bookDates(lotId, maya.id, today + 3, today + 5, System.currentTimeMillis(), guests = 2, agreedToRules = true) }

        val cancelled = attempt { maya.store.giveUpSpot(lotId, maya.id, System.currentTimeMillis()) }
        step("the guest cancels their booked stay", cancelled.isSuccess, cancelled.exceptionOrNull()?.message ?: "")
        val notes = call { maya.store.observeNotifications(maya.id).first { l -> l.any { it.type == NotificationType.CANCELLED } } }
        val note = notes.first { it.type == NotificationType.CANCELLED }
        step("the confirmation is in their notifications", note.lotteryId == lotId && note.title.contains("cancelled"), note.title)
        val lot = call { owner.store.observeLottery(lotId).first { it?.spotsFree == 1 } }!!
        step("the spot is back for the owner to offer", lot.spotsFree == 1)
    }

    @Test fun aGuest_canFileOnlyACancellationForThemselves() {
        assumeTrue(System.getenv("FIRESTORE_EMULATOR") != null && System.getenv("AUTH_EMULATOR") != null)
        val maya = Who("Maya Chen").signUp(device = "dev-maya2-$tag")
        val liam = Who("Liam Okafor").signUp(device = "dev-liam-$tag")
        val now = System.currentTimeMillis()
        val fakeWin = AppNotification("", maya.id, NotificationType.LOTTERY_WON, "You won!", "Not really.", "lot-x", "site-x", now)
        val win = attempt { maya.db.collection("users").document(maya.id).collection("notifications").add(fakeWin.toMap()).await() }
        step("a guest can't give themselves a win", denied(win), win.exceptionOrNull()?.message ?: "allowed!")
        val toOther = attempt { maya.db.collection("users").document(liam.id).collection("notifications").add(fakeWin.copy(recipientId = liam.id, type = NotificationType.CANCELLED).toMap()).await() }
        step("a guest can't file a cancellation in someone else's notifications", denied(toOther), toOther.exceptionOrNull()?.message ?: "allowed!")
    }
}
