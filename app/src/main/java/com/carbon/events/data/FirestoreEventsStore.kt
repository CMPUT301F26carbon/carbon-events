/*
 * FirestoreEventsStore.kt (Vaibhav)
 *
 * The Firestore version of every repository: what the app reads and writes on the server.
 *
 * Outstanding issues: automatic draws run on the owner's phone while the app is
 * open, because there is no server; push notifications would need Cloud Functions.
 */
package com.carbon.events

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlin.random.Random
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** How long opening the app waits for the server to say whether this phone is trusted. */
private const val SERVER_CHECK_MS = 5_000L

/**
 * Firestore implementation of all four repositories. Lookups by ID are direct document reads;
 * lists across campsites use indexed collection-group queries (see docs/firestore.indexes.json),
 * so each costs the size of its result, not the size of the database.
 *
 * @param db the Firestore instance.
 */
class FirestoreEventsStore(private val db: FirebaseFirestore) :
    ProfileRepository, CampsiteRepository, LotteryRepository, NotificationRepository, SocialRepository {

    private val accounts get() = db.collection(Paths.ACCOUNTS)
    private val devices get() = db.collection(Paths.DEVICES)
    private val users get() = db.collection(Paths.USERS)
    private val campsites get() = db.collection(Paths.CAMPSITES)
    private val allLotteries get() = db.collectionGroup(Paths.LOTTERIES)
    private val allEntrants get() = db.collectionGroup(Paths.ENTRANTS)

    private fun lotteriesOf(campsiteId: String) = campsites.document(campsiteId).collection(Paths.LOTTERIES)
    private fun lotteryRef(lotteryId: String) = lotteriesOf(Paths.campsiteOf(lotteryId)).document(Paths.keyOf(lotteryId))
    private fun entrantsOf(lotteryId: String) = lotteryRef(lotteryId).collection(Paths.ENTRANTS)
    private fun notificationsOf(userId: String) = users.document(userId).collection(Paths.NOTIFICATIONS)

    /**
     * Listens to a query and maps each snapshot. If the server refuses or fails (e.g. a rule or
     * a missing index), the error is logged and the flow emits an empty result before ending, so
     * screens that combine several streams still draw instead of waiting forever.
     */
    private fun <T> Query.listen(map: (List<DocumentSnapshot>) -> T): Flow<T> = callbackFlow {
        val reg = addSnapshotListener { snap, err ->
            if (err != null) { logListenError(err); trySend(map(emptyList())); close() } else if (snap != null) trySend(map(snap.documents))
        }
        awaitClose { reg.remove() }
    }

    /** Listens to one document. Emits null while it does not exist, or if reading it is refused. */
    private fun <T> DocumentReference.listen(map: (DocumentSnapshot) -> T): Flow<T?> = callbackFlow {
        val reg = addSnapshotListener { snap, err ->
            if (err != null) { logListenError(err); trySend(null); close() } else trySend(snap?.takeIf { it.exists() }?.let(map))
        }
        awaitClose { reg.remove() }
    }

    private fun logListenError(err: Exception) {
        runCatching { android.util.Log.w("Events", "Firestore listen failed: ${err.message}") }
    }

    // ---- Profiles and trusted phones ----

    override fun observeUserIdForDevice(deviceId: String): Flow<String?> =
        devices.document(deviceId).listen { it.getString("userId") }

    override fun observeProfile(userId: String): Flow<UserProfile?> = users.document(userId).listen { it.toProfile() }

    /** Like [listen], but a read that fails ends the flow with [ProfileLoadException] instead of looking empty. */
    private fun <T> DocumentReference.listenOrFail(map: (DocumentSnapshot) -> T): Flow<T?> = callbackFlow {
        val reg = addSnapshotListener { snap, err ->
            if (err != null) { logListenError(err); close(ProfileLoadException(err)) } else trySend(snap?.takeIf { it.exists() }?.let(map))
        }
        awaitClose { reg.remove() }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeProfileForAccount(authUid: String): Flow<UserProfile?> =
        // accounts/{authUid} holds the user ID, so signing in is two direct reads, no search.
        savedLink(authUid).flatMapLatest { userId ->
            if (userId == null) kotlinx.coroutines.flow.flowOf(null) else users.document(userId).listenOrFail { it.toProfile() }
        }

    /**
     * The user ID linked to login [authUid], once the server has saved it. While sign-up is still
     * being saved the app is already watching, and the rules refuse to read a profile the server
     * can't yet tie to this login; reading it too early ended the watch, so a finished sign-up
     * looked signed out and went back to Welcome.
     */
    private fun savedLink(authUid: String): Flow<String?> = callbackFlow {
        val reg = accounts.document(authUid).addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
            if (err != null) { logListenError(err); close(ProfileLoadException(err)) }
            else if (snap != null && !snap.metadata.hasPendingWrites()) trySend(snap.takeIf { it.exists() }?.getString("userId"))
        }
        awaitClose { reg.remove() }
    }.distinctUntilChanged()

    override suspend fun createProfile(profile: UserProfile, trust: TrustedDevice?): String {
        val account = accounts.document(profile.authUid)
        // A login can be linked to a profile only once. If this login still has a link from an
        // earlier sign-up, keep that profile if it's still there; otherwise clear the old link.
        val linked = account.get().await().getString("userId")
        if (linked != null) {
            if (users.document(linked).get().await().exists()) {
                if (trust != null) trustDevice(trust.copy(userId = linked))
                return linked
            }
            account.delete().await()
        }
        val userId = Ids.readable(profile.name)
        // One batch: the rules only trust a phone for a person whose account is the caller's.
        // Setting the phone's record replaces whoever had this phone before.
        val batch = db.batch().set(users.document(userId), profile.copy(id = userId).toMap())
            .set(account, mapOf("userId" to userId))
        if (trust != null) batch.set(devices.document(trust.deviceId), trust.copy(userId = userId).toMap())
        batch.commit().await()
        return userId
    }

    override suspend fun trustDevice(device: TrustedDevice) {
        devices.document(device.deviceId).set(device.toMap()).await()
    }

    override suspend fun saveProfile(profile: UserProfile) {
        users.document(profile.id).set(profile.toMap()).await()
    }

    override suspend fun deleteProfile(profile: UserProfile, now: Long) {
        leaveEverything(profile, now)
        val batch = db.batch()
        devices.whereEqualTo("userId", profile.id).get().await().documents.forEach { batch.delete(it.reference) }
        notificationsOf(profile.id).get().await().documents.forEach { batch.delete(it.reference) }
        if (profile.authUid.isNotBlank()) batch.delete(accounts.document(profile.authUid))
        batch.delete(users.document(profile.id))
        batch.commit().await()
    }

    override suspend fun removeProfile(profile: UserProfile, now: Long) {
        leaveEverything(profile, now)
        val batch = db.batch()
        notificationsOf(profile.id).get().await().documents.forEach { batch.delete(it.reference) }
        // The login keeps pointing at this record, so signing in again lands on "account removed".
        batch.set(users.document(profile.id), profile.removedRecord().toMap())
        devices.whereEqualTo("userId", profile.id).get().await().documents.forEach { batch.update(it.reference, "blocked", true) }
        batch.commit().await()
    }

    override suspend fun restoreProfile(profile: UserProfile) {
        val batch = db.batch()
        batch.set(users.document(profile.id), profile.copy(blocked = false, removed = false).toMap())
        devices.whereEqualTo("userId", profile.id).get().await().documents.forEach { batch.update(it.reference, "blocked", false) }
        batch.commit().await()
    }

    override suspend fun isPhoneBlocked(deviceId: String): Boolean =
        devices.document(deviceId).get().await().getBoolean("blocked") == true

    /**
     * While the caller can still act for this person: leave open waiting lists, hand back spots
     * won but not stayed in, and delete their campsites. Each step is tried on its own, so one
     * list that has already closed doesn't stop the rest.
     */
    private suspend fun leaveEverything(profile: UserProfile, now: Long) {
        for (doc in allEntrants.whereEqualTo("userId", profile.id).get().await().documents) {
            val entry = doc.toEntry()
            runCatching {
                when {
                    entry.status == EntryStatus.WAITING -> leaveWaitlist(entry.lotteryId, profile.id, now)
                    entry.givesSpotBackOnDelete(now) -> giveUpSpot(entry.lotteryId, profile.id, now)
                }
            }
        }
        for (site in campsites.whereEqualTo("ownerId", profile.id).get().await().documents) deleteCampsite(site.id)
    }

    override fun observeProfiles(): Flow<List<UserProfile>> =
        users.listen { docs -> docs.map { it.toProfile() }.sortedBy { it.name.lowercase() } }

    override fun observeDevices(userId: String): Flow<List<TrustedDevice>> =
        devices.whereEqualTo("userId", userId).listen { docs -> docs.map { it.toDevice() }.sortedBy { it.addedAt } }

    override suspend fun removeDevice(deviceId: String) {
        devices.document(deviceId).delete().await()
    }

    override suspend fun removeAllDevices(userId: String) {
        val batch = db.batch()
        devices.whereEqualTo("userId", userId).get().await().documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }

    override suspend fun trustedUserIdNow(deviceId: String): String? {
        val ref = devices.document(deviceId)
        // The server's answer; the saved copy only when the server can't be reached.
        val snap = withTimeoutOrNull(SERVER_CHECK_MS) { runCatching { ref.get(Source.SERVER).await() }.getOrNull() }
            ?: runCatching { ref.get(Source.CACHE).await() }.getOrNull()
        return snap?.takeIf { it.exists() }?.getString("userId")
    }

    override suspend fun signOutEverywhere(userId: String, now: Long) {
        users.document(userId).update("signedOutAt", now.toTimestamp()).await()
    }

    // ---- Campsites ----

    override fun observeCampsites(): Flow<List<Campsite>> =
        campsites.orderBy("nameLower").listen { docs -> docs.map { it.toCampsite() } }

    override fun observeCampsite(id: String): Flow<Campsite?> = campsites.document(id).listen { it.toCampsite() }

    override fun observeCampsitesByOwner(ownerId: String): Flow<List<Campsite>> =
        campsites.whereEqualTo("ownerId", ownerId).listen { docs -> docs.map { it.toCampsite() }.sortedByDescending { it.createdAt } }

    override suspend fun createCampsite(campsite: Campsite): String {
        val id = Ids.readable(campsite.name)
        campsites.document(id).set(campsite.copy(id = id).toMap()).await()
        return id
    }

    override suspend fun updateCampsite(campsite: Campsite) {
        // Ratings belong to the guests (RatingPolicy) and the creation time never changes, so an
        // edit leaves them as they are in the database.
        val fields = campsite.toMap() - setOf("rating", "ratingCount", "createdAt")
        campsites.document(campsite.id).set(fields, com.google.firebase.firestore.SetOptions.merge()).await()
    }

    override suspend fun deleteCampsite(id: String) {
        // Firestore doesn't delete sub-collections with their parent, so remove them first.
        val batch = db.batch()
        val site = campsites.document(id)
        for (lot in lotteriesOf(id).get().await().documents) {
            lot.reference.collection(Paths.ENTRANTS).get().await().documents.forEach { batch.delete(it.reference) }
            lot.reference.collection(Paths.ANNOUNCEMENTS).get().await().documents.forEach { batch.delete(it.reference) }
            batch.delete(lot.reference)
        }
        site.collection(Paths.RATINGS).get().await().documents.forEach { batch.delete(it.reference) }
        site.collection(Paths.FOLLOWERS).get().await().documents.forEach { batch.delete(it.reference) }
        batch.delete(site)
        batch.commit().await()
    }

    // ---- Lotteries ----

    override fun observeLotteries(): Flow<List<Lottery>> = allLotteries.listen { docs -> docs.map { it.toLottery() } }

    override fun observeOpenLotteries(): Flow<List<Lottery>> =
        allLotteries.whereEqualTo("status", LotteryStatus.OPEN.word()).listen { docs -> docs.map { it.toLottery() } }

    override fun observeLottery(id: String): Flow<Lottery?> = lotteryRef(id).listen { it.toLottery() }

    override fun observeLotteriesForCampsite(campsiteId: String): Flow<List<Lottery>> =
        lotteriesOf(campsiteId).listen { docs -> docs.map { it.toLottery() }.sortedByDescending { it.registrationStart } }

    override fun observeLotteriesByOwner(ownerId: String): Flow<List<Lottery>> =
        allLotteries.whereEqualTo("ownerId", ownerId)
            .listen { docs -> docs.map { it.toLottery() }.sortedByDescending { it.registrationStart } }

    override suspend fun createLottery(lottery: Lottery): String {
        val parent = lotteriesOf(lottery.campsiteId)
        StayPeriods.requireApart(lottery, parent.get().await().documents.map { it.toLottery() })
        return db.runTransaction { t ->
            // "nov-2026", or "nov-2026-2" if that campsite already has a November lottery.
            val key = generateSequence(1) { it + 1 }.take(50)
                .map { Ids.lotteryKey(lottery.bookingStartDay, lottery.registrationEnd, it) }
                .first { !t.get(parent.document(it)).exists() }
            val id = Paths.lotteryId(lottery.campsiteId, key)
            t.set(parent.document(key), lottery.copy(id = id, entrantCount = 0).toMap())
            id
        }.await()
    }

    override fun observeEntries(lotteryId: String): Flow<List<WaitlistEntry>> =
        entrantsOf(lotteryId).listen { docs -> docs.map { it.toEntry() }.sortedBy { it.joinedAt } }

    override fun observeEntriesForEntrant(entrantId: String): Flow<List<WaitlistEntry>> =
        allEntrants.whereEqualTo("userId", entrantId).listen { docs -> docs.map { it.toEntry() } }

    override suspend fun joinWaitlist(lotteryId: String, entrantId: String, entrantName: String, now: Long) {
        val lotRef = lotteryRef(lotteryId)
        val entryRef = entrantsOf(lotteryId).document(entrantId)
        db.runTransaction { t ->
            val lottery = t.get(lotRef).toLottery()
            val joined = t.get(entryRef).exists()
            WaitlistPolicy.checkJoin(lottery, joined, lottery.entrantCount, now)?.let { throw WaitlistException(it) }
            // Campsite and owner are copied onto the entry so owners can list bookings in one query.
            val entry = WaitlistEntry(
                lotteryId, entrantId, entrantName, now,
                campsiteId = lottery.campsiteId, campsiteName = lottery.campsiteName, ownerId = lottery.ownerId,
            )
            t.set(entryRef, entry.toMap())
            t.update(lotRef, "entrantCount", FieldValue.increment(1))
        }.await()
    }

    override suspend fun leaveWaitlist(lotteryId: String, entrantId: String, now: Long) {
        val lotRef = lotteryRef(lotteryId)
        val entryRef = entrantsOf(lotteryId).document(entrantId)
        db.runTransaction { t ->
            val lottery = t.get(lotRef).toLottery()
            val joined = t.get(entryRef).exists()
            WaitlistPolicy.checkLeave(lottery, joined, now)?.let { throw WaitlistException(it) }
            t.delete(entryRef)
            t.update(lotRef, "entrantCount", FieldValue.increment(-1))
        }.await()
    }

    override suspend fun runDrawIfDue(lotteryId: String, now: Long, random: Random): Boolean {
        val lotRef = lotteryRef(lotteryId)
        // Registration is closed once a draw is due, so the waiting list can no longer change.
        val waitlist = entrantsOf(lotteryId).get().await().documents.map { it.toEntry() }
        return db.runTransaction { t ->
            val lottery = t.get(lotRef).toLottery()
            if (!lottery.isDrawDue(now)) return@runTransaction false
            val result = LotteryDrawer.draw(lottery, waitlist, now, random)
            result.entries.forEach { e -> t.update(entrantsOf(lotteryId).document(e.entrantId), mapOf("status" to e.status.word(), "wonAt" to e.wonAt?.toTimestamp())) }
            result.notifications.forEach { n -> t.set(notificationsOf(n.recipientId).document(), n.toMap()) }
            t.update(lotRef, mapOf("status" to LotteryStatus.DRAWN.word(), "drawnAt" to now.toTimestamp()))
            true
        }.await()
    }

    override suspend fun updateLottery(updated: Lottery, now: Long) {
        val lotRef = lotteryRef(updated.id)
        val siblings = lotteriesOf(Paths.campsiteOf(updated.id)).get().await().documents.map { it.toLottery() }
        db.runTransaction { t ->
            val current = t.get(lotRef).toLottery()
            LotteryEditPolicy.check(current, updated, now)?.let { throw LotteryEditException(it) }
            StayPeriods.requireApart(current.copy(bookingStartDay = updated.bookingStartDay, bookingEndDay = updated.bookingEndDay), siblings)
            t.update(
                lotRef, mapOf(
                    "opensAt" to updated.registrationStart.toTimestamp(), "closesAt" to updated.registrationEnd.toTimestamp(),
                    "winnerCount" to updated.winnerCount, "waitlistCap" to updated.waitlistCap,
                    "stayFrom" to updated.bookingStartDay?.toIsoDay(), "stayUntil" to updated.bookingEndDay?.toIsoDay(),
                ),
            )
        }.await()
    }

    override suspend fun bookDates(
        lotteryId: String, entrantId: String, checkInDay: Long, checkOutDay: Long, now: Long,
        guests: Int, agreedToRules: Boolean,
    ) {
        val lotRef = lotteryRef(lotteryId)
        // After the draw the winners are fixed, so their IDs are known up front and the
        // transaction can re-read each one to rule out overlapping bookings.
        val winnerIds = entrantsOf(lotteryId).whereEqualTo("status", EntryStatus.WON.word()).get().await().documents.map { it.id }
        db.runTransaction { t ->
            val lottery = t.get(lotRef).toLottery()
            val winners = winnerIds.map { id -> t.get(entrantsOf(lotteryId).document(id)).toEntry() }
            val mine = winners.firstOrNull { it.entrantId == entrantId }
            BookingPolicy.check(lottery, mine, checkInDay, checkOutDay, winners)?.let { throw BookingException(it) }
            t.update(
                entrantsOf(lotteryId).document(entrantId),
                mapOf(
                    "checkIn" to checkInDay.toIsoDay(), "checkOut" to checkOutDay.toIsoDay(), "bookedAt" to now.toTimestamp(),
                    "guests" to guests, "agreedToRules" to agreedToRules,
                ),
            )
        }.await()
    }

    override suspend fun giveUpSpot(lotteryId: String, entrantId: String, now: Long) {
        val lotRef = lotteryRef(lotteryId)
        val entryRef = entrantsOf(lotteryId).document(entrantId)
        db.runTransaction { t ->
            val lottery = t.get(lotRef).toLottery()
            val entry = t.get(entryRef).takeIf { it.exists() }?.toEntry()
            // Read before any write: the campsite's cancellation policy and price, for the notice.
            val site = t.get(campsites.document(lottery.campsiteId)).takeIf { it.exists() }?.toCampsite()
            if (entry == null || entry.status != EntryStatus.WON) throw BookingException(BookingError.NOT_A_WINNER)
            val status = if (entry.isBooked) EntryStatus.CANCELLED else EntryStatus.DECLINED
            t.update(entryRef, mapOf("status" to status.word(), "endedAt" to now.toTimestamp()))
            t.update(lotRef, "spotsFree", FieldValue.increment(1))
            // A booked stay that is cancelled gets its confirmation in the guest's notifications (US 01.07.02).
            if (entry.isBooked) t.set(notificationsOf(entrantId).document(), LotteryDrawer.cancelledNotice(lottery, entrantId, entry.checkInDay!!, entry.checkOutDay!!, now, site).toMap())
        }.await()
    }

    override suspend fun rejectReservation(lotteryId: String, entrantId: String, reason: String, now: Long) {
        val lotRef = lotteryRef(lotteryId)
        val entryRef = entrantsOf(lotteryId).document(entrantId)
        db.runTransaction { t ->
            val lottery = t.get(lotRef).toLottery()
            val entry = t.get(entryRef).takeIf { it.exists() }?.toEntry()
            if (entry == null || entry.status != EntryStatus.WON) throw BookingException(BookingError.NOT_A_WINNER)
            t.update(entryRef, mapOf("status" to EntryStatus.REJECTED.word(), "note" to reason, "endedAt" to now.toTimestamp()))
            t.update(lotRef, "spotsFree", FieldValue.increment(1))
            t.set(notificationsOf(entrantId).document(), LotteryDrawer.rejectedNotice(lottery, entrantId, reason, now).toMap())
        }.await()
    }

    override suspend fun fillFreeSpots(lotteryId: String, now: Long, random: Random): Int {
        val lotRef = lotteryRef(lotteryId)
        val entries = entrantsOf(lotteryId).get().await().documents.map { it.toEntry() }
        return db.runTransaction { t ->
            val lottery = t.get(lotRef).toLottery()
            if (lottery.status != LotteryStatus.DRAWN || lottery.spotsFree <= 0) return@runTransaction 0
            val result = LotteryDrawer.fillSpots(lottery, entries, now, random)
            result.entries.forEach { e -> t.update(entrantsOf(lotteryId).document(e.entrantId), mapOf("status" to EntryStatus.WON.word(), "wonAt" to now.toTimestamp())) }
            result.notifications.forEach { n -> t.set(notificationsOf(n.recipientId).document(), n.toMap()) }
            t.update(lotRef, "spotsFree", 0)
            result.entries.size
        }.await()
    }

    override suspend fun expireUnbookedWinners(lotteryId: String, now: Long): Int {
        val lotRef = lotteryRef(lotteryId)
        val entries = entrantsOf(lotteryId).whereEqualTo("status", EntryStatus.WON.word()).get().await().documents.map { it.toEntry() }
        return db.runTransaction { t ->
            val lottery = t.get(lotRef).toLottery()
            // Read again inside the transaction: a winner who just booked keeps their spot.
            val current = entries.mapNotNull { e -> t.get(entrantsOf(lotteryId).document(e.entrantId)).takeIf { it.exists() }?.toEntry() }
            if (lottery.status != LotteryStatus.DRAWN) return@runTransaction 0
            val result = LotteryDrawer.expireUnbooked(lottery, current, now)
            result.entries.forEach { e ->
                t.update(entrantsOf(lotteryId).document(e.entrantId), mapOf("status" to e.status.word(), "endedAt" to now.toTimestamp(), "note" to e.note))
            }
            result.notifications.forEach { n -> t.set(notificationsOf(n.recipientId).document(), n.toMap()) }
            if (result.entries.isNotEmpty()) t.update(lotRef, "spotsFree", FieldValue.increment(result.entries.size.toLong()))
            result.entries.size
        }.await()
    }

    // ---- Lotteries shared with other owners (US 02.04.08) ----

    private val invites get() = db.collection(Paths.LOTTERY_INVITES)

    override suspend fun inviteToLottery(invite: LotteryInvite): String {
        val ref = invites.document()
        // The invitation and the invited owner's notice go together.
        db.batch()
            .set(ref, invite.copy(id = ref.id).toMap())
            .set(notificationsOf(invite.toOwnerId).document(), LotteryInvites.invitedNotice(invite, invite.createdAt).toMap())
            .commit().await()
        return ref.id
    }

    override fun observeInvitesTo(ownerId: String): Flow<List<LotteryInvite>> =
        invites.whereEqualTo("toOwnerId", ownerId).listen { docs -> docs.map { it.toInvite() }.sortedByDescending { it.createdAt } }

    override fun observeInvitesFrom(ownerId: String): Flow<List<LotteryInvite>> =
        invites.whereEqualTo("fromOwnerId", ownerId).listen { docs -> docs.map { it.toInvite() }.sortedByDescending { it.createdAt } }

    override suspend fun answerInvite(inviteId: String, ownerId: String, accept: Boolean, now: Long): String? {
        val ref = invites.document(inviteId)
        // The invited campsite's own lotteries, so accepting can't offer nights one of them already offers.
        val campsiteOf = ref.get().await().getString("campsiteId").orEmpty()
        val siblings = if (accept && campsiteOf.isNotBlank()) lotteriesOf(campsiteOf).get().await().documents.map { it.toLottery() } else emptyList()
        return db.runTransaction { t ->
            val snap = t.get(ref)
            if (!snap.exists()) throw LotteryEditException("This invitation no longer exists.")
            val invite = snap.toInvite()
            if (!accept) {
                if (invite.toOwnerId != ownerId || invite.status != InviteStatus.PENDING) throw LotteryEditException("This invitation has already been answered.")
                t.update(ref, mapOf("status" to InviteStatus.DECLINED.word(), "answeredAt" to now.toTimestamp()))
                t.set(notificationsOf(invite.fromOwnerId).document(), LotteryInvites.answeredNotice(invite, false, now).toMap())
                return@runTransaction null
            }
            val site = t.get(campsites.document(invite.campsiteId)).takeIf { it.exists() }?.toCampsite()
            LotteryInvites.checkAccept(invite, ownerId, site, now)?.let { throw LotteryEditException(it) }
            // Same naming as createLottery: "nov-2026", or "nov-2026-2" if that campsite already has one.
            val lottery = LotteryInvites.lotteryFor(invite)
            StayPeriods.requireApart(lottery, siblings)
            val parent = lotteriesOf(invite.campsiteId)
            val key = generateSequence(1) { it + 1 }.take(50)
                .map { Ids.lotteryKey(lottery.bookingStartDay, lottery.registrationEnd, it) }
                .first { !t.get(parent.document(it)).exists() }
            val id = Paths.lotteryId(invite.campsiteId, key)
            t.set(parent.document(key), lottery.copy(id = id).toMap())
            t.update(ref, mapOf("status" to InviteStatus.ACCEPTED.word(), "lotteryId" to id, "answeredAt" to now.toTimestamp()))
            t.set(notificationsOf(invite.fromOwnerId).document(), LotteryInvites.answeredNotice(invite.copy(lotteryId = id), true, now).toMap())
            id
        }.await()
    }

    override fun observeEndedReservationsForOwner(ownerId: String): Flow<List<Reservation>> =
        allEntrants.whereEqualTo("ownerId", ownerId)
            .listen { docs -> docs.mapNotNull { it.toEntry().toEndedReservation() }.sortedByDescending { it.endedAt ?: 0L } }

    private fun Query.reservations(): Flow<List<Reservation>> =
        listen { docs -> docs.mapNotNull { it.toEntry().toReservation() }.sortedBy { it.checkInDay } }

    override fun observeReservationsForOwner(ownerId: String): Flow<List<Reservation>> =
        allEntrants.whereEqualTo("ownerId", ownerId).reservations()

    override fun observeReservationsForCampsite(campsiteId: String): Flow<List<Reservation>> =
        allEntrants.whereEqualTo("campsiteId", campsiteId).reservations()

    override fun observeReservationsForEntrant(entrantId: String): Flow<List<Reservation>> =
        allEntrants.whereEqualTo("userId", entrantId).reservations()


    // ---- Notifications ----

    override fun observeNotifications(recipientId: String): Flow<List<AppNotification>> =
        notificationsOf(recipientId).listen { docs -> docs.map { it.toNotification() }.sortedByDescending { it.createdAt } }

    override suspend fun markAllRead(recipientId: String) {
        val unread = notificationsOf(recipientId).whereEqualTo("read", false).get().await().documents
        if (unread.isEmpty()) return
        val batch = db.batch()
        unread.forEach { batch.update(it.reference, "read", true) }
        batch.commit().await()
    }

    // ---- Ratings, follows, chats, announcements, reports ----

    private val chats get() = db.collection(Paths.CHATS)
    private val reports get() = db.collection(Paths.REPORTS)
    private fun ratingsOf(campsiteId: String) = campsites.document(campsiteId).collection(Paths.RATINGS)
    private fun followersOf(campsiteId: String) = campsites.document(campsiteId).collection(Paths.FOLLOWERS)
    private fun announcementsOf(lotteryId: String) = lotteryRef(lotteryId).collection(Paths.ANNOUNCEMENTS)

    override fun observeRatings(campsiteId: String): Flow<List<Rating>> =
        ratingsOf(campsiteId).listen { docs -> docs.map { it.toRating() }.sortedByDescending { it.createdAt } }

    override suspend fun rate(rating: Rating) {
        val site = campsites.document(rating.campsiteId)
        val ref = ratingsOf(rating.campsiteId).document(rating.userId)
        db.runTransaction { t ->
            val campsite = t.get(site).toCampsite()
            val previous = t.get(ref).takeIf { it.exists() }?.getLong("stars")?.toInt()
            val (average, count) = RatingPolicy.aggregate(campsite.rating, campsite.ratingCount, rating.stars, previous)
            t.set(ref, rating.toMap())
            t.update(site, mapOf("rating" to average, "ratingCount" to count))
        }.await()
    }

    override fun observeFollowing(campsiteId: String, userId: String): Flow<Boolean> =
        followersOf(campsiteId).document(userId).listen { true }.map { it == true }

    override suspend fun setFollowing(campsiteId: String, userId: String, userName: String, follow: Boolean, now: Long) {
        val ref = followersOf(campsiteId).document(userId)
        if (follow) ref.set(mapOf("userName" to userName, "followedAt" to now.toTimestamp())).await() else ref.delete().await()
    }

    override suspend fun notifyFollowers(lottery: Lottery, now: Long): Int {
        val followers = followersOf(lottery.campsiteId).get().await().documents.map { it.id }
        if (followers.isEmpty()) return 0
        val batch = db.batch()
        followers.forEach { batch.set(notificationsOf(it).document(), openedNotice(lottery, it, now).toMap()) }
        batch.commit().await()
        return followers.size
    }

    override fun observeChatsFor(userId: String): Flow<List<Chat>> =
        kotlinx.coroutines.flow.combine(
            chats.whereEqualTo("ownerId", userId).listen { d -> d.map { it.toChat() } },
            chats.whereEqualTo("entrantId", userId).listen { d -> d.map { it.toChat() } },
        ) { a, b -> (a + b).distinctBy { it.id }.sortedByDescending { it.lastAt } }

    override fun observeAllChats(): Flow<List<Chat>> = chats.listen { d -> d.map { it.toChat() }.sortedByDescending { it.lastAt } }

    override fun observeChat(chatId: String): Flow<Chat?> = chats.document(chatId).listen { it.toChat() }

    override fun observeMessages(chatId: String): Flow<List<ChatMessage>> =
        chats.document(chatId).collection(Paths.MESSAGES).listen { d -> d.map { it.toMessage() }.sortedBy { it.sentAt } }

    override suspend fun sendMessage(chat: Chat, senderId: String, senderName: String, text: String, now: Long, photoBase64: String?) {
        val fromOwner = senderId == chat.ownerId
        val ref = chats.document(chat.id)
        // The other person's unread count goes up by one; the rest of the conversation is kept.
        val fields = chat.copy(lastText = ChatPhotos.preview(text, photoBase64 != null), lastAt = now, lastSenderId = senderId).toMap()
            .minus(listOf("unreadForOwner", "unreadForEntrant"))
            .plus((if (fromOwner) "unreadForEntrant" else "unreadForOwner") to FieldValue.increment(1))
        db.batch()
            .set(ref, fields, SetOptions.merge())
            .set(ref.collection(Paths.MESSAGES).document(), mapOf("senderId" to senderId, "senderName" to senderName, "text" to text, "sentAt" to now.toTimestamp())
                + (if (photoBase64 != null) mapOf("photo" to photoBase64) else emptyMap()))
            .commit().await()
    }

    override suspend fun react(chatId: String, messageId: String, userId: String, emoji: String?) {
        chats.document(chatId).collection(Paths.MESSAGES).document(messageId)
            .update(FieldPath.of("reactions", userId), emoji ?: FieldValue.delete()).await()
    }

    override suspend fun markChatRead(chatId: String, userId: String) {
        val ref = chats.document(chatId)
        val chat = ref.get().await().takeIf { it.exists() }?.toChat() ?: return
        if (!chat.unreadFor(userId)) return
        ref.update(if (userId == chat.ownerId) "unreadForOwner" else "unreadForEntrant", 0).await()
    }

    override suspend fun sendAnnouncement(lottery: Lottery, audience: Audience, text: String, now: Long): Int {
        val recipients = entrantsOf(lottery.id).get().await().documents.map { it.toEntry() }.filter(audience::includes)
        val ownerName = campsites.document(lottery.campsiteId).get().await().getString("ownerName").orEmpty()
        // The notice goes into each recipient's conversation for this campsite (made if needed),
        // adding one unread for them. 150 people per batch keeps under Firestore's 500-write limit.
        recipients.chunked(150).forEach { group ->
            val batch = db.batch()
            group.forEach { e ->
                val ref = chats.document(Chat.idFor(lottery.campsiteId, e.entrantId))
                val chat = Chat(ref.id, lottery.campsiteId, lottery.campsiteName, lottery.ownerId, ownerName, e.entrantId, e.entrantName,
                    lastText = text, lastAt = now, lastSenderId = lottery.ownerId, lotteryKey = Paths.keyOf(lottery.id))
                batch.set(ref, chat.toMap().minus(listOf("unreadForOwner", "unreadForEntrant")).plus("unreadForEntrant" to FieldValue.increment(1)), SetOptions.merge())
                batch.set(ref.collection(Paths.MESSAGES).document(), mapOf(
                    "senderId" to lottery.ownerId, "senderName" to ownerName, "text" to text, "sentAt" to now.toTimestamp(), "announcement" to true,
                ))
            }
            batch.commit().await()
        }
        announcementsOf(lottery.id).document().set(
            Announcement("", lottery.id, lottery.campsiteId, lottery.campsiteName, lottery.ownerId, text, audience, recipients.size, now).toMap(),
        ).await()
        return recipients.size
    }

    override fun observeAnnouncements(lotteryId: String): Flow<List<Announcement>> =
        announcementsOf(lotteryId).listen { d -> d.map { it.toAnnouncement() }.sortedByDescending { it.sentAt } }

    override fun observeAnnouncementsByOwner(ownerId: String): Flow<List<Announcement>> =
        db.collectionGroup(Paths.ANNOUNCEMENTS).whereEqualTo("ownerId", ownerId)
            .listen { d -> d.map { it.toAnnouncement() }.sortedByDescending { it.sentAt } }

    override fun observeAllAnnouncements(): Flow<List<Announcement>> =
        db.collectionGroup(Paths.ANNOUNCEMENTS).listen { d -> d.map { it.toAnnouncement() }.sortedByDescending { it.sentAt } }

    override fun observeAllNotifications(): Flow<List<AppNotification>> =
        db.collectionGroup(Paths.NOTIFICATIONS).listen { d -> d.map { it.toNotification() }.sortedByDescending { it.createdAt } }

    override suspend fun report(report: Report) {
        reports.document().set(report.toMap()).await()
    }

    override fun observeReports(): Flow<List<Report>> = reports.listen { d -> d.map { it.toReport() }.sortedByDescending { it.createdAt } }

    override suspend fun dismissReports(campsiteId: String) {
        val docs = reports.whereEqualTo("campsiteId", campsiteId).get().await().documents
        if (docs.isEmpty()) return
        val batch = db.batch()
        docs.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }

    override suspend fun warn(profile: UserProfile, text: String, now: Long) {
        db.batch()
            .set(notificationsOf(profile.id).document(), warningNotice(profile.id, text, now).toMap())
            .update(users.document(profile.id), "warnings", FieldValue.increment(1))
            .commit().await()
    }
}
