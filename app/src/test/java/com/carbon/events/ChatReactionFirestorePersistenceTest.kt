/*
 * ChatReactionFirestorePersistenceTest.kt (Tausif)
 *
 * Purpose: Emoji reactions in a chat against the security rules on the local Firebase emulators,
 * never the production project (enabled only when FIRESTORE_EMULATOR and AUTH_EMULATOR are set):
 * the owner and their guest react to messages and see each other's reactions, while reacting for
 * someone else, an emoji that isn't on offer, editing a message, and reacting in someone else's
 * chat are refused.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 21, 2026). Prompt: "Write
 * tests for emoji reactions in the chat on the Firebase emulators." Reviewed, edited and run by
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

/** An owner and the guest they selected, reacting to messages in their conversation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class ChatReactionFirestorePersistenceTest {
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

    @Test fun anOwnerAndTheirGuest_reactToMessages_andSeeEachOthersReactions() {
        assumeTrue(System.getenv("FIRESTORE_EMULATOR") != null && System.getenv("AUTH_EMULATOR") != null)
        val (owner, wren, chat) = selectedGuest()
        call { owner.store.sendMessage(chat, owner.id, owner.name, "Firewood is $8 at the shack.", System.currentTimeMillis()) }
        val id = call { wren.store.observeMessages(chat.id).first { it.isNotEmpty() } }.single().id

        val liked = attempt { wren.store.react(chat.id, id, wren.id, "👍") }
        step("the guest reacts to the owner's message", liked.isSuccess, liked.exceptionOrNull()?.message ?: "")
        val loved = attempt { owner.store.react(chat.id, id, owner.id, "❤️") }
        step("the owner reacts to their own message", loved.isSuccess, loved.exceptionOrNull()?.message ?: "")
        val both = call { owner.store.observeMessages(chat.id).first { it.single().reactions.size == 2 } }.single()
        step("both reactions are saved", both.reactions == mapOf(wren.id to "👍", owner.id to "❤️"), both.reactions.toString())

        val changed = attempt { wren.store.react(chat.id, id, wren.id, "🔥") }
        step("the guest changes their reaction", changed.isSuccess, changed.exceptionOrNull()?.message ?: "")
        val removed = attempt { wren.store.react(chat.id, id, wren.id, null) }
        step("the guest takes their reaction off", removed.isSuccess, removed.exceptionOrNull()?.message ?: "")
        val after = call { owner.store.observeMessages(chat.id).first { it.single().reactions.size == 1 } }.single()
        step("only the owner's reaction is left", after.reactions == mapOf(owner.id to "❤️"), after.reactions.toString())
    }

    @Test fun reactingForSomeoneElse_withAnotherEmoji_editingAMessage_orFromOutside_isRefused() {
        assumeTrue(System.getenv("FIRESTORE_EMULATOR") != null && System.getenv("AUTH_EMULATOR") != null)
        val (owner, wren, chat) = selectedGuest()
        call { owner.store.sendMessage(chat, owner.id, owner.name, "Quiet hours start at 10 PM.", System.currentTimeMillis()) }
        val id = call { wren.store.observeMessages(chat.id).first { it.isNotEmpty() } }.single().id
        val doc = wren.db.collection("chats").document(chat.id).collection("messages").document(id)

        val forOwner = attempt { wren.store.react(chat.id, id, owner.id, "👍") }
        step("the guest can't react for the owner", denied(forOwner), forOwner.exceptionOrNull()?.message ?: "allowed!")
        val other = attempt { wren.store.react(chat.id, id, wren.id, "💩") }
        step("an emoji that isn't on offer is refused", denied(other), other.exceptionOrNull()?.message ?: "allowed!")
        val edit = attempt { doc.update("text", "Quiet hours start at midnight.").await() }
        step("the guest can't change the owner's message", denied(edit), edit.exceptionOrNull()?.message ?: "allowed!")
        val sneaky = attempt { doc.update(mapOf("reactions.${wren.id}" to "👍", "text" to "changed")).await() }
        step("a reaction can't carry another change with it", denied(sneaky), sneaky.exceptionOrNull()?.message ?: "allowed!")

        val stranger = Who("Sam Stranger").signUp(device = "dev-sam-$tag")
        val outside = attempt { stranger.store.react(chat.id, id, stranger.id, "👍") }
        step("someone outside the chat can't react", denied(outside), outside.exceptionOrNull()?.message ?: "allowed!")
        val untouched = call { owner.store.observeMessages(chat.id).first() }.single()
        step("the message is unchanged", untouched.text == "Quiet hours start at 10 PM." && untouched.reactions.isEmpty())
    }
}
