/*
 * Data.kt (Vaibhav)
 *
 * Data layer: the models, the repository interfaces, the Firestore and
 * in-memory stores, and the AppContainer that hands them to every screen.
 */
package com.carbon.events

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await

/**
 * The role a profile is currently using the app as. The same person can switch roles
 * from the Profile screen ("Viewing as").
 */
enum class Role {
    /** Searches campsites and joins lottery waiting lists. */
    ENTRANT,

    /** Lists campsites and runs lotteries. */
    OWNER,

    /** Moderates content. Only available when [UserProfile.isAdmin] is true. */
    ADMIN,
}

/**
 * A person's profile.
 *
 * @property deviceId stable ID of the device this profile belongs to; also the document ID.
 * @property name full name shown to owners and on waiting lists.
 * @property email contact email.
 * @property phone optional phone number; blank when not provided.
 * @property activeRole role the user is currently viewing the app as.
 * @property isAdmin whether this profile may switch to [Role.ADMIN].
 * @property notificationsEnabled whether the user wants lottery notifications.
 * @property createdAt creation time in epoch milliseconds.
 */
data class UserProfile(
    val deviceId: String,
    val name: String,
    val email: String,
    val phone: String = "",
    val activeRole: Role = Role.ENTRANT,
    val isAdmin: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val createdAt: Long = 0L,
) {
    /** First name used in greetings, e.g. "Adam" for "Adam Kent". */
    val firstName: String get() = name.trim().substringBefore(' ').ifBlank { name }

    /** Up to two initials for avatar badges, e.g. "AK". */
    val initials: String
        get() = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .take(2).joinToString("") { it.first().uppercase() }

    /** Roles this profile is allowed to switch between. */
    val availableRoles: List<Role>
        get() = if (isAdmin) Role.entries else listOf(Role.ENTRANT, Role.OWNER)
}

/**
 * A campsite listing.
 *
 * @property id document ID; also what the promo QR code encodes.
 * @property ownerId device ID of the owner's profile.
 * @property ownerName owner display name, copied for list screens.
 * @property name campsite name, e.g. "Two Jack Lakeside".
 * @property location human-readable place, e.g. "Banff, AB".
 * @property latitude map pin latitude, or null if not set.
 * @property longitude map pin longitude, or null if not set.
 * @property maxOccupancy maximum guests per site.
 * @property amenities free-text amenities, e.g. "Fire pits, potable water".
 * @property description short description shown on the details screen.
 * @property tags short labels such as "Lakeside" or "Pet friendly".
 * @property pricePerNight price in Canadian dollars.
 * @property imageBase64 optional promo photo as a compressed base64 JPEG.
 * @property rating average rating out of 5 (display only at this checkpoint).
 * @property ratingCount number of ratings behind [rating].
 * @property createdAt creation time in epoch milliseconds.
 */
data class Campsite(
    val id: String,
    val ownerId: String,
    val ownerName: String,
    val name: String,
    val location: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val maxOccupancy: Int,
    val amenities: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val pricePerNight: Int = 0,
    val imageBase64: String? = null,
    val rating: Double = 0.0,
    val ratingCount: Int = 0,
    val createdAt: Long = 0L,
) {
    /** True when the owner has dropped a pin on the map. */
    val hasMapLocation: Boolean get() = latitude != null && longitude != null
}

/** Where a lottery is in its life cycle. */
enum class LotteryStatus {
    /** Waiting list is (or will be) open; winners not drawn yet. */
    OPEN,

    /** Winners have been drawn and notified. */
    DRAWN,
}

/**
 * A campsite lottery.
 *
 * @property id document ID.
 * @property campsiteId the campsite this lottery is for.
 * @property campsiteName campsite name, copied for list screens.
 * @property ownerId device ID of the owner running it.
 * @property registrationStart when entrants may start joining (epoch ms).
 * @property registrationEnd when the waiting list closes and the draw runs (epoch ms).
 * @property winnerCount how many entrants are drawn as winners.
 * @property waitlistCap optional maximum waiting-list size; null means unlimited.
 * @property status [LotteryStatus.OPEN] until the draw has run.
 * @property drawnAt when the draw ran (epoch ms), or null.
 * @property entrantCount number of entrants currently on the waiting list.
 */
