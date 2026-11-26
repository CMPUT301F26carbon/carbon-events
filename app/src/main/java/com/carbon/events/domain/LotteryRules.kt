/*
 * LotteryRules.kt (Vaibhav)
 *
 * Lottery rules: the fair random draw, the automatic draw scheduler and the waitlist rules.
 *
 * Outstanding issues: the automatic draw runs on the owner's phone while the app is
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
        val updated = entries.map { if (it.entrantId in winners) it.copy(status = EntryStatus.WON, wonAt = now) else it.copy(status = EntryStatus.LOST) }
        val notifications = updated.map { entry ->
            val won = entry.status == EntryStatus.WON
            AppNotification(
                id = "",
                recipientId = entry.entrantId,
                type = if (won) NotificationType.LOTTERY_WON else NotificationType.LOTTERY_LOST,
                title = if (won) "You won ${lottery.campsiteName}!" else "${lottery.campsiteName} results",
                body = if (won) {
                    "You were drawn in the lottery. Open My Lotteries and book your nights within ${REPLY_HOURS} hours, or the spot goes to someone else."
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
     * Nobody is promoted once the booking period has ended: there would be no nights left to book.
     *
     * @param lottery a drawn lottery.
     * @param entries its whole waiting list, including earlier winners.
     * @return only the promoted entries, plus one notification each.
     */
    fun fillSpots(lottery: Lottery, entries: List<WaitlistEntry>, now: Long, random: Random = Random.Default): DrawResult {
        if (!hasNightsLeft(lottery, now)) return DrawResult(emptyList(), emptyList())
        val active = entries.count { it.status == EntryStatus.WON }
        val open = minOf(lottery.spotsFree, (lottery.winnerCount - active).coerceAtLeast(0))
        val chosen = entries.filter { it.status == EntryStatus.LOST }.shuffled(random).take(open)
            .map { it.copy(status = EntryStatus.WON, wonAt = now) }
        val notices = chosen.map { e ->
            AppNotification(
                id = "", recipientId = e.entrantId, type = NotificationType.SPOT_OPENED,
                title = "A spot opened at ${lottery.campsiteName}!",
                body = "A winner gave their spot back and you were drawn next. Open My Lotteries and book your nights within $REPLY_HOURS hours.",
                lotteryId = lottery.id, campsiteId = lottery.campsiteId, createdAt = now,
            )
        }
        return DrawResult(chosen, notices)
    }

    /**
     * Whether a spot given back at [now] could still be used: the lottery has no booking period yet,
     * or at least its last night (the day before [Lottery.bookingEndDay]) is still to come.
     */
    fun hasNightsLeft(lottery: Lottery, now: Long): Boolean = lottery.bookingEndDay == null || dayOf(now) < lottery.bookingEndDay

    /** How long a winner has to book their nights before the spot goes to someone else. */
    const val REPLY_HOURS = 48

    /**
     * When [entry], a winner of [lottery], must book by (epoch ms), or null if it isn't waiting on
     * them. Winners drawn before the deadline existed (no selection time saved) have none.
     */
    fun replyDeadline(lottery: Lottery, entry: WaitlistEntry): Long? {
        if (entry.status != EntryStatus.WON || entry.isBooked) return null
        return entry.wonAt?.plus(REPLY_HOURS * 3_600_000L)
    }

    /**
     * The winners of [lottery] who didn't book in time: each is marked declined, with a notice
     * saying why, so their spot can be offered to the next person.
     */
    fun expireUnbooked(lottery: Lottery, entries: List<WaitlistEntry>, now: Long): DrawResult {
        val expired = entries.filter { e -> replyDeadline(lottery, e)?.let { now >= it } == true }
            .map { it.copy(status = EntryStatus.DECLINED, endedAt = now, note = "Didn't book within $REPLY_HOURS hours") }
        val notices = expired.map { e ->
            AppNotification(
                id = "", recipientId = e.entrantId, type = NotificationType.LOTTERY_LOST,
                title = "Your spot at ${lottery.campsiteName} went to someone else",
                body = "You didn't book your nights within $REPLY_HOURS hours of being selected, so the spot was offered to the next person.",
                lotteryId = lottery.id, campsiteId = lottery.campsiteId, createdAt = now,
            )
        }
        return DrawResult(expired, notices)
    }

    /**
     * The confirmation a guest gets after cancelling their booked nights (US 01.07.02), saying what
     * it costs under [site]'s cancellation policy (nothing before the free-cancellation date).
     */
    fun cancelledNotice(lottery: Lottery, entrantId: String, checkInDay: Long, checkOutDay: Long, now: Long, site: Campsite? = null) = AppNotification(
        id = "", recipientId = entrantId, type = NotificationType.CANCELLED,
        title = "Booking at ${lottery.campsiteName} cancelled",
        body = "Your stay ${formatStay(checkInDay, checkOutDay).substringBefore(" ·")} is cancelled and your spot goes to the next person on the waitlist. " +
            (site?.cancellation?.chargeLine(checkInDay, dayOf(now), site.pricePerNight) ?: "Nothing was charged."),
        lotteryId = lottery.id, campsiteId = lottery.campsiteId, createdAt = now,
    )

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
    /** Who this phone draws for: the signed-in person, for the lotteries of campsites they own. */
    data class Actor(val userId: String)

    private val actor = MutableStateFlow<Actor?>(null)

    /** When this phone last looked for winners who didn't book in time, by lottery. */
    private val lastExpiryCheck = mutableMapOf<String, Long>()

    /** Number of draws this device has performed (for diagnostics and tests). */
    val drawsPerformed = MutableStateFlow(0)

    /**
     * Sets the signed-in person, or null when signed out (then nothing is drawn). A draw belongs
     * to the lottery's owner, so a phone only ever draws its own person's lotteries, admin or not.
     */
    fun actAs(userId: String?) {
        actor.value = userId?.let { Actor(it) }
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
        for (lottery in current.filter { it.status == LotteryStatus.DRAWN && LotteryDrawer.hasNightsLeft(it, now) }) {
            // Reading the whole list is checked every few minutes, not on every tick.
            val expired = if (now - (lastExpiryCheck[lottery.id] ?: 0L) >= EXPIRY_CHECK_MS) {
                lastExpiryCheck[lottery.id] = now
                runCatching { lotteries.expireUnbookedWinners(lottery.id, now) }.getOrDefault(0)
            } else 0
            if (lottery.spotsFree > 0 || expired > 0) runCatching { lotteries.fillFreeSpots(lottery.id, now) }
        }
        if (count > 0) drawsPerformed.value += count
        return count
    }

    /**
     * Starts a process-local timer and subscriptions for the actor set with [actAs].
     * Signed-out actors draw nothing; everyone else watches only the lotteries they own.
     * Cancelling [scope] stops the job. This is not a server-side scheduling service.
     * @return the child job, which callers may cancel without cancelling the containing scope.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope): Job = scope.launch {
        val ticks = flow { while (true) { emit(Unit); delay(tickMillis) } }
        actor.flatMapLatest { a ->
            when {
                a == null -> flowOf(emptyList())
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

/** How often the owner's phone looks for winners who didn't book in time. */
private const val EXPIRY_CHECK_MS = 5 * 60_000L
