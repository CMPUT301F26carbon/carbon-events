/*
 * SocialData.kt (Tausif)
 *
 * Models and the repository for everything people send each other: ratings, follows
 * ("notify me when a lottery opens"), guest-owner chats, owner announcements to a waiting list,
 * reports to admins, admin warnings and the admin logs. Firestore and in-memory versions live in
 * Data.kt next to the other stores.
 *
 * Outstanding issues: messages and announcements only pop up while the app is open (no push server).
 */
package com.carbon.events

import kotlinx.coroutines.flow.Flow

/**
 * A guest's rating of a campsite they booked.
 *
 * @property userId who rated (also the document ID, so one rating per person).
 * @property stars 1 to 5.
 * @property comment optional short review.
 * @property lotteryId the lottery they booked through, which proves they stayed.
 */
data class Rating(
    val campsiteId: String,
    val userId: String,
    val userName: String,
    val stars: Int,
    val comment: String,
    val lotteryId: String,
    val createdAt: Long,
)

/** Rating rules. */
object RatingPolicy {
    /** Longest review comment. */
    const val MAX_COMMENT = 280

    /**
     * New average and count after [stars] replaces [previous] (or is added when null).
     * @return the rounded-to-0.1 average and the count.
     */
    fun aggregate(average: Double, count: Int, stars: Int, previous: Int?): Pair<Double, Int> {
        require(stars in 1..5) { "stars must be 1-5" }
        val sum = average * count - (previous ?: 0) + stars
        val n = if (previous == null) count + 1 else count
        return (Math.round(sum / n * 10) / 10.0) to n
    }

    /** Whether [entry] lets its entrant rate the campsite: they won and booked nights. */
    fun canRate(entry: WaitlistEntry): Boolean = entry.status == EntryStatus.WON && entry.checkInDay != null
}

/**
 * A conversation between a guest and a campsite's owner (one per guest per campsite).
 *
 * @property id "{campsiteId}--{entrantId}".
 * @property unreadForOwner whether the owner hasn't seen the last message.
 * @property unreadForEntrant whether the guest hasn't seen the last message.
 */
data class Chat(
    val id: String,
    val campsiteId: String,
    val campsiteName: String,
    val ownerId: String,
    val ownerName: String,
    val entrantId: String,
    val entrantName: String,
    val lastText: String = "",
    val lastAt: Long = 0L,
    val lastSenderId: String = "",
    val unreadForOwner: Boolean = false,
    val unreadForEntrant: Boolean = false,
    /** The lottery (its key) the entrant was selected in; a chat can only start after selection. */
    val lotteryKey: String = "",
) {
    /** Whether [userId] has an unread message here. */
    fun unreadFor(userId: String): Boolean = if (userId == ownerId) unreadForOwner else if (userId == entrantId) unreadForEntrant else false

    /** The other person's name, as seen by [userId]. */
    fun otherName(userId: String): String = if (userId == ownerId) entrantName else ownerName

    companion object {
        /** The chat ID for a guest at a campsite. */
        fun idFor(campsiteId: String, entrantId: String) = "$campsiteId--$entrantId"
    }
}

/** One message in a [Chat]. */
data class ChatMessage(val id: String, val chatId: String, val senderId: String, val senderName: String, val text: String, val sentAt: Long)

/** Who an owner's announcement goes to. */
enum class Audience(val label: String) {
    ALL("Everyone"),
    WINNERS("Winners"),
    NOT_SELECTED("Not selected"),
    WAITING("Waiting for the draw");

    /** Whether [entry] is in this audience. */
    fun includes(entry: WaitlistEntry): Boolean = when (this) {
        ALL -> true
        WINNERS -> entry.status == EntryStatus.WON
        NOT_SELECTED -> entry.status == EntryStatus.LOST
        WAITING -> entry.status == EntryStatus.WAITING
    }
}

/** A message an owner sent to (part of) a lottery's waiting list, kept as the sent log. */
data class Announcement(
    val id: String,
    val lotteryId: String,
    val campsiteId: String,
    val campsiteName: String,
    val ownerId: String,
    val text: String,
    val audience: Audience,
    val recipientCount: Int,
    val sentAt: Long,
)

/** A campsite flagged to the admins. */
data class Report(
    val id: String,
    val campsiteId: String,
    val campsiteName: String,
    val reporterId: String,
    val reporterName: String,
    val reason: String,
    val createdAt: Long,
)

/** Reasons offered when reporting a campsite. */
val REPORT_REASONS = listOf("Misleading listing", "Inappropriate photo", "Unsafe or closed", "Owner behaviour", "Spam or scam", "Other")

/**
 * Observable social data and moderation commands. Firestore implementations enforce access using
 * server rules; the in-memory implementation is a test double, not an authorization boundary.
 * Observer subscriptions must be cancelled when their owning screen or controller is destroyed.
 */
interface SocialRepository {
    /** Emits a campsite's ratings, newest first. */
    fun observeRatings(campsiteId: String): Flow<List<Rating>>

    /** Saves (or replaces) [rating] and updates the campsite's average. */
    suspend fun rate(rating: Rating)

    /** Emits whether [userId] follows [campsiteId]. */
    fun observeFollowing(campsiteId: String, userId: String): Flow<Boolean>

    /** Starts or stops following a campsite ("notify me when a lottery opens"). */
    suspend fun setFollowing(campsiteId: String, userId: String, userName: String, follow: Boolean, now: Long)

    /**
     * Tells every follower of [lottery]'s campsite that it opened (owner, after creating it).
     * @return how many people were told.
     */
    suspend fun notifyFollowers(lottery: Lottery, now: Long): Int

    /** Emits the chats where [userId] is the owner or the guest, newest first. */
    fun observeChatsFor(userId: String): Flow<List<Chat>>

    /** Emits every chat (admin logs). */
    fun observeAllChats(): Flow<List<Chat>>

    /** Emits one chat, or null before its first message. */
    fun observeChat(chatId: String): Flow<Chat?>

    /** Emits a chat's messages, oldest first. */
    fun observeMessages(chatId: String): Flow<List<ChatMessage>>

    /** Sends [text] in [chat] (creating it with the first message). */
    suspend fun sendMessage(chat: Chat, senderId: String, senderName: String, text: String, now: Long)

    /** Marks [chatId] read for [userId]. */
    suspend fun markChatRead(chatId: String, userId: String)

    /**
     * Sends an owner's [text] to the [audience] of a lottery's waiting list (US 02.07.x notify)
     * and logs it.
     * @return how many people it went to.
     */
    suspend fun sendAnnouncement(lottery: Lottery, audience: Audience, text: String, now: Long): Int

    /** Emits a lottery's sent announcements, newest first. */
    fun observeAnnouncements(lotteryId: String): Flow<List<Announcement>>

    /** Emits every announcement [ownerId] has sent, across all their lotteries, newest first. */
    fun observeAnnouncementsByOwner(ownerId: String): Flow<List<Announcement>>

    /** Emits every announcement (admin logs). */
    fun observeAllAnnouncements(): Flow<List<Announcement>>

    /** Emits every notification sent to anyone (admin logs). */
    fun observeAllNotifications(): Flow<List<AppNotification>>

    /** Flags a campsite to the admins. */
    suspend fun report(report: Report)

    /** Emits every open report, newest first. */
    fun observeReports(): Flow<List<Report>>

    /** Clears the reports about [campsiteId] (admin dismissed them or removed the campsite). */
    suspend fun dismissReports(campsiteId: String)

    /** Sends [profile] an admin warning and counts it on their profile. */
    suspend fun warn(profile: UserProfile, text: String, now: Long)
}