data class Lottery(
    val id: String,
    val campsiteId: String,
    val campsiteName: String,
    val ownerId: String,
    val registrationStart: Long,
    val registrationEnd: Long,
    val winnerCount: Int,
    val waitlistCap: Int? = null,
    val status: LotteryStatus = LotteryStatus.OPEN,
    val drawnAt: Long? = null,
    val entrantCount: Int = 0,
) {
    /** Whether entrants can join or leave the waiting list at time [now]. */
    fun isRegistrationOpen(now: Long): Boolean =
        status == LotteryStatus.OPEN && now in registrationStart until registrationEnd

    /** Whether registration has closed but the draw has not run yet at time [now]. */
    fun isDrawDue(now: Long): Boolean = status == LotteryStatus.OPEN && now >= registrationEnd

    /** Whether registration has not started yet at time [now]. */
    fun isUpcoming(now: Long): Boolean = status == LotteryStatus.OPEN && now < registrationStart
}

/** Outcome of an entry once the draw has run. */
enum class EntryStatus {
    /** Still waiting for the draw. */
    WAITING,

    /** Drawn as a winner. */
    WON,

    /** Not selected in the draw. */
    LOST,
}

/**
 * An entrant on a lottery's waiting list.
 *
 * @property lotteryId the lottery joined.
 * @property entrantId device ID of the entrant.
 * @property entrantName entrant's name, copied for the owner's waitlist screen.
 * @property joinedAt when the entrant joined (epoch ms).
 * @property status draw outcome; [EntryStatus.WAITING] before the draw.
 */
data class WaitlistEntry(
    val lotteryId: String,
    val entrantId: String,
    val entrantName: String,
    val joinedAt: Long,
    val status: EntryStatus = EntryStatus.WAITING,
)

/** What a notification is about. */
enum class NotificationType {
    /** The user won a lottery. */
    LOTTERY_WON,

    /** The user was not selected in a lottery. */
    LOTTERY_LOST,
}

/**
 * A notification addressed to one user.
 *
 * @property id document ID.
 * @property recipientId device ID of the user it is for.
 * @property type win or loss.
 * @property title short headline, e.g. "You won Two Jack Lakeside!".
 * @property body one-line explanation.
 * @property lotteryId related lottery.
 * @property campsiteId related campsite, so tapping can open its details.
 * @property createdAt creation time (epoch ms).
 * @property read whether the user has opened the notifications list since it arrived.
 */
data class AppNotification(
    val id: String,
    val recipientId: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val lotteryId: String,
    val campsiteId: String,
    val createdAt: Long,
    val read: Boolean = false,
)

/** Reads and writes user profiles, keyed by device ID. */
interface ProfileRepository {
    /** Emits the profile for [deviceId], or null if none exists yet, and every later change. */
    fun observeProfile(deviceId: String): Flow<UserProfile?>

    /** Creates or replaces [profile]. */
    suspend fun saveProfile(profile: UserProfile)
}

/** Reads and writes campsites. */
interface CampsiteRepository {
    /** Emits every campsite, and every later change. */
    fun observeCampsites(): Flow<List<Campsite>>

    /** Emits the campsite with [id], or null if it does not exist (e.g. removed). */
    fun observeCampsite(id: String): Flow<Campsite?>

    /** Emits the campsites owned by [ownerId], newest first. */
    fun observeCampsitesByOwner(ownerId: String): Flow<List<Campsite>>

    /**
     * Creates a campsite. The [Campsite.id] of [campsite] is ignored.
     * @return the new campsite's ID.
     */
    suspend fun createCampsite(campsite: Campsite): String

    /** Replaces an existing campsite's details. */
    suspend fun updateCampsite(campsite: Campsite)

    /** Removes a campsite together with its lotteries and waiting lists (admin, US 03.01.01). */
    suspend fun deleteCampsite(id: String)
}

/** Reads and writes lotteries and their waiting lists. */
interface LotteryRepository {
    /** Emits every lottery. */
    fun observeLotteries(): Flow<List<Lottery>>

    /** Emits the lottery with [id], or null. */
    fun observeLottery(id: String): Flow<Lottery?>

    /** Emits the lotteries for one campsite, newest first. */
    fun observeLotteriesForCampsite(campsiteId: String): Flow<List<Lottery>>

    /** Emits the lotteries run by [ownerId], newest first. */
    fun observeLotteriesByOwner(ownerId: String): Flow<List<Lottery>>

    /**
     * Creates a lottery. The [Lottery.id] of [lottery] is ignored.
     * @return the new lottery's ID.
     */
    suspend fun createLottery(lottery: Lottery): String

    /** Emits the waiting list of one lottery, in join order. */
    fun observeEntries(lotteryId: String): Flow<List<WaitlistEntry>>

    /** Emits every waiting-list entry belonging to one entrant. */
    fun observeEntriesForEntrant(entrantId: String): Flow<List<WaitlistEntry>>

    /**
     * Adds an entrant to a waiting list.
     * @throws com.carbon.events.WaitlistException if the lottery rules refuse it.
     */
    suspend fun joinWaitlist(lotteryId: String, entrantId: String, entrantName: String, now: Long)

