/*
 * SharedLotteryFirestorePersistenceTest.kt (Suvir)
 *
 * Purpose: Lotteries shared between campsite owners (US 02.04.08) against the security rules on
 * the local Firebase emulators, never the production project (enabled only when
 * FIRESTORE_EMULATOR and AUTH_EMULATOR are set): an owner invites another owner's campsite into
 * their lottery; a third owner can't see or answer it; the invited owner accepts, adding their
 * campsite's lottery to the group; and an entrant joining once is on both waitlists. Inviting
 * your own campsite, or inviting for a campsite you don't run, is refused.
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

/** Two owners sharing a lottery, a third owner, and an entrant. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class SharedLotteryFirestorePersistenceTest {
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

    @Test fun twoOwners_shareOneLottery() {
        assumeTrue(System.getenv("FIRESTORE_EMULATOR") != null && System.getenv("AUTH_EMULATOR") != null)
        val now = System.currentTimeMillis()
        val today = dayOf(now)
        val parks = Who("Parks Lodge").signUp(Role.OWNER, "dev-parks-$tag")
        val jasper = Who("Jasper Outfitters").signUp(Role.OWNER, "dev-jasper-$tag")
        val other = Who("Hinton Camps").signUp(Role.OWNER, "dev-hinton-$tag")
        val twoJack = call { parks.store.createCampsite(Campsite("", parks.id, parks.name, "Two Jack $tag", "Banff, AB", maxOccupancy = 6, createdAt = now)) }
        val whistlers = call { jasper.store.createCampsite(Campsite("", jasper.id, jasper.name, "Whistlers $tag", "Jasper, AB", maxOccupancy = 8, createdAt = now)) }
        val group = LotteryGroups.newGroupId(now)
        val lottery = Lottery("", twoJack, "Two Jack $tag", parks.id, now - 1_000, now + 3_600_000, winnerCount = 2, bookingStartDay = today + 10, bookingEndDay = today + 20, groupId = group)
        val mine = call { parks.store.createLottery(lottery) }
        val site = call { parks.store.observeCampsite(twoJack).first() }!!
        val invited = call { jasper.store.observeCampsite(whistlers).first() }!!

        val invite = LotteryInvites.forCampsites(lottery.copy(id = mine), group, parks.name, site, listOf(invited), now).single()
        val sent = attempt { parks.store.inviteToLottery(invite) }
        step("an owner invites another owner's campsite", sent.isSuccess, sent.exceptionOrNull()?.message ?: "")
        step("they can't invite their own campsite as if it were someone else's",
            denied(attempt { parks.store.inviteToLottery(invite.copy(campsiteId = twoJack, campsiteName = "Two Jack $tag", toOwnerId = parks.id)) }))
        step("nor invite for a campsite they don't run", denied(attempt { other.store.inviteToLottery(invite.copy(fromOwnerId = other.id)) }))

        val inviteId = call { jasper.store.observeInvitesTo(jasper.id).first { it.isNotEmpty() } }.single().id
        // Notifying another owner is only allowed about a real invitation between the two of them.
        fun notice(extra: Map<String, Any?>) = attempt {
            other.db.collection("users").document(jasper.id).collection("notifications")
                .add(LotteryInvites.invitedNotice(invite, now).toMap() + extra).await()
        }
        step("a third owner can't notify them, even naming this invitation", denied(notice(mapOf("inviteId" to inviteId))))
        step("nor without an invitation", denied(notice(emptyMap())))
        step("the inviting owner sees it", call { parks.store.observeInvitesFrom(parks.id).first { it.isNotEmpty() } }.single().id == inviteId)
        step("the invited owner is told", call { jasper.store.observeNotifications(jasper.id).first { it.isNotEmpty() } }.any { "shared lottery" in it.title })
        step("a third owner can't answer it", attempt { other.store.answerInvite(inviteId, other.id, true, System.currentTimeMillis()) }.isFailure)
        step("the inviting owner can't accept for them", attempt { parks.store.answerInvite(inviteId, parks.id, true, System.currentTimeMillis()) }.isFailure)

        val accepted = attempt { jasper.store.answerInvite(inviteId, jasper.id, true, System.currentTimeMillis()) }
        step("the invited owner accepts", accepted.isSuccess, accepted.exceptionOrNull()?.message ?: "")
        val theirs = call { jasper.store.observeLottery(accepted.getOrThrow()!!).first { it != null } }!!
        step("their campsite's lottery joins the group with the same settings",
            theirs.groupId == group && theirs.ownerId == jasper.id && theirs.registrationEnd / 1000 == lottery.registrationEnd / 1000 && theirs.winnerCount == 2)
        step("the inviting owner is told", call { parks.store.observeNotifications(parks.id).first { it.isNotEmpty() } }.any { "joined your lottery" in it.title })
        step("it can't be answered again", attempt { jasper.store.answerInvite(inviteId, jasper.id, true, System.currentTimeMillis()) }.isFailure)

        val maya = Who("Maya Chen").signUp(device = "dev-maya-$tag")
        val joined = attempt { LotteryGroups.joinAll(maya.store, call { maya.store.observeLottery(mine).first() }!!, maya.id, maya.name, System.currentTimeMillis()) }
        step("an entrant joins once", joined.isSuccess, joined.exceptionOrNull()?.message ?: "")
        val entries = call { maya.store.observeEntriesForEntrant(maya.id).first { it.size == 2 } }
        step("and is on both owners' waitlists", entries.map { it.lotteryId }.toSet() == setOf(mine, theirs.id))
    }
}
