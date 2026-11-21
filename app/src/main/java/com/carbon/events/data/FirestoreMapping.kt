/*
 * FirestoreMapping.kt (Vaibhav)
 *
 * Turning the app's data into Firestore documents and back: times as timestamps, days as
 * readable dates, enums as lower-case words.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

// ---- Value conversions: the app uses epoch ms and day numbers; Firestore stores readable types ----

internal fun Long.toTimestamp() = Timestamp(Math.floorDiv(this, 1000L), (Math.floorMod(this, 1000L) * 1_000_000).toInt())

private fun Timestamp.toMillis() = seconds * 1000 + nanoseconds / 1_000_000

internal fun Long.toIsoDay(): String = java.time.LocalDate.ofEpochDay(this).toString()

private fun String.toDayNumber(): Long? = runCatching { java.time.LocalDate.parse(this).toEpochDay() }.getOrNull()

internal fun Enum<*>.word() = name.lowercase()

private fun DocumentSnapshot.str(key: String) = getString(key) ?: ""

private fun DocumentSnapshot.int(key: String) = (getLong(key) ?: 0L).toInt()

internal fun DocumentSnapshot.time(key: String): Long = getTimestamp(key)?.toMillis() ?: 0L

private fun DocumentSnapshot.timeOrNull(key: String): Long? = getTimestamp(key)?.toMillis()

internal fun DocumentSnapshot.day(key: String): Long? = getString(key)?.toDayNumber()

internal inline fun <reified E : Enum<E>> DocumentSnapshot.enum(key: String, default: E): E =
    getString(key)?.let { word -> enumValues<E>().firstOrNull { it.name.equals(word, ignoreCase = true) } } ?: default

/** Profile -> `users` document. */
fun UserProfile.toMap(): Map<String, Any?> = mapOf(
    "authUid" to authUid, "name" to name, "email" to email, "phone" to phone, "age" to age,
    "role" to activeRole.word(), "isAdmin" to isAdmin, "createdAt" to createdAt.toTimestamp(),
    "photo" to photoBase64, "blocked" to blocked, "warnings" to warnings,
    "prefs" to prefs.toMap(), "removed" to removed,
    "signedOutAt" to signedOutAt.takeIf { it > 0 }?.toTimestamp(),
)

/** `users` document -> profile. */
fun DocumentSnapshot.toProfile(): UserProfile = UserProfile(
    id = id, name = str("name"), email = str("email"), phone = str("phone"),
    activeRole = enum("role", Role.ENTRANT), isAdmin = getBoolean("isAdmin") ?: false, createdAt = time("createdAt"),
    authUid = str("authUid"), age = getLong("age")?.toInt(),
    photoBase64 = getString("photo"), blocked = getBoolean("blocked") ?: false, warnings = int("warnings"),
    prefs = NotificationPrefs.from { getBoolean("prefs.$it") }, removed = getBoolean("removed") ?: false,
    signedOutAt = time("signedOutAt"),
)

/** Trusted phone -> `devices` document. */
fun TrustedDevice.toMap(): Map<String, Any?> = mapOf("userId" to userId, "model" to model, "addedAt" to addedAt.toTimestamp(), "blocked" to blocked)

/** `devices` document -> trusted phone. */
fun DocumentSnapshot.toDevice(): TrustedDevice = TrustedDevice(id, str("userId"), str("model"), time("addedAt"), getBoolean("blocked") ?: false)

/** Campsite -> `campsites` document. */
fun Campsite.toMap(): Map<String, Any?> = mapOf(
    "ownerId" to ownerId, "ownerName" to ownerName, "name" to name, "nameLower" to name.lowercase(),
    "location" to location, "latitude" to latitude, "longitude" to longitude, "maxGuests" to maxOccupancy,
    "amenities" to amenities, "description" to description, "tags" to tags, "pricePerNight" to pricePerNight,
    "photo" to imageBase64, "rating" to rating, "ratingCount" to ratingCount, "createdAt" to createdAt.toTimestamp(),
    "checkInTime" to checkInTime, "checkOutTime" to checkOutTime, "minNights" to minNights, "maxNights" to maxNights,
    "rules" to rules, "cancellation" to cancellation.name.lowercase(),
)