    /**
     * Removes an entrant from a waiting list.
     * @throws com.carbon.events.WaitlistException if the lottery rules refuse it.
     */
    suspend fun leaveWaitlist(lotteryId: String, entrantId: String, now: Long)

    /**
     * Runs the automatic draw for one lottery if it is due, exactly once even if several
     * devices try at the same moment (US 02.05.01). Marks every entry won/lost and writes
     * one notification per entrant.
     *
     * @return true if this call performed the draw, false if it was not due or already done.
     */
    suspend fun runDrawIfDue(lotteryId: String, now: Long, random: Random = Random.Default): Boolean
}

/** Reads user notifications. Notifications are written by [LotteryRepository.runDrawIfDue]. */
interface NotificationRepository {
    /** Emits the notifications for one user, newest first. */
    fun observeNotifications(recipientId: String): Flow<List<AppNotification>>

    /** Marks every notification for [recipientId] as read. */
    suspend fun markAllRead(recipientId: String)
}

/** Firestore collection names. */
object Collections {
    const val PROFILES = "profiles"
    const val CAMPSITES = "campsites"
    const val LOTTERIES = "lotteries"
    const val ENTRIES = "entries"
    const val NOTIFICATIONS = "notifications"

    /** Document ID of an entrant's entry on a lottery's waiting list. */
    fun entryId(lotteryId: String, entrantId: String) = "${lotteryId}_$entrantId"
}

private fun DocumentSnapshot.str(key: String) = getString(key) ?: ""
private fun DocumentSnapshot.long(key: String) = getLong(key) ?: 0L
private fun DocumentSnapshot.int(key: String) = (getLong(key) ?: 0L).toInt()
private inline fun <reified E : Enum<E>> DocumentSnapshot.enum(key: String, default: E): E =
    getString(key)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

/** Profile -> document fields. */
fun UserProfile.toMap(): Map<String, Any?> = mapOf(
    "deviceId" to deviceId, "name" to name, "email" to email, "phone" to phone,
    "activeRole" to activeRole.name, "isAdmin" to isAdmin, "notificationsEnabled" to notificationsEnabled,
    "createdAt" to createdAt,
)

/** Document -> profile. */
fun DocumentSnapshot.toProfile(): UserProfile = UserProfile(
    deviceId = id, name = str("name"), email = str("email"), phone = str("phone"),
    activeRole = enum("activeRole", Role.ENTRANT), isAdmin = getBoolean("isAdmin") ?: false,
    notificationsEnabled = getBoolean("notificationsEnabled") ?: true, createdAt = long("createdAt"),
)

/** Campsite -> document fields. */
fun Campsite.toMap(): Map<String, Any?> = mapOf(
    "ownerId" to ownerId, "ownerName" to ownerName, "name" to name, "nameLower" to name.lowercase(),
    "location" to location, "latitude" to latitude, "longitude" to longitude, "maxOccupancy" to maxOccupancy,
    "amenities" to amenities, "description" to description, "tags" to tags, "pricePerNight" to pricePerNight,
    "imageBase64" to imageBase64, "rating" to rating, "ratingCount" to ratingCount, "createdAt" to createdAt,
)

/** Document -> campsite. */
@Suppress("UNCHECKED_CAST")
fun DocumentSnapshot.toCampsite(): Campsite = Campsite(
    id = id, ownerId = str("ownerId"), ownerName = str("ownerName"), name = str("name"), location = str("location"),
    latitude = getDouble("latitude"), longitude = getDouble("longitude"), maxOccupancy = int("maxOccupancy"),
    amenities = str("amenities"), description = str("description"),
    tags = (get("tags") as? List<String>).orEmpty(), pricePerNight = int("pricePerNight"),
    imageBase64 = getString("imageBase64"), rating = getDouble("rating") ?: 0.0, ratingCount = int("ratingCount"),
    createdAt = long("createdAt"),
)

/** Lottery -> document fields. */
fun Lottery.toMap(): Map<String, Any?> = mapOf(
    "campsiteId" to campsiteId, "campsiteName" to campsiteName, "ownerId" to ownerId,
    "registrationStart" to registrationStart, "registrationEnd" to registrationEnd, "winnerCount" to winnerCount,
    "waitlistCap" to waitlistCap, "status" to status.name, "drawnAt" to drawnAt, "entrantCount" to entrantCount,
)

/** Document -> lottery. */
fun DocumentSnapshot.toLottery(): Lottery = Lottery(
    id = id, campsiteId = str("campsiteId"), campsiteName = str("campsiteName"), ownerId = str("ownerId"),
    registrationStart = long("registrationStart"), registrationEnd = long("registrationEnd"),
    winnerCount = int("winnerCount"), waitlistCap = getLong("waitlistCap")?.toInt(),
    status = enum("status", LotteryStatus.OPEN), drawnAt = getLong("drawnAt"), entrantCount = int("entrantCount"),
)

