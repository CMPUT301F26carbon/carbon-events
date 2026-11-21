/*
 * FirestoreMappingTest.kt (Vaibhav)
 *
 * Purpose: Unit tests for FirestoreMapping.kt: the record left when an admin removes a profile
 * (US 03.02.01), which spots a deleted profile gives back, and the documents the app writes.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write tests for every part of FirestoreMapping.kt that isn't tested yet."
 * Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreMappingTest {
    private val now = TestData.NOW
    private val day = TestData.DAY

    // ---- A reservation ----

    private fun booked(checkIn: Long, checkOut: Long) =
        WaitlistEntry("lot-1", "entrant-1", "Entrant 1", now, status = EntryStatus.WON, checkInDay = checkIn, checkOutDay = checkOut, bookedAt = now, guests = 3)

    // ---- When a profile goes ----

    @Test fun removedRecord_keepsTheNameAndLogin_butDropsContactDetails() {
        val adam = UserProfile("adam-kent", "Adam Kent", "adam@example.com", phone = "+1 780 555 0142", authUid = "uid-1", photoBase64 = "abc")
        val record = adam.removedRecord()
        assertTrue(record.removed)
        assertTrue(record.blocked)
        assertEquals("", record.phone)
        assertNull(record.photoBase64)
        assertEquals("Adam Kent", record.name)
        assertEquals("uid-1", record.authUid)
    }

    @Test fun deletingAProfile_givesBackSpotsNotYetStayedIn() {
        val today = now / day
        assertTrue("won, not booked", WaitlistEntry("l", "e", "E", now, status = EntryStatus.WON).givesSpotBackOnDelete(now))
        assertTrue("stay still to come", booked(today + 2, today + 4).givesSpotBackOnDelete(now))
        assertTrue("checks out today", booked(today - 2, today).givesSpotBackOnDelete(now))
        assertFalse("stay already over", booked(today - 5, today - 2).givesSpotBackOnDelete(now))
        assertFalse("lost", WaitlistEntry("l", "e", "E", now, status = EntryStatus.LOST).givesSpotBackOnDelete(now))
    }

    // ---- Documents the app writes ----

    @Test fun profileDocument_storesTheRoleAsAWord_andTheLogOutMarkOnlyOnceSet() {
        val adam = UserProfile("adam", "Adam Kent", "adam@example.com", activeRole = Role.OWNER, authUid = "uid-1", createdAt = now)
        val doc = adam.toMap()
        assertEquals("owner", doc["role"])
        assertEquals("uid-1", doc["authUid"])
        assertEquals(false, doc["removed"])
        assertNull("never logged out everywhere", doc["signedOutAt"])
        assertTrue(adam.copy(signedOutAt = now).toMap()["signedOutAt"] is com.google.firebase.Timestamp)
    }

    @Test fun profileDocument_keepsEveryNotificationChoice() {
        val prefs = NotificationPrefs(optOut = true, results = false)
        val doc = UserProfile("adam", "Adam Kent", "", prefs = prefs).toMap()
        assertEquals(prefs.toMap(), doc["prefs"])
    }

    @Test fun lotteryDocument_storesStayDaysAsDates_andTheStatusAsAWord() {
        val doc = TestData.lottery().copy(bookingStartDay = 20_400, bookingEndDay = 20_410, status = LotteryStatus.DRAWN).toMap()
        assertEquals(java.time.LocalDate.ofEpochDay(20_400).toString(), doc["stayFrom"])
        assertEquals(java.time.LocalDate.ofEpochDay(20_410).toString(), doc["stayUntil"])
        assertEquals("drawn", doc["status"])
        assertNull(TestData.lottery().toMap()["stayFrom"])
    }

    @Test fun notificationDocument_storesTheTypeWord_andTheLotteryKey() {
        val doc = AppNotification("n1", "adam", NotificationType.CANCELLED, "T", "B", "two-jack/lot-1", "two-jack", now).toMap()
        assertEquals("cancelled", doc["type"])
        assertEquals("lot-1", doc["lotteryId"])
        assertEquals(false, doc["read"])
    }

    @Test fun trustedPhoneDocument_isWhoseItIs_andWhetherItIsBlocked() {
        val doc = TrustedDevice("pixel", "adam", "Pixel 8", now).toMap()
        assertEquals("adam", doc["userId"])
        assertEquals("Pixel 8", doc["model"])
        assertEquals(false, doc["blocked"])
    }
}
