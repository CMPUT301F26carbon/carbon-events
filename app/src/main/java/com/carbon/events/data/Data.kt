/*
 * Data.kt (Vaibhav)
 *
 * Data layer: the models, the repository interfaces, the Firestore and
 * in-memory stores, and the AppContainer that hands them to every screen.
 *
 * Firestore layout and naming rules: see [Paths] and docs/firestore-structure.md.
 *
 * Outstanding issues: automatic draws run on the owner's (or an admin's) phone while the app is
 * open, because there is no server; push notifications would need Cloud Functions.
 */
package com.carbon.events

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
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
import kotlinx.coroutines.flow.flatMapLatest
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
 * @property id the person's user ID (document ID in `users`), e.g. "maya-chen-x9k2".
 * @property authUid the Firebase Authentication account that signs in as this person.
 * @property name full name shown to owners and on waiting lists.
 * @property email contact email.
 * @property phone optional phone number; blank when not provided.
 * @property activeRole role the user is currently viewing the app as.
 * @property isAdmin whether this profile is an administrator (set only in the Firebase console).
 * @property age age given at sign-up, or null.
 * @property createdAt creation time in epoch milliseconds.
 * @property photoBase64 optional profile photo as a small base64 JPEG.
 * @property blocked set by an admin; a blocked person can sign in but can't use the app.
 * @property warnings how many warnings an admin has sent.
 * @property prefs which phone notifications the person wants.
 */
data class UserProfile(
    val id: String,
    val name: String,
    val email: String,
    val phone: String = "",
    val activeRole: Role = Role.ENTRANT,
    val isAdmin: Boolean = false,
    val createdAt: Long = 0L,
    val authUid: String = "",
    val age: Int? = null,
    val photoBase64: String? = null,
    val blocked: Boolean = false,
    val warnings: Int = 0,
    val prefs: NotificationPrefs = NotificationPrefs(),
) {
    /** First name used in greetings, e.g. "Adam" for "Adam Kent". */
    val firstName: String get() = name.trim().substringBefore(' ').ifBlank { name }

    /** Up to two initials for avatar badges, e.g. "AK". */
    val initials: String
        get() = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .take(2).joinToString("") { it.first().uppercase() }

    /** Roles this profile may view the app as: entrants and owners keep the role they signed up with; admins can view as any (Figma A-ACCOUNT). */
    val availableRoles: List<Role>
        get() = if (isAdmin) Role.entries else listOf(activeRole)
}

/**
 * Which phone notifications a person wants (Figma S-NOTIFS, S-NOTIFS-OWNER and the Profile
 * switch). The in-app Notifications tab always keeps everything.
 *
 * @property results entrant: lottery wins, losses and spots that open up.
 * @property reservations entrant: reservation updates (rejections, cancellations).
 * @property messages entrant "Messages from owners" / owner "Messages from entrants".
 * @property news entrant: news & tips (no such notifications are sent yet).
 * @property openings new lotteries at campsites the person follows (they asked for these).
 * @property bookings owner: bookings (stored for when owners get booking alerts).
 * @property draws owner: lottery draws (stored for when owners get draw alerts).
 * @property waitlist owner: waitlist activity (stored for when owners get waitlist alerts).
 * @property optOut "Opt out of all notifications": nothing pops up except admin warnings.
 */
data class NotificationPrefs(
    val results: Boolean = true,
    val reservations: Boolean = true,
    val messages: Boolean = true,
    val news: Boolean = false,
    val openings: Boolean = true,
    val bookings: Boolean = true,
    val draws: Boolean = true,
    val waitlist: Boolean = true,
    val optOut: Boolean = false,
) {
    /** Whether a notification of [type] should pop up on the phone. */
    fun allows(type: NotificationType): Boolean = when {
        type == NotificationType.WARNING -> true
        optOut -> false
        else -> when (type) {
            NotificationType.LOTTERY_WON, NotificationType.LOTTERY_LOST, NotificationType.SPOT_OPENED -> results
            NotificationType.REJECTED -> reservations
            NotificationType.MESSAGE -> messages
            NotificationType.OPENED -> openings
            NotificationType.WARNING -> true
        }
    }

    /** Firestore form (the `prefs` map on a `users` document). */
    fun toMap(): Map<String, Boolean> = mapOf(
        "results" to results, "reservations" to reservations, "messages" to messages, "news" to news, "openings" to openings,
        "bookings" to bookings, "draws" to draws, "waitlist" to waitlist, "optOut" to optOut,
    )

    companion object {
        /** Reads the `prefs` map; missing keys keep their defaults. */
        fun from(get: (String) -> Boolean?): NotificationPrefs {
            val d = NotificationPrefs()
            return NotificationPrefs(
                get("results") ?: d.results, get("reservations") ?: d.reservations, get("messages") ?: d.messages,
                get("news") ?: d.news, get("openings") ?: d.openings, get("bookings") ?: d.bookings, get("draws") ?: d.draws,
                get("waitlist") ?: d.waitlist, get("optOut") ?: d.optOut,
            )
        }
    }
}

/**
 * When guests can cancel for free (Figma O-AVAILABILITY "Cancellation & refunds").
 *
 * @property freeDaysBefore free cancellation until this many days before check-in.
 */
enum class CancellationPolicy(val label: String, val freeDaysBefore: Int, val summary: String) {
    FLEXIBLE("Flexible", 1, "Free cancellation until the day before check-in."),
    MODERATE("Moderate", 5, "Free cancellation until 5 days before check-in; after that the first night isn't refunded."),
    STRICT("Strict", 14, "Free cancellation until 14 days before check-in; after that the first night isn't refunded."),
    ;

    /** The last day (a day number) a stay starting on [checkInDay] can be cancelled for free. */
    fun freeUntil(checkInDay: Long): Long = checkInDay - freeDaysBefore

    companion object {
        /** Reads the stored word, defaulting to Flexible. */
        fun of(word: String?): CancellationPolicy = entries.firstOrNull { it.name.equals(word, true) } ?: FLEXIBLE
    }
}

/**
 * A campsite listing.
 *
 * @property id document ID; also what the promo QR code encodes.
 * @property ownerId user-profile ID of the owner's profile.
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
 * @property rating average of every guest rating, out of 5; 0 until someone rates.
 * @property ratingCount number of ratings behind [rating].
 * @property createdAt creation time in epoch milliseconds.
 * @property checkInTime check-in time shown to guests, e.g. "3:00 PM".
 * @property checkOutTime check-out time shown to guests, e.g. "11:00 AM".
 * @property minNights fewest nights a winner may book.
 * @property maxNights most nights a winner may book.
 * @property rules house rules guests accept when they book.
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
    val checkInTime: String = "3:00 PM",
    val checkOutTime: String = "11:00 AM",
    val minNights: Int = 1,
    val maxNights: Int = 7,
    val rules: String = "",
    val cancellation: CancellationPolicy = CancellationPolicy.FLEXIBLE,
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
 * @property ownerId user-profile ID of the owner running it.
 * @property registrationStart when entrants may start joining (epoch ms).
 * @property registrationEnd when the waiting list closes and the draw runs (epoch ms).
 * @property winnerCount how many entrants are drawn as winners.
 * @property waitlistCap optional maximum waiting-list size; null means unlimited (US 02.04.07).
 * @property status [LotteryStatus.OPEN] until the draw has run.
 * @property drawnAt when the draw ran (epoch ms), or null.
 * @property entrantCount number of entrants currently on the waiting list.
 * @property bookingStartDay first night winners may book, as a day number
 *   ([java.time.LocalDate.toEpochDay]); null until the owner sets a booking period (US 02.04.04).
 * @property bookingEndDay last check-out day of the booking period, as a day number; null until set.
 * @property spotsFree winner spots given back (declined, cancelled or rejected) that the owner's
 *   phone still has to offer to someone else.
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
    val bookingStartDay: Long? = null,
    val bookingEndDay: Long? = null,
    val spotsFree: Int = 0,
) {
    /** Whether the owner has set a booking period winners can pick dates from. */
    val hasBookingPeriod: Boolean get() = bookingStartDay != null && bookingEndDay != null

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

    /** Not selected in the draw (may still get a spot someone gives back). */
    LOST,

    /** Won, then turned the spot down before booking. */
    DECLINED,

    /** Booked, then cancelled the reservation. */
    CANCELLED,

    /** The owner rejected the reservation (see [WaitlistEntry.note]). */
    REJECTED;

    /** Whether this entry gave its winner spot back. */
    val gaveUpSpot: Boolean get() = this == DECLINED || this == CANCELLED || this == REJECTED
}