/** Waitlist entry -> document fields. */
fun WaitlistEntry.toMap(): Map<String, Any?> = mapOf(
    "lotteryId" to lotteryId, "entrantId" to entrantId, "entrantName" to entrantName,
    "joinedAt" to joinedAt, "status" to status.name,
)

/** Document -> waitlist entry. */
fun DocumentSnapshot.toEntry(): WaitlistEntry = WaitlistEntry(
    lotteryId = str("lotteryId"), entrantId = str("entrantId"), entrantName = str("entrantName"),
    joinedAt = long("joinedAt"), status = enum("status", EntryStatus.WAITING),
)

/** Notification -> document fields. */
fun AppNotification.toMap(): Map<String, Any?> = mapOf(
    "recipientId" to recipientId, "type" to type.name, "title" to title, "body" to body,
    "lotteryId" to lotteryId, "campsiteId" to campsiteId, "createdAt" to createdAt, "read" to read,
)

/** Document -> notification. */
fun DocumentSnapshot.toNotification(): AppNotification = AppNotification(
    id = id, recipientId = str("recipientId"), type = enum("type", NotificationType.LOTTERY_LOST),
    title = str("title"), body = str("body"), lotteryId = str("lotteryId"), campsiteId = str("campsiteId"),
    createdAt = long("createdAt"), read = getBoolean("read") ?: false,
)

/**
 * Firestore implementation of all four repositories.
 *
 * @param db the Firestore instance.
 */
