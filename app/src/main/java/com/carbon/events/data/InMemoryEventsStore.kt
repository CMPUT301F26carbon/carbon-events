/*
 * InMemoryEventsStore.kt (Vaibhav)
 *
 * The in-memory version of every repository, for tests and the offline build (nothing is
 * saved when the app closes).
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Everything the in-memory store holds, as one immutable snapshot.
 */
data class EventsSnapshot(
    val profiles: Map<String, UserProfile> = emptyMap(),
    val devices: Map<String, TrustedDevice> = emptyMap(),
    val campsites: Map<String, Campsite> = emptyMap(),
    val lotteries: Map<String, Lottery> = emptyMap(),
    val entries: List<WaitlistEntry> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val ratings: List<Rating> = emptyList(),
    val follows: Set<Pair<String, String>> = emptySet(),
    val chats: Map<String, Chat> = emptyMap(),
    val messages: List<ChatMessage> = emptyList(),
    val announcements: List<Announcement> = emptyList(),
    val reports: List<Report> = emptyList(),
)

/**
 * In-memory implementation of the profile, campsite, lottery, notification and social repositories.
 * StateFlow snapshots provide observable updates for previews and deterministic tests. No state
 * survives process termination. Firebase permission checks are not simulated by this store.
 *
 * @param initial starting data (tests pass realistic fixtures; the app starts empty).
 */