/**
 * An entrant on a lottery's waiting list.
 *
 * @property lotteryId the lottery joined.
 * @property entrantId user-profile ID of the entrant.
 * @property entrantName entrant's name, copied for the owner's waitlist screen.
 * @property joinedAt when the entrant joined (epoch ms).
 * @property status draw outcome; [EntryStatus.WAITING] before the draw.
 * @property checkInDay first night a winner booked (day number), or null if not booked.
 * @property checkOutDay check-out day a winner booked (day number), or null if not booked.
 * @property bookedAt when the winner booked (epoch ms), or null.
 * @property campsiteId campsite of the lottery, copied when booking so reservations can be listed per campsite.
 * @property campsiteName campsite name, copied when booking.
 * @property ownerId owner of the campsite, copied when booking so owners can list their reservations.
 * @property guests number of guests on the reservation, or null if not booked.
 * @property agreedToRules whether the guest accepted the campsite rules when booking.
 * @property note the owner's reason when [EntryStatus.REJECTED].
 * @property endedAt when the entry was declined, cancelled or rejected (epoch ms), or null.
 */
data class WaitlistEntry(
    val lotteryId: String,
    val entrantId: String,
    val entrantName: String,
    val joinedAt: Long,
    val status: EntryStatus = EntryStatus.WAITING,
    val checkInDay: Long? = null,
    val checkOutDay: Long? = null,
    val bookedAt: Long? = null,
    val campsiteId: String = "",
    val campsiteName: String = "",
    val ownerId: String = "",
    val guests: Int? = null,
    val agreedToRules: Boolean = false,
    val note: String = "",
    val endedAt: Long? = null,
) {
    /** Whether this winner has booked dates. */
    val isBooked: Boolean get() = status == EntryStatus.WON && checkInDay != null && checkOutDay != null

    /** The reservation this entry represents, or null if no dates are booked. */
    fun toReservation(): Reservation? = if (!isBooked) null else Reservation(
        lotteryId, campsiteId, campsiteName, ownerId, entrantId, entrantName, checkInDay!!, checkOutDay!!, bookedAt ?: 0L, guests ?: 1,
    )

    /** A cancelled or rejected reservation, or null if this entry never had booked dates or is still active. */
    fun toEndedReservation(): Reservation? = if (!(status == EntryStatus.CANCELLED || status == EntryStatus.REJECTED) || checkInDay == null || checkOutDay == null) null
    else Reservation(
        lotteryId, campsiteId, campsiteName, ownerId, entrantId, entrantName, checkInDay, checkOutDay, bookedAt ?: 0L, guests ?: 1,
        status = status, note = note, endedAt = endedAt,
    )
}

/**
 * A confirmed campsite reservation: a lottery winner's booked nights (US 02.06.01, US 02.07.01,
 * US 02.07.02). Stored on the winner's waiting-list entry, so each winner has at most one.
 *
 * @property lotteryId the lottery the entrant won.
 * @property campsiteId the reserved campsite.
 * @property campsiteName campsite name for lists.
 * @property ownerId owner of the campsite.
 * @property entrantId the guest's user-profile ID.
 * @property entrantName the guest's name.
 * @property checkInDay first night (day number, see [java.time.LocalDate.toEpochDay]).
 * @property checkOutDay check-out day (day number); the guest stays the nights before it.
 * @property bookedAt when it was booked (epoch ms).
 * @property guests number of guests.
 * @property status [EntryStatus.WON] while active, or how it ended.
 * @property note the owner's reason if rejected.
 * @property endedAt when it was cancelled or rejected.
 */
data class Reservation(
    val lotteryId: String,
    val campsiteId: String,
    val campsiteName: String,
    val ownerId: String,
    val entrantId: String,
    val entrantName: String,
    val checkInDay: Long,
    val checkOutDay: Long,
    val bookedAt: Long,
    val guests: Int = 1,
    val status: EntryStatus = EntryStatus.WON,
    val note: String = "",
    val endedAt: Long? = null,
) {
    /** Number of nights booked. */
    val nights: Long get() = checkOutDay - checkInDay

    /** Whether the stay is upcoming or in progress on [today] (a day number). */
    fun isActive(today: Long): Boolean = checkOutDay > today

    /** Whether the stay has ended by [today] (a day number). */
    fun isPast(today: Long): Boolean = !isActive(today)

    /** The day numbers of every booked night. */
    fun nightDays(): LongRange = checkInDay until checkOutDay
}

/** What a notification is about. */
enum class NotificationType {
    /** The user won a lottery. */
    LOTTERY_WON,

    /** The user was not selected in a lottery. */
    LOTTERY_LOST,

    /** A spot someone gave back went to this user. */
    SPOT_OPENED,

    /** The owner rejected this user's reservation. */
    REJECTED,

    /** An owner's announcement to a waiting list, or a chat message. */
    MESSAGE,

    /** A campsite the user follows opened a new lottery. */
    OPENED,

    /** A warning from an administrator. */
    WARNING;

    /** The word stored in Firestore ("won", "lost", "spot", ...). */
    val word: String get() = when (this) {
        LOTTERY_WON -> "won"; LOTTERY_LOST -> "lost"; SPOT_OPENED -> "spot"; REJECTED -> "rejected"
        MESSAGE -> "message"; OPENED -> "opened"; WARNING -> "warning"
    }

    companion object {
        /** The type stored as [word], or [MESSAGE] for anything unknown. */
        fun of(word: String?): NotificationType = entries.firstOrNull { it.word == word } ?: MESSAGE
    }
}

/**
 * A notification addressed to one user.
 *
 * @property id document ID.
 * @property recipientId user ID of the person it is for.
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

/**
 * A phone trusted to stay signed in as a person (US 01.09.01, Figma S-TRUST). A person may trust
 * several phones, and removing one signs it out the next time the app starts.
 *
 * @property deviceId the phone's Android ID (document ID in `devices`).
 * @property userId the person it belongs to.
 * @property model phone name shown in the trusted-phones list, e.g. "Google Pixel 8".
 * @property addedAt when it was trusted (epoch ms).
 */
data class TrustedDevice(val deviceId: String, val userId: String, val model: String, val addedAt: Long)

/**
 * A signed-in Firebase Authentication account; separate from the application profile.
 * @property uid credential identity used by server access rules.
 * @property email sign-in email, not an editable profile contact field.
 * @property emailVerified verification status reported by Firebase.
 */
data class AuthAccount(val uid: String, val email: String, val emailVerified: Boolean)

/** Why signing up or in failed. */
enum class AuthError(val message: String) {
    WRONG_PASSWORD("Incorrect email or password."),
    EMAIL_TAKEN("An account already uses this email. Sign in instead."),
    WEAK_PASSWORD("Use 8 or more characters with at least one number."),
    BAD_EMAIL("Enter a valid email address."),
    NETWORK("Couldn't reach the server. Check your connection and try again."),
    PROVIDER_OFF("Email sign-in is turned off for this app. In Firebase, enable Email/Password under Authentication → Sign-in method."),
    TOO_MANY("Too many attempts. Wait a few minutes and try again."),
    OTHER("Firebase sign-in failed. Check the app's Firebase setup and try again."),
}

/** Thrown by [AuthRepository] when signing up or in fails. */
class AuthException(val error: AuthError) : Exception(error.message)

/**
 * Email and password accounts. Passwords go straight to Firebase Authentication, which stores
 * them hashed; the app and the database never see or keep them.
 */
interface AuthRepository {
    /** Emits the signed-in account, or null when signed out. */
    fun observeAccount(): Flow<AuthAccount?>

    /** Creates an account and signs in. @throws AuthException */
    suspend fun signUp(email: String, password: String): AuthAccount

    /** Signs in. @throws AuthException */
    suspend fun signIn(email: String, password: String): AuthAccount

    /** Emails a link that verifies the address. */
    suspend fun sendEmailVerification()

    /** Re-reads the account (e.g. after the person tapped the verification link). */
    suspend fun refresh(): AuthAccount?

    /** Signs out of this phone. */
    fun signOut()

    /**
     * Deletes the signed-in login account (Delete profile).
     * @return false if Firebase wants a fresh sign-in first.
     */
    suspend fun deleteAccount(): Boolean
}

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

    /** Emits the profile for [userId], or null if none exists, and every later change. */
    fun observeProfile(userId: String): Flow<UserProfile?>

    /** Creates or replaces [profile]. */
    suspend fun saveProfile(profile: UserProfile)

    /**
     * Deletes a person and everything that is only theirs: their profile, login lookup, trusted
     * phones, inbox and campsites (with those campsites' lotteries). Entries on other people's
     * waiting lists stay so owners keep their records.
     */
    suspend fun deleteProfile(profile: UserProfile)

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

// ---- Value conversions: the app uses epoch ms and day numbers; Firestore stores readable types ----

private fun Long.toTimestamp() = Timestamp(Math.floorDiv(this, 1000L), (Math.floorMod(this, 1000L) * 1_000_000).toInt())
private fun Timestamp.toMillis() = seconds * 1000 + nanoseconds / 1_000_000
private fun Long.toIsoDay(): String = java.time.LocalDate.ofEpochDay(this).toString()
private fun String.toDayNumber(): Long? = runCatching { java.time.LocalDate.parse(this).toEpochDay() }.getOrNull()
private fun Enum<*>.word() = name.lowercase()