class FirestoreEventsStore(private val db: FirebaseFirestore) :
    ProfileRepository, CampsiteRepository, LotteryRepository, NotificationRepository {

    private val profiles get() = db.collection(Collections.PROFILES)
    private val campsites get() = db.collection(Collections.CAMPSITES)
    private val lotteries get() = db.collection(Collections.LOTTERIES)
    private val entries get() = db.collection(Collections.ENTRIES)
    private val notifications get() = db.collection(Collections.NOTIFICATIONS)

    /** Listens to a query and maps each snapshot. Errors end the flow with the exception. */
    private fun <T> Query.listen(map: (List<DocumentSnapshot>) -> T): Flow<T> = callbackFlow {
        val reg = addSnapshotListener { snap, err ->
            if (err != null) close(err) else if (snap != null) trySend(map(snap.documents))
        }
        awaitClose { reg.remove() }
    }

    /** Listens to one document. Emits null while it does not exist. */
    private fun <T> DocumentReference.listen(map: (DocumentSnapshot) -> T): Flow<T?> = callbackFlow {
        val reg = addSnapshotListener { snap, err ->
            if (err != null) close(err) else trySend(snap?.takeIf { it.exists() }?.let(map))
        }
        awaitClose { reg.remove() }
    }

    // ---- Profiles ----

    override fun observeProfile(deviceId: String): Flow<UserProfile?> = profiles.document(deviceId).listen { it.toProfile() }

    override suspend fun saveProfile(profile: UserProfile) {
        profiles.document(profile.deviceId).set(profile.toMap()).await()
    }

    // ---- Campsites ----

    override fun observeCampsites(): Flow<List<Campsite>> =
        campsites.orderBy("nameLower").listen { docs -> docs.map { it.toCampsite() } }

    override fun observeCampsite(id: String): Flow<Campsite?> = campsites.document(id).listen { it.toCampsite() }

    override fun observeCampsitesByOwner(ownerId: String): Flow<List<Campsite>> =
        campsites.whereEqualTo("ownerId", ownerId).listen { docs -> docs.map { it.toCampsite() }.sortedByDescending { it.createdAt } }

    override suspend fun createCampsite(campsite: Campsite): String {
        val ref = campsites.document()
        ref.set(campsite.copy(id = ref.id).toMap()).await()
        return ref.id
    }

    override suspend fun updateCampsite(campsite: Campsite) {
        campsites.document(campsite.id).set(campsite.toMap()).await()
    }

    override suspend fun deleteCampsite(id: String) {
        val lotteryDocs = lotteries.whereEqualTo("campsiteId", id).get().await().documents
        val batch = db.batch()
        for (lot in lotteryDocs) {
            entries.whereEqualTo("lotteryId", lot.id).get().await().documents.forEach { batch.delete(it.reference) }
            batch.delete(lot.reference)
        }
        batch.delete(campsites.document(id))
        batch.commit().await()
    }

    // ---- Lotteries ----

    override fun observeLotteries(): Flow<List<Lottery>> = lotteries.listen { docs -> docs.map { it.toLottery() } }

    override fun observeLottery(id: String): Flow<Lottery?> = lotteries.document(id).listen { it.toLottery() }

    override fun observeLotteriesForCampsite(campsiteId: String): Flow<List<Lottery>> =
        lotteries.whereEqualTo("campsiteId", campsiteId)
            .listen { docs -> docs.map { it.toLottery() }.sortedByDescending { it.registrationStart } }

    override fun observeLotteriesByOwner(ownerId: String): Flow<List<Lottery>> =
        lotteries.whereEqualTo("ownerId", ownerId)
            .listen { docs -> docs.map { it.toLottery() }.sortedByDescending { it.registrationStart } }

    override suspend fun createLottery(lottery: Lottery): String {
        val ref = lotteries.document()
        ref.set(lottery.copy(id = ref.id, entrantCount = 0).toMap()).await()
        return ref.id
    }

    override fun observeEntries(lotteryId: String): Flow<List<WaitlistEntry>> =
        entries.whereEqualTo("lotteryId", lotteryId).listen { docs -> docs.map { it.toEntry() }.sortedBy { it.joinedAt } }

    override fun observeEntriesForEntrant(entrantId: String): Flow<List<WaitlistEntry>> =
        entries.whereEqualTo("entrantId", entrantId).listen { docs -> docs.map { it.toEntry() } }

    override suspend fun joinWaitlist(lotteryId: String, entrantId: String, entrantName: String, now: Long) {
        val lotRef = lotteries.document(lotteryId)
        val entryRef = entries.document(Collections.entryId(lotteryId, entrantId))
        db.runTransaction { t ->
            val lottery = t.get(lotRef).toLottery()
            val joined = t.get(entryRef).exists()
            WaitlistPolicy.checkJoin(lottery, joined, lottery.entrantCount, now)?.let { throw WaitlistException(it) }
            t.set(entryRef, WaitlistEntry(lotteryId, entrantId, entrantName, now).toMap())
            t.update(lotRef, "entrantCount", FieldValue.increment(1))
        }.await()
    }

    override suspend fun leaveWaitlist(lotteryId: String, entrantId: String, now: Long) {
        val lotRef = lotteries.document(lotteryId)
        val entryRef = entries.document(Collections.entryId(lotteryId, entrantId))
        db.runTransaction { t ->
            val lottery = t.get(lotRef).toLottery()
            val joined = t.get(entryRef).exists()
            WaitlistPolicy.checkLeave(lottery, joined, now)?.let { throw WaitlistException(it) }
            t.delete(entryRef)
            t.update(lotRef, "entrantCount", FieldValue.increment(-1))
        }.await()
    }

    override suspend fun runDrawIfDue(lotteryId: String, now: Long, random: Random): Boolean {
        val lotRef = lotteries.document(lotteryId)
        // Registration is closed once a draw is due, so the waiting list can no longer change.
        val waitlist = entries.whereEqualTo("lotteryId", lotteryId).get().await().documents.map { it.toEntry() }
        return db.runTransaction { t ->
            val lottery = t.get(lotRef).toLottery()
            if (!lottery.isDrawDue(now)) return@runTransaction false
            val result = LotteryDrawer.draw(lottery, waitlist, now, random)
            result.entries.forEach { e ->
                t.update(entries.document(Collections.entryId(lotteryId, e.entrantId)), "status", e.status.name)
            }
            result.notifications.forEach { n -> t.set(notifications.document(), n.toMap()) }
            t.update(lotRef, mapOf("status" to LotteryStatus.DRAWN.name, "drawnAt" to now))
            true
        }.await()
    }

    // ---- Notifications ----

    override fun observeNotifications(recipientId: String): Flow<List<AppNotification>> =
        notifications.whereEqualTo("recipientId", recipientId)
            .listen { docs -> docs.map { it.toNotification() }.sortedByDescending { it.createdAt } }

    override suspend fun markAllRead(recipientId: String) {
        val unread = notifications.whereEqualTo("recipientId", recipientId).get().await().documents
            .filter { it.getBoolean("read") != true }
        if (unread.isEmpty()) return
        val batch = db.batch()
        unread.forEach { batch.update(it.reference, "read", true) }
        batch.commit().await()
    }
}

/** Builds the sample data set. */
object SampleData {
    /** Device ID of the sample owner, "Parks Lodge". */
    const val OWNER_ID = "owner-parks-lodge"

    private const val MINUTE = 60_000L
    private const val DAY = 24 * 60 * MINUTE

