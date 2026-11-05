/*
 * LotteryRules.kt (Vaibhav)
 *
 * Lottery rules: the fair random draw, the automatic draw scheduler, waitlist
 * rules, search, form validators and promo QR codes.
 *
 * Outstanding issues: the automatic draw runs on the owner's (or an admin's) phone while the app is
 * open; a server function would draw even when nobody has it open.
 */
package com.carbon.events

import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Supplies the current time in epoch milliseconds. */
fun interface Clock {
    /** @return the current time in epoch milliseconds. */
    fun now(): Long

    companion object {
        /** The real device clock. */
        val System: Clock = Clock { java.lang.System.currentTimeMillis() }
    }
}

/**
 * A clock that returns whatever time it is set to. Used by tests and previews.
 *
 * @property time the time [now] returns, in epoch milliseconds.
 */
class FixedClock(var time: Long) : Clock {
    override fun now(): Long = time

    /** Moves the clock forward by [millis]. */
    fun advanceBy(millis: Long) {
        time += millis
    }
}

/** Result of a draw: every entry with its new status, plus one notification per entrant. */
data class DrawResult(
    /** All waiting-list entries, each marked [EntryStatus.WON] or [EntryStatus.LOST]. */
    val entries: List<WaitlistEntry>,
    /** One notification per entrant, without IDs (the repository assigns them). */
    val notifications: List<AppNotification>,
) {
    /** Entrant IDs of the winners. */
    val winnerIds: Set<String> get() = entries.filter { it.status == EntryStatus.WON }.map { it.entrantId }.toSet()
}

/**
 * Pure lottery operations. Repositories persist results and notification records; this object
 * does not deliver Android notifications. Supply a seeded Random for repeatable tests.
 */
object LotteryDrawer {

    /**
     * Chooses up to [winnerCount] winners uniformly at random from [entries].
     * Every entrant has the same chance; join order does not matter.
     *
     * @param entries the waiting list.
     * @param winnerCount how many winners to draw; if larger than the list, everyone wins.
     * @param random source of randomness (seeded in tests).
     * @return the entrant IDs of the winners.
     */
    fun pickWinners(entries: List<WaitlistEntry>, winnerCount: Int, random: Random = Random.Default): Set<String> {
        require(winnerCount >= 0) { "winnerCount must not be negative" }
        val ids = entries.map { it.entrantId }.distinct()
        return ids.shuffled(random).take(winnerCount).toSet()
    }

    /**
     * Runs the full draw for [lottery]: picks winners and creates the notifications.
     *
     * @param lottery the lottery being drawn.
     * @param entries its waiting list at the time of the draw.
     * @param now the draw time (epoch ms), used as the notifications' timestamp.
     * @param random source of randomness.
     * @return updated entries and the notifications to send.
     */
    fun draw(lottery: Lottery, entries: List<WaitlistEntry>, now: Long, random: Random = Random.Default): DrawResult {
        val winners = pickWinners(entries, lottery.winnerCount, random)
        val updated = entries.map { it.copy(status = if (it.entrantId in winners) EntryStatus.WON else EntryStatus.LOST) }
        val notifications = updated.map { entry ->
            val won = entry.status == EntryStatus.WON
            AppNotification(
                id = "",
                recipientId = entry.entrantId,
                type = if (won) NotificationType.LOTTERY_WON else NotificationType.LOTTERY_LOST,
                title = if (won) "You won ${lottery.campsiteName}!" else "${lottery.campsiteName} results",
                body = if (won) {
                    "You were drawn in the lottery. Open My Lotteries to book your nights."
                } else {
                    "Not selected this time. Thanks for entering, and good luck next time."
                },
                lotteryId = lottery.id,
                campsiteId = lottery.campsiteId,
                createdAt = now,
            )
        }
        return DrawResult(updated, notifications)
    }