private fun DocumentSnapshot.str(key: String) = getString(key) ?: ""
private fun DocumentSnapshot.int(key: String) = (getLong(key) ?: 0L).toInt()
private fun DocumentSnapshot.time(key: String): Long = getTimestamp(key)?.toMillis() ?: 0L
private fun DocumentSnapshot.timeOrNull(key: String): Long? = getTimestamp(key)?.toMillis()
private fun DocumentSnapshot.day(key: String): Long? = getString(key)?.toDayNumber()
private inline fun <reified E : Enum<E>> DocumentSnapshot.enum(key: String, default: E): E =
    getString(key)?.let { word -> enumValues<E>().firstOrNull { it.name.equals(word, ignoreCase = true) } } ?: default

/** Profile -> `users` document. */
fun UserProfile.toMap(): Map<String, Any?> = mapOf(
    "authUid" to authUid, "name" to name, "email" to email, "phone" to phone, "age" to age,
    "role" to activeRole.word(), "isAdmin" to isAdmin, "createdAt" to createdAt.toTimestamp(),
    "photo" to photoBase64, "blocked" to blocked, "warnings" to warnings,
    "prefs" to prefs.toMap(),
)

/** `users` document -> profile. */
fun DocumentSnapshot.toProfile(): UserProfile = UserProfile(
    id = id, name = str("name"), email = str("email"), phone = str("phone"),
    activeRole = enum("role", Role.ENTRANT), isAdmin = getBoolean("isAdmin") ?: false, createdAt = time("createdAt"),
    authUid = str("authUid"), age = getLong("age")?.toInt(),
    photoBase64 = getString("photo"), blocked = getBoolean("blocked") ?: false, warnings = int("warnings"),
    prefs = NotificationPrefs.from { getBoolean("prefs.$it") },
)

/** Trusted phone -> `devices` document. */
fun TrustedDevice.toMap(): Map<String, Any?> = mapOf("userId" to userId, "model" to model, "addedAt" to addedAt.toTimestamp())

/** `devices` document -> trusted phone. */
fun DocumentSnapshot.toDevice(): TrustedDevice = TrustedDevice(id, str("userId"), str("model"), time("addedAt"))

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
)

/** `lotteries` document -> lottery (its app ID is "campsiteId/lotteryId"). */
fun DocumentSnapshot.toLottery(): Lottery = Lottery(
    id = Paths.lotteryId(reference.parent.parent?.id ?: str("campsiteId"), id),
    campsiteId = str("campsiteId"), campsiteName = str("campsiteName"), ownerId = str("ownerId"),
    registrationStart = time("opensAt"), registrationEnd = time("closesAt"), winnerCount = int("winnerCount"), waitlistCap = getLong("waitlistCap")?.toInt(),
    status = enum("status", LotteryStatus.OPEN), drawnAt = timeOrNull("drawnAt"), entrantCount = int("entrantCount"),
    bookingStartDay = day("stayFrom"), bookingEndDay = day("stayUntil"), spotsFree = int("spotsFree"),
)

/** Waiting-list entry -> `entrants` document (keyed by the user ID). */
fun WaitlistEntry.toMap(): Map<String, Any?> = mapOf(
    "userId" to entrantId, "userName" to entrantName, "joinedAt" to joinedAt.toTimestamp(), "status" to status.word(),
    "checkIn" to checkInDay?.toIsoDay(), "checkOut" to checkOutDay?.toIsoDay(), "bookedAt" to bookedAt?.toTimestamp(),
    "campsiteId" to campsiteId, "campsiteName" to campsiteName, "lotteryId" to Paths.keyOf(lotteryId), "ownerId" to ownerId,
    "guests" to guests, "agreedToRules" to agreedToRules, "note" to note, "endedAt" to endedAt?.toTimestamp(),
)

/** `entrants` document -> waiting-list entry. */
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
    str("lastText"), time("lastAt"), str("lastSenderId"), getBoolean("unreadForOwner") ?: false, getBoolean("unreadForEntrant") ?: false,
    str("lotteryId"),
)

/** `messages` document -> chat message. */
fun DocumentSnapshot.toMessage(): ChatMessage =
    ChatMessage(id, reference.parent.parent?.id.orEmpty(), str("senderId"), str("senderName"), str("text"), time("sentAt"))

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

/** The notification each follower gets when a campsite they follow opens a lottery. */
fun openedNotice(lottery: Lottery, recipientId: String, now: Long) = AppNotification(
    id = "", recipientId = recipientId, type = NotificationType.OPENED,
    title = "New lottery at ${lottery.campsiteName}",
    body = "Registration is open ${formatWindow(lottery)}. Join the waiting list before it closes.",
    lotteryId = lottery.id, campsiteId = lottery.campsiteId, createdAt = now,
)

private fun formatWindow(lottery: Lottery): String {
    val f = java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.CANADA)
    val zone = java.time.ZoneId.systemDefault()
    fun d(ms: Long) = f.format(java.time.Instant.ofEpochMilli(ms).atZone(zone))
    return "${d(lottery.registrationStart)} – ${d(lottery.registrationEnd)}"
}

/** The notification an announcement becomes for each recipient. */
fun announcementNotice(lottery: Lottery, recipientId: String, text: String, now: Long) = AppNotification(
    id = "", recipientId = recipientId, type = NotificationType.MESSAGE,
    title = "Message from ${lottery.campsiteName}", body = text,
    lotteryId = lottery.id, campsiteId = lottery.campsiteId, createdAt = now,
)

/** The notification an admin warning becomes. */
fun warningNotice(recipientId: String, text: String, now: Long) = AppNotification(
    id = "", recipientId = recipientId, type = NotificationType.WARNING,
    title = "Warning from the Events team", body = text, lotteryId = "", campsiteId = "", createdAt = now,
)

/**
 * Firestore implementation of all four repositories. Lookups by ID are direct document reads;
 * lists across campsites use indexed collection-group queries (see docs/firestore.indexes.json),
 * so each costs the size of its result, not the size of the database.
 *
 * @param db the Firestore instance.
 */
