/*
 * BackgroundSync.kt (Rayner)
 *
 * Free background work with Android's WorkManager (no paid server): about every 15 minutes, even
 * when the app is closed, it shows phone notifications for new results and messages
 * (US 01.03.01, US 01.03.02) and runs any lottery draw that is due on the owner's or an admin's
 * phone (US 02.05.01).
 */
package com.carbon.events

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/** One background pass for whoever is signed in on this phone. */
class BackgroundSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (FirebaseApp.getApps(applicationContext).isEmpty()) return Result.success() // offline demo build
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success() // signed out
        val store = FirestoreEventsStore(FirebaseFirestore.getInstance())
        return runCatching {
            withTimeoutOrNull(60_000) { BackgroundSync.run(applicationContext, store, store, store, store, uid, System.currentTimeMillis()) }
            Result.success()
        }.getOrElse { Result.retry() }
    }
}

/** The work itself, separate from Android so tests can run it against any store. */
object BackgroundSync {
    private const val NAME = "events-background-sync"

    /** Schedules the pass about every 15 minutes (Android's shortest) while online. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        runCatching { WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request) }
    }

    /**
     * Runs one pass for the account [authUid]: draws that are due (owners and admins), then phone
     * notifications for new results and unread messages, honouring Profile > Notifications.
     * Returns how many draws it ran.
     */
    suspend fun run(
        context: Context?,
        profiles: ProfileRepository,
        lotteries: LotteryRepository,
        notifications: NotificationRepository,
        social: SocialRepository,
        authUid: String,
        now: Long,
    ): Int {
        val profile = profiles.observeProfileForAccount(authUid).first() ?: return 0
        if (profile.blocked) return 0
        var drawn = 0
        val mayDraw = profile.isAdmin || profile.activeRole == Role.OWNER
        if (mayDraw) {
            val mine = if (profile.isAdmin) lotteries.observeLotteries().first() else lotteries.observeLotteriesByOwner(profile.id).first()
            mine.filter { it.isDrawDue(now) }.forEach { if (runCatching { lotteries.runDrawIfDue(it.id, now) }.getOrDefault(false)) drawn++ }
        }
        if (context != null) {
            val prefs = profile.prefs
            LocalNotifier.showNew(context, notifications.observeNotifications(profile.id).first()) { prefs.allows(it.type) }
            if (prefs.allows(NotificationType.MESSAGE)) {
                val unread = social.observeChatsFor(profile.id).first().filter { it.unreadFor(profile.id) && it.lastSenderId != profile.id }.map { c ->
                    AppNotification("chat-${c.id}-${c.lastAt}", profile.id, NotificationType.MESSAGE,
                        "Message from ${if (c.lastSenderId == c.ownerId) c.ownerName else c.entrantName}", c.lastText, "", c.campsiteId, c.lastAt)
                }
                LocalNotifier.showNew(context, unread)
            }
        }
        return drawn
    }
}