    private val entrantNames = listOf(
        "Maya Chen", "Liam Okafor", "Sofia Rossi", "Noah Singh", "Ethan Moreau", "Priya Patel", "Akshat Kumar", "Jordan Lee",
    )

    /** The sample owner profile. */
    fun owner(now: Long) = UserProfile(OWNER_ID, "Parks Lodge", "hello@parkslodge.ca", "+1 403 555 0199", createdAt = now - 90 * DAY)

    /** Sample campsites from the Figma mockups. */
    fun campsites(now: Long): List<Campsite> = listOf(
        Campsite("two-jack", OWNER_ID, "Parks Lodge", "Two Jack Lakeside", "Banff, AB", 51.2316, -115.4983, 6,
            "Fire pits, potable water, flush toilets, bear lockers, canoe launch",
            "Lakeside sites under Mount Rundle with fire pits, potable water and flush toilets. Canoe launch 2 min away.",
            listOf("Lakeside", "Fire pits", "Pet friendly"), 65, rating = 4.8, ratingCount = 126, createdAt = now - 60 * DAY),
        Campsite("tunnel-mountain", OWNER_ID, "Parks Lodge", "Tunnel Mountain Village", "Banff, AB", 51.1891, -115.5376, 8,
            "Showers, power hookups, Wi-Fi at the hub", "Forested sites five minutes from downtown Banff, with showers and hookups.",
            listOf("Mountain", "Wi-Fi", "Family friendly"), 42, rating = 4.6, ratingCount = 342, createdAt = now - 55 * DAY),
        Campsite("lake-louise", OWNER_ID, "Parks Lodge", "Lake Louise Tent Sites", "Lake Louise, AB", 51.4194, -116.1795, 4,
            "Electric-fenced tent area, food lockers, flush toilets", "Tent-only sites inside an electric fence along the Bow River.",
            listOf("Riverside", "Quiet hours"), 38, rating = 4.9, ratingCount = 211, createdAt = now - 50 * DAY),
        Campsite("johnston-canyon", OWNER_ID, "Parks Lodge", "Johnston Canyon", "Banff, AB", 51.2453, -115.8389, 6,
            "Firewood, potable water", "Steps from the canyon trail and its waterfalls.",
            listOf("Forest", "Fire pits"), 36, rating = 4.7, ratingCount = 98, createdAt = now - 40 * DAY),
        Campsite("moraine-lake", OWNER_ID, "Parks Lodge", "Moraine Lake Sites", "Lake Louise, AB", 51.3217, -116.1860, 4,
            "Vault toilets, bear lockers", "Small walk-in sites near the Valley of the Ten Peaks.",
            listOf("Lakeside", "Mountain"), 48, rating = 4.9, ratingCount = 64, createdAt = now - 30 * DAY),
        Campsite("waterfowl-lakes", OWNER_ID, "Parks Lodge", "Waterfowl Lakes", "Icefields Pkwy, AB", 51.8380, -116.6170, 6,
            "Kitchen shelter, potable water", "Glacier-fed lakes along the Icefields Parkway.",
            listOf("Lakeside", "Quiet hours"), 30, rating = 4.5, ratingCount = 51, createdAt = now - 20 * DAY),
    )

    /** Builds the complete starting snapshot for [now]. */
    fun snapshot(now: Long): EventsSnapshot {
        val sites = campsites(now).associateBy { it.id }
        val lotteries = listOf(
            Lottery("lot-two-jack", "two-jack", "Two Jack Lakeside", OWNER_ID, now - 2 * DAY, now + 3 * DAY, 8),
            Lottery("lot-lake-louise", "lake-louise", "Lake Louise Tent Sites", OWNER_ID, now - 4 * DAY, now + 2 * MINUTE, 3),
            Lottery("lot-moraine", "moraine-lake", "Moraine Lake Sites", OWNER_ID, now + 2 * DAY, now + 9 * DAY, 4),
            Lottery("lot-johnston", "johnston-canyon", "Johnston Canyon", OWNER_ID, now - 20 * DAY, now - 6 * DAY, 2,
                status = LotteryStatus.DRAWN, drawnAt = now - 6 * DAY),
        )
        val entries = buildList {
            entrantNames.take(6).forEachIndexed { i, n -> add(WaitlistEntry("lot-two-jack", "sample-entrant-$i", n, now - 2 * DAY + i * 3_600_000L)) }
            entrantNames.take(5).forEachIndexed { i, n -> add(WaitlistEntry("lot-lake-louise", "sample-entrant-$i", n, now - 3 * DAY + i * 3_600_000L)) }
            entrantNames.take(4).forEachIndexed { i, n ->
                add(WaitlistEntry("lot-johnston", "sample-entrant-$i", n, now - 15 * DAY, if (i < 2) EntryStatus.WON else EntryStatus.LOST))
            }
        }
        val counted = lotteries.associate { lot -> lot.id to lot.copy(entrantCount = entries.count { it.lotteryId == lot.id }) }
        return EventsSnapshot(
            profiles = mapOf(OWNER_ID to owner(now)),
            campsites = sites,
            lotteries = counted,
            entries = entries,
        )
    }
}

