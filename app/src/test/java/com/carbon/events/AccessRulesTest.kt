/*
 * AccessRulesTest.kt (Tausif)
 *
 * Purpose: runs every main data flow as real signed-in users against docs/firestore.rules on the
 * Firebase emulators, checking who can read and write what (CI "firestore" job; skipped
 * when FIRESTORE_EMULATOR and AUTH_EMULATOR are not set).
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

/** Every main flow, as real signed-in people, against the security rules: who can read and write what. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class AccessRulesTest {
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

    @Test fun everyFlow() {
        assumeTrue(System.getenv("FIRESTORE_EMULATOR") != null)
        val now = System.currentTimeMillis()
        val today = dayOf(now)
        val owner = Who("Parks Lodge").signUp(Role.OWNER, "dev-owner-$tag")
        val site = call { owner.store.createCampsite(Campsite("", owner.id, "Parks Lodge", "Bow River $tag", "Banff, AB", maxOccupancy = 6, pricePerNight = 65, createdAt = now)) }
        step("owner creates campsite", site.isNotBlank())
        val lotId = call { owner.store.createLottery(Lottery("", site, "Bow River $tag", owner.id, now - 1_000, now + 20_000, winnerCount = 1, bookingStartDay = today + 2, bookingEndDay = today + 12)) }
        step("owner creates lottery", lotId.isNotBlank(), lotId)

        val a = Who("Adam Kent").signUp(device = "dev-a1-$tag")
        val b = Who("Maya Chen").signUp(device = "dev-b1-$tag")
        step("entrant A joins", attempt { a.store.joinWaitlist(lotId, a.id, a.name, System.currentTimeMillis()) }.isSuccess)
        step("entrant B joins", attempt { b.store.joinWaitlist(lotId, b.id, b.name, System.currentTimeMillis()) }.isSuccess)
        val aOwn = attempt { a.store.observeEntriesForEntrant(a.id).first { it.isNotEmpty() } }
        step("entrant reads own entries", aOwn.getOrNull()?.size == 1, aOwn.exceptionOrNull()?.message ?: "")
        val aAll = attempt { a.store.observeEntries(lotId).first() }
        step("non-winner listing everyone's entries gets an empty list (no freeze)", aAll.getOrNull()?.isEmpty() == true, "${aAll.getOrNull()?.size} ${aAll.exceptionOrNull()?.message ?: ""}")
        val anon = Who("Nobody")
        val anonRead = attempt { anon.db.document("${Paths.CAMPSITES}/$site/${Paths.LOTTERIES}/${Paths.keyOf(lotId)}/${Paths.ENTRANTS}/${a.id}").get().await() }
        step("signed-out read of an entry is refused", denied(anonRead), anonRead.exceptionOrNull()?.message ?: "allowed!")
        val bReadsA = attempt { b.db.document("${Paths.CAMPSITES}/$site/${Paths.LOTTERIES}/${Paths.keyOf(lotId)}/${Paths.ENTRANTS}/${a.id}").get().await() }
        step("entrant can't read another entrant's entry", denied(bReadsA))
        val ownerEntries = attempt { owner.store.observeEntries(lotId).first { it.size == 2 } }
        step("owner lists the waitlist", ownerEntries.isSuccess, ownerEntries.exceptionOrNull()?.message ?: "")
        val details = attempt { a.store.observeLottery(lotId).first { it != null } }
        step("entrant sees lottery with joined count", details.getOrNull()?.entrantCount == 2, "count=${details.getOrNull()?.entrantCount}")

        Thread.sleep(maxOf(0, now + 21_000 - System.currentTimeMillis()))
        val drew = attempt { owner.store.runDrawIfDue(lotId, System.currentTimeMillis()) }
        step("owner's phone runs the draw", drew.getOrNull() == true, drew.exceptionOrNull()?.message ?: "")
        val entries = call { owner.store.observeEntries(lotId).first { l -> l.any { it.status == EntryStatus.WON } } }
        val winner = if (entries.first { it.status == EntryStatus.WON }.entrantId == a.id) a else b
        val loser = if (winner === a) b else a
        step("draw picked one winner", entries.count { it.status == EntryStatus.WON } == 1, "winner=${winner.name}")

        val wAll = attempt { winner.store.observeEntries(lotId).first { it.size == 2 } }
        step("winner can list entries (to see booked nights)", wAll.isSuccess, wAll.exceptionOrNull()?.message ?: "")
        val booked = attempt { winner.store.bookDates(lotId, winner.id, today + 3, today + 5, System.currentTimeMillis(), guests = 2) }
        step("winner books nights", booked.isSuccess, booked.exceptionOrNull()?.message ?: "")
        val lAll = attempt { loser.store.observeEntries(lotId).first() }
        step("loser listing entries gets empty, no freeze", lAll.getOrNull()?.isEmpty() == true)
        val wRes = attempt { winner.store.observeReservationsForEntrant(winner.id).first { it.isNotEmpty() } }
        step("winner sees reservation in Bookings", wRes.isSuccess, wRes.exceptionOrNull()?.message ?: "")
        val oRes = attempt { owner.store.observeReservationsForOwner(owner.id).first { l -> l.any { it.campsiteId == site } } }
        step("owner sees the booking for this campsite", oRes.isSuccess, oRes.exceptionOrNull()?.message ?: "")
        val oldCampQuery = attempt { owner.store.observeReservationsForCampsite(site).first() }
        step("(old) campsite-only query now refused for owner → empty, no freeze", oldCampQuery.getOrNull()?.isEmpty() == true)

        val chat = Chat(Chat.idFor(site, winner.id), site, "Bow River $tag", owner.id, owner.name, winner.id, winner.name, lotteryKey = Paths.keyOf(lotId))
        val empty = attempt { winner.store.observeMessages(chat.id).first() }
        step("messages of a not-yet-started chat read as empty", empty.getOrNull()?.isEmpty() == true, empty.exceptionOrNull()?.message ?: "")
        val emptyChat = attempt { winner.store.observeChat(chat.id).first() }
        step("not-yet-started chat reads as null", emptyChat.isSuccess && emptyChat.getOrNull() == null)
        val sent = attempt { winner.store.sendMessage(chat, winner.id, winner.name, "Is the canoe launch open?", System.currentTimeMillis()) }
        step("winner starts chat with owner", sent.isSuccess, sent.exceptionOrNull()?.message ?: "")
        val ownerMsgs = attempt { owner.store.observeMessages(chat.id).first { it.isNotEmpty() } }
        step("owner reads the message", ownerMsgs.isSuccess)
        val reply = attempt { owner.store.sendMessage(chat.copy(lastText = "x"), owner.id, owner.name, "Yes until the lake freezes.", System.currentTimeMillis()) }
        step("owner replies", reply.isSuccess, reply.exceptionOrNull()?.message ?: "")
        val lChat = Chat(Chat.idFor(site, loser.id), site, "Bow River $tag", owner.id, owner.name, loser.id, loser.name, lotteryKey = Paths.keyOf(lotId))
        val lSent = attempt { loser.store.sendMessage(lChat, loser.id, loser.name, "Hi", System.currentTimeMillis()) }
        step("loser can't start a chat", denied(lSent), lSent.exceptionOrNull()?.message ?: "allowed!")
        val peek = attempt { loser.store.observeMessages(chat.id).first() }
        step("other entrant can't read someone's chat (gets empty)", peek.getOrNull()?.isEmpty() == true)

        val notified = attempt { owner.store.sendAnnouncement(call { owner.store.observeLottery(lotId).first { it != null } }!!, Audience.ALL, "Firewood is $8 at the shack.", System.currentTimeMillis()) }
        step("owner notifies everyone who joined", notified.getOrNull() == 2, notified.exceptionOrNull()?.message ?: "")
        val loserThread = attempt { loser.store.observeMessages(Chat.idFor(site, loser.id)).first { it.isNotEmpty() } }
        step("not-selected entrant sees the notice in their conversation", loserThread.getOrNull()?.any { it.announcement } == true, loserThread.exceptionOrNull()?.message ?: "")
        val loserChat = attempt { loser.store.observeChat(Chat.idFor(site, loser.id)).first { it != null } }
        step("their conversation shows 1 unread", loserChat.getOrNull()?.unreadCount(loser.id) == 1)
        val loserReply = attempt { loser.store.sendMessage(loserChat.getOrThrow()!!, loser.id, loser.name, "Can I still come?", System.currentTimeMillis()) }
        step("not-selected entrant still can't reply", denied(loserReply), loserReply.exceptionOrNull()?.message ?: "allowed!")
        val markRead = attempt { loser.store.markChatRead(Chat.idFor(site, loser.id), loser.id) }
        step("not-selected entrant can mark the notice read", markRead.isSuccess, markRead.exceptionOrNull()?.message ?: "")

        call { winner.store.trustDevice(TrustedDevice("dev-a2-$tag", winner.id, "Tablet", System.currentTimeMillis())) }
        step("winner has 2 trusted phones", call { winner.store.observeDevices(winner.id).first() }.size == 2)
        val all = attempt { winner.store.removeAllDevices(winner.id) }
        step("log out of all devices removes every phone", all.isSuccess && call { winner.store.observeDevices(winner.id).first() }.isEmpty(), all.exceptionOrNull()?.message ?: "")

        val admin = Who("Admin Tausif").signUp()
        bypassPatch("users/${admin.id}", """{"fields":{"isAdmin":{"booleanValue":true}}}""")
        Thread.sleep(500)
        val loserProfile = call { admin.store.observeProfile(loser.id).first { it != null } }!!
        val removed = attempt { admin.store.deleteProfile(loserProfile) }
        step("admin removes a profile", removed.isSuccess, removed.exceptionOrNull()?.message ?: "")
        step("removed profile's phones are gone", call { admin.store.observeDevices(loser.id).first() }.isEmpty())
        val gone = attempt { loser.store.observeProfile(loser.id).first() }
        step("removed person's phone sees no profile (signs out)", gone.isSuccess && gone.getOrNull() == null)
        val ownerProfile = call { admin.store.observeProfile(owner.id).first { it != null } }!!
        val block = attempt { admin.store.saveProfile(ownerProfile.copy(blocked = true)); admin.store.removeAllDevices(owner.id) }
        step("admin blocks owner and signs out their phones", block.isSuccess && call { admin.store.observeDevices(owner.id).first() }.isEmpty(), block.exceptionOrNull()?.message ?: "")
        val blockedWrite = attempt { owner.store.createCampsite(Campsite("", owner.id, "Parks Lodge", "Blocked $tag", "Banff, AB", maxOccupancy = 2, createdAt = System.currentTimeMillis())) }
        step("blocked owner can't create campsites", blockedWrite.isFailure)

    }
}