class FirestoreEventsStore(private val db: FirebaseFirestore) :
    ProfileRepository, CampsiteRepository, LotteryRepository, NotificationRepository, SocialRepository {

    private val accounts get() = db.collection(Paths.ACCOUNTS)
    private val devices get() = db.collection(Paths.DEVICES)
    private val users get() = db.collection(Paths.USERS)
    private val campsites get() = db.collection(Paths.CAMPSITES)
    private val allLotteries get() = db.collectionGroup(Paths.LOTTERIES)
    private val allEntrants get() = db.collectionGroup(Paths.ENTRANTS)

    private fun lotteriesOf(campsiteId: String) = campsites.document(campsiteId).collection(Paths.LOTTERIES)
    private fun lotteryRef(lotteryId: String) = lotteriesOf(Paths.campsiteOf(lotteryId)).document(Paths.keyOf(lotteryId))
    private fun entrantsOf(lotteryId: String) = lotteryRef(lotteryId).collection(Paths.ENTRANTS)
    private fun notificationsOf(userId: String) = users.document(userId).collection(Paths.NOTIFICATIONS)

    /**
     * Listens to a query and maps each snapshot. If the server refuses or fails (e.g. a rule or
     * a missing index), the error is logged and the flow ends, so a screen keeps what it showed
     * instead of crashing.
     */
    private fun <T> Query.listen(map: (List<DocumentSnapshot>) -> T): Flow<T> = callbackFlow {
        val reg = addSnapshotListener { snap, err ->
            if (err != null) { logListenError(err); close() } else if (snap != null) trySend(map(snap.documents))
        }
        awaitClose { reg.remove() }
    }

    /** Listens to one document. Emits null while it does not exist. */
    private fun <T> DocumentReference.listen(map: (DocumentSnapshot) -> T): Flow<T?> = callbackFlow {
        val reg = addSnapshotListener { snap, err ->
            if (err != null) { logListenError(err); close() } else trySend(snap?.takeIf { it.exists() }?.let(map))
        }
        awaitClose { reg.remove() }
    }

    private fun logListenError(err: Exception) {
        runCatching { android.util.Log.w("Events", "Firestore listen failed: ${err.message}") }
    }

    // ---- Profiles and trusted phones ----

    override fun observeUserIdForDevice(deviceId: String): Flow<String?> =
        devices.document(deviceId).listen { it.getString("userId") }

    override fun observeProfile(userId: String): Flow<UserProfile?> = users.document(userId).listen { it.toProfile() }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeProfileForAccount(authUid: String): Flow<UserProfile?> =
        // accounts/{authUid} holds the user ID, so signing in is two direct reads, no search.
        accounts.document(authUid).listen { it.getString("userId") }.flatMapLatest { userId ->
            if (userId == null) kotlinx.coroutines.flow.flowOf(null) else observeProfile(userId)
        }

    override suspend fun createProfile(profile: UserProfile, trust: TrustedDevice?): String {
        val userId = Ids.readable(profile.name)
        // One batch: the rules only trust a phone for a person whose account is the caller's.
        val batch = db.batch().set(users.document(userId), profile.copy(id = userId).toMap())
            .set(accounts.document(profile.authUid), mapOf("userId" to userId))
        if (trust != null) batch.set(devices.document(trust.deviceId), trust.copy(userId = userId).toMap())
        batch.commit().await()
        return userId
    }

    override suspend fun trustDevice(device: TrustedDevice) {
        devices.document(device.deviceId).set(device.toMap()).await()
    }

    override suspend fun saveProfile(profile: UserProfile) {
        users.document(profile.id).set(profile.toMap()).await()
    }

    override suspend fun deleteProfile(profile: UserProfile) {
        for (site in campsites.whereEqualTo("ownerId", profile.id).get().await().documents) deleteCampsite(site.id)
        val batch = db.batch()
        devices.whereEqualTo("userId", profile.id).get().await().documents.forEach { batch.delete(it.reference) }
        notificationsOf(profile.id).get().await().documents.forEach { batch.delete(it.reference) }
        if (profile.authUid.isNotBlank()) batch.delete(accounts.document(profile.authUid))
        batch.delete(users.document(profile.id))
        batch.commit().await()
    }

    override fun observeProfiles(): Flow<List<UserProfile>> =
        users.listen { docs -> docs.map { it.toProfile() }.sortedBy { it.name.lowercase() } }

    override fun observeDevices(userId: String): Flow<List<TrustedDevice>> =
        devices.whereEqualTo("userId", userId).listen { docs -> docs.map { it.toDevice() }.sortedBy { it.addedAt } }

    override suspend fun removeDevice(deviceId: String) {
        devices.document(deviceId).delete().await()
    }

    // ---- Campsites ----

    override fun observeCampsites(): Flow<List<Campsite>> =
        campsites.orderBy("nameLower").listen { docs -> docs.map { it.toCampsite() } }

    override fun observeCampsite(id: String): Flow<Campsite?> = campsites.document(id).listen { it.toCampsite() }

    override fun observeCampsitesByOwner(ownerId: String): Flow<List<Campsite>> =
        campsites.whereEqualTo("ownerId", ownerId).listen { docs -> docs.map { it.toCampsite() }.sortedByDescending { it.createdAt } }

    override suspend fun createCampsite(campsite: Campsite): String {
        val id = Ids.readable(campsite.name)
        campsites.document(id).set(campsite.copy(id = id).toMap()).await()
        return id
    }

    override suspend fun updateCampsite(campsite: Campsite) {
        // Ratings belong to the guests (RatingPolicy) and the creation time never changes, so an
        // edit leaves them as they are in the database.
        val fields = campsite.toMap() - setOf("rating", "ratingCount", "createdAt")
        campsites.document(campsite.id).set(fields, com.google.firebase.firestore.SetOptions.merge()).await()
    }

    override suspend fun deleteCampsite(id: String) {
        // Firestore doesn't delete sub-collections with their parent, so remove them first.
        val batch = db.batch()
        val site = campsites.document(id)
        for (lot in lotteriesOf(id).get().await().documents) {
            lot.reference.collection(Paths.ENTRANTS).get().await().documents.forEach { batch.delete(it.reference) }
            lot.reference.collection(Paths.ANNOUNCEMENTS).get().await().documents.forEach { batch.delete(it.reference) }
            batch.delete(lot.reference)
        }
        site.collection(Paths.RATINGS).get().await().documents.forEach { batch.delete(it.reference) }
        site.collection(Paths.FOLLOWERS).get().await().documents.forEach { batch.delete(it.reference) }
        batch.delete(site)
        batch.commit().await()
    }

    // ---- Lotteries ----

    override fun observeLotteries(): Flow<List<Lottery>> = allLotteries.listen { docs -> docs.map { it.toLottery() } }

    override fun observeOpenLotteries(): Flow<List<Lottery>> =
        allLotteries.whereEqualTo("status", LotteryStatus.OPEN.word()).listen { docs -> docs.map { it.toLottery() } }

    override fun observeLottery(id: String): Flow<Lottery?> = lotteryRef(id).listen { it.toLottery() }

    override fun observeLotteriesForCampsite(campsiteId: String): Flow<List<Lottery>> =
        lotteriesOf(campsiteId).listen { docs -> docs.map { it.toLottery() }.sortedByDescending { it.registrationStart } }

    override fun observeLotteriesByOwner(ownerId: String): Flow<List<Lottery>> =
        allLotteries.whereEqualTo("ownerId", ownerId)
            .listen { docs -> docs.map { it.toLottery() }.sortedByDescending { it.registrationStart } }

    override suspend fun createLottery(lottery: Lottery): String {
        val parent = lotteriesOf(lottery.campsiteId)
        return db.runTransaction { t ->
            // "nov-2026", or "nov-2026-2" if that campsite already has a November lottery.
            val key = generateSequence(1) { it + 1 }.take(50)
                .map { Ids.lotteryKey(lottery.bookingStartDay, lottery.registrationEnd, it) }
                .first { !t.get(parent.document(it)).exists() }
            val id = Paths.lotteryId(lottery.campsiteId, key)
            t.set(parent.document(key), lottery.copy(id = id, entrantCount = 0).toMap())
            id
        }.await()
    }

    override fun observeEntries(lotteryId: String): Flow<List<WaitlistEntry>> =
        entrantsOf(lotteryId).listen { docs -> docs.map { it.toEntry() }.sortedBy { it.joinedAt } }

    override fun observeEntriesForEntrant(entrantId: String): Flow<List<WaitlistEntry>> =
        allEntrants.whereEqualTo("userId", entrantId).listen { docs -> docs.map { it.toEntry() } }

    override suspend fun joinWaitlist(lotteryId: String, entrantId: String, entrantName: String, now: Long) {
        val lotRef = lotteryRef(lotteryId)
        val entryRef = entrantsOf(lotteryId).document(entrantId)
        db.runTransaction { t ->
            val lottery = t.get(lotRef).toLottery()
            val joined = t.get(entryRef).exists()
            WaitlistPolicy.checkJoin(lottery, joined, lottery.entrantCount, now)?.let { throw WaitlistException(it) }
            // Campsite and owner are copied onto the entry so owners can list bookings in one query.
            val entry = WaitlistEntry(
                lotteryId, entrantId, entrantName, now,
                campsiteId = lottery.campsiteId, campsiteName = lottery.campsiteName, ownerId = lottery.ownerId,
            )
            t.set(entryRef, entry.toMap())
            t.update(lotRef, "entrantCount", FieldValue.increment(1))
        }.await()
    }

    override suspend fun leaveWaitlist(lotteryId: String, entrantId: String, now: Long) {
        val lotRef = lotteryRef(lotteryId)
        val entryRef = entrantsOf(lotteryId).document(entrantId)
        db.runTransaction { t ->
            val lottery = t.get(lotRef).toLottery()
            val joined = t.get(entryRef).exists()
            WaitlistPolicy.checkLeave(lottery, joined, now)?.let { throw WaitlistException(it) }
            t.delete(entryRef)
            t.update(lotRef, "entrantCount", FieldValue.increment(-1))
        }.await()
    }

    override suspend fun runDrawIfDue(lotteryId: String, now: Long, random: Random): Boolean {
        val lotRef = lotteryRef(lotteryId)
        // Registration is closed once a draw is due, so the waiting list can no longer change.
        val waitlist = entrantsOf(lotteryId).get().await().documents.map { it.toEntry() }
        return db.runTransaction { t ->
            val lottery = t.get(lotRef).toLottery()
            if (!lottery.isDrawDue(now)) return@runTransaction false
            val result = LotteryDrawer.draw(lottery, waitlist, now, random)
            result.entries.forEach { e -> t.update(entrantsOf(lotteryId).document(e.entrantId), "status", e.status.word()) }
            result.notifications.forEach { n -> t.set(notificationsOf(n.recipientId).document(), n.toMap()) }
            t.update(lotRef, mapOf("status" to LotteryStatus.DRAWN.word(), "drawnAt" to now.toTimestamp()))
            true
        }.await()
    }

    override suspend fun updateLottery(updated: Lottery, now: Long) {
        val lotRef = lotteryRef(updated.id)
        db.runTransaction { t ->
            val current = t.get(lotRef).toLottery()
            LotteryEditPolicy.check(current, updated, now)?.let { throw LotteryEditException(it) }
            t.update(
                lotRef, mapOf(
                    "opensAt" to updated.registrationStart.toTimestamp(), "closesAt" to updated.registrationEnd.toTimestamp(),
                    "winnerCount" to updated.winnerCount, "waitlistCap" to updated.waitlistCap,
                    "stayFrom" to updated.bookingStartDay?.toIsoDay(), "stayUntil" to updated.bookingEndDay?.toIsoDay(),
                ),
            )
        }.await()
    }

    override suspend fun bookDates(
        lotteryId: String, entrantId: String, checkInDay: Long, checkOutDay: Long, now: Long,
        guests: Int, agreedToRules: Boolean,
    ) {
        val lotRef = lotteryRef(lotteryId)
        // After the draw the winners are fixed, so their IDs are known up front and the
        // transaction can re-read each one to rule out overlapping bookings.
        val winnerIds = entrantsOf(lotteryId).whereEqualTo("status", EntryStatus.WON.word()).get().await().documents.map { it.id }
        db.runTransaction { t ->
            val lottery = t.get(lotRef).toLottery()
            val winners = winnerIds.map { id -> t.get(entrantsOf(lotteryId).document(id)).toEntry() }
            val mine = winners.firstOrNull { it.entrantId == entrantId }
            BookingPolicy.check(lottery, mine, checkInDay, checkOutDay, winners)?.let { throw BookingException(it) }
            t.update(
                entrantsOf(lotteryId).document(entrantId),
                mapOf(
                    "checkIn" to checkInDay.toIsoDay(), "checkOut" to checkOutDay.toIsoDay(), "bookedAt" to now.toTimestamp(),
                    "guests" to guests, "agreedToRules" to agreedToRules,
                ),
            )
        }.await()
    }

    override suspend fun giveUpSpot(lotteryId: String, entrantId: String, now: Long) {
        val lotRef = lotteryRef(lotteryId)
        val entryRef = entrantsOf(lotteryId).document(entrantId)
        db.runTransaction { t ->
            t.get(lotRef)
            val entry = t.get(entryRef).takeIf { it.exists() }?.toEntry()
            if (entry == null || entry.status != EntryStatus.WON) throw BookingException(BookingError.NOT_A_WINNER)
            val status = if (entry.isBooked) EntryStatus.CANCELLED else EntryStatus.DECLINED
            t.update(entryRef, mapOf("status" to status.word(), "endedAt" to now.toTimestamp()))
            t.update(lotRef, "spotsFree", FieldValue.increment(1))
        }.await()
    }

    override suspend fun rejectReservation(lotteryId: String, entrantId: String, reason: String, now: Long) {
        val lotRef = lotteryRef(lotteryId)
        val entryRef = entrantsOf(lotteryId).document(entrantId)
        db.runTransaction { t ->
            val lottery = t.get(lotRef).toLottery()
            val entry = t.get(entryRef).takeIf { it.exists() }?.toEntry()
            if (entry == null || entry.status != EntryStatus.WON) throw BookingException(BookingError.NOT_A_WINNER)
            t.update(entryRef, mapOf("status" to EntryStatus.REJECTED.word(), "note" to reason, "endedAt" to now.toTimestamp()))
            t.update(lotRef, "spotsFree", FieldValue.increment(1))
            t.set(notificationsOf(entrantId).document(), LotteryDrawer.rejectedNotice(lottery, entrantId, reason, now).toMap())
        }.await()
    }

    override suspend fun fillFreeSpots(lotteryId: String, now: Long, random: Random): Int {
        val lotRef = lotteryRef(lotteryId)
        val entries = entrantsOf(lotteryId).get().await().documents.map { it.toEntry() }
        return db.runTransaction { t ->
            val lottery = t.get(lotRef).toLottery()
            if (lottery.status != LotteryStatus.DRAWN || lottery.spotsFree <= 0) return@runTransaction 0
            val result = LotteryDrawer.fillSpots(lottery, entries, now, random)
            result.entries.forEach { e -> t.update(entrantsOf(lotteryId).document(e.entrantId), "status", EntryStatus.WON.word()) }
            result.notifications.forEach { n -> t.set(notificationsOf(n.recipientId).document(), n.toMap()) }
            t.update(lotRef, "spotsFree", 0)
            result.entries.size
        }.await()
    }

    override fun observeEndedReservationsForOwner(ownerId: String): Flow<List<Reservation>> =
        allEntrants.whereEqualTo("ownerId", ownerId)
            .listen { docs -> docs.mapNotNull { it.toEntry().toEndedReservation() }.sortedByDescending { it.endedAt ?: 0L } }

    private fun Query.reservations(): Flow<List<Reservation>> =
        listen { docs -> docs.mapNotNull { it.toEntry().toReservation() }.sortedBy { it.checkInDay } }

    override fun observeReservationsForOwner(ownerId: String): Flow<List<Reservation>> =
        allEntrants.whereEqualTo("ownerId", ownerId).reservations()

    override fun observeReservationsForCampsite(campsiteId: String): Flow<List<Reservation>> =
        allEntrants.whereEqualTo("campsiteId", campsiteId).reservations()

    override fun observeReservationsForEntrant(entrantId: String): Flow<List<Reservation>> =
        allEntrants.whereEqualTo("userId", entrantId).reservations()


    // ---- Notifications ----

    override fun observeNotifications(recipientId: String): Flow<List<AppNotification>> =
        notificationsOf(recipientId).listen { docs -> docs.map { it.toNotification() }.sortedByDescending { it.createdAt } }

    override suspend fun markAllRead(recipientId: String) {
        val unread = notificationsOf(recipientId).whereEqualTo("read", false).get().await().documents
        if (unread.isEmpty()) return
        val batch = db.batch()
        unread.forEach { batch.update(it.reference, "read", true) }
        batch.commit().await()
    }

    // ---- Ratings, follows, chats, announcements, reports ----

    private val chats get() = db.collection(Paths.CHATS)
    private val reports get() = db.collection(Paths.REPORTS)
    private fun ratingsOf(campsiteId: String) = campsites.document(campsiteId).collection(Paths.RATINGS)
    private fun followersOf(campsiteId: String) = campsites.document(campsiteId).collection(Paths.FOLLOWERS)
    private fun announcementsOf(lotteryId: String) = lotteryRef(lotteryId).collection(Paths.ANNOUNCEMENTS)

    override fun observeRatings(campsiteId: String): Flow<List<Rating>> =
        ratingsOf(campsiteId).listen { docs -> docs.map { it.toRating() }.sortedByDescending { it.createdAt } }

    override suspend fun rate(rating: Rating) {
        val site = campsites.document(rating.campsiteId)
        val ref = ratingsOf(rating.campsiteId).document(rating.userId)
        db.runTransaction { t ->
            val campsite = t.get(site).toCampsite()
            val previous = t.get(ref).takeIf { it.exists() }?.getLong("stars")?.toInt()
            val (average, count) = RatingPolicy.aggregate(campsite.rating, campsite.ratingCount, rating.stars, previous)
            t.set(ref, rating.toMap())
            t.update(site, mapOf("rating" to average, "ratingCount" to count))
        }.await()
    }

    override fun observeFollowing(campsiteId: String, userId: String): Flow<Boolean> =
        followersOf(campsiteId).document(userId).listen { true }.map { it == true }

    override suspend fun setFollowing(campsiteId: String, userId: String, userName: String, follow: Boolean, now: Long) {
        val ref = followersOf(campsiteId).document(userId)
        if (follow) ref.set(mapOf("userName" to userName, "followedAt" to now.toTimestamp())).await() else ref.delete().await()
    }

    override suspend fun notifyFollowers(lottery: Lottery, now: Long): Int {
        val followers = followersOf(lottery.campsiteId).get().await().documents.map { it.id }
        if (followers.isEmpty()) return 0
        val batch = db.batch()
        followers.forEach { batch.set(notificationsOf(it).document(), openedNotice(lottery, it, now).toMap()) }
        batch.commit().await()
        return followers.size
    }

    override fun observeChatsFor(userId: String): Flow<List<Chat>> =
        kotlinx.coroutines.flow.combine(
            chats.whereEqualTo("ownerId", userId).listen { d -> d.map { it.toChat() } },
            chats.whereEqualTo("entrantId", userId).listen { d -> d.map { it.toChat() } },
        ) { a, b -> (a + b).distinctBy { it.id }.sortedByDescending { it.lastAt } }

    override fun observeAllChats(): Flow<List<Chat>> = chats.listen { d -> d.map { it.toChat() }.sortedByDescending { it.lastAt } }

    override fun observeChat(chatId: String): Flow<Chat?> = chats.document(chatId).listen { it.toChat() }

    override fun observeMessages(chatId: String): Flow<List<ChatMessage>> =
        chats.document(chatId).collection(Paths.MESSAGES).listen { d -> d.map { it.toMessage() }.sortedBy { it.sentAt } }

    override suspend fun sendMessage(chat: Chat, senderId: String, senderName: String, text: String, now: Long) {
        val fromOwner = senderId == chat.ownerId
        val updated = chat.copy(lastText = text, lastAt = now, lastSenderId = senderId, unreadForOwner = !fromOwner, unreadForEntrant = fromOwner)
        val ref = chats.document(chat.id)
        db.batch()
            .set(ref, updated.toMap())
            .set(ref.collection(Paths.MESSAGES).document(), mapOf("senderId" to senderId, "senderName" to senderName, "text" to text, "sentAt" to now.toTimestamp()))
            .commit().await()
    }

    override suspend fun markChatRead(chatId: String, userId: String) {
        val ref = chats.document(chatId)
        val chat = ref.get().await().takeIf { it.exists() }?.toChat() ?: return
        if (!chat.unreadFor(userId)) return
        ref.update(if (userId == chat.ownerId) "unreadForOwner" else "unreadForEntrant", false).await()
    }

    override suspend fun sendAnnouncement(lottery: Lottery, audience: Audience, text: String, now: Long): Int {
        val recipients = entrantsOf(lottery.id).get().await().documents.map { it.toEntry() }.filter(audience::includes)
        val batch = db.batch()
        recipients.forEach { batch.set(notificationsOf(it.entrantId).document(), announcementNotice(lottery, it.entrantId, text, now).toMap()) }
        batch.set(
            announcementsOf(lottery.id).document(),
            Announcement("", lottery.id, lottery.campsiteId, lottery.campsiteName, lottery.ownerId, text, audience, recipients.size, now).toMap(),
        )
        batch.commit().await()
        return recipients.size
    }

    override fun observeAnnouncements(lotteryId: String): Flow<List<Announcement>> =
        announcementsOf(lotteryId).listen { d -> d.map { it.toAnnouncement() }.sortedByDescending { it.sentAt } }

    override fun observeAnnouncementsByOwner(ownerId: String): Flow<List<Announcement>> =
        db.collectionGroup(Paths.ANNOUNCEMENTS).whereEqualTo("ownerId", ownerId)
            .listen { d -> d.map { it.toAnnouncement() }.sortedByDescending { it.sentAt } }

    override fun observeAllAnnouncements(): Flow<List<Announcement>> =
        db.collectionGroup(Paths.ANNOUNCEMENTS).listen { d -> d.map { it.toAnnouncement() }.sortedByDescending { it.sentAt } }

    override fun observeAllNotifications(): Flow<List<AppNotification>> =
        db.collectionGroup(Paths.NOTIFICATIONS).listen { d -> d.map { it.toNotification() }.sortedByDescending { it.createdAt } }

    override suspend fun report(report: Report) {
        reports.document().set(report.toMap()).await()
    }

    override fun observeReports(): Flow<List<Report>> = reports.listen { d -> d.map { it.toReport() }.sortedByDescending { it.createdAt } }

    override suspend fun dismissReports(campsiteId: String) {
        val docs = reports.whereEqualTo("campsiteId", campsiteId).get().await().documents
        if (docs.isEmpty()) return
        val batch = db.batch()
        docs.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }

    override suspend fun warn(profile: UserProfile, text: String, now: Long) {
        db.batch()
            .set(notificationsOf(profile.id).document(), warningNotice(profile.id, text, now).toMap())
            .update(users.document(profile.id), "warnings", FieldValue.increment(1))
            .commit().await()
    }
}