/**
 * Everything the in-memory store holds, as one immutable snapshot.
 */
data class EventsSnapshot(
    val profiles: Map<String, UserProfile> = emptyMap(),
    val campsites: Map<String, Campsite> = emptyMap(),
    val lotteries: Map<String, Lottery> = emptyMap(),
    val entries: List<WaitlistEntry> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
)

/**
 * In-memory implementation of all four repositories.
 *
 * @param initial starting data, e.g. [SampleData.snapshot].
 */
class InMemoryEventsStore(initial: EventsSnapshot = EventsSnapshot()) :
    ProfileRepository, CampsiteRepository, LotteryRepository, NotificationRepository {

    private val state = MutableStateFlow(initial)
    private val ids = AtomicLong(1000)

    /** The current contents, for test assertions. */
    val snapshot: EventsSnapshot get() = state.value

    private fun newId(prefix: String) = "$prefix${ids.incrementAndGet()}"

    private fun <T> select(block: (EventsSnapshot) -> T): Flow<T> = state.map(block).distinctUntilChanged()

    // ---- Profiles ----

    override fun observeProfile(deviceId: String): Flow<UserProfile?> = select { it.profiles[deviceId] }

    override suspend fun saveProfile(profile: UserProfile) {
        state.update { it.copy(profiles = it.profiles + (profile.deviceId to profile)) }
    }

    // ---- Campsites ----

    override fun observeCampsites(): Flow<List<Campsite>> = select { s -> s.campsites.values.sortedBy { it.name } }

    override fun observeCampsite(id: String): Flow<Campsite?> = select { it.campsites[id] }

    override fun observeCampsitesByOwner(ownerId: String): Flow<List<Campsite>> =
        select { s -> s.campsites.values.filter { it.ownerId == ownerId }.sortedByDescending { it.createdAt } }

    override suspend fun createCampsite(campsite: Campsite): String {
        val id = newId("site")
        state.update { it.copy(campsites = it.campsites + (id to campsite.copy(id = id))) }
        return id
    }

    override suspend fun updateCampsite(campsite: Campsite) {
        state.update { s ->
            require(campsite.id in s.campsites) { "No campsite ${campsite.id}" }
            s.copy(campsites = s.campsites + (campsite.id to campsite))
        }
    }

    override suspend fun deleteCampsite(id: String) {
        state.update { s ->
            val lotteryIds = s.lotteries.values.filter { it.campsiteId == id }.map { it.id }.toSet()
            s.copy(
                campsites = s.campsites - id,
                lotteries = s.lotteries - lotteryIds,
                entries = s.entries.filterNot { it.lotteryId in lotteryIds },
            )
        }
    }

    // ---- Lotteries ----

    override fun observeLotteries(): Flow<List<Lottery>> = select { it.lotteries.values.toList() }

    override fun observeLottery(id: String): Flow<Lottery?> = select { it.lotteries[id] }

    override fun observeLotteriesForCampsite(campsiteId: String): Flow<List<Lottery>> =
        select { s -> s.lotteries.values.filter { it.campsiteId == campsiteId }.sortedByDescending { it.registrationStart } }

    override fun observeLotteriesByOwner(ownerId: String): Flow<List<Lottery>> =
        select { s -> s.lotteries.values.filter { it.ownerId == ownerId }.sortedByDescending { it.registrationStart } }

    override suspend fun createLottery(lottery: Lottery): String {
        val id = newId("lot")
        state.update { it.copy(lotteries = it.lotteries + (id to lottery.copy(id = id, entrantCount = 0))) }
        return id
    }

    override fun observeEntries(lotteryId: String): Flow<List<WaitlistEntry>> =
        select { s -> s.entries.filter { it.lotteryId == lotteryId }.sortedBy { it.joinedAt } }

    override fun observeEntriesForEntrant(entrantId: String): Flow<List<WaitlistEntry>> =
        select { s -> s.entries.filter { it.entrantId == entrantId } }

    override suspend fun joinWaitlist(lotteryId: String, entrantId: String, entrantName: String, now: Long) {
        state.update { s ->
            val lottery = s.lotteries[lotteryId] ?: error("No lottery $lotteryId")
            val list = s.entries.filter { it.lotteryId == lotteryId }
            WaitlistPolicy.checkJoin(lottery, list.any { it.entrantId == entrantId }, list.size, now)
                ?.let { throw WaitlistException(it) }
            s.copy(
                entries = s.entries + WaitlistEntry(lotteryId, entrantId, entrantName, now),
                lotteries = s.lotteries + (lotteryId to lottery.copy(entrantCount = list.size + 1)),
            )
        }
    }

    override suspend fun leaveWaitlist(lotteryId: String, entrantId: String, now: Long) {
        state.update { s ->
            val lottery = s.lotteries[lotteryId] ?: error("No lottery $lotteryId")
            val list = s.entries.filter { it.lotteryId == lotteryId }
            WaitlistPolicy.checkLeave(lottery, list.any { it.entrantId == entrantId }, now)
                ?.let { throw WaitlistException(it) }
            s.copy(
                entries = s.entries.filterNot { it.lotteryId == lotteryId && it.entrantId == entrantId },
                lotteries = s.lotteries + (lotteryId to lottery.copy(entrantCount = list.size - 1)),
            )
        }
    }

    override suspend fun runDrawIfDue(lotteryId: String, now: Long, random: Random): Boolean {
        var drew = false
        state.update { s ->
            drew = false
            val lottery = s.lotteries[lotteryId] ?: return@update s
            if (!lottery.isDrawDue(now)) return@update s
            val list = s.entries.filter { it.lotteryId == lotteryId }
            val result = LotteryDrawer.draw(lottery, list, now, random)
            drew = true
            s.copy(
                lotteries = s.lotteries + (lotteryId to lottery.copy(status = LotteryStatus.DRAWN, drawnAt = now)),
                entries = s.entries.filterNot { it.lotteryId == lotteryId } + result.entries,
                notifications = s.notifications + result.notifications.map { it.copy(id = newId("note")) },
            )
        }
        return drew
    }

    // ---- Notifications ----

    override fun observeNotifications(recipientId: String): Flow<List<AppNotification>> =
        select { s -> s.notifications.filter { it.recipientId == recipientId }.sortedByDescending { it.createdAt } }

    override suspend fun markAllRead(recipientId: String) {
        state.update { s ->
            s.copy(notifications = s.notifications.map { if (it.recipientId == recipientId) it.copy(read = true) else it })
        }
    }
}