/** `campsites` document -> campsite. */
@Suppress("UNCHECKED_CAST")
fun DocumentSnapshot.toCampsite(): Campsite = Campsite(
    id = id, ownerId = str("ownerId"), ownerName = str("ownerName"), name = str("name"), location = str("location"),
    latitude = getDouble("latitude"), longitude = getDouble("longitude"), maxOccupancy = int("maxGuests"),
    amenities = str("amenities"), description = str("description"),
    tags = (get("tags") as? List<String>).orEmpty(), pricePerNight = int("pricePerNight"),
    imageBase64 = getString("photo"), rating = getDouble("rating") ?: 0.0, ratingCount = int("ratingCount"),
    createdAt = time("createdAt"),
    checkInTime = getString("checkInTime") ?: "3:00 PM", checkOutTime = getString("checkOutTime") ?: "11:00 AM",
    minNights = getLong("minNights")?.toInt() ?: 1, maxNights = getLong("maxNights")?.toInt() ?: 7, rules = str("rules"),
    cancellation = CancellationPolicy.of(getString("cancellation")),
)

/** Lottery -> `lotteries` document. */
fun Lottery.toMap(): Map<String, Any?> = mapOf(
    "campsiteId" to campsiteId, "campsiteName" to campsiteName, "ownerId" to ownerId,
    "opensAt" to registrationStart.toTimestamp(), "closesAt" to registrationEnd.toTimestamp(), "winnerCount" to winnerCount, "waitlistCap" to waitlistCap,
    "stayFrom" to bookingStartDay?.toIsoDay(), "stayUntil" to bookingEndDay?.toIsoDay(),
    "status" to status.word(), "drawnAt" to drawnAt?.toTimestamp(), "entrantCount" to entrantCount, "spotsFree" to spotsFree,
    "groupId" to groupId,
)

/** `lotteries` document -> lottery (its app ID is "campsiteId/lotteryId"). */
fun DocumentSnapshot.toLottery(): Lottery = Lottery(
    id = Paths.lotteryId(reference.parent.parent?.id ?: str("campsiteId"), id),
    campsiteId = str("campsiteId"), campsiteName = str("campsiteName"), ownerId = str("ownerId"),
    registrationStart = time("opensAt"), registrationEnd = time("closesAt"), winnerCount = int("winnerCount"), waitlistCap = getLong("waitlistCap")?.toInt(),
    status = enum("status", LotteryStatus.OPEN), drawnAt = timeOrNull("drawnAt"), entrantCount = int("entrantCount"),
    bookingStartDay = day("stayFrom"), bookingEndDay = day("stayUntil"), spotsFree = int("spotsFree"),
    groupId = str("groupId"),
)

/** Waiting-list entry -> `entrants` document (keyed by the user ID). */
fun WaitlistEntry.toMap(): Map<String, Any?> = mapOf(
    "userId" to entrantId, "userName" to entrantName, "joinedAt" to joinedAt.toTimestamp(), "status" to status.word(),
    "checkIn" to checkInDay?.toIsoDay(), "checkOut" to checkOutDay?.toIsoDay(), "bookedAt" to bookedAt?.toTimestamp(),
    "campsiteId" to campsiteId, "campsiteName" to campsiteName, "lotteryId" to Paths.keyOf(lotteryId), "ownerId" to ownerId,
    "guests" to guests, "agreedToRules" to agreedToRules, "note" to note, "endedAt" to endedAt?.toTimestamp(),
)

/** `entrants` document -> waiting-list entry. */
/**
 * What stays of a person an admin removed: their name and email so admins know who it was,
 * blocked and marked removed; contact phone and photo are cleared.
 */
internal fun UserProfile.removedRecord(): UserProfile = copy(blocked = true, removed = true, phone = "", photoBase64 = null)

/** A won spot whose stay hasn't finished (or was never booked) goes back when its winner's profile is deleted. */
internal fun WaitlistEntry.givesSpotBackOnDelete(now: Long): Boolean =
    status == EntryStatus.WON && (checkOutDay == null || checkOutDay >= dayOf(now))

fun DocumentSnapshot.toEntry(): WaitlistEntry {
    val lotteryRef = reference.parent.parent
    val campsiteId = lotteryRef?.parent?.parent?.id ?: str("campsiteId")
    return WaitlistEntry(
        lotteryId = Paths.lotteryId(campsiteId, lotteryRef?.id ?: str("lotteryId")), entrantId = id, entrantName = str("userName"),
        joinedAt = time("joinedAt"), status = enum("status", EntryStatus.WAITING),
        checkInDay = day("checkIn"), checkOutDay = day("checkOut"), bookedAt = timeOrNull("bookedAt"),
        campsiteId = campsiteId, campsiteName = str("campsiteName"), ownerId = str("ownerId"),
        guests = getLong("guests")?.toInt(), agreedToRules = getBoolean("agreedToRules") ?: false,
        note = str("note"), endedAt = timeOrNull("endedAt"),
    )
}

