/*
 * CancellationNoticeTest.kt (Vaibhav)
 *
 * Purpose: Unit tests for the automatic cancellation confirmation (Figma storyboard "E-CANCELLED
 * + automatic notification"): cancelling a booked stay sends the guest a notification, giving
 * back a spot that was never booked doesn't, and the Bookings setting decides whether it pops
 * up on the phone (US 01.07.02).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 18, 2026). Prompt: "Write
 * tests for the cancellation confirmation notification." Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** One drawn lottery with two winners: Maya booked her nights, Liam hasn't yet. */
class CancellationNoticeTest {
    private val now = TestData.NOW
    private val today = dayOf(now)
    private val lot = TestData.lottery().copy(status = LotteryStatus.DRAWN, bookingStartDay = today + 2, bookingEndDay = today + 20)
    private val maya = WaitlistEntry(lot.id, "maya", "Maya Chen", now, EntryStatus.WON, today + 4, today + 6, now, lot.campsiteId, lot.campsiteName, lot.ownerId, 2, true)
    private val liam = WaitlistEntry(lot.id, "liam", "Liam Okafor", now, EntryStatus.WON, campsiteId = lot.campsiteId, campsiteName = lot.campsiteName, ownerId = lot.ownerId)
    private val store = InMemoryEventsStore(EventsSnapshot(lotteries = mapOf(lot.id to lot), entries = listOf(maya, liam)))

    private fun noticesFor(id: String) = store.snapshot.notifications.filter { it.recipientId == id }

    @Test fun cancellingABookedStay_sendsTheGuestAConfirmation() = runTest {
        store.giveUpSpot(lot.id, "maya", now)
        val n = noticesFor("maya").single()
        assertEquals(NotificationType.CANCELLED, n.type)
        assertEquals("Booking at ${lot.campsiteName} cancelled", n.title)
        assertTrue(n.body.contains(formatStay(today + 4, today + 6).substringBefore(" ·")))
        assertEquals(lot.id, n.lotteryId)
        assertEquals(lot.campsiteId, n.campsiteId)
        assertFalse(n.read)
    }

    @Test fun decliningASpotThatWasNeverBooked_sendsNothing() = runTest {
        store.giveUpSpot(lot.id, "liam", now)
        assertEquals(EntryStatus.DECLINED, store.snapshot.entries.first { it.entrantId == "liam" }.status)
        assertTrue(noticesFor("liam").isEmpty())
    }

    @Test fun nobodyElse_getsTheConfirmation() = runTest {
        store.giveUpSpot(lot.id, "maya", now)
        assertTrue(noticesFor("liam").isEmpty())
        assertTrue(noticesFor(lot.ownerId).isEmpty())
    }

    @Test fun theNotice_saysNothingWasCharged() {
        val n = LotteryDrawer.cancelledNotice(lot, "maya", today + 4, today + 6, now)
        assertTrue(n.body.contains("Nothing was charged"))
    }

    @Test fun theBookingsSetting_decidesWhetherItPopsUp() {
        assertTrue(NotificationPrefs().allows(NotificationType.CANCELLED))
        assertFalse(NotificationPrefs(reservations = false).allows(NotificationType.CANCELLED))
        assertFalse(NotificationPrefs(optOut = true).allows(NotificationType.CANCELLED))
    }

    @Test fun theTypeIsStoredAsCancelled() {
        assertEquals("cancelled", NotificationType.CANCELLED.word)
        assertEquals(NotificationType.CANCELLED, NotificationType.of("cancelled"))
    }
}
