/*
 * LotteryGroups.kt (Aditya, Suvir)
 *
 * One lottery over several campsites (Figma O-LOTTERY-NEW-CAMPSITES, E-WON-PICK). An owner who
 * picks more than one campsite publishes one lottery per campsite, all sharing a group ID and
 * the same periods (US 02.04.02). An entrant who joins one joins them all, and a winner who
 * wins more than one site chooses which to reserve; the others go to the next person on the
 * waitlist (US 01.04.02).
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import kotlin.random.Random
import kotlinx.coroutines.flow.first

/** The rules for lotteries that were published together. */
object LotteryGroups {

    /** A new ID for the campsites published together at [now], e.g. "grp-lq3k2f9a-7m". */
    fun newGroupId(now: Long, random: Random = Random.Default): String =
        "grp-${now.toString(36)}-${random.nextInt(36 * 36).toString(36).padStart(2, '0')}"

    /** The lotteries published together with [lottery], itself included; just itself if it stands alone. */
    fun members(lottery: Lottery, all: List<Lottery>): List<Lottery> =
        if (lottery.groupId.isBlank()) listOf(lottery)
        else (all.filter { it.groupId == lottery.groupId && it.id != lottery.id } + lottery).sortedBy { it.campsiteName.lowercase() }

    /**
     * The spots [entrantId] won in [lottery]'s group and hasn't booked or given back yet.
     * More than one means they have to choose a site.
     */
    fun openWins(lottery: Lottery, all: List<Lottery>, mine: List<WaitlistEntry>, entrantId: String): List<Lottery> {
        val won = mine.filter { it.entrantId == entrantId && it.status == EntryStatus.WON && !it.isBooked }.map { it.lotteryId }.toSet()
        return members(lottery, all).filter { it.id in won }
    }

    /**
     * Joins [lottery]'s waiting list and those of the campsites published with it. The one the
     * entrant asked for must succeed; a sibling that is full or closed is skipped.
     */
    suspend fun joinAll(repo: LotteryRepository, lottery: Lottery, entrantId: String, entrantName: String, now: Long) {
        repo.joinWaitlist(lottery.id, entrantId, entrantName, now)
        others(repo, lottery).forEach { runCatching { repo.joinWaitlist(it.id, entrantId, entrantName, now) } }
    }

    /** Leaves [lottery]'s waiting list and those of the campsites published with it. */
    suspend fun leaveAll(repo: LotteryRepository, lottery: Lottery, entrantId: String, now: Long) {
        repo.leaveWaitlist(lottery.id, entrantId, now)
        others(repo, lottery).forEach { runCatching { repo.leaveWaitlist(it.id, entrantId, now) } }
    }

    /**
     * After [booked] is reserved, hands back the other sites [entrantId] won in the same group,
     * so they go to the next person on the waitlist.
     * @return how many spots were given back.
     */
    suspend fun giveBackOthers(repo: LotteryRepository, booked: Lottery, entrantId: String, now: Long): Int {
        if (booked.groupId.isBlank()) return 0
        val mine = repo.observeEntriesForEntrant(entrantId).first()
        val rest = openWins(booked, repo.observeLotteries().first(), mine, entrantId).filter { it.id != booked.id }
        return rest.count { runCatching { repo.giveUpSpot(it.id, entrantId, now) }.isSuccess }
    }

    /** The other lotteries of [ownerId]'s own campsites in [lottery]'s group, which an edit changes too. */
    fun ownSiblings(lottery: Lottery, all: List<Lottery>, ownerId: String): List<Lottery> =
        members(lottery, all).filter { it.id != lottery.id && it.ownerId == ownerId }

    /** Whether [lottery]'s group includes campsites of other owners (US 02.04.08), which locks its shared dates. */
    fun sharedWithOthers(lottery: Lottery, all: List<Lottery>): Boolean =
        members(lottery, all).any { it.ownerId != lottery.ownerId }

    /** Whether [updated] moves any of [current]'s shared dates (waitlist opening or closing, or stays). */
    fun changesDates(current: Lottery, updated: Lottery): Boolean =
        current.registrationStart != updated.registrationStart || current.registrationEnd != updated.registrationEnd ||
            current.bookingStartDay != updated.bookingStartDay || current.bookingEndDay != updated.bookingEndDay

    /** [sibling] with the settings of [updated], an edit of another lottery in its group. */
    fun withEdit(sibling: Lottery, updated: Lottery): Lottery = sibling.copy(
        registrationStart = updated.registrationStart, registrationEnd = updated.registrationEnd,
        winnerCount = updated.winnerCount, waitlistCap = updated.waitlistCap,
        bookingStartDay = updated.bookingStartDay, bookingEndDay = updated.bookingEndDay,
    )

    /** Why an edit of a lottery shared with other owners can't move its dates. */
    const val SHARED_DATES_LOCKED = "This lottery is shared with other owners' campsites, so its dates can't change: they would change for those campsites too. You can still change the winners and the cap."

    /**
     * Saves [updated] (an edit of one lottery) to it and to every other lottery of [ownerId]'s own
     * campsites in its group, so the group keeps the same settings. If the group includes other
     * owners' campsites, its dates can't move ([SHARED_DATES_LOCKED]); the winners and cap still
     * change for this owner's campsites. Every lottery is checked before any is saved.
     *
     * @return the lotteries saved.
     * @throws LotteryEditException when the change isn't allowed for one of them.
     */
    suspend fun saveEdit(repo: LotteryRepository, current: Lottery, updated: Lottery, ownerId: String, now: Long): List<Lottery> {
        val all = if (current.groupId.isBlank()) listOf(current) else repo.observeLotteries().first()
        if (sharedWithOthers(current, all) && changesDates(current, updated)) throw LotteryEditException(SHARED_DATES_LOCKED)
        val edits = listOf(updated) + ownSiblings(current, all, ownerId).map { withEdit(it, updated) }
        for (e in edits) {
            val before = all.firstOrNull { it.id == e.id } ?: current
            LotteryEditPolicy.check(before, e, now)?.let { throw LotteryEditException("${e.campsiteName}: $it") }
            StayPeriods.clash(e, all)?.let { throw LotteryEditException(StayPeriods.message(e.campsiteName, it)) }
        }
        edits.forEach { repo.updateLottery(it, now) }
        return edits
    }

    private suspend fun others(repo: LotteryRepository, lottery: Lottery): List<Lottery> =
        if (lottery.groupId.isBlank()) emptyList() else members(lottery, repo.observeLotteries().first()).filter { it.id != lottery.id }
}
