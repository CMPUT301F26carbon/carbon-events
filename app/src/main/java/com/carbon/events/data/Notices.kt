/*
 * Notices.kt (Vaibhav)
 *
 * The notifications a new lottery, an owner's message and an admin warning become.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

/** The notification each follower gets when a campsite they follow opens a lottery. */
fun openedNotice(lottery: Lottery, recipientId: String, now: Long) = AppNotification(
    id = "", recipientId = recipientId, type = NotificationType.OPENED,
    title = "New lottery at ${lottery.campsiteName}",
    body = "Registration is open ${formatWindow(lottery)}. Join the waiting list before it closes.",
    lotteryId = lottery.id, campsiteId = lottery.campsiteId, createdAt = now,
)

private fun formatWindow(lottery: Lottery): String {
    val f = java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.CANADA)
    val zone = java.time.ZoneId.systemDefault()
    fun d(ms: Long) = f.format(java.time.Instant.ofEpochMilli(ms).atZone(zone))
    return "${d(lottery.registrationStart)} – ${d(lottery.registrationEnd)}"
}

/** The notification an announcement becomes for each recipient. */
fun announcementNotice(lottery: Lottery, recipientId: String, text: String, now: Long) = AppNotification(
    id = "", recipientId = recipientId, type = NotificationType.MESSAGE,
    title = "Message from ${lottery.campsiteName}", body = text,
    lotteryId = lottery.id, campsiteId = lottery.campsiteId, createdAt = now,
)

/** The notification an admin warning becomes. */
fun warningNotice(recipientId: String, text: String, now: Long) = AppNotification(
    id = "", recipientId = recipientId, type = NotificationType.WARNING,
    title = "Warning from the Events team", body = text, lotteryId = "", campsiteId = "", createdAt = now,
)
