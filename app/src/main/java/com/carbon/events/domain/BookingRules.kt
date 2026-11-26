/*
 * BookingRules.kt (Vaibhav)
 *
 * Rules for winners booking their nights, for owners changing a published lottery, and for
 * keeping one campsite's lotteries from offering the same nights twice.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events


/** Why booking a winner's dates was refused. */
enum class BookingError(val message: String) {
    NOT_A_WINNER("Only lottery winners can book dates."),
    ALREADY_BOOKED("You've already booked your dates for this lottery."),
    NO_BOOKING_PERIOD("The owner hasn't set a booking period for this lottery yet."),
    BAD_RANGE("Check-out must be after check-in."),
    OUTSIDE_PERIOD("Pick nights inside the booking period."),
    TOO_LONG("You can book at most ${BookingPolicy.MAX_NIGHTS} nights."),
    TAKEN("Some of those nights are already booked by another winner."),
}

/** Thrown by repositories when a booking breaks a [BookingPolicy] rule. */
class BookingException(val error: BookingError) : Exception(error.message)

/** Rules for lottery winners booking their nights (minimal reservation flow for US 02.06.01, US 02.07.01/02). */
object BookingPolicy {
    /** Longest stay any winner can book; each campsite can set a lower limit. */
    const val MAX_NIGHTS = 14

    /** Whether the stays [aIn]..[aOut] and [bIn]..[bOut] share a night (check-out days are free). */
    fun overlaps(aIn: Long, aOut: Long, bIn: Long, bOut: Long): Boolean = aIn < bOut && bIn < aOut

    /**
     * Checks whether a winner may book [checkInDay] to [checkOutDay].
     *
     * @param lottery the lottery the entrant won.
     * @param entry the entrant's own entry, or null if they are not on the list.
     * @param checkInDay first night (day number).
     * @param checkOutDay check-out day (day number).
     * @param others every winner's entry for this lottery (the entrant's own is ignored).
     * @return null if allowed, otherwise the reason it is not.
     */
    fun check(lottery: Lottery, entry: WaitlistEntry?, checkInDay: Long, checkOutDay: Long, others: List<WaitlistEntry>): BookingError? {
        val start = lottery.bookingStartDay
        val end = lottery.bookingEndDay
        return when {
            entry == null || entry.status != EntryStatus.WON -> BookingError.NOT_A_WINNER
            entry.isBooked -> BookingError.ALREADY_BOOKED
            start == null || end == null -> BookingError.NO_BOOKING_PERIOD
            checkOutDay <= checkInDay -> BookingError.BAD_RANGE
            checkInDay < start || checkOutDay > end -> BookingError.OUTSIDE_PERIOD
            checkOutDay - checkInDay > MAX_NIGHTS -> BookingError.TOO_LONG
            others.any { it.entrantId != entry.entrantId && it.isBooked && overlaps(checkInDay, checkOutDay, it.checkInDay!!, it.checkOutDay!!) } ->
                BookingError.TAKEN
            else -> null
        }
    }

    /** Day numbers of every night already booked by the given winners. */
    fun takenNights(entries: List<WaitlistEntry>): Set<Long> =
        entries.mapNotNull { it.toReservation() }.flatMap { it.nightDays() }.toSet()
}

/**
 * One campsite's lotteries can't share stay nights. Winners of different lotteries can't see each
 * other's bookings, so if two lotteries offered the same nights, both sets of winners could book
 * them; keeping the stay periods apart means each night belongs to one lottery only.
 */
object StayPeriods {
    /** Another lottery at [lottery]'s campsite whose stay period shares a night with it, or null. */
    fun clash(lottery: Lottery, others: List<Lottery>): Lottery? {
        if (!lottery.hasBookingPeriod) return null
        return others.firstOrNull {
            it.id != lottery.id && it.campsiteId == lottery.campsiteId && it.hasBookingPeriod &&
                BookingPolicy.overlaps(lottery.bookingStartDay!!, lottery.bookingEndDay!!, it.bookingStartDay!!, it.bookingEndDay!!)
        }
    }

    /** What the owner is told when [lottery] at [campsiteName] would share nights with [other]. */
    fun message(campsiteName: String, other: Lottery): String =
        "$campsiteName already has a lottery for stays ${formatStay(other.bookingStartDay!!, other.bookingEndDay!!).substringBefore(" ·")}. Pick stay dates that don't overlap it."

    /** Throws [LotteryEditException] if [lottery] would share nights with one of [others]. */
    fun requireApart(lottery: Lottery, others: List<Lottery>) {
        clash(lottery, others)?.let { throw LotteryEditException(message(lottery.campsiteName, it)) }
    }
}

/** Why an owner's lottery change was refused (US 02.04.06). */
class LotteryEditException(message: String) : Exception(message)

/** Rules for updating a lottery listing after it has been published (US 02.04.06). */
object LotteryEditPolicy {
    /**
     * Checks whether [current] may be changed to [updated] at time [now].
     *
     * @return null if allowed, otherwise a message explaining why not.
     */
    fun check(current: Lottery, updated: Lottery, now: Long): String? = when {
        current.status == LotteryStatus.DRAWN -> "Winners have been drawn, so this lottery can no longer be changed."
        updated.campsiteId != current.campsiteId -> "A lottery can't be moved to another campsite."
        now >= current.registrationStart && updated.registrationStart != current.registrationStart ->
            "Registration has already opened, so its start can't change."
        updated.registrationEnd <= now -> "The closing time must be in the future."
        updated.registrationEnd <= updated.registrationStart -> "Closing must be after opening."
        updated.winnerCount < 1 -> "Draw at least 1 winner."
        updated.waitlistCap != null && updated.waitlistCap < current.entrantCount ->
            "The cap can't be below the ${current.entrantCount} people already on the waitlist."
        else -> null
    }
}