/**
 * In-memory accounts for tests and builds without Firebase. Passwords are only compared in
 * memory and never leave the process.
 *
 * @param signedInUid the account already signed in, or null to start signed out.
 */
class InMemoryAuth(signedInUid: String? = null) : AuthRepository {
    private data class Account(val uid: String, val email: String, val password: String, var verified: Boolean = false)

    private val accounts = mutableMapOf<String, Account>()
    private val current = MutableStateFlow(signedInUid?.let { AuthAccount(it, "", true) })

    override fun observeAccount(): Flow<AuthAccount?> = current

    override suspend fun signUp(email: String, password: String): AuthAccount {
        val key = email.trim().lowercase()
        if (key in accounts) throw AuthException(AuthError.EMAIL_TAKEN)
        if (!ProfileValidator.isStrongPassword(password)) throw AuthException(AuthError.WEAK_PASSWORD)
        val account = Account("uid-${accounts.size + 1}", key, password)
        accounts[key] = account
        return AuthAccount(account.uid, key, false).also { current.value = it }
    }

    override suspend fun signIn(email: String, password: String): AuthAccount {
        val account = accounts[email.trim().lowercase()]?.takeIf { it.password == password } ?: throw AuthException(AuthError.WRONG_PASSWORD)
        return AuthAccount(account.uid, account.email, account.verified).also { current.value = it }
    }

