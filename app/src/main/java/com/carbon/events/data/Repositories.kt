/*
 * Repositories.kt (Vaibhav)
 *
 * What the screens can read and change (profiles, campsites, lotteries, notifications), the
 * Firestore collection names, and readable IDs.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Reads and writes user profiles and the phones they trust. */
interface ProfileRepository {
    /** Emits the user ID this phone is trusted for, or null if it isn't trusted. */
    fun observeUserIdForDevice(deviceId: String): Flow<String?>

    /** Emits the profile that login account [authUid] signs in as, or null if it has none yet. */
    fun observeProfileForAccount(authUid: String): Flow<UserProfile?>

    /**
     * Creates the profile for a new account and, if [trust] is given, trusts that phone so it
     * stays signed in (Figma S-TRUST).
     * @return the new user ID.
     */
    suspend fun createProfile(profile: UserProfile, trust: TrustedDevice?): String

    /** Trusts a phone for a person, so it stays signed in next time. */
    suspend fun trustDevice(device: TrustedDevice)

    /** Emits the phones [userId] trusts, oldest first. */
    fun observeDevices(userId: String): Flow<List<TrustedDevice>>

    /** Stops trusting a phone. */
    suspend fun removeDevice(deviceId: String)

    /** Stops trusting every phone for [userId] ("Log out of all devices"). */
    suspend fun removeAllDevices(userId: String)

    /**
     * Who this phone is trusted for right now. The app asks this when it opens, so a phone whose
     * trust was removed on another phone isn't let in by an old saved copy.
     */
    suspend fun trustedUserIdNow(deviceId: String): String? = observeUserIdForDevice(deviceId).first()

    /**
     * Marks [userId]'s profile at [now] so every phone that has the app open signs out, trusted or
     * not ("Log out of all devices", after [removeAllDevices]).
     */
    suspend fun signOutEverywhere(userId: String, now: Long) {
        val profile = observeProfile(userId).first() ?: return
        saveProfile(profile.copy(signedOutAt = now))
    }

    /** Emits the profile for [userId], or null if none exists, and every later change. */
    fun observeProfile(userId: String): Flow<UserProfile?>

    /** Creates or replaces [profile]. */
    suspend fun saveProfile(profile: UserProfile)

    /**
     * Deletes a person and everything that is only theirs: their profile, login lookup, trusted
     * phones, inbox and campsites (with those campsites' lotteries). They also leave the waiting
     * lists that are still open and give back any spot they won but haven't stayed in yet, so it
     * can go to someone else. Finished stays and old results stay in the owners' records.
     *
     * @param now the current time, to tell upcoming stays from finished ones.
     */
    suspend fun deleteProfile(profile: UserProfile, now: Long = System.currentTimeMillis())

    /**
     * An admin removing [profile] for good (US 03.02.01, US 03.07.01). Everything that is theirs
     * goes, as with [deleteProfile], but a removed, blocked record stays on their login and the
     * phones they used are blocked, so they can't use the app or sign up on those phones again.
     */
    suspend fun removeProfile(profile: UserProfile, now: Long = System.currentTimeMillis())

    /** Undoes [removeProfile]: the person and their phones can use the app again. */
    suspend fun restoreProfile(profile: UserProfile)

    /** Whether [deviceId] was used by someone an admin removed, so it can't be signed up or trusted on. */
    suspend fun isPhoneBlocked(deviceId: String): Boolean

    /** Emits every profile, sorted by name, for the admin browser (US 03.05.01). */
    fun observeProfiles(): Flow<List<UserProfile>>
}

/** Reads and writes campsites. */
interface CampsiteRepository {
    /** Emits every campsite, and every later change. */
    fun observeCampsites(): Flow<List<Campsite>>

    /** Emits the campsite with [id], or null if it does not exist (e.g. removed). */
    fun observeCampsite(id: String): Flow<Campsite?>

    /** Emits the campsites owned by [ownerId]; callers should apply their required display order. */
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

    /** Emits lotteries that haven't been drawn yet (what the automatic draw watches). */
    fun observeOpenLotteries(): Flow<List<Lottery>>

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

    /**
     * Saves an owner's changes to a lottery (US 02.04.06). Only the dates, winner count, cap and
     * booking period change; the campsite, owner, status and entrant count are kept.
     * @throws LotteryEditException if [LotteryEditPolicy] refuses the change.
     */
    suspend fun updateLottery(updated: Lottery, now: Long)

    /**
     * Books a winner's nights inside the lottery's booking period. Fails if the entrant did not
     * win, already booked, or the nights overlap another winner in the same lottery.
     *
     * Dates are LocalDate epoch days; check-out is exclusive. The current repository contract
     * does not prevent conflicts across different lotteries for one campsite. Guest limits,
     * minimum stays and rule acceptance are currently validated by BookDatesViewModel.
     * @param now booking timestamp in epoch milliseconds (not an epoch day).
     * @param guests party size recorded on the reservation.
     * @param agreedToRules acceptance flag supplied by the booking form.
     * @throws BookingException if [BookingPolicy] refuses it.
     */
    suspend fun bookDates(
        lotteryId: String, entrantId: String, checkInDay: Long, checkOutDay: Long, now: Long,
        guests: Int = 1, agreedToRules: Boolean = true,
    )

