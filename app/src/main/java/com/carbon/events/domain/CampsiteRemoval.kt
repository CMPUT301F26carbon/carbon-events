/*
 * CampsiteRemoval.kt (Suvir)
 *
 * Who is told when a campsite is removed, by its owner or by an admin (US 03.01.01): everyone it
 * still mattered to, before their booking or waitlist entry disappears with it.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

/** The notices sent when a campsite is removed. */
object CampsiteRemoval {

    /**
     * One notice for each person [site]'s removal affects, from its [entries] (every lottery's
     * waiting list) on [today] (a day number): people still waiting for a draw, winners who haven't
     * booked yet, and guests whose stay hasn't ended. Nobody whose stay is over, who wasn't
     * selected, or who already gave their spot back is told.
     */
    fun notices(site: Campsite, entries: List<WaitlistEntry>, today: Long, now: Long): List<AppNotification> =
        entries.mapNotNull { e ->
            val body = when {
                e.status == EntryStatus.WAITING ->
                    "Its lottery is cancelled, so you're no longer on its waitlist."
                e.status == EntryStatus.WON && e.isBooked && e.checkOutDay!! > today ->
                    "Your stay ${formatStay(e.checkInDay!!, e.checkOutDay).substringBefore(" ·")} is cancelled. Nothing is charged."
                e.status == EntryStatus.WON && !e.isBooked ->
                    "The spot you won there is cancelled, so there are no nights to book."
                else -> null
            } ?: return@mapNotNull null
            AppNotification(
                id = "", recipientId = e.entrantId, type = NotificationType.REMOVED,
                title = "${site.name} was removed", body = body,
                lotteryId = e.lotteryId, campsiteId = site.id, createdAt = now,
            )
        }.distinctBy { it.recipientId to it.lotteryId }

    /** How many people [notices] would tell, for the owner's "Delete campsite?" question. */
    fun affected(site: Campsite, entries: List<WaitlistEntry>, today: Long): Int =
        notices(site, entries, today, 0L).map { it.recipientId }.distinct().size
}