class InMemoryEventsStore(initial: EventsSnapshot = EventsSnapshot()) :
    ProfileRepository, CampsiteRepository, LotteryRepository, NotificationRepository, SocialRepository {

    private val state = MutableStateFlow(initial)
    private val ids = AtomicLong(1000)

    /** The current contents, for test assertions. */
    val snapshot: EventsSnapshot get() = state.value

    private fun newId(prefix: String) = "$prefix${ids.incrementAndGet()}"

    private fun <T> select(block: (EventsSnapshot) -> T): Flow<T> = state.map(block).distinctUntilChanged()

    // ---- Profiles ----

    override fun observeUserIdForDevice(deviceId: String): Flow<String?> = select { it.devices[deviceId]?.userId }

    override fun observeProfile(userId: String): Flow<UserProfile?> = select { it.profiles[userId] }

    override fun observeProfileForAccount(authUid: String): Flow<UserProfile?> = select { s ->
        // Test fixtures often skip accounts: a profile with no authUid is found by its own ID.
        s.profiles.values.firstOrNull { it.authUid == authUid } ?: s.profiles[authUid]?.takeIf { it.authUid.isEmpty() }
    }

    override suspend fun createProfile(profile: UserProfile, trust: TrustedDevice?): String {
        val userId = Ids.readable(profile.name)
        state.update {
            it.copy(
                profiles = it.profiles + (userId to profile.copy(id = userId)),
                devices = if (trust == null) it.devices else it.devices + (trust.deviceId to trust.copy(userId = userId)),
            )
        }
        return userId
    }

    override suspend fun trustDevice(device: TrustedDevice) {
        // As on the server: a phone of someone an admin removed can't be trusted again.
        check(state.value.devices[device.deviceId]?.blocked != true) { "This phone can't be trusted." }
        state.update { it.copy(devices = it.devices + (device.deviceId to device.copy(blocked = false))) }
    }

    override suspend fun saveProfile(profile: UserProfile) {
        state.update { it.copy(profiles = it.profiles + (profile.id to profile)) }
    }

    /** Trusts [deviceId] for [userId] directly (test setup; production uses trustDevice after authentication). */
    fun linkDevice(deviceId: String, userId: String, model: String = "Test phone", addedAt: Long = 0L) {
        state.update { it.copy(devices = it.devices + (deviceId to TrustedDevice(deviceId, userId, model, addedAt))) }
    }

    override fun observeDevices(userId: String): Flow<List<TrustedDevice>> =
        select { s -> s.devices.values.filter { it.userId == userId }.sortedBy { it.addedAt } }

    override suspend fun removeDevice(deviceId: String) {
        state.update { it.copy(devices = it.devices - deviceId) }
    }

    override suspend fun removeAllDevices(userId: String) {
        state.update { s -> s.copy(devices = s.devices.filterValues { it.userId != userId }) }
    }

    override fun observeProfiles(): Flow<List<UserProfile>> = select { s -> s.profiles.values.sortedBy { it.name.lowercase() } }

    override suspend fun removeProfile(profile: UserProfile, now: Long) {
        leaveEverything(profile, now)
        state.update { s ->
            s.copy(
                profiles = s.profiles + (profile.id to profile.removedRecord()),
                devices = s.devices.mapValues { (_, d) -> if (d.userId == profile.id) d.copy(blocked = true) else d },
                notifications = s.notifications.filterNot { it.recipientId == profile.id },
            )
        }
    }

    override suspend fun restoreProfile(profile: UserProfile) {
        state.update { s ->
            s.copy(
                profiles = s.profiles + (profile.id to profile.copy(blocked = false, removed = false)),
                devices = s.devices.mapValues { (_, d) -> if (d.userId == profile.id) d.copy(blocked = false) else d },
            )
        }
    }

    override suspend fun isPhoneBlocked(deviceId: String): Boolean = state.value.devices[deviceId]?.blocked == true

    private suspend fun leaveEverything(profile: UserProfile, now: Long) {
        state.value.entries.filter { it.entrantId == profile.id }.forEach { entry ->
            runCatching {
                when {
                    entry.status == EntryStatus.WAITING -> leaveWaitlist(entry.lotteryId, profile.id, now)
                    entry.givesSpotBackOnDelete(now) -> giveUpSpot(entry.lotteryId, profile.id, now)
                }
            }
        }
        state.value.campsites.values.filter { it.ownerId == profile.id }.forEach { deleteCampsite(it.id) }
    }

    override suspend fun deleteProfile(profile: UserProfile, now: Long) {
        leaveEverything(profile, now)
        state.update { s ->
            s.copy(
                profiles = s.profiles - profile.id,
                devices = s.devices.filterValues { it.userId != profile.id },
                notifications = s.notifications.filterNot { it.recipientId == profile.id },
            )
        }
    }

    // ---- Campsites ----

    override fun observeCampsites(): Flow<List<Campsite>> = select { s -> s.campsites.values.sortedBy { it.name } }

    override fun observeCampsite(id: String): Flow<Campsite?> = select { it.campsites[id] }

    override fun observeCampsitesByOwner(ownerId: String): Flow<List<Campsite>> =
        select { s -> s.campsites.values.filter { it.ownerId == ownerId }.sortedByDescending { it.createdAt } }

    override suspend fun createCampsite(campsite: Campsite): String {
        val id = newId("site")
        state.update { it.copy(campsites = it.campsites + (id to campsite.copy(id = id))) }
        return id
    }

    override suspend fun updateCampsite(campsite: Campsite) {
        state.update { s ->
            require(campsite.id in s.campsites) { "No campsite ${campsite.id}" }
            s.copy(campsites = s.campsites + (campsite.id to campsite))
        }
    }

    override suspend fun deleteCampsite(id: String) {
        state.update { s ->
            val lotteryIds = s.lotteries.values.filter { it.campsiteId == id }.map { it.id }.toSet()
            s.copy(
                campsites = s.campsites - id,
                lotteries = s.lotteries - lotteryIds,
                entries = s.entries.filterNot { it.lotteryId in lotteryIds },
            )
        }
    }

    // ---- Lotteries ----

    override fun observeLotteries(): Flow<List<Lottery>> = select { it.lotteries.values.toList() }

    override fun observeOpenLotteries(): Flow<List<Lottery>> =
        select { s -> s.lotteries.values.filter { it.status == LotteryStatus.OPEN } }

    override fun observeLottery(id: String): Flow<Lottery?> = select { it.lotteries[id] }

    override fun observeLotteriesForCampsite(campsiteId: String): Flow<List<Lottery>> =
        select { s -> s.lotteries.values.filter { it.campsiteId == campsiteId }.sortedByDescending { it.registrationStart } }

    override fun observeLotteriesByOwner(ownerId: String): Flow<List<Lottery>> =
        select { s -> s.lotteries.values.filter { it.ownerId == ownerId }.sortedByDescending { it.registrationStart } }

    override suspend fun createLottery(lottery: Lottery): String {
        val id = newId("lot")
        state.update {
            StayPeriods.requireApart(lottery, it.lotteries.values.toList())
            it.copy(lotteries = it.lotteries + (id to lottery.copy(id = id, entrantCount = 0)))
        }
        return id
    }

    override fun observeEntries(lotteryId: String): Flow<List<WaitlistEntry>> =
        select { s -> s.entries.filter { it.lotteryId == lotteryId }.sortedBy { it.joinedAt } }

    override fun observeEntriesForEntrant(entrantId: String): Flow<List<WaitlistEntry>> =
        select { s -> s.entries.filter { it.entrantId == entrantId } }

    override suspend fun joinWaitlist(lotteryId: String, entrantId: String, entrantName: String, now: Long) {
        state.update { s ->
            val lottery = s.lotteries[lotteryId] ?: error("No lottery $lotteryId")
            val list = s.entries.filter { it.lotteryId == lotteryId }
            WaitlistPolicy.checkJoin(lottery, list.any { it.entrantId == entrantId }, list.size, now)
                ?.let { throw WaitlistException(it) }
            s.copy(
                entries = s.entries + WaitlistEntry(lotteryId, entrantId, entrantName, now),
                lotteries = s.lotteries + (lotteryId to lottery.copy(entrantCount = list.size + 1)),
            )
        }
    }

    override suspend fun leaveWaitlist(lotteryId: String, entrantId: String, now: Long) {
        state.update { s ->
            val lottery = s.lotteries[lotteryId] ?: error("No lottery $lotteryId")
            val list = s.entries.filter { it.lotteryId == lotteryId }
            WaitlistPolicy.checkLeave(lottery, list.any { it.entrantId == entrantId }, now)
                ?.let { throw WaitlistException(it) }
            s.copy(
                entries = s.entries.filterNot { it.lotteryId == lotteryId && it.entrantId == entrantId },
                lotteries = s.lotteries + (lotteryId to lottery.copy(entrantCount = list.size - 1)),
            )
        }
    }

    override suspend fun runDrawIfDue(lotteryId: String, now: Long, random: Random): Boolean {
        var drew = false
        state.update { s ->
            drew = false
            val lottery = s.lotteries[lotteryId] ?: return@update s
            if (!lottery.isDrawDue(now)) return@update s
            val list = s.entries.filter { it.lotteryId == lotteryId }
            val result = LotteryDrawer.draw(lottery, list, now, random)
            drew = true
            s.copy(
                lotteries = s.lotteries + (lotteryId to lottery.copy(status = LotteryStatus.DRAWN, drawnAt = now)),
                entries = s.entries.filterNot { it.lotteryId == lotteryId } + result.entries,
                notifications = s.notifications + result.notifications.map { it.copy(id = newId("note")) },
            )
        }
        return drew
    }

    override suspend fun updateLottery(updated: Lottery, now: Long) {
        state.update { s ->
            val current = s.lotteries[updated.id] ?: error("No lottery ${updated.id}")
            LotteryEditPolicy.check(current, updated, now)?.let { throw LotteryEditException(it) }
            StayPeriods.requireApart(current.copy(bookingStartDay = updated.bookingStartDay, bookingEndDay = updated.bookingEndDay), s.lotteries.values.toList())
            val merged = current.copy(
                registrationStart = updated.registrationStart, registrationEnd = updated.registrationEnd,
                winnerCount = updated.winnerCount, waitlistCap = updated.waitlistCap,
                bookingStartDay = updated.bookingStartDay, bookingEndDay = updated.bookingEndDay,
            )
            s.copy(lotteries = s.lotteries + (merged.id to merged))
        }
    }

    override suspend fun bookDates(
        lotteryId: String, entrantId: String, checkInDay: Long, checkOutDay: Long, now: Long,
        guests: Int, agreedToRules: Boolean,
    ) {
        state.update { s ->
            val lottery = s.lotteries[lotteryId] ?: error("No lottery $lotteryId")
            val winners = s.entries.filter { it.lotteryId == lotteryId && it.status == EntryStatus.WON }
            val mine = winners.firstOrNull { it.entrantId == entrantId }
            BookingPolicy.check(lottery, mine, checkInDay, checkOutDay, winners)?.let { throw BookingException(it) }
            s.copy(entries = s.entries.map {
                if (it.lotteryId == lotteryId && it.entrantId == entrantId) it.copy(
                    checkInDay = checkInDay, checkOutDay = checkOutDay, bookedAt = now,
                    campsiteId = lottery.campsiteId, campsiteName = lottery.campsiteName, ownerId = lottery.ownerId,
                    guests = guests, agreedToRules = agreedToRules,
                ) else it
            })
        }
    }

    private fun MutableList<WaitlistEntry>.winner(lotteryId: String, entrantId: String): Int {
        val i = indexOfFirst { it.lotteryId == lotteryId && it.entrantId == entrantId }
        if (i < 0 || this[i].status != EntryStatus.WON) throw BookingException(BookingError.NOT_A_WINNER)
        return i
    }

    override suspend fun giveUpSpot(lotteryId: String, entrantId: String, now: Long) {
        state.update { s ->
            val lottery = s.lotteries[lotteryId] ?: error("No lottery $lotteryId")
            val entries = s.entries.toMutableList()
            val i = entries.winner(lotteryId, entrantId)
            val booked = entries[i]
            entries[i] = booked.copy(status = if (booked.isBooked) EntryStatus.CANCELLED else EntryStatus.DECLINED, endedAt = now)
            val notice = if (!booked.isBooked) emptyList()
                else listOf(LotteryDrawer.cancelledNotice(lottery, entrantId, booked.checkInDay!!, booked.checkOutDay!!, now, s.campsites[lottery.campsiteId]).copy(id = newId("note")))
            s.copy(entries = entries, lotteries = s.lotteries + (lotteryId to lottery.copy(spotsFree = lottery.spotsFree + 1)), notifications = s.notifications + notice)
        }
    }

    override suspend fun rejectReservation(lotteryId: String, entrantId: String, reason: String, now: Long) {
        state.update { s ->
            val lottery = s.lotteries[lotteryId] ?: error("No lottery $lotteryId")
            val entries = s.entries.toMutableList()
            val i = entries.winner(lotteryId, entrantId)
            entries[i] = entries[i].copy(status = EntryStatus.REJECTED, note = reason, endedAt = now)
            s.copy(
                entries = entries,
                lotteries = s.lotteries + (lotteryId to lottery.copy(spotsFree = lottery.spotsFree + 1)),
                notifications = s.notifications + LotteryDrawer.rejectedNotice(lottery, entrantId, reason, now).copy(id = newId("note")),
            )
        }
    }

    override suspend fun fillFreeSpots(lotteryId: String, now: Long, random: Random): Int {
        var filled = 0
        state.update { s ->
            filled = 0
            val lottery = s.lotteries[lotteryId] ?: return@update s
            if (lottery.status != LotteryStatus.DRAWN || lottery.spotsFree <= 0) return@update s
            val result = LotteryDrawer.fillSpots(lottery, s.entries.filter { it.lotteryId == lotteryId }, now, random)
            filled = result.entries.size
            val promoted = result.winnerIds
            s.copy(
                entries = s.entries.map { if (it.lotteryId == lotteryId && it.entrantId in promoted) it.copy(status = EntryStatus.WON, wonAt = now) else it },
                lotteries = s.lotteries + (lotteryId to lottery.copy(spotsFree = 0)),
                notifications = s.notifications + result.notifications.map { it.copy(id = newId("note")) },
            )
        }
        return filled
    }

    override suspend fun expireUnbookedWinners(lotteryId: String, now: Long): Int {
        var expired = 0
        state.update { s ->
            expired = 0
            val lottery = s.lotteries[lotteryId] ?: return@update s
            if (lottery.status != LotteryStatus.DRAWN) return@update s
            val result = LotteryDrawer.expireUnbooked(lottery, s.entries.filter { it.lotteryId == lotteryId }, now)
            expired = result.entries.size
            val byId = result.entries.associateBy { it.entrantId }
            s.copy(
                entries = s.entries.map { if (it.lotteryId == lotteryId) byId[it.entrantId] ?: it else it },
                lotteries = s.lotteries + (lotteryId to lottery.copy(spotsFree = lottery.spotsFree + expired)),
                notifications = s.notifications + result.notifications.map { it.copy(id = newId("note")) },
            )
        }
        return expired
    }

    override fun observeEndedReservationsForOwner(ownerId: String): Flow<List<Reservation>> = select { s ->
        s.entries.mapNotNull { it.toEndedReservation() }.filter { it.ownerId == ownerId }.sortedByDescending { it.endedAt ?: 0L }
    }

    private fun reservations(filter: (Reservation) -> Boolean): Flow<List<Reservation>> =
        select { s -> s.entries.mapNotNull { it.toReservation() }.filter(filter).sortedBy { it.checkInDay } }

    override fun observeReservationsForOwner(ownerId: String): Flow<List<Reservation>> = reservations { it.ownerId == ownerId }

    override fun observeReservationsForCampsite(campsiteId: String): Flow<List<Reservation>> = reservations { it.campsiteId == campsiteId }

    override fun observeReservationsForEntrant(entrantId: String): Flow<List<Reservation>> = reservations { it.entrantId == entrantId }


    // ---- Notifications ----

    override fun observeNotifications(recipientId: String): Flow<List<AppNotification>> =
        select { s -> s.notifications.filter { it.recipientId == recipientId }.sortedByDescending { it.createdAt } }

    override suspend fun markAllRead(recipientId: String) {
        state.update { s ->
            s.copy(notifications = s.notifications.map { if (it.recipientId == recipientId) it.copy(read = true) else it })
        }
    }

    // ---- Ratings, follows, chats, announcements, reports ----

    override fun observeRatings(campsiteId: String): Flow<List<Rating>> =
        select { s -> s.ratings.filter { it.campsiteId == campsiteId }.sortedByDescending { it.createdAt } }

    override suspend fun rate(rating: Rating) {
        state.update { s ->
            val site = s.campsites[rating.campsiteId] ?: error("No campsite ${rating.campsiteId}")
            val previous = s.ratings.firstOrNull { it.campsiteId == rating.campsiteId && it.userId == rating.userId }
            val (average, count) = RatingPolicy.aggregate(site.rating, site.ratingCount, rating.stars, previous?.stars)
            s.copy(
                ratings = s.ratings.filterNot { it === previous } + rating,
                campsites = s.campsites + (site.id to site.copy(rating = average, ratingCount = count)),
            )
        }
    }

    override fun observeFollowing(campsiteId: String, userId: String): Flow<Boolean> = select { (campsiteId to userId) in it.follows }

    override suspend fun setFollowing(campsiteId: String, userId: String, userName: String, follow: Boolean, now: Long) {
        state.update { s -> s.copy(follows = if (follow) s.follows + (campsiteId to userId) else s.follows - (campsiteId to userId)) }
    }

    override suspend fun notifyFollowers(lottery: Lottery, now: Long): Int {
        val followers = state.value.follows.filter { it.first == lottery.campsiteId }.map { it.second }
        state.update { s -> s.copy(notifications = s.notifications + followers.map { openedNotice(lottery, it, now).copy(id = newId("note")) }) }
        return followers.size
    }

    override fun observeChatsFor(userId: String): Flow<List<Chat>> =
        select { s -> s.chats.values.filter { it.ownerId == userId || it.entrantId == userId }.sortedByDescending { it.lastAt } }

    override fun observeAllChats(): Flow<List<Chat>> = select { s -> s.chats.values.sortedByDescending { it.lastAt } }

    override fun observeChat(chatId: String): Flow<Chat?> = select { it.chats[chatId] }

    override fun observeMessages(chatId: String): Flow<List<ChatMessage>> = select { s -> s.messages.filter { it.chatId == chatId }.sortedBy { it.sentAt } }

    override suspend fun sendMessage(chat: Chat, senderId: String, senderName: String, text: String, now: Long, photoBase64: String?) {
        val fromOwner = senderId == chat.ownerId
        val preview = ChatPhotos.preview(text, photoBase64 != null)
        state.update { s ->
            s.copy(
                chats = s.chats + (chat.id to (s.chats[chat.id] ?: chat).let { c ->
                    c.copy(
                        lastText = preview, lastAt = now, lastSenderId = senderId,
                        unreadForOwner = if (fromOwner) c.unreadForOwner else c.unreadForOwner + 1,
                        unreadForEntrant = if (fromOwner) c.unreadForEntrant + 1 else c.unreadForEntrant,
                    )
                }),
                messages = s.messages + ChatMessage(newId("msg"), chat.id, senderId, senderName, text, now, photoBase64 = photoBase64),
            )
        }
    }

    override suspend fun react(chatId: String, messageId: String, userId: String, emoji: String?) {
        state.update { s ->
            s.copy(messages = s.messages.map { m ->
                if (m.chatId != chatId || m.id != messageId) m
                else m.copy(reactions = if (emoji == null) m.reactions - userId else m.reactions + (userId to emoji))
            })
        }
    }

    override suspend fun markChatRead(chatId: String, userId: String) {
        state.update { s ->
            val chat = s.chats[chatId] ?: return@update s
            s.copy(chats = s.chats + (chatId to if (userId == chat.ownerId) chat.copy(unreadForOwner = 0) else chat.copy(unreadForEntrant = 0)))
        }
    }

    override suspend fun sendAnnouncement(lottery: Lottery, audience: Audience, text: String, now: Long): Int {
        val recipients = state.value.entries.filter { it.lotteryId == lottery.id && audience.includes(it) }
        val ownerName = state.value.campsites[lottery.campsiteId]?.ownerName.orEmpty()
        state.update { s ->
            // The notice goes into each recipient's conversation for this campsite, adding one unread.
            val chats = recipients.associate { e ->
                val id = Chat.idFor(lottery.campsiteId, e.entrantId)
                val c = s.chats[id] ?: Chat(id, lottery.campsiteId, lottery.campsiteName, lottery.ownerId, ownerName, e.entrantId, e.entrantName, lotteryKey = lottery.id)
                id to c.copy(lastText = text, lastAt = now, lastSenderId = lottery.ownerId, unreadForEntrant = c.unreadForEntrant + 1)
            }
            s.copy(
                chats = s.chats + chats,
                messages = s.messages + chats.keys.map { ChatMessage(newId("msg"), it, lottery.ownerId, ownerName, text, now, announcement = true) },
                announcements = s.announcements + Announcement(newId("ann"), lottery.id, lottery.campsiteId, lottery.campsiteName, lottery.ownerId, text, audience, recipients.size, now),
            )
        }
        return recipients.size
    }

    override fun observeAnnouncements(lotteryId: String): Flow<List<Announcement>> =
        select { s -> s.announcements.filter { it.lotteryId == lotteryId }.sortedByDescending { it.sentAt } }

    override fun observeAnnouncementsByOwner(ownerId: String): Flow<List<Announcement>> =
        select { s -> s.announcements.filter { it.ownerId == ownerId }.sortedByDescending { it.sentAt } }

    override fun observeAllAnnouncements(): Flow<List<Announcement>> = select { s -> s.announcements.sortedByDescending { it.sentAt } }

    override fun observeAllNotifications(): Flow<List<AppNotification>> = select { s -> s.notifications.sortedByDescending { it.createdAt } }

    override suspend fun report(report: Report) {
        state.update { s -> s.copy(reports = s.reports + report.copy(id = newId("rep"))) }
    }

    override fun observeReports(): Flow<List<Report>> = select { s -> s.reports.sortedByDescending { it.createdAt } }

    override suspend fun dismissReports(campsiteId: String) {
        state.update { s -> s.copy(reports = s.reports.filterNot { it.campsiteId == campsiteId }) }
    }

    override suspend fun warn(profile: UserProfile, text: String, now: Long) {
        state.update { s ->
            val current = s.profiles[profile.id] ?: profile
            s.copy(
                notifications = s.notifications + warningNotice(profile.id, text, now).copy(id = newId("note")),
                profiles = s.profiles + (profile.id to current.copy(warnings = current.warnings + 1)),
            )
        }
    }
}
