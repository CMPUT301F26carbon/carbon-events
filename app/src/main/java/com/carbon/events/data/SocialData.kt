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
 * @property unreadForOwner how many messages the owner hasn't read yet.
 * @property unreadForEntrant how many messages (including notices) the guest hasn't read yet.
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
    val unreadForOwner: Int = 0,
    val unreadForEntrant: Int = 0,
    /** The lottery (its key) the entrant was selected in; a chat can only start after selection. */
    val lotteryKey: String = "",
) {
    /** Unread messages in this conversation for [userId]. */
    fun unreadCount(userId: String): Int = if (userId == ownerId) unreadForOwner else if (userId == entrantId) unreadForEntrant else 0

    /** Whether [userId] has an unread message here. */
    fun unreadFor(userId: String): Boolean = unreadCount(userId) > 0

    /** The other person's name, as seen by [userId]. */
    fun otherName(userId: String): String = if (userId == ownerId) entrantName else ownerName

    companion object {
        /** The chat ID for a guest at a campsite. */
        fun idFor(campsiteId: String, entrantId: String) = "$campsiteId--$entrantId"
    }
}

/** One message in a [Chat]. */
/** One message in a conversation. [announcement] marks an owner's notice sent with "Notify". */
data class ChatMessage(
    val id: String,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val sentAt: Long,
    val announcement: Boolean = false,
)

/** Who may type in a campsite conversation. */
object ChatRules {
    /**
     * Everyone who joined sees the owner's notices, but only a selected (won) entrant, and the
     * owner talking to that winner, can send messages. [entry] is the entrant's entry in the lottery.
     */
    fun canSend(chat: Chat, userId: String, entry: WaitlistEntry?): Boolean {
        val won = entry != null && entry.entrantId == chat.entrantId && entry.status == EntryStatus.WON
        return (userId == chat.entrantId || userId == chat.ownerId) && won
    }
}

/** One row in the bell's WhatsApp-style list: a conversation or an app update. */
data class InboxRow(
    val key: String,
    val title: String,
    val subtitle: String,
    val preview: String,
    val at: Long,
    val unread: Int,
    /** The conversation to open, or null for an app update. */
    val chatId: String?,
    val notification: AppNotification? = null,
)

/** The bell list for [userId]: every conversation and app update, newest first. */
fun buildInbox(userId: String, chats: List<Chat>, notifications: List<AppNotification>): List<InboxRow> {
    val chatRows = chats.filter { userId == it.ownerId || userId == it.entrantId }.map { c ->
        val asOwner = userId == c.ownerId
        InboxRow(
            key = "chat:${c.id}",
            title = if (asOwner) c.entrantName else "${c.campsiteName} – from ${c.ownerName}",
            subtitle = if (asOwner) c.campsiteName else "",
            preview = c.lastText, at = c.lastAt, unread = c.unreadCount(userId), chatId = c.id,
        )
    }
    val noteRows = notifications.map { n -> InboxRow("note:${n.id}", n.title, "", n.body, n.createdAt, if (n.read) 0 else 1, null, n) }
    return (chatRows + noteRows).sortedByDescending { it.at }
}

/** The red badge on the bell: hidden at 0, the count up to 9, then "9+". */
fun bellBadge(rows: List<InboxRow>): String? {
    val total = rows.sumOf { it.unread }
    return when { total <= 0 -> null; total > 9 -> "9+"; else -> total.toString() }
}

/**
 * The conversation to announce in the top banner, or null: one that gained unread messages from
 * someone else, other than the one open on screen. [before] is null on the first load, so old
 * unread messages don't pop up when the app starts.
 */
fun newMessageBanner(before: List<Chat>?, after: List<Chat>, userId: String, openChatId: String?): Chat? {
    if (before == null) return null
    val old = before.associateBy { it.id }
    return after
        .filter { it.id != openChatId && it.lastSenderId != userId && it.unreadCount(userId) > (old[it.id]?.unreadCount(userId) ?: 0) }
        .maxByOrNull { it.lastAt }
}

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
