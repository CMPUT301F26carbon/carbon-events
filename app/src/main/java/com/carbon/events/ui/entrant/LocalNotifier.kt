/*
 * LocalNotifier.kt (Suvir)
 *
 * Phone notifications: the channel, the Events logo with its corner picture, turning new
 * notifications and messages into phone notifications (US 01.03.01, US 01.03.02), and opening
 * the chat or campsite a notification is about when it's tapped.
 *
 * Outstanding issues: with the app closed, notifications arrive through BackgroundSync about
 * every 15 minutes rather than instantly (no push server).
 */
package com.carbon.events

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Posts system notifications for new in-app notifications. */
object LocalNotifier {
    private const val CHANNEL_ID = "lottery_results"
    private const val PREFS = "local_notifier"
    private const val KEY_SHOWN = "shown_ids"

    /** Creates the "Lottery results" channel (safe to call repeatedly). */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, context.getString(R.string.notification_channel_lottery), NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Whether the app may post notifications (Android 13+ asks the user). */
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * Shows a system notification for every unread item in [items] not shown before.
     * Tapping one opens what it's about ([tapTarget]).
     */
    fun showNew(context: Context, items: List<AppNotification>, allowed: (AppNotification) -> Boolean = { true }) {
        if (!hasPermission(context)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val shown = prefs.getStringSet(KEY_SHOWN, emptySet()).orEmpty()
        val unseen = items.filter { !it.read && it.id !in shown }
        if (unseen.isEmpty()) return
        // Everything unseen is marked shown, so switching a type back on doesn't replay old ones.
        prefs.edit().putStringSet(KEY_SHOWN, shown + unseen.map { it.id }).apply()
        val fresh = unseen.filter(allowed)
        if (fresh.isEmpty()) return
        val manager = NotificationManagerCompat.from(context)
        for (item in fresh) {
            val open = PendingIntent.getActivity(
                context, item.id.hashCode(), tapIntent(context, tapTarget(item)),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(notificationArt(context, item.type))
                .setColor(NIGHT_SKY)
                .setContentTitle(item.title)
                .setContentText(item.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(item.body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                manager.notify(item.id.hashCode(), n)
            } catch (_: SecurityException) {
                return
            }
        }
    }

    /** Opens the app at [target]: a campsite as a promo link would, a chat through [NotificationTaps]. */
    fun tapIntent(context: Context, target: TapTarget): Intent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        when (target) {
            is TapTarget.Chat -> intent.putExtra(NotificationTaps.EXTRA_CHAT, target.chatId)
            is TapTarget.Campsite -> intent.setAction(Intent.ACTION_VIEW).setData(android.net.Uri.parse(QrCodes.payloadFor(target.campsiteId)))
            TapTarget.App -> Unit
        }
        return intent
    }
}

/** What tapping a phone notification opens. */
sealed interface TapTarget {
    /** The conversation a message arrived in. */
    data class Chat(val chatId: String) : TapTarget
    /** The campsite a lottery result, spot, booking change or new lottery is about. */
    data class Campsite(val campsiteId: String) : TapTarget
    /** Just the app (a warning, or anything without a campsite). */
    data object App : TapTarget
}

/** Prefix of the IDs given to chat messages turned into phone notifications ("chat-{chatId}-{time}"). */
const val CHAT_NOTICE_PREFIX = "chat-"

/** What tapping [item] should open. */
fun tapTarget(item: AppNotification): TapTarget = when {
    item.id.startsWith(CHAT_NOTICE_PREFIX) -> TapTarget.Chat(item.id.removePrefix(CHAT_NOTICE_PREFIX).substringBeforeLast('-'))
    item.type != NotificationType.WARNING && item.campsiteId.isNotBlank() && QrCodes.parseCampsiteId(QrCodes.PREFIX + item.campsiteId) != null -> TapTarget.Campsite(item.campsiteId)
    else -> TapTarget.App
}

/** A chat to open because its phone notification was tapped; the open screen opens it, then clears it. */
object NotificationTaps {
    /** The extra on the tap's intent holding the chat ID. */
    const val EXTRA_CHAT = "open_chat"

    /** The chat waiting to open, or null. */
    var pendingChat by mutableStateOf<String?>(null)
        private set

    /** Remembers the chat from a tapped notification's [intent]. */
    fun handle(intent: Intent?) {
        intent?.getStringExtra(EXTRA_CHAT)?.takeIf { it.isNotBlank() }?.let { pendingChat = it }
    }

    /** Called once the chat has been opened. */
    fun consume() { pendingChat = null }
}

/** The corner picture's fill: the app's primary colour (EventsColors.Primary). */
const val BADGE_COLOR: Int = 0xFFB9A9FF.toInt()

/** The corner picture's icon colour (EventsColors.OnPrimary). */
private const val BADGE_ICON_COLOR: Int = 0xFF2A2160.toInt()

/** The logo's night-sky blue, used to tint the status-bar logo in the notification shade. */
private const val NIGHT_SKY: Int = 0xFF1C1F56.toInt()

/**
 * The small picture in the corner of the logo on a phone notification, saying who it's from: a
 * speech bubble for messages, a warning sign for a warning from the Events team, and a bell for
 * lottery news (results, spots opening, rejected or cancelled bookings).
 */
fun notificationBadge(type: NotificationType): Int = when (type) {
    NotificationType.MESSAGE -> R.drawable.ic_chat
    NotificationType.WARNING -> R.drawable.ic_warning
    else -> R.drawable.ic_bell
}

/** Where the corner picture sits on a logo [size] pixels wide: a circle in the bottom-right corner. */
fun badgeBounds(size: Int): RectF {
    val d = size * 0.38f
    val margin = size * 0.01f
    return RectF(size - margin - d, size - margin - d, size - margin, size - margin)
}

private val notificationArtCache = mutableMapOf<NotificationType, Bitmap>()

/**
 * The picture on the right of a phone notification: the Events logo with [type]'s corner picture
 * ([notificationBadge]). Made once per kind of notification.
 */
fun notificationArt(context: Context, type: NotificationType): Bitmap = synchronized(notificationArtCache) {
    notificationArtCache.getOrPut(type) { drawNotificationArt(context, type) }
}

private fun drawNotificationArt(context: Context, type: NotificationType): Bitmap {
    val size = (64 * context.resources.displayMetrics.density).toInt().coerceAtLeast(64)
    val art = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(art)
    // The logo is a rounded square on white: keep only the rounded square.
    val inset = size * 0.035f
    val edge = Path().apply { addRoundRect(RectF(inset, inset, size - inset, size - inset), size * 0.22f, size * 0.22f, Path.Direction.CW) }
    canvas.save()
    canvas.clipPath(edge)
    BitmapFactory.decodeResource(context.resources, R.drawable.logo_mark)?.let { logo ->
        canvas.drawBitmap(logo, null, RectF(0f, 0f, size.toFloat(), size.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }
    canvas.restore()
    // The corner picture: a white ring, the primary colour, and the icon on top.
    val corner = badgeBounds(size)
    val radius = corner.width() / 2
    canvas.drawCircle(corner.centerX(), corner.centerY(), radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
    canvas.drawCircle(corner.centerX(), corner.centerY(), radius - size * 0.025f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BADGE_COLOR })
    ContextCompat.getDrawable(context, notificationBadge(type))?.mutate()?.let { icon ->
        icon.setTint(BADGE_ICON_COLOR)
        val pad = corner.width() * 0.24f
        icon.setBounds((corner.left + pad).toInt(), (corner.top + pad).toInt(), (corner.right - pad).toInt(), (corner.bottom - pad).toInt())
        icon.draw(canvas)
    }
    return art
}

/**
 * Turns new notifications and chat messages into phone notifications while the app is open
 * (US 01.03.01, US 01.03.02), honouring Profile > Notifications. Asks for permission on Android 13+.
 *
 * @param container the signed-in person's container, so only their notifications are shown.
 */
@Composable
fun NotificationEffect(container: AppContainer) {
    val context = LocalContext.current
    // The notification pop-up is not shown on launch: it's asked when you join a lottery or turn
    // Notifications on in Profile. Until then, alerts still show inside the app.
    LaunchedEffect(container) {
        // Only the kinds the person switched on in Profile > Notifications pop up.
        combine(container.notifications.observeNotifications(container.userId), container.profiles.observeProfile(container.userId)) { list, p -> list to (p?.prefs ?: NotificationPrefs()) }
            .collect { (list, prefs) -> LocalNotifier.showNew(context, list) { prefs.allows(it.type) } }
    }
    LaunchedEffect(container) {
        // New chat messages, shown as "Message from …" while the app is open.
        combine(container.social.observeChatsFor(container.userId), container.profiles.observeProfile(container.userId)) { chats, p -> chats to (p?.prefs ?: NotificationPrefs()) }
            .collect { (chats, prefs) ->
                if (!prefs.allows(NotificationType.MESSAGE)) return@collect
                val unread = chats.filter { it.unreadFor(container.userId) && it.lastSenderId != container.userId }.map { c ->
                    AppNotification("$CHAT_NOTICE_PREFIX${c.id}-${c.lastAt}", container.userId, NotificationType.MESSAGE, "Message from ${if (c.lastSenderId == c.ownerId) c.ownerName else c.entrantName}", c.lastText, "", c.campsiteId, c.lastAt)
                }
                LocalNotifier.showNew(context, unread)
            }
    }
}

/**
 * Opens the chat whose phone notification was tapped ([NotificationTaps]), once it has loaded,
 * if the signed-in person is in it.
 */
@Composable
fun OpenTappedChat(container: AppContainer, onOpen: (Chat) -> Unit) {
    val pending = NotificationTaps.pendingChat
    LaunchedEffect(pending, container.userId) {
        val id = pending ?: return@LaunchedEffect
        val chat = kotlinx.coroutines.withTimeoutOrNull(10_000) { container.social.observeChat(id).first { it != null } }
        NotificationTaps.consume()
        if (chat != null && (chat.ownerId == container.userId || chat.entrantId == container.userId)) onOpen(chat)
    }
}