    /**
     * Offers the spots winners gave back (US 02.05.03): picks, at random, up to as many
     * not-selected entrants as there are free winner spots and marks them [EntryStatus.WON].
     *
     * @param lottery a drawn lottery.
     * @param entries its whole waiting list, including earlier winners.
     * @return only the promoted entries, plus one notification each.
     */
    fun fillSpots(lottery: Lottery, entries: List<WaitlistEntry>, now: Long, random: Random = Random.Default): DrawResult {
        val active = entries.count { it.status == EntryStatus.WON }
        val open = minOf(lottery.spotsFree, (lottery.winnerCount - active).coerceAtLeast(0))
        val chosen = entries.filter { it.status == EntryStatus.LOST }.shuffled(random).take(open)
            .map { it.copy(status = EntryStatus.WON) }
        val notices = chosen.map { e ->
            AppNotification(
                id = "", recipientId = e.entrantId, type = NotificationType.SPOT_OPENED,
                title = "A spot opened at ${lottery.campsiteName}!",
                body = "A winner gave their spot back and you were drawn next. Open My Lotteries to book your nights.",
                lotteryId = lottery.id, campsiteId = lottery.campsiteId, createdAt = now,
            )
        }
        return DrawResult(chosen, notices)
    }

    /** The notification a guest gets when the owner rejects their reservation. */
    fun rejectedNotice(lottery: Lottery, entrantId: String, reason: String, now: Long) = AppNotification(
        id = "", recipientId = entrantId, type = NotificationType.REJECTED,
        title = "Reservation at ${lottery.campsiteName} rejected",
        body = "The owner rejected your reservation: ${reason.ifBlank { "no reason given" }}. No payment was taken.",
        lotteryId = lottery.id, campsiteId = lottery.campsiteId, createdAt = now,
    )
}

/**
 * Runs due lottery draws.
 *
 * @param lotteries the lottery repository.
 * @param clock time source.
 * @param tickMillis how often to re-check while lotteries are unchanged.
 */
class DrawScheduler(
    private val lotteries: LotteryRepository,
    private val clock: Clock,
    private val tickMillis: Long = 15_000L,
) {
    /** Who this phone draws for. Only owners (their own lotteries) and admins (all) may draw. */
    data class Actor(val userId: String, val isAdmin: Boolean)

    private val actor = MutableStateFlow<Actor?>(null)

    /** Number of draws this device has performed (for diagnostics and tests). */
    val drawsPerformed = MutableStateFlow(0)

    /** Sets the signed-in person, or null when signed out (then nothing is drawn). */
    fun actAs(userId: String?, isAdmin: Boolean) {
        actor.value = userId?.let { Actor(it, isAdmin) }
    }

    /**
     * Checks every lottery once: draws those that are due and offers given-back spots.
     * @return how many draws this call performed.
     */
    suspend fun drawDue(current: List<Lottery>): Int {
        val now = clock.now()
        var count = 0
        for (lottery in current.filter { it.isDrawDue(now) }) {
            val drew = runCatching { lotteries.runDrawIfDue(lottery.id, now) }.getOrDefault(false)
            if (drew) count++
        }
        for (lottery in current.filter { it.status == LotteryStatus.DRAWN && it.spotsFree > 0 }) {
            runCatching { lotteries.fillFreeSpots(lottery.id, now) }
        }
        if (count > 0) drawsPerformed.value += count
        return count
    }

    /**
     * Starts a process-local timer and subscriptions for the actor set with [actAs].
     * Signed-out actors draw nothing; ordinary profiles watch their own lotteries, while admins
     * watch all. Cancelling [scope] stops the job. This is not a server-side scheduling service.
     * @return the child job, which callers may cancel without cancelling the containing scope.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope): Job = scope.launch {
        val ticks = flow { while (true) { emit(Unit); delay(tickMillis) } }
        actor.flatMapLatest { a ->
            when {
                a == null -> flowOf(emptyList())
                a.isAdmin -> lotteries.observeLotteries()
                else -> lotteries.observeLotteriesByOwner(a.userId)
            }
        }.combine(ticks) { list, _ -> list }.collectLatest { drawDue(it) }
    }
}

/** Why joining or leaving a waiting list was refused. */
enum class WaitlistError(val message: String) {
    NOT_OPEN_YET("Registration for this lottery hasn't opened yet."),
    CLOSED("Registration for this lottery has closed."),
    ALREADY_JOINED("You're already on this waiting list."),
    FULL("This waiting list is full."),
    NOT_JOINED("You're not on this waiting list."),
}

/** Thrown by repositories when a waiting-list change breaks a [WaitlistPolicy] rule. */
class WaitlistException(val error: WaitlistError) : Exception(error.message)

/** Rules for joining and leaving lottery waiting lists. */
object WaitlistPolicy {

    /**
     * Checks whether an entrant may join.
     *
     * @param lottery the lottery.
     * @param alreadyJoined whether the entrant is already on its waiting list.
     * @param currentSize current waiting-list size.
     * @param now current time (epoch ms).
     * @return null if joining is allowed, otherwise the reason it is not.
     */
    fun checkJoin(lottery: Lottery, alreadyJoined: Boolean, currentSize: Int, now: Long): WaitlistError? = when {
        alreadyJoined -> WaitlistError.ALREADY_JOINED
        lottery.isUpcoming(now) -> WaitlistError.NOT_OPEN_YET
        !lottery.isRegistrationOpen(now) -> WaitlistError.CLOSED
        lottery.waitlistCap != null && currentSize >= lottery.waitlistCap -> WaitlistError.FULL
        else -> null
    }

    /**
     * Checks whether an entrant may leave.
     *
     * @param lottery the lottery.
     * @param joined whether the entrant is on its waiting list.
     * @param now current time (epoch ms).
     * @return null if leaving is allowed, otherwise the reason it is not.
     */
    fun checkLeave(lottery: Lottery, joined: Boolean, now: Long): WaitlistError? = when {
        !joined -> WaitlistError.NOT_JOINED
        !lottery.isRegistrationOpen(now) -> WaitlistError.CLOSED
        else -> null
    }
}

/** Searches campsites by keyword. */
object CampsiteSearch {

    /**
     * Returns the campsites matching [query].
     *
     * A campsite matches when every word in [query] appears somewhere in its searchable
     * text. A blank query returns all campsites. Results are ordered with name matches
     * first, then alphabetically.
     *
     * @param campsites campsites to search.
     * @param query what the user typed.
     * @return matching campsites, best matches first.
     */
    fun search(campsites: List<Campsite>, query: String): List<Campsite> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return campsites.sortedBy { it.name.lowercase() }
        return campsites
            .filter { site -> val text = searchableText(site); words.all { it in text } }
            .sortedWith(compareByDescending<Campsite> { site -> words.all { it in site.name.lowercase() } }
                .thenBy { it.name.lowercase() })
    }

    /** Everything about a campsite that search looks at, in lower case. */
    fun searchableText(site: Campsite): String = listOf(
        site.name, site.location, site.amenities, site.description, site.tags.joinToString(" "),
    ).joinToString(" ").lowercase()
}

