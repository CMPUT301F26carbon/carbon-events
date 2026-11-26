/*
 * Models.kt (Vaibhav)
 *
 * The app's data: people, campsites, lotteries, waiting-list entries, reservations,
 * notifications and trusted phones.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import kotlinx.coroutines.flow.first

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
 * @property removed set when an admin removes the person (US 03.02.01, US 03.07.01): everything
 *   of theirs is gone and only this blocked record stays on their login, so they can't sign up again.
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
    val removed: Boolean = false,
    /** When this person last used "Log out of all devices" (0 if never): every open phone signs out when it changes. */
    val signedOutAt: Long = 0L,
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
            NotificationType.REJECTED, NotificationType.CANCELLED -> reservations
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
    FLEXIBLE("Flexible", 1, "Free cancellation until the day before check-in; after that the first night isn't refunded."),
    MODERATE("Moderate", 5, "Free cancellation until 5 days before check-in; after that the first night isn't refunded."),
    STRICT("Strict", 14, "Free cancellation until 14 days before check-in; after that the first night isn't refunded."),
    ;

    /** The last day (a day number) a stay starting on [checkInDay] can be cancelled for free. */
    fun freeUntil(checkInDay: Long): Long = checkInDay - freeDaysBefore

    /**
     * What cancelling on [today] (a day number) costs, in dollars, for a stay starting on
     * [checkInDay] at [pricePerNight]: nothing up to [freeUntil], then the first night.
     */
    fun charge(checkInDay: Long, today: Long, pricePerNight: Int): Int = if (today <= freeUntil(checkInDay)) 0 else pricePerNight

    /** One line for the guest about what cancelling on [today] costs. */
    fun chargeLine(checkInDay: Long, today: Long, pricePerNight: Int): String {
        val owed = charge(checkInDay, today, pricePerNight)
        return if (owed == 0) "Free cancellation: nothing is charged."
        else "Under the $label cancellation policy, the first night ($$owed) isn't refunded."
    }

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
 * @property groupId shared by the lotteries an owner published together for several campsites
 *   (US 02.04.02); blank for a lottery on its own. See [LotteryGroups].
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
    val groupId: String = "",
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
 * @property wonAt when the entrant was selected (epoch ms), or null (then the lottery's draw time).
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
    val wonAt: Long? = null,
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
    WARNING,

    /** The confirmation a guest gets after cancelling a booked stay (US 01.07.02). */
    CANCELLED;

    /** The word stored in Firestore ("won", "lost", "spot", ...). */
    val word: String get() = when (this) {
        LOTTERY_WON -> "won"; LOTTERY_LOST -> "lost"; SPOT_OPENED -> "spot"; REJECTED -> "rejected"
        MESSAGE -> "message"; OPENED -> "opened"; WARNING -> "warning"; CANCELLED -> "cancelled"
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
data class TrustedDevice(val deviceId: String, val userId: String, val model: String, val addedAt: Long, val blocked: Boolean = false)