    override suspend fun sendEmailVerification() {}

    /** Marks [email]'s address verified, as if its link was tapped (tests). */
    fun verify(email: String) { accounts[email.lowercase()]?.verified = true }

    override suspend fun refresh(): AuthAccount? {
        val now = current.value ?: return null
        val account = accounts.values.firstOrNull { it.uid == now.uid } ?: return now
        return now.copy(emailVerified = account.verified).also { current.value = it }
    }

    override fun signOut() { current.value = null }

    override suspend fun deleteAccount(): Boolean {
        val uid = current.value?.uid ?: return true
        accounts.values.removeAll { it.uid == uid }
        current.value = null
        return true
    }
}

/**
 * Accounts on Firebase Authentication (email and password). Google hashes and stores the
 * passwords; nothing about them reaches Firestore or this app's storage.
 *
 * @param auth the FirebaseAuth instance.
 */
class FirebaseAuthStore(private val auth: FirebaseAuth) : AuthRepository {
    private fun com.google.firebase.auth.FirebaseUser.toAccount() = AuthAccount(uid, email.orEmpty(), isEmailVerified)

    override fun observeAccount(): Flow<AuthAccount?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAccount()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    private suspend fun <T> mapped(block: suspend () -> T): T = try {
        block()
    } catch (e: FirebaseAuthWeakPasswordException) {
        throw AuthException(AuthError.WEAK_PASSWORD)
    } catch (e: FirebaseAuthUserCollisionException) {
        throw AuthException(AuthError.EMAIL_TAKEN)
    } catch (e: FirebaseAuthInvalidUserException) {
        throw AuthException(AuthError.WRONG_PASSWORD)
    } catch (e: FirebaseAuthInvalidCredentialsException) {
        throw AuthException(if (e.errorCode == "ERROR_INVALID_EMAIL") AuthError.BAD_EMAIL else AuthError.WRONG_PASSWORD)
    } catch (e: FirebaseNetworkException) {
        throw AuthException(AuthError.NETWORK)
    } catch (e: com.google.firebase.FirebaseTooManyRequestsException) {
        throw AuthException(AuthError.TOO_MANY)
    } catch (e: com.google.firebase.auth.FirebaseAuthException) {
        android.util.Log.w("Events", "Firebase Auth error ${e.errorCode}", e)
        throw AuthException(if (e.errorCode == "ERROR_OPERATION_NOT_ALLOWED") AuthError.PROVIDER_OFF else AuthError.OTHER)
    }

    override suspend fun signUp(email: String, password: String): AuthAccount =
        mapped { auth.createUserWithEmailAndPassword(email.trim(), password).await().user!!.toAccount() }

    override suspend fun signIn(email: String, password: String): AuthAccount =
        mapped { auth.signInWithEmailAndPassword(email.trim(), password).await().user!!.toAccount() }

    override suspend fun sendEmailVerification() {
        auth.currentUser?.sendEmailVerification()?.await()
    }

    override suspend fun refresh(): AuthAccount? {
        val user = auth.currentUser ?: return null
        user.reload().await()
        return auth.currentUser?.toAccount()
    }

    override fun signOut() = auth.signOut()

    override suspend fun deleteAccount(): Boolean {
        val user = auth.currentUser ?: return true
        return try {
            user.delete().await(); true
        } catch (e: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
            auth.signOut(); false
        }
    }
}

/**
 * Everything the in-memory store holds, as one immutable snapshot.
 */
data class EventsSnapshot(
    val profiles: Map<String, UserProfile> = emptyMap(),
    val devices: Map<String, TrustedDevice> = emptyMap(),
    val campsites: Map<String, Campsite> = emptyMap(),
    val lotteries: Map<String, Lottery> = emptyMap(),
    val entries: List<WaitlistEntry> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val ratings: List<Rating> = emptyList(),
    val follows: Set<Pair<String, String>> = emptySet(),
    val chats: Map<String, Chat> = emptyMap(),
    val messages: List<ChatMessage> = emptyList(),
    val announcements: List<Announcement> = emptyList(),
    val reports: List<Report> = emptyList(),
)

/**
 * In-memory implementation of the profile, campsite, lottery, notification and social repositories.
 * StateFlow snapshots provide observable updates for previews and deterministic tests. No state
 * survives process termination. Firebase permission checks are not simulated by this store.
 *
 * @param initial starting data (tests pass realistic fixtures; the app starts empty).
 */
