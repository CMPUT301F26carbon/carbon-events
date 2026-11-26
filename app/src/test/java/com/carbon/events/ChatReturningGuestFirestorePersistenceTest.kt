/*
 * ChatReturningGuestFirestorePersistenceTest.kt (Suvir)
 *
 * Purpose: A returning guest messaging the owner (US 01.14.01) against the security rules on the
 * local Firebase emulators, never the production project (enabled only when FIRESTORE_EMULATOR and
 * AUTH_EMULATOR are set): Maya isn't selected in a campsite's first lottery and the owner's notice
 * ties their conversation to it; she then wins and books in a second lottery there, and can message
 * the owner about it. Someone who never won there can't post, even naming a lottery they joined.
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

/** An owner with two lotteries at one campsite, Maya who lost the first and won the second, and Adam who never won. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class ChatReturningGuestFirestorePersistenceTest {
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

    /** A lottery at [site] closing in 8 s, with stays [fromDay] to [toDay], that [people] join, then drawn. */
    private fun drawn(owner: Who, site: String, fromDay: Long, toDay: Long, winners: Int, vararg people: Who): String {
        val now = System.currentTimeMillis()
        val lotId = call { owner.store.createLottery(Lottery("", site, "Bow River $tag", owner.id, now - 1_000, now + 8_000, winnerCount = winners, bookingStartDay = fromDay, bookingEndDay = toDay)) }
        people.forEach { w -> call { w.store.joinWaitlist(lotId, w.id, w.name, System.currentTimeMillis()) } }
        Thread.sleep(maxOf(0, now + 9_000 - System.currentTimeMillis()))
        step("the owner's phone draws", call { owner.store.runDrawIfDue(lotId, System.currentTimeMillis()) })
        return lotId
    }

    @Test fun aReturningGuest_messagesTheOwner_aboutTheirNewBooking() {
        assumeTrue(System.getenv("FIRESTORE_EMULATOR") != null && System.getenv("AUTH_EMULATOR") != null)
        val now = System.currentTimeMillis(); val today = dayOf(now)
        val owner = Who("Parks Lodge").signUp(Role.OWNER, "dev-owner-$tag")
        val site = call { owner.store.createCampsite(Campsite("", owner.id, owner.name, "Bow River $tag", "Banff, AB", maxOccupancy = 6, createdAt = now)) }
        val maya = Who("Maya Chen").signUp(device = "dev-maya-$tag")
        val lee = Who("Lee Park").signUp(device = "dev-lee-$tag")
        // First lottery: one spot. Whoever loses is our returning guest.
        val first = drawn(owner, site, today + 2, today + 10, 1, maya, lee)
        val firstEntries = call { owner.store.observeEntries(first).first { l -> l.none { it.status == EntryStatus.WAITING } } }
        val guest = listOf(maya, lee).single { w -> firstEntries.single { it.entrantId == w.id }.status == EntryStatus.LOST }
        val firstLottery = call { owner.store.observeLottery(first).first() }!!
        call { owner.store.sendAnnouncement(firstLottery, Audience.NOT_SELECTED, "Thanks for entering!", System.currentTimeMillis()) }
        val chat = call { guest.store.observeChat(Chat.idFor(site, guest.id)).first { it != null } }!!
        step("the notice ties the conversation to the first lottery", Paths.keyOf(chat.lotteryKey) == Paths.keyOf(first))
        step("not having won, she can't post there", denied(attempt { guest.store.sendMessage(chat, guest.id, guest.name, "Hi!", System.currentTimeMillis()) }))

        // Second lottery: she wins and books.
        val second = drawn(owner, site, today + 12, today + 20, 1, guest)
        call { guest.store.observeEntriesForEntrant(guest.id).first { l -> l.any { it.lotteryId == second && it.status == EntryStatus.WON } } }
        call { guest.store.bookDates(second, guest.id, today + 13, today + 15, System.currentTimeMillis(), 2, true) }
        val mine = call { guest.store.observeEntriesForEntrant(guest.id).first() }
        val entry = ChatRules.entryFor(chat, mine)
        step("her win in the second lottery is the one that counts", entry?.lotteryId == second)
        val sent = attempt { guest.store.sendMessage(ChatRules.tiedTo(chat, entry), guest.id, guest.name, "Can we arrive late?", System.currentTimeMillis()) }
        step("she can message the owner about her new booking", sent.isSuccess, sent.exceptionOrNull()?.message ?: "")
        step("the owner reads it", call { owner.store.observeMessages(chat.id).first { l -> l.any { it.text == "Can we arrive late?" } } }.isNotEmpty())

        val adam = Who("Adam Kent").signUp(device = "dev-adam-$tag")
        val third = drawn(owner, site, today + 22, today + 28, 0, adam)
        val adamChat = Chat(Chat.idFor(site, adam.id), site, "Bow River $tag", owner.id, owner.name, adam.id, adam.name, lotteryKey = Paths.keyOf(third))
        step("someone who never won there can't start one, even naming a lottery they joined",
            denied(attempt { adam.store.sendMessage(adamChat, adam.id, adam.name, "Hello?", System.currentTimeMillis()) }))
    }
}
