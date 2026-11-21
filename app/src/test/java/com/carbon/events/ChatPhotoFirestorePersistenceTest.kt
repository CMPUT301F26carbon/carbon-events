/*
 * ChatPhotoFirestorePersistenceTest.kt (Tausif)
 *
 * Purpose: Photos in a chat against the security rules on the local Firebase emulators, never the
 * production project (enabled only when FIRESTORE_EMULATOR and AUTH_EMULATOR are set): an owner
 * and the guest they selected send photos with and without a caption and both see them, while a
 * photo too large for one message, a photo that isn't text, and an empty message are refused.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 21, 2026). Prompt: "Write
 * tests for sending photos in the chat on the Firebase emulators." Reviewed, edited and run by
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

/** An owner and the guest they selected, sending photos in their conversation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class ChatPhotoFirestorePersistenceTest {
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

    private val photo = "/9j/" + "A".repeat(2_000)

    /** Signs up an owner with a campsite and a guest, and runs a one-winner draw so the guest is selected. */
    private fun selectedGuest(): Triple<Who, Who, Chat> {
        val now = System.currentTimeMillis()
        val today = dayOf(now)
        val owner = Who("Parks Lodge").signUp(Role.OWNER, "dev-owner-$tag")
        val site = call { owner.store.createCampsite(Campsite("", owner.id, "Parks Lodge", "Bow River $tag", "Banff, AB", maxOccupancy = 6, createdAt = now)) }
        val lotId = call { owner.store.createLottery(Lottery("", site, "Bow River $tag", owner.id, now - 1_000, now + 8_000, winnerCount = 1, bookingStartDay = today + 2, bookingEndDay = today + 9)) }
        val wren = Who("Wren Hale").signUp(device = "dev-wren-$tag")
        call { wren.store.joinWaitlist(lotId, wren.id, wren.name, System.currentTimeMillis()) }
        Thread.sleep(maxOf(0, now + 9_000 - System.currentTimeMillis()))
        step("the owner's phone draws", call { owner.store.runDrawIfDue(lotId, System.currentTimeMillis()) })
        call { wren.store.observeEntriesForEntrant(wren.id).first { l -> l.any { it.status == EntryStatus.WON } } }
        val chat = Chat(Chat.idFor(site, wren.id), site, "Bow River $tag", owner.id, owner.name, wren.id, wren.name, lotteryKey = Paths.keyOf(lotId))
        return Triple(owner, wren, chat)
    }

    @Test fun anOwnerAndTheirGuest_sendPhotos_andBothSeeThem() {
        assumeTrue(System.getenv("FIRESTORE_EMULATOR") != null && System.getenv("AUTH_EMULATOR") != null)
        val (owner, wren, chat) = selectedGuest()
        val sent = attempt { owner.store.sendMessage(chat, owner.id, owner.name, "Your site, number 14", System.currentTimeMillis(), photoBase64 = photo) }
        step("the owner sends a photo with a caption", sent.isSuccess, sent.exceptionOrNull()?.message ?: "")
        val seen = call { wren.store.observeMessages(chat.id).first { it.isNotEmpty() } }.single()
        step("the guest gets the photo and its caption", seen.photoBase64 == photo && seen.text == "Your site, number 14")
        step("the guest's inbox says it's a photo", call { wren.store.observeChat(chat.id).first { it != null } }!!.lastText == "📷 Photo · Your site, number 14")

        val reply = attempt { wren.store.sendMessage(chat, wren.id, wren.name, "", System.currentTimeMillis(), photoBase64 = photo) }
        step("the selected guest replies with just a photo", reply.isSuccess, reply.exceptionOrNull()?.message ?: "")
        val both = call { owner.store.observeMessages(chat.id).first { it.size == 2 } }
        step("the owner sees the guest's photo", both.any { it.senderId == wren.id && it.photoBase64 == photo && it.text.isEmpty() })
    }

    @Test fun aPhotoTooLarge_notText_orAnEmptyMessage_isRefused() {
        assumeTrue(System.getenv("FIRESTORE_EMULATOR") != null && System.getenv("AUTH_EMULATOR") != null)
        val (owner, _, chat) = selectedGuest()
        call { owner.store.sendMessage(chat, owner.id, owner.name, "Welcome!", System.currentTimeMillis()) }
        val messages = owner.db.collection("chats").document(chat.id).collection("messages")
        fun message(vararg extra: Pair<String, Any>) = mapOf("senderId" to owner.id, "senderName" to owner.name, "text" to "", "sentAt" to com.google.firebase.Timestamp.now()) + extra

        val huge = attempt { messages.add(message("photo" to "A".repeat(900_001))).await() }
        step("a photo over 900,000 characters is refused", denied(huge), huge.exceptionOrNull()?.message ?: "allowed!")
        val number = attempt { messages.add(message("photo" to 42)).await() }
        step("a photo that isn't text is refused", denied(number), number.exceptionOrNull()?.message ?: "allowed!")
        val blankPhoto = attempt { messages.add(message("photo" to "")).await() }
        step("an empty photo is refused", denied(blankPhoto), blankPhoto.exceptionOrNull()?.message ?: "allowed!")
        val empty = attempt { messages.add(message()).await() }
        step("a message with no text and no photo is refused", denied(empty), empty.exceptionOrNull()?.message ?: "allowed!")
        val fine = attempt { messages.add(message("photo" to photo)).await() }
        step("a photo of a normal size is allowed", fine.isSuccess, fine.exceptionOrNull()?.message ?: "")
    }
}