class InMemoryEventsStore(initial: EventsSnapshot = EventsSnapshot()) :
    ProfileRepository, CampsiteRepository, LotteryRepository, NotificationRepository, SocialRepository {

    private val state = MutableStateFlow(initial)
    private val ids = AtomicLong(1000)

    /** The current contents, for test assertions. */
    val snapshot: EventsSnapshot get() = state.value

    private fun newId(prefix: String) = "$prefix${ids.incrementAndGet()}"

    private fun <T> select(block: (EventsSnapshot) -> T): Flow<T> = state.map(block).distinctUntilChanged()

    // ---- Profiles ----

    override fun observeUserIdForDevice(deviceId: String): Flow<String?> = select { it.devices[deviceId]?.userId }

    override fun observeProfile(userId: String): Flow<UserProfile?> = select { it.profiles[userId] }

    override fun observeProfileForAccount(authUid: String): Flow<UserProfile?> = select { s ->
        // Test fixtures often skip accounts: a profile with no authUid is found by its own ID.
        s.profiles.values.firstOrNull { it.authUid == authUid } ?: s.profiles[authUid]?.takeIf { it.authUid.isEmpty() }
    }

    override suspend fun createProfile(profile: UserProfile, trust: TrustedDevice?): String {
        val userId = Ids.readable(profile.name)
        state.update {
            it.copy(
                profiles = it.profiles + (userId to profile.copy(id = userId)),
                devices = if (trust == null) it.devices else it.devices + (trust.deviceId to trust.copy(userId = userId)),
            )
        }
        return userId
    }

    override suspend fun trustDevice(device: TrustedDevice) {
        state.update { it.copy(devices = it.devices + (device.deviceId to device)) }
    }

    override suspend fun saveProfile(profile: UserProfile) {
        state.update { it.copy(profiles = it.profiles + (profile.id to profile)) }
    }

    /** Trusts [deviceId] for [userId] directly (test setup; production uses trustDevice after authentication). */
    fun linkDevice(deviceId: String, userId: String, model: String = "Test phone", addedAt: Long = 0L) {
        state.update { it.copy(devices = it.devices + (deviceId to TrustedDevice(deviceId, userId, model, addedAt))) }
    }

    override fun observeDevices(userId: String): Flow<List<TrustedDevice>> =
        select { s -> s.devices.values.filter { it.userId == userId }.sortedBy { it.addedAt } }

    override suspend fun removeDevice(deviceId: String) {
        state.update { it.copy(devices = it.devices - deviceId) }
    }

    override fun observeProfiles(): Flow<List<UserProfile>> = select { s -> s.profiles.values.sortedBy { it.name.lowercase() } }

    override suspend fun deleteProfile(profile: UserProfile) {
        state.value.campsites.values.filter { it.ownerId == profile.id }.forEach { deleteCampsite(it.id) }
        state.update { s ->
            s.copy(
                profiles = s.profiles - profile.id,
                devices = s.devices.filterValues { it.userId != profile.id },
                notifications = s.notifications.filterNot { it.recipientId == profile.id },
            )
        }
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

    override fun observeOpenLotteries(): Flow<List<Lottery>> =
        select { s -> s.lotteries.values.filter { it.status == LotteryStatus.OPEN } }

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

    override suspend fun updateLottery(updated: Lottery, now: Long) {
        state.update { s ->
            val current = s.lotteries[updated.id] ?: error("No lottery ${updated.id}")
            LotteryEditPolicy.check(current, updated, now)?.let { throw LotteryEditException(it) }
            val merged = current.copy(
                registrationStart = updated.registrationStart, registrationEnd = updated.registrationEnd,
                winnerCount = updated.winnerCount, waitlistCap = updated.waitlistCap,
                bookingStartDay = updated.bookingStartDay, bookingEndDay = updated.bookingEndDay,
            )
            s.copy(lotteries = s.lotteries + (merged.id to merged))
        }
    }

    override suspend fun bookDates(
        lotteryId: String, entrantId: String, checkInDay: Long, checkOutDay: Long, now: Long,
        guests: Int, agreedToRules: Boolean,
    ) {
        state.update { s ->
            val lottery = s.lotteries[lotteryId] ?: error("No lottery $lotteryId")
            val winners = s.entries.filter { it.lotteryId == lotteryId && it.status == EntryStatus.WON }
            val mine = winners.firstOrNull { it.entrantId == entrantId }
            BookingPolicy.check(lottery, mine, checkInDay, checkOutDay, winners)?.let { throw BookingException(it) }
            s.copy(entries = s.entries.map {
                if (it.lotteryId == lotteryId && it.entrantId == entrantId) it.copy(
                    checkInDay = checkInDay, checkOutDay = checkOutDay, bookedAt = now,
                    campsiteId = lottery.campsiteId, campsiteName = lottery.campsiteName, ownerId = lottery.ownerId,
                    guests = guests, agreedToRules = agreedToRules,
                ) else it
            })
        }
    }

    private fun MutableList<WaitlistEntry>.winner(lotteryId: String, entrantId: String): Int {
        val i = indexOfFirst { it.lotteryId == lotteryId && it.entrantId == entrantId }
        if (i < 0 || this[i].status != EntryStatus.WON) throw BookingException(BookingError.NOT_A_WINNER)
        return i
    }

    override suspend fun giveUpSpot(lotteryId: String, entrantId: String, now: Long) {
        state.update { s ->
            val lottery = s.lotteries[lotteryId] ?: error("No lottery $lotteryId")
            val entries = s.entries.toMutableList()
            val i = entries.winner(lotteryId, entrantId)
            entries[i] = entries[i].copy(status = if (entries[i].isBooked) EntryStatus.CANCELLED else EntryStatus.DECLINED, endedAt = now)
            s.copy(entries = entries, lotteries = s.lotteries + (lotteryId to lottery.copy(spotsFree = lottery.spotsFree + 1)))
        }
    }

    override suspend fun rejectReservation(lotteryId: String, entrantId: String, reason: String, now: Long) {
        state.update { s ->
            val lottery = s.lotteries[lotteryId] ?: error("No lottery $lotteryId")
            val entries = s.entries.toMutableList()
            val i = entries.winner(lotteryId, entrantId)
            entries[i] = entries[i].copy(status = EntryStatus.REJECTED, note = reason, endedAt = now)
            s.copy(
                entries = entries,
                lotteries = s.lotteries + (lotteryId to lottery.copy(spotsFree = lottery.spotsFree + 1)),
                notifications = s.notifications + LotteryDrawer.rejectedNotice(lottery, entrantId, reason, now).copy(id = newId("note")),
            )
        }
    }

    override suspend fun fillFreeSpots(lotteryId: String, now: Long, random: Random): Int {
        var filled = 0
        state.update { s ->
            filled = 0
            val lottery = s.lotteries[lotteryId] ?: return@update s
            if (lottery.status != LotteryStatus.DRAWN || lottery.spotsFree <= 0) return@update s
            val result = LotteryDrawer.fillSpots(lottery, s.entries.filter { it.lotteryId == lotteryId }, now, random)
            filled = result.entries.size
            val promoted = result.winnerIds
            s.copy(
                entries = s.entries.map { if (it.lotteryId == lotteryId && it.entrantId in promoted) it.copy(status = EntryStatus.WON) else it },
                lotteries = s.lotteries + (lotteryId to lottery.copy(spotsFree = 0)),
                notifications = s.notifications + result.notifications.map { it.copy(id = newId("note")) },
            )
        }
        return filled
    }

    override fun observeEndedReservationsForOwner(ownerId: String): Flow<List<Reservation>> = select { s ->
        s.entries.mapNotNull { it.toEndedReservation() }.filter { it.ownerId == ownerId }.sortedByDescending { it.endedAt ?: 0L }
    }

    private fun reservations(filter: (Reservation) -> Boolean): Flow<List<Reservation>> =
        select { s -> s.entries.mapNotNull { it.toReservation() }.filter(filter).sortedBy { it.checkInDay } }

    override fun observeReservationsForOwner(ownerId: String): Flow<List<Reservation>> = reservations { it.ownerId == ownerId }

    override fun observeReservationsForCampsite(campsiteId: String): Flow<List<Reservation>> = reservations { it.campsiteId == campsiteId }

    override fun observeReservationsForEntrant(entrantId: String): Flow<List<Reservation>> = reservations { it.entrantId == entrantId }


    // ---- Notifications ----

    override fun observeNotifications(recipientId: String): Flow<List<AppNotification>> =
        select { s -> s.notifications.filter { it.recipientId == recipientId }.sortedByDescending { it.createdAt } }

    override suspend fun markAllRead(recipientId: String) {
        state.update { s ->
            s.copy(notifications = s.notifications.map { if (it.recipientId == recipientId) it.copy(read = true) else it })
        }
    }

    // ---- Ratings, follows, chats, announcements, reports ----

    override fun observeRatings(campsiteId: String): Flow<List<Rating>> =
        select { s -> s.ratings.filter { it.campsiteId == campsiteId }.sortedByDescending { it.createdAt } }

    override suspend fun rate(rating: Rating) {
        state.update { s ->
            val site = s.campsites[rating.campsiteId] ?: error("No campsite ${rating.campsiteId}")
            val previous = s.ratings.firstOrNull { it.campsiteId == rating.campsiteId && it.userId == rating.userId }
            val (average, count) = RatingPolicy.aggregate(site.rating, site.ratingCount, rating.stars, previous?.stars)
            s.copy(
                ratings = s.ratings.filterNot { it === previous } + rating,
                campsites = s.campsites + (site.id to site.copy(rating = average, ratingCount = count)),
            )
        }
    }

    override fun observeFollowing(campsiteId: String, userId: String): Flow<Boolean> = select { (campsiteId to userId) in it.follows }

    override suspend fun setFollowing(campsiteId: String, userId: String, userName: String, follow: Boolean, now: Long) {
        state.update { s -> s.copy(follows = if (follow) s.follows + (campsiteId to userId) else s.follows - (campsiteId to userId)) }
    }

    override suspend fun notifyFollowers(lottery: Lottery, now: Long): Int {
        val followers = state.value.follows.filter { it.first == lottery.campsiteId }.map { it.second }
        state.update { s -> s.copy(notifications = s.notifications + followers.map { openedNotice(lottery, it, now).copy(id = newId("note")) }) }
        return followers.size
    }

    override fun observeChatsFor(userId: String): Flow<List<Chat>> =
        select { s -> s.chats.values.filter { it.ownerId == userId || it.entrantId == userId }.sortedByDescending { it.lastAt } }

    override fun observeAllChats(): Flow<List<Chat>> = select { s -> s.chats.values.sortedByDescending { it.lastAt } }

    override fun observeChat(chatId: String): Flow<Chat?> = select { it.chats[chatId] }

    override fun observeMessages(chatId: String): Flow<List<ChatMessage>> = select { s -> s.messages.filter { it.chatId == chatId }.sortedBy { it.sentAt } }

    override suspend fun sendMessage(chat: Chat, senderId: String, senderName: String, text: String, now: Long) {
        val fromOwner = senderId == chat.ownerId
        state.update { s ->
            s.copy(
                chats = s.chats + (chat.id to chat.copy(lastText = text, lastAt = now, lastSenderId = senderId, unreadForOwner = !fromOwner, unreadForEntrant = fromOwner)),
                messages = s.messages + ChatMessage(newId("msg"), chat.id, senderId, senderName, text, now),
            )
        }
    }

    override suspend fun markChatRead(chatId: String, userId: String) {
        state.update { s ->
            val chat = s.chats[chatId] ?: return@update s
            s.copy(chats = s.chats + (chatId to if (userId == chat.ownerId) chat.copy(unreadForOwner = false) else chat.copy(unreadForEntrant = false)))
        }
    }

    override suspend fun sendAnnouncement(lottery: Lottery, audience: Audience, text: String, now: Long): Int {
        val recipients = state.value.entries.filter { it.lotteryId == lottery.id && audience.includes(it) }
        state.update { s ->
            s.copy(
                notifications = s.notifications + recipients.map { announcementNotice(lottery, it.entrantId, text, now).copy(id = newId("note")) },
                announcements = s.announcements + Announcement(newId("ann"), lottery.id, lottery.campsiteId, lottery.campsiteName, lottery.ownerId, text, audience, recipients.size, now),
            )
        }
        return recipients.size
    }

    override fun observeAnnouncements(lotteryId: String): Flow<List<Announcement>> =
        select { s -> s.announcements.filter { it.lotteryId == lotteryId }.sortedByDescending { it.sentAt } }

    override fun observeAnnouncementsByOwner(ownerId: String): Flow<List<Announcement>> =
        select { s -> s.announcements.filter { it.ownerId == ownerId }.sortedByDescending { it.sentAt } }

    override fun observeAllAnnouncements(): Flow<List<Announcement>> = select { s -> s.announcements.sortedByDescending { it.sentAt } }

    override fun observeAllNotifications(): Flow<List<AppNotification>> = select { s -> s.notifications.sortedByDescending { it.createdAt } }

    override suspend fun report(report: Report) {
        state.update { s -> s.copy(reports = s.reports + report.copy(id = newId("rep"))) }
    }

    override fun observeReports(): Flow<List<Report>> = select { s -> s.reports.sortedByDescending { it.createdAt } }

    override suspend fun dismissReports(campsiteId: String) {
        state.update { s -> s.copy(reports = s.reports.filterNot { it.campsiteId == campsiteId }) }
    }

    override suspend fun warn(profile: UserProfile, text: String, now: Long) {
        state.update { s ->
            val current = s.profiles[profile.id] ?: profile
            s.copy(
                notifications = s.notifications + warningNotice(profile.id, text, now).copy(id = newId("note")),
                profiles = s.profiles + (profile.id to current.copy(warnings = current.warnings + 1)),
            )
        }
    }
}

/**
 * The dependencies every screen can use.
 *
 * @property deviceId stable phone identifier used to look up a trusted profile; not a login credential.
 * @property profiles profile data.
 * @property campsites campsite data.
 * @property lotteries lottery and waiting-list data.
 * @property notifications notification data.
 * @property clock time source.
 * @property usingServer true when repositories use Firestore; this is not a network-health check.
 * @property social ratings, follows, chats, announcements, reports and moderation.
 */
open class AppContainer(
    val deviceId: String,
    val deviceName: String = "This phone",
    val profiles: ProfileRepository,
    val campsites: CampsiteRepository,
    val lotteries: LotteryRepository,
    val notifications: NotificationRepository,
    val clock: Clock,
    val usingServer: Boolean,
    val userId: String = deviceId,
    val auth: AuthRepository = InMemoryAuth(deviceId),
    val social: SocialRepository = InMemoryEventsStore(),
) {
    /** The same data layer, acting as the signed-in person [id] (set once their profile loads). */
    fun forUser(id: String): AppContainer =
        if (id == userId) this
        else AppContainer(deviceId, deviceName, profiles, campsites, lotteries, notifications, clock, usingServer, id, auth, social).also {
            it.sharedScheduler = drawScheduler
        }

    private var sharedScheduler: DrawScheduler? = null

    /** Runs automatic lottery draws while the app is open. */
    val drawScheduler: DrawScheduler by lazy { sharedScheduler ?: DrawScheduler(lotteries, clock) }

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
         * an empty in-memory store (used only for builds without google-services.json, e.g. CI).
         */
        fun create(context: Context): AppContainer {
            val deviceId = readDeviceId(context)
            val firebase = FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context)
            return if (firebase != null) {
                val store = FirestoreEventsStore(FirebaseFirestore.getInstance(firebase))
                AppContainer(
                    deviceId, readDeviceName(), store, store, store, store, Clock.System, usingServer = true,
                    auth = FirebaseAuthStore(FirebaseAuth.getInstance(firebase)), social = store,
                )
            } else {
                inMemory(deviceId, Clock.System)
            }
        }

        /** "Google Pixel 8"-style name for the trusted-phones list. */
        private fun readDeviceName(): String {
            val maker = android.os.Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
            val model = android.os.Build.MODEL.orEmpty()
            return (if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model").trim().ifBlank { "Android phone" }
        }

        /** A container on an in-memory store, for tests and builds without Firebase. */
        fun inMemory(deviceId: String, clock: Clock, store: InMemoryEventsStore = InMemoryEventsStore(), auth: AuthRepository = InMemoryAuth(deviceId)) =
            AppContainer(deviceId, "This phone", store, store, store, store, clock, usingServer = false, auth = auth, social = store)

        /**
         * The Android ID: unique per device, user and app signing key, and stable across
         * reinstalls for the same signing/user context. It looks up trust after credential-based
         * signup; it does not replace authentication. If Android gives
         * none, a random ID is made once and kept on this phone, so two such phones never share
         * one (the old shared "unknown-device" fallback would have mixed them up).
         */
        @SuppressLint("HardwareIds")
        private fun readDeviceId(context: Context): String =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?.takeIf { it.isNotBlank() && it != BROKEN_ANDROID_ID } ?: fallbackDeviceId(context)

        /** A known emulator bug returns this same ID on every phone. */
        private const val BROKEN_ANDROID_ID = "9774d56d682e549c"

        /** A random ID created on first use and stored on this phone. */
        private fun fallbackDeviceId(context: Context): String {
            val prefs = context.getSharedPreferences("device_identity", Context.MODE_PRIVATE)
            prefs.getString("id", null)?.let { return it }
            val id = "phone-" + java.util.UUID.randomUUID().toString()
            prefs.edit().putString("id", id).apply()
            return id
        }
    }
}