/** How search results are ordered. */
enum class SearchSort(val label: String) { BEST("Best match"), NEAREST("Nearest"), PRICE("Lowest price"), RATING("Top rated") }

/**
 * Search filters (Figma E-FILTERS). Every null / empty value means "any".
 *
 * @property guests at least this many guests must fit.
 * @property maxPrice highest price per night.
 * @property stayFrom with [stayUntil], the nights the camper wants; matches lotteries whose booking period covers them.
 * @property withinKm only campsites within this distance of the phone (needs its location).
 * @property openOnly only campsites whose lottery is open (or opening soon).
 * @property tags the campsite must have every one of these tags (Figma "Vibe").
 * @property location the campsite's place must contain this (Figma "Location"), or null.
 * @property sort ordering applied after every enabled filter is satisfied.
 * @property amenities each must appear in the campsite's amenities or tags (Figma "Amenities").
 */
data class SearchFilters(
    val guests: Int? = null,
    val maxPrice: Int? = null,
    val stayFrom: Long? = null,
    val stayUntil: Long? = null,
    val withinKm: Int? = null,
    val openOnly: Boolean = false,
    val tags: Set<String> = emptySet(),
    val sort: SearchSort = SearchSort.BEST,
    val location: String? = null,
    val amenities: Set<String> = emptySet(),
) {
    /** How many filters are switched on (for the Filters button badge). */
    val count: Int get() = listOfNotNull(guests, maxPrice, stayFrom, withinKm, location).size + (if (openOnly) 1 else 0) + tags.size + amenities.size
}

/** The Figma E-FILTERS amenity chips and the words that match them in a campsite's amenities. */
val AMENITY_FILTERS: Map<String, List<String>> = linkedMapOf(
    "Fire pit" to listOf("fire"),
    "Water" to listOf("water"),
    "Wi-Fi" to listOf("wi-fi", "wifi"),
    "Parking" to listOf("parking"),
)

