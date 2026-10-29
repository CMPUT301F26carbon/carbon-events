/*
 * LotteryRules.kt (Vaibhav)
 *
 * Lottery rules: the fair random draw, the automatic draw scheduler, waitlist
 * rules, search, form validators and promo QR codes.
 */
package com.carbon.events

import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
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

/** Picks lottery winners and builds draw notifications. */
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
                    "You were drawn in the lottery. The owner will contact you about booking your dates."
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
    /** Number of draws this device has performed (for diagnostics and tests). */
    val drawsPerformed = MutableStateFlow(0)

    /**
     * Checks every lottery once and draws those that are due.
     * @return how many draws this call performed.
     */
    suspend fun drawDue(current: List<Lottery>): Int {
        val now = clock.now()
        var count = 0
        for (lottery in current.filter { it.isDrawDue(now) }) {
            val drew = runCatching { lotteries.runDrawIfDue(lottery.id, now) }.getOrDefault(false)
            if (drew) count++
        }
        if (count > 0) drawsPerformed.value += count
        return count
    }

    /** Starts watching in [scope] until the scope is cancelled. */
    fun start(scope: CoroutineScope): Job = scope.launch {
        val ticks = flow { while (true) { emit(Unit); delay(tickMillis) } }
        lotteries.observeLotteries().combine(ticks) { list, _ -> list }.collectLatest { drawDue(it) }
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

/** Form fields that can carry a validation error. */
enum class Field { NAME, EMAIL, PHONE, LOCATION, OCCUPANCY, PRICE, REG_START, REG_END, WINNERS }

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
}

/** Builds and parses campsite QR payloads. */
object QrCodes {
    /** URI prefix every campsite QR code starts with. */
    const val PREFIX = "carbon-events://campsite/"

    private val idPattern = Regex("^[A-Za-z0-9_-]{1,64}$")

    /**
     * @param campsiteId the campsite's ID.
     * @return the text to encode in the campsite's QR code.
     */
    fun payloadFor(campsiteId: String): String = PREFIX + campsiteId

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