/** Notification -> `notifications` document under its recipient. */
fun AppNotification.toMap(): Map<String, Any?> = mapOf(
    "type" to type.word, "title" to title, "body" to body,
    "campsiteId" to campsiteId, "lotteryId" to Paths.keyOf(lotteryId), "createdAt" to createdAt.toTimestamp(), "read" to read,
)

/** `notifications` document -> notification. */
fun DocumentSnapshot.toNotification(): AppNotification = AppNotification(
    id = id, recipientId = reference.parent.parent?.id.orEmpty(),
    type = NotificationType.of(getString("type")),
    title = str("title"), body = str("body"), lotteryId = Paths.lotteryId(str("campsiteId"), str("lotteryId")), campsiteId = str("campsiteId"),
    createdAt = time("createdAt"), read = getBoolean("read") ?: false,
)

/** Rating -> `ratings` document (keyed by the rater's user ID). */
fun Rating.toMap(): Map<String, Any?> = mapOf(
    "userName" to userName, "stars" to stars, "comment" to comment, "lotteryId" to Paths.keyOf(lotteryId), "createdAt" to createdAt.toTimestamp(),
)

/** `ratings` document -> rating. */
fun DocumentSnapshot.toRating(): Rating {
    val campsiteId = reference.parent.parent?.id.orEmpty()
    return Rating(campsiteId, id, str("userName"), int("stars"), str("comment"), Paths.lotteryId(campsiteId, str("lotteryId")), time("createdAt"))
}

/** Chat -> `chats` document. */
fun Chat.toMap(): Map<String, Any?> = mapOf(
    "campsiteId" to campsiteId, "campsiteName" to campsiteName, "ownerId" to ownerId, "ownerName" to ownerName,
    "entrantId" to entrantId, "entrantName" to entrantName, "lastText" to lastText, "lastAt" to lastAt.toTimestamp(),
    "lastSenderId" to lastSenderId, "unreadForOwner" to unreadForOwner, "unreadForEntrant" to unreadForEntrant,
    "lotteryId" to lotteryKey,
)

/** `chats` document -> chat. */
fun DocumentSnapshot.toChat(): Chat = Chat(
    id, str("campsiteId"), str("campsiteName"), str("ownerId"), str("ownerName"), str("entrantId"), str("entrantName"),
    str("lastText"), time("lastAt"), str("lastSenderId"), unreadOf("unreadForOwner"), unreadOf("unreadForEntrant"),
    str("lotteryId"),
)

/** An unread counter; older chats stored true/false, which reads as 1/0. */
private fun DocumentSnapshot.unreadOf(field: String): Int = when (val v = get(field)) {
    is Number -> v.toInt().coerceAtLeast(0)
    is Boolean -> if (v) 1 else 0
    else -> 0
}

/** `messages` document -> chat message. */
fun DocumentSnapshot.toMessage(): ChatMessage =
    ChatMessage(id, reference.parent.parent?.id.orEmpty(), str("senderId"), str("senderName"), str("text"), time("sentAt"), getBoolean("announcement") ?: false, getString("photo"),
        reactions = (get("reactions") as? Map<*, *>).orEmpty().mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }.toMap())

/** Announcement -> `announcements` document under its lottery. */
fun Announcement.toMap(): Map<String, Any?> = mapOf(
    "campsiteId" to campsiteId, "campsiteName" to campsiteName, "ownerId" to ownerId, "text" to text,
    "audience" to audience.word(), "recipientCount" to recipientCount, "sentAt" to sentAt.toTimestamp(),
)

/** `announcements` document -> announcement. */
fun DocumentSnapshot.toAnnouncement(): Announcement {
    val lotteryKey = reference.parent.parent?.id.orEmpty()
    return Announcement(
        id, Paths.lotteryId(str("campsiteId"), lotteryKey), str("campsiteId"), str("campsiteName"), str("ownerId"), str("text"),
        enum("audience", Audience.ALL), int("recipientCount"), time("sentAt"),
    )
}

/** Report -> `reports` document. */
fun Report.toMap(): Map<String, Any?> = mapOf(
    "campsiteId" to campsiteId, "campsiteName" to campsiteName, "reporterId" to reporterId, "reporterName" to reporterName,
    "reason" to reason, "createdAt" to createdAt.toTimestamp(),
)

/** `reports` document -> report. */
fun DocumentSnapshot.toReport(): Report =
    Report(id, str("campsiteId"), str("campsiteName"), str("reporterId"), str("reporterName"), str("reason"), time("createdAt"))