    /**
     * A winner gives their spot back: [EntryStatus.DECLINED] before booking, or
     * [EntryStatus.CANCELLED] after. The owner's phone then offers the spot to someone else.
     * @throws BookingException with [BookingError.NOT_A_WINNER] if the entrant isn't a current winner.
     */
    suspend fun giveUpSpot(lotteryId: String, entrantId: String, now: Long)

    /** The owner rejects a winner's reservation with a [reason]; the guest is told why and the spot is offered again. */
    suspend fun rejectReservation(lotteryId: String, entrantId: String, reason: String, now: Long)

    /**
     * Offers given-back spots to entrants who weren't selected, at random, and tells them
     * (US 02.05.03). Run by the owner's or an admin's phone.
     * @return how many spots were filled.
     */
    suspend fun fillFreeSpots(lotteryId: String, now: Long, random: Random = Random.Default): Int

    /** Emits the owner's cancelled and rejected reservations, newest first. */
    fun observeEndedReservationsForOwner(ownerId: String): Flow<List<Reservation>>

    /** Emits every reservation on campsites owned by [ownerId], by check-in day. */
    fun observeReservationsForOwner(ownerId: String): Flow<List<Reservation>>

    /** Emits every reservation on one campsite, by check-in day. */
    fun observeReservationsForCampsite(campsiteId: String): Flow<List<Reservation>>

    /** Emits the entrant's own reservations (US 01.06.01, US 01.06.02). */
    fun observeReservationsForEntrant(entrantId: String): Flow<List<Reservation>>

}

/** Reads user notifications. Notifications are written by [LotteryRepository.runDrawIfDue]. */
interface NotificationRepository {
    /** Emits the notifications for one user, newest first. */
    fun observeNotifications(recipientId: String): Flow<List<AppNotification>>

    /** Marks every notification for [recipientId] as read. */
    suspend fun markAllRead(recipientId: String)
}

/**
 * Where everything lives in Firestore. Collections are lowercase plural camelCase, fields are
 * camelCase, times are Timestamps, stay days are "yyyy-MM-dd" text and enum values are
 * lowercase words. Every document ID is generated by the app:
 *
 * ```
 * accounts/{authUid}                                   login account -> user ID (one read at sign-in)
 * devices/{androidId}                                  phones trusted to stay signed in
 * users/{userId}                                       e.g. users/maya-chen-x9k2
 * users/{userId}/notifications/{autoId}
 * campsites/{campsiteId}                               e.g. campsites/two-jack-lakeside-7f3k
 * campsites/{campsiteId}/ratings/{userId}
 * campsites/{campsiteId}/followers/{userId}             "notify me when a lottery opens"
 * campsites/{campsiteId}/lotteries/{lotteryId}             e.g. .../lotteries/nov-2026
 * campsites/{campsiteId}/lotteries/{lotteryId}/entrants/{userId}
 * campsites/{campsiteId}/lotteries/{lotteryId}/announcements/{autoId}
 * chats/{campsiteId}--{userId}                         a guest's conversation with an owner
 * chats/{chatId}/messages/{autoId}
 * reports/{autoId}                                     campsites flagged for an admin
 * ```
 *
 * Inside the app a lottery's ID is "campsiteId/lotteryId", so one string finds its document.
 */
object Paths {
    const val ACCOUNTS = "accounts"
    const val RATINGS = "ratings"
    const val FOLLOWERS = "followers"
    const val ANNOUNCEMENTS = "announcements"
    const val CHATS = "chats"
    const val MESSAGES = "messages"
    const val REPORTS = "reports"
    const val DEVICES = "devices"
    const val USERS = "users"
    const val NOTIFICATIONS = "notifications"
    const val CAMPSITES = "campsites"
    const val LOTTERIES = "lotteries"
    const val ENTRANTS = "entrants"

    /** App-side lottery ID for lottery [key] of [campsiteId]. */
    fun lotteryId(campsiteId: String, key: String) = "$campsiteId/$key"

    /** The campsite part of a lottery ID. */
    fun campsiteOf(lotteryId: String) = lotteryId.substringBefore('/')

    /** The lottery's own document ID, e.g. "nov-2026". */
    fun keyOf(lotteryId: String) = lotteryId.substringAfter('/')
}

/** Generates readable document IDs. */
object Ids {
    private const val TAIL_CHARS = "abcdefghijkmnpqrstuvwxyz23456789"
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    /** Lowercase words joined by dashes, e.g. "Two Jack Lakeside!" -> "two-jack-lakeside". */
    fun slug(text: String): String =
        java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).trim('-').ifBlank { "item" }

    /** A readable, unique ID: the name's slug plus four random characters, e.g. "maya-chen-x9k2". */
    fun readable(name: String, random: Random = Random.Default): String =
        slug(name) + "-" + (1..4).map { TAIL_CHARS[random.nextInt(TAIL_CHARS.length)] }.joinToString("")

    /**
     * A lottery's key from the month its stays start, e.g. "nov-2026"; uses the closing time
     * if there's no booking period. [attempt] > 1 adds "-2", "-3"... when the month is taken.
     */
    fun lotteryKey(stayFromDay: Long?, closesAt: Long, attempt: Int = 1): String {
        val date = stayFromDay?.let { java.time.LocalDate.ofEpochDay(it) }
            ?: java.time.Instant.ofEpochMilli(closesAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val base = "${months[date.monthValue - 1]}-${date.year}"
        return if (attempt <= 1) base else "$base-$attempt"
    }
}
