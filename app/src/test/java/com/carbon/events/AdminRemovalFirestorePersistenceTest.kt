/*
 * AdminRemovalFirestorePersistenceTest.kt (Tausif)
 *
 * Purpose: An admin removing someone for good, against the security rules on the local Firebase
 * emulators, never the production project (enabled only when FIRESTORE_EMULATOR and
 * AUTH_EMULATOR are set): the removed person can't join or trust a phone, can't lift the block
 * on their phone, nobody can sign up on that phone with it trusted, and Restore undoes it all
 * (US 03.02.01, US 03.07.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 18, 2026). Prompt: "Write
 * tests for an admin removing someone so they can't use the app again." Reviewed, edited and run by Team Carbon.
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

/** Maya on an owner's open waitlist, with her phone trusted, and an admin. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class AdminRemovalFirestorePersistenceTest {
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

    @Test fun aRemovedPerson_staysOut_untilRestored() {
        assumeTrue(System.getenv("FIRESTORE_EMULATOR") != null && System.getenv("AUTH_EMULATOR") != null)
        val now = System.currentTimeMillis()
        val phone = "dev-maya-$tag"
        val owner = Who("Parks Lodge").signUp(Role.OWNER, "dev-owner-$tag")
        val site = call { owner.store.createCampsite(Campsite("", owner.id, "Parks Lodge", "Bow River $tag", "Banff, AB", maxOccupancy = 6, createdAt = now)) }
        val lotId = call { owner.store.createLottery(Lottery("", site, "Bow River $tag", owner.id, now - 1_000, now + 3_600_000, winnerCount = 1)) }
        val maya = Who("Maya Chen").signUp(device = phone)
        call { maya.store.joinWaitlist(lotId, maya.id, maya.name, System.currentTimeMillis()) }
        val admin = Who("Alex Admin").signUp()
        bypassPatch("users/${admin.id}", """{"fields":{"isAdmin":{"booleanValue":true}}}""")

        val profile = call { admin.store.observeProfile(maya.id).first { it != null } }!!
        val removed = attempt { admin.store.removeProfile(profile, System.currentTimeMillis()) }
        step("the admin removes Maya", removed.isSuccess, removed.exceptionOrNull()?.message ?: "")
        val record = call { admin.store.observeProfile(maya.id).first { it?.removed == true } }!!
        step("a removed, blocked record stays", record.blocked && record.removed)
        step("she's off the waitlist", call { owner.store.observeLottery(lotId).first { it?.entrantCount == 0 } }!!.entrantCount == 0)
        step("her phone is blocked", call { admin.store.isPhoneBlocked(phone) })

        val rejoin = attempt { maya.store.joinWaitlist(lotId, maya.id, maya.name, System.currentTimeMillis()) }
        step("she can't join again", rejoin.isFailure)
        val retrust = attempt { maya.store.trustDevice(TrustedDevice(phone, maya.id, "Pixel 8", System.currentTimeMillis())) }
        step("she can't lift the block by trusting her phone again", denied(retrust), retrust.exceptionOrNull()?.message ?: "allowed!")
        val unblock = attempt { maya.store.removeDevice(phone) }
        step("she can't delete her phone's record", denied(unblock), unblock.exceptionOrNull()?.message ?: "allowed!")
        val campsite = attempt { maya.store.createCampsite(Campsite("", maya.id, "Maya Chen", "Back Again $tag", "Banff, AB", maxOccupancy = 2, createdAt = System.currentTimeMillis())) }
        step("she can't list a campsite", campsite.isFailure)

        val newcomer = attempt { Who("New Name").signUp(device = phone) }
        step("nobody can sign up with her phone trusted", newcomer.isFailure)
        step("the phone still points at her record", call { admin.store.observeUserIdForDevice(phone).first() } == maya.id)

        val restored = attempt { admin.store.restoreProfile(record) }
        step("the admin restores her", restored.isSuccess, restored.exceptionOrNull()?.message ?: "")
        step("her phone works again", !call { admin.store.isPhoneBlocked(phone) })
        val back = attempt { maya.store.joinWaitlist(lotId, maya.id, maya.name, System.currentTimeMillis()) }
        step("she can join again", back.isSuccess, back.exceptionOrNull()?.message ?: "")
    }

    @Test fun aRemovedPerson_cantMarkThemselvesNotRemoved() {
        assumeTrue(System.getenv("FIRESTORE_EMULATOR") != null && System.getenv("AUTH_EMULATOR") != null)
        val maya = Who("Maya Chen").signUp(device = "dev-maya2-$tag")
        val admin = Who("Alex Admin").signUp()
        bypassPatch("users/${admin.id}", """{"fields":{"isAdmin":{"booleanValue":true}}}""")
        val profile = call { admin.store.observeProfile(maya.id).first { it != null } }!!
        call { admin.store.removeProfile(profile, System.currentTimeMillis()) }
        val record = call { maya.store.observeProfile(maya.id).first { it?.removed == true } }!!
        val undo = attempt { maya.store.saveProfile(record.copy(removed = false, blocked = false)) }
        step("she can't undo her own removal", denied(undo), undo.exceptionOrNull()?.message ?: "allowed!")
    }
}