/** Applies [SearchFilters] to keyword results. */
object CampsiteFilter {
    /**
     * @param results keyword matches, best first.
     * @param lotteryOf each campsite's current lottery, if any.
     * @param here the phone's location, or null.
     * @param now current time (epoch ms).
     */
    fun apply(results: List<Campsite>, filters: SearchFilters, lotteryOf: (String) -> Lottery?, here: GeoPoint?, now: Long): List<Campsite> {
        val kept = results.filter { site ->
            val lot = lotteryOf(site.id)
            (filters.guests == null || site.maxOccupancy >= filters.guests) &&
                (filters.maxPrice == null || site.pricePerNight <= filters.maxPrice) &&
                (filters.tags.isEmpty() || filters.tags.all { t -> site.tags.any { it.equals(t, true) } }) &&
                (!filters.openOnly || (lot != null && (lot.isRegistrationOpen(now) || lot.isUpcoming(now)))) &&
                (filters.stayFrom == null || filters.stayUntil == null || (lot != null && lot.hasBookingPeriod &&
                    lot.bookingStartDay!! <= filters.stayFrom && lot.bookingEndDay!! >= filters.stayUntil)) &&
                (filters.withinKm == null || here == null || (distanceKm(here, site)?.let { it <= filters.withinKm } ?: false)) &&
                (filters.location == null || site.location.contains(filters.location, ignoreCase = true)) &&
                filters.amenities.all { a ->
                    val text = (site.amenities + " " + site.tags.joinToString(" ")).lowercase()
                    (AMENITY_FILTERS[a] ?: listOf(a.lowercase())).any { it in text }
                }
        }
        return when (filters.sort) {
            SearchSort.BEST -> kept
            SearchSort.NEAREST -> nearestFirst(kept, here) { it }
            SearchSort.PRICE -> kept.sortedBy { it.pricePerNight }
            SearchSort.RATING -> kept.sortedWith(compareByDescending<Campsite> { it.rating }.thenByDescending { it.ratingCount })
        }
    }
}

/** Form fields that can carry a validation error. */
enum class Field { NAME, EMAIL, PHONE, LOCATION, OCCUPANCY, PRICE, REG_START, REG_END, WINNERS, BOOK_START, BOOK_END, CAP, PASSWORD, AGE }

/** Validates the entrant profile form. */
object ProfileValidator {
    private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private val phonePattern = Regex("^[+]?[0-9 ()-]{7,20}$")

    /**
     * @param name full name (required).
     * @param email email address (required).
     * @param phone phone number (optional).
     * @return errors by field; empty when the form is valid.
     */
    fun validate(name: String, email: String, phone: String): Map<Field, String> = buildMap {
        if (name.isBlank()) put(Field.NAME, "Enter your name")
        if (email.isBlank()) put(Field.EMAIL, "Email is required")
        else if (!emailPattern.matches(email.trim())) put(Field.EMAIL, "Enter a valid email")
        if (phone.isNotBlank() && !phonePattern.matches(phone.trim())) put(Field.PHONE, "Enter a valid phone number")
    }

    /** Figma S-SIGNUP's rule: 8 or more characters with at least one number. */
    fun isStrongPassword(password: String): Boolean = password.length >= 8 && password.any(Char::isDigit)

    /** Youngest age accepted at sign-up ("You must be 18+ to reserve a campsite"). */
    const val MIN_AGE = 18

    /**
     * Validates the Create Account form (Figma S-SIGNUP).
     *
     * @param password the new password, or null when the account already exists.
     * @param age age as typed.
     * @return errors by field; empty when the form is valid.
     */
    fun validateSignUp(name: String, email: String, password: String?, phone: String, age: String): Map<Field, String> = buildMap {
        putAll(validate(name, email, phone))
        if (password != null && !isStrongPassword(password)) put(Field.PASSWORD, "Use 8 or more characters with at least one number")
        val years = age.trim().toIntOrNull()
        if (years == null) put(Field.AGE, "Enter your age")
        else if (years < MIN_AGE || years > 120) put(Field.AGE, "You must be $MIN_AGE+ to use Events")
    }
}

/** Validates the campsite registration form. */
object CampsiteValidator {
    /** Largest occupancy accepted for one site. */
    const val MAX_OCCUPANCY = 50

