/*
 * LotteryInvites.kt (Suvir)
 *
 * One lottery over campsites of several owners (US 02.04.08). An owner publishing a lottery can
 * invite other owners' campsites into it. Each invited owner accepts or declines; accepting adds
 * a lottery for their campsite to the same group (same periods, winners and cap), which they run
 * and draw themselves. Entrants then join once for every campsite in the group and, if they win
 * more than one, choose which to reserve, exactly as for one owner's campsites (LotteryGroups).
 *
 * Outstanding issues: entrants who joined the group before an owner accepted are not added to
 * that owner's campsite; they can join it from its page.
 */
package com.carbon.events

/** Where an invitation to a shared lottery stands. */
enum class InviteStatus {
    /** Sent; the invited owner hasn't answered yet. */
    PENDING,

    /** Accepted: the invited campsite's lottery is in the group (see [LotteryInvite.lotteryId]). */
    ACCEPTED,

    /** Turned down by the invited owner. */
    DECLINED,
}

/**
 * An invitation for another owner's campsite to join a shared lottery (US 02.04.08).
 *
 * @property id document ID.
 * @property groupId the group of lotteries the campsite would join ([Lottery.groupId]).
 * @property fromOwnerId the owner who published the lottery and sent the invitation.
 * @property fromOwnerName their name, for the invited owner's screen.
 * @property fromCampsiteId the inviting owner's campsite in the group (shown as "with …").
 * @property fromCampsiteName its name.
 * @property campsiteId the invited campsite.
 * @property campsiteName its name.
 * @property toOwnerId the invited campsite's owner, who answers.
 * @property registrationStart, registrationEnd, bookingStartDay, bookingEndDay, winnerCount,
 *   waitlistCap the lottery's settings, the same for every campsite in it.
 * @property status pending, accepted or declined.
 * @property lotteryId the invited campsite's lottery once accepted, else blank.
 * @property createdAt when it was sent (epoch ms).
 * @property answeredAt when the invited owner answered, or null.
 */
data class LotteryInvite(
    val id: String,
    val groupId: String,
    val fromOwnerId: String,
    val fromOwnerName: String,
    val fromCampsiteId: String,
    val fromCampsiteName: String,
    val campsiteId: String,
    val campsiteName: String,
    val toOwnerId: String,
    val registrationStart: Long,
    val registrationEnd: Long,
    val bookingStartDay: Long?,
    val bookingEndDay: Long?,
    val winnerCount: Int,
    val waitlistCap: Int?,
    val status: InviteStatus = InviteStatus.PENDING,
    val lotteryId: String = "",
    val createdAt: Long = 0L,
    val answeredAt: Long? = null,
)

/** Rules for inviting other owners' campsites into a shared lottery (US 02.04.08). */
object LotteryInvites {

    /**
     * The invitations to send when [published] (the inviting owner's own lottery in group
     * [groupId], on [fromCampsite]) also lists [invited] campsites of other owners. Campsites
     * of the inviting owner are skipped: they are added to the group directly.
     */
    fun forCampsites(published: Lottery, groupId: String, fromOwnerName: String, fromCampsite: Campsite, invited: List<Campsite>, now: Long): List<LotteryInvite> =
        invited.filter { it.ownerId != published.ownerId }.distinctBy { it.id }.map { c ->
            LotteryInvite(
                id = "", groupId = groupId, fromOwnerId = published.ownerId, fromOwnerName = fromOwnerName,
                fromCampsiteId = fromCampsite.id, fromCampsiteName = fromCampsite.name,
                campsiteId = c.id, campsiteName = c.name, toOwnerId = c.ownerId,
                registrationStart = published.registrationStart, registrationEnd = published.registrationEnd,
                bookingStartDay = published.bookingStartDay, bookingEndDay = published.bookingEndDay,
                winnerCount = published.winnerCount, waitlistCap = published.waitlistCap, createdAt = now,
            )
        }

    /**
     * Whether [ownerId] may accept [invite] at [now]: it is theirs, still pending, for a campsite
     * they still own ([campsite], null if it was removed), and its waitlist hasn't closed.
     *
     * @return null if they may, otherwise why not.
     */
    fun checkAccept(invite: LotteryInvite, ownerId: String, campsite: Campsite?, now: Long): String? = when {
        invite.toOwnerId != ownerId -> "This invitation is for another owner."
        invite.status != InviteStatus.PENDING -> "This invitation has already been answered."
        campsite == null || campsite.ownerId != ownerId -> "That campsite is no longer yours."
        now >= invite.registrationEnd -> "This lottery's waitlist has already closed."
        else -> null
    }

    /** The lottery accepting [invite] adds for the invited campsite: the shared settings, run by its own owner. */
    fun lotteryFor(invite: LotteryInvite): Lottery = Lottery(
        id = "", campsiteId = invite.campsiteId, campsiteName = invite.campsiteName, ownerId = invite.toOwnerId,
        registrationStart = invite.registrationStart, registrationEnd = invite.registrationEnd, winnerCount = invite.winnerCount,
        waitlistCap = invite.waitlistCap, bookingStartDay = invite.bookingStartDay, bookingEndDay = invite.bookingEndDay,
        groupId = invite.groupId,
    )

    /** Campsites of other owners whose name, place or owner matches [query], best first, at most [limit]. */
    fun searchOthers(all: List<Campsite>, ownerId: String, query: String, limit: Int = 6): List<Campsite> {
        if (query.isBlank()) return emptyList()
        val others = all.filter { it.ownerId != ownerId }
        val byOwner = others.filter { site -> query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }.all { it in site.ownerName.lowercase() } }
        return (CampsiteSearch.search(others, query) + byOwner).distinctBy { it.id }.take(limit)
    }

    /** The notice the invited owner gets. */
    fun invitedNotice(invite: LotteryInvite, now: Long) = AppNotification(
        id = "", recipientId = invite.toOwnerId, type = NotificationType.MESSAGE,
        title = "${invite.fromOwnerName.ifBlank { "An owner" }} invited ${invite.campsiteName} to a shared lottery",
        body = "With ${invite.fromCampsiteName}, closing ${formatDay(invite.registrationEnd)}. Open Lotteries to accept or decline.",
        lotteryId = "", campsiteId = invite.fromCampsiteId, createdAt = now,
    )

    /** The notice the inviting owner gets when [invite] is answered. */
    fun answeredNotice(invite: LotteryInvite, accepted: Boolean, now: Long) = AppNotification(
        id = "", recipientId = invite.fromOwnerId, type = NotificationType.MESSAGE,
        title = if (accepted) "${invite.campsiteName} joined your lottery" else "${invite.campsiteName} declined your lottery",
        body = if (accepted) "Entrants of your shared lottery with ${invite.fromCampsiteName} can now win a stay there too."
        else "Your lottery with ${invite.fromCampsiteName} goes ahead without it.",
        lotteryId = invite.lotteryId, campsiteId = invite.campsiteId, createdAt = now,
    )
}
