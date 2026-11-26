/*
 * NotificationAccessRulesTest.kt (Rayner)
 *
 * Purpose: Who an owner can send notifications to, against the security rules on the local
 * Firebase emulators, never the production project (enabled only when FIRESTORE_EMULATOR and
 * AUTH_EMULATOR are set): draw results only to people on that lottery's waiting list, a new
 * lottery only to the campsite's followers, and nothing to anyone else.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 25, 2026). Prompt: "Write
 * emulator tests for who an owner can send notifications to." Reviewed, edited and run by Team
 * Carbon.
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

/** Parks Lodge with a lottery, Maya on its waiting list, Ella following it, and Sam, who is neither. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class NotificationAccessRulesTest {
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

    private fun Who.send(to: Who, n: AppNotification) = attempt { db.collection("users").document(to.id).collection("notifications").add(n.toMap()).await() }

    @Test fun ownersNotifyOnlyTheirWaitingListAndFollowers() {
        assumeTrue(System.getenv("FIRESTORE_EMULATOR") != null && System.getenv("AUTH_EMULATOR") != null)
        val now = System.currentTimeMillis()
        val owner = Who("Parks Lodge").signUp(Role.OWNER, "dev-owner-$tag")
        val maya = Who("Maya Chen").signUp()
        val ella = Who("Ella Ward").signUp()
        val sam = Who("Sam Park").signUp()
        val site = call { owner.store.createCampsite(Campsite("", owner.id, "Parks Lodge", "Bow River $tag", "Banff, AB", maxOccupancy = 6, createdAt = now)) }
        val draft = Lottery("", site, "Bow River $tag", owner.id, now - 1_000, now + 86_400_000L, 2)
        val lotteryId = call { owner.store.createLottery(draft) }
        val lottery = draft.copy(id = lotteryId)
        call { maya.store.joinWaitlist(lotteryId, maya.id, maya.name, now) }
        call { ella.store.setFollowing(site, ella.id, ella.name, true, now) }
        fun won(to: Who) = AppNotification("", to.id, NotificationType.LOTTERY_WON, "You won", "A spot at Bow River.", lotteryId, site, now)

        val toEntrant = owner.send(maya, won(maya))
        step("the owner tells someone on the waiting list they won", toEntrant.isSuccess, toEntrant.exceptionOrNull()?.message ?: "")
        val toStranger = owner.send(sam, won(sam))
        step("the owner can't send a result to someone not on the list", denied(toStranger), toStranger.exceptionOrNull()?.message ?: "allowed!")
        val toFollower = owner.send(ella, openedNotice(lottery, ella.id, now))
        step("the owner tells a follower about the new lottery", toFollower.isSuccess, toFollower.exceptionOrNull()?.message ?: "")
        val openedToStranger = owner.send(sam, openedNotice(lottery, sam.id, now))
        step("the owner can't send a new-lottery notice to someone who doesn't follow", denied(openedToStranger), openedToStranger.exceptionOrNull()?.message ?: "allowed!")
        val message = owner.send(maya, announcementNotice(lottery, maya.id, "Win a free stay, tap here", now))
        step("the owner can't send free-text notices, which go through the chat", denied(message), message.exceptionOrNull()?.message ?: "allowed!")
        val otherOwner = Who("Other Lodge").signUp(Role.OWNER, "dev-other-$tag")
        val notTheirs = otherOwner.send(maya, won(maya))
        step("another owner can't send results for this campsite", denied(notTheirs), notTheirs.exceptionOrNull()?.message ?: "allowed!")
    }
}