    /**
     * @param name campsite name (required).
     * @param location place name (required).
     * @param maxOccupancy guests per site (1..[MAX_OCCUPANCY]).
     * @param pricePerNight price in dollars (0 or more).
     * @return errors by field; empty when the form is valid.
     */
    fun validate(name: String, location: String, maxOccupancy: Int, pricePerNight: Int): Map<Field, String> = buildMap {
        if (name.isBlank()) put(Field.NAME, "Give your campsite a name")
        if (location.isBlank()) put(Field.LOCATION, "Set a location")
        if (maxOccupancy !in 1..MAX_OCCUPANCY) put(Field.OCCUPANCY, "Occupancy must be 1–$MAX_OCCUPANCY")
        if (pricePerNight < 0) put(Field.PRICE, "Price can't be negative")
    }
}

/** Validates the new-lottery form. */
object LotteryValidator {
    /**
     * @param registrationStart when the waiting list opens (epoch ms).
     * @param registrationEnd when it closes and the draw runs (epoch ms).
     * @param winnerCount number of winners (at least 1).
     * @param now current time (epoch ms); the close time must be in the future.
     * @return errors by field; empty when the form is valid.
     */
    fun validate(registrationStart: Long?, registrationEnd: Long?, winnerCount: Int, now: Long): Map<Field, String> = buildMap {
        if (registrationStart == null) put(Field.REG_START, "Pick when the waitlist opens")
        if (registrationEnd == null) put(Field.REG_END, "Pick when the waitlist closes")
        if (registrationStart != null && registrationEnd != null && registrationEnd <= registrationStart) {
            put(Field.REG_END, "Closing must be after opening")
        }
        if (registrationEnd != null && registrationEnd <= now) put(Field.REG_END, "Closing must be in the future")
        if (winnerCount < 1) put(Field.WINNERS, "Draw at least 1 winner")
    }

    /**
     * Validates a lottery's booking period (US 02.04.04): the nights winners may book.
     *
     * @param registrationEndDay day number on which registration closes, or null if not chosen yet.
     * @param startDay first night guests can stay (day number).
     * @param endDay last check-out day (day number).
     * @return errors by field; empty when valid.
     */
    fun validateBooking(registrationEndDay: Long?, startDay: Long?, endDay: Long?): Map<Field, String> = buildMap {
        if (startDay == null) put(Field.BOOK_START, "Pick the stay dates winners can book")
        if (startDay != null && endDay == null) put(Field.BOOK_END, "Pick the last check-out day")
        if (startDay != null && endDay != null && endDay <= startDay) put(Field.BOOK_END, "Check-out must be after the first night")
        if (startDay != null && registrationEndDay != null && startDay < registrationEndDay) {
            put(Field.BOOK_START, "Stays must start after registration closes")
        }
    }

    /**
     * Validates the optional waitlist cap (US 02.04.07).
     * @param cap maximum entrants, or null for no limit.
     * @return an error message, or null when valid.
     */
    fun validateCap(cap: Int?): String? = if (cap != null && cap < 1) "The cap must be at least 1" else null
}

/** Builds and parses campsite QR payloads. */
object QrCodes {
    /** URI prefix every campsite QR code starts with. */
    const val PREFIX = "carbon-events://campsite/"

    private val idPattern = Regex("^[A-Za-z0-9_-]{1,64}$")

    /**
     * @param campsiteId the campsite's ID.
     * @return the text to encode in the campsite's QR code.
     * @throws IllegalArgumentException if the ID contains a path, whitespace, or exceeds 64 characters.
     */
    fun payloadFor(campsiteId: String): String {
        // Generation and scanning share one ID contract, so published QR codes can round-trip.
        require(idPattern.matches(campsiteId)) { "Invalid campsite ID for a promo QR code" }
        return PREFIX + campsiteId
    }

    /**
     * Extracts the campsite ID from scanned or typed text.
     *
     * Accepts a full payload ("carbon-events://campsite/abc") or, for manual entry, the
     * bare ID ("abc"). Anything else (a website link, random text) returns null.
     *
     * @param text the scanned or typed text.
     * @return the campsite ID, or null if [text] is not a campsite code.
     */
    fun parseCampsiteId(text: String): String? {
        val trimmed = text.trim()
        val id = if (trimmed.startsWith(PREFIX, ignoreCase = true)) trimmed.substring(PREFIX.length).trimEnd('/') else trimmed
        return id.takeIf { idPattern.matches(it) }
    }
}

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