/**
 * The dependencies every screen can use.
 *
 * @property deviceId stable ID of this device; the user's profile ID (US 01.09.01).
 * @property profiles profile data.
 * @property campsites campsite data.
 * @property lotteries lottery and waiting-list data.
 * @property notifications notification data.
 * @property clock time source.
 * @property usingServer true when connected to Firestore, false on sample data.
 */
open class AppContainer(
    val deviceId: String,
    val profiles: ProfileRepository,
    val campsites: CampsiteRepository,
    val lotteries: LotteryRepository,
    val notifications: NotificationRepository,
    val clock: Clock,
    val usingServer: Boolean,
) {
    /** Runs automatic lottery draws while the app is open. */
    val drawScheduler: DrawScheduler by lazy { DrawScheduler(lotteries, clock) }

    companion object {
        /** The app's container, set up by MainActivity.setupDataLayer(). */
        lateinit var instance: AppContainer
            private set

        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var drawsStarted = false

        /** Creates the container the first time; later calls keep the same one. */
        fun init(context: Context): AppContainer {
            if (!::instance.isInitialized) instance = create(context.applicationContext)
            return instance
        }

        /** Starts the automatic lottery draws once for the life of the app. */
        fun startDraws() {
            if (drawsStarted) return
            drawsStarted = true
            instance.drawScheduler.start(appScope)
        }

        /**
         * Builds the production container: Firestore if Firebase is configured, otherwise
         * an in-memory store seeded with [SampleData].
         */
        fun create(context: Context): AppContainer {
            val deviceId = readDeviceId(context)
            val firebase = FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context)
            return if (firebase != null) {
                val store = FirestoreEventsStore(FirebaseFirestore.getInstance(firebase))
                AppContainer(deviceId, store, store, store, store, Clock.System, usingServer = true)
            } else {
                inMemory(deviceId, Clock.System)
            }
        }

        /** A container on sample data, for demos and tests. */
        fun inMemory(deviceId: String, clock: Clock, store: InMemoryEventsStore = InMemoryEventsStore(SampleData.snapshot(clock.now()))) =
            AppContainer(deviceId, store, store, store, store, clock, usingServer = false)

        /**
         * The Android ID: unique per device, user and app signing key, and stable across
         * reinstalls. It identifies the user without a username or password.
         */
        @SuppressLint("HardwareIds")
        private fun readDeviceId(context: Context): String =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?.takeIf { it.isNotBlank() } ?: "unknown-device"
    }
}
