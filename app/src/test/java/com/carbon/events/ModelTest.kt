/*
 * ModelTest.kt
 *
 * Purpose: unit tests for the model classes: lottery registration windows (US 02.04.03)
 * and profile helpers used for device-based profiles and role switching (US 01.09.01).
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import com.carbon.events.TestData
import com.carbon.events.TestData.DAY
import com.carbon.events.TestData.NOW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LotteryModelTest {
    private val lottery = TestData.lottery(start = NOW, end = NOW + DAY)

    @Test fun upcomingBeforeStart() = assertTrue(lottery.isUpcoming(NOW - 1))
    @Test fun openFromStartUntilEnd() {
        assertTrue(lottery.isRegistrationOpen(NOW))
        assertTrue(lottery.isRegistrationOpen(NOW + DAY - 1))
        assertFalse(lottery.isRegistrationOpen(NOW + DAY))
    }
    @Test fun drawDueAtEnd() {
        assertFalse(lottery.isDrawDue(NOW + DAY - 1))
        assertTrue(lottery.isDrawDue(NOW + DAY))
    }
    @Test fun drawnLotteryIsNeverOpenOrDue() {
        val drawn = lottery.copy(status = LotteryStatus.DRAWN)
        assertFalse(drawn.isRegistrationOpen(NOW))
        assertFalse(drawn.isDrawDue(NOW + 2 * DAY))
    }
}

class UserProfileTest {
    private val adam = UserProfile("device-1", "Adam Kent", "adam@example.com")

    @Test fun firstNameAndInitials() {
        assertEquals("Adam", adam.firstName)
        assertEquals("AK", adam.initials)
        assertEquals("M", adam.copy(name = "Maya").initials)
    }

    @Test fun nonAdminCanOnlyBeEntrantOrOwner() = assertEquals(listOf(Role.ENTRANT, Role.OWNER), adam.availableRoles)

    @Test fun adminCanUseEveryRole() = assertEquals(Role.entries, adam.copy(isAdmin = true).availableRoles)

    @Test fun campsiteMapLocationFlag() {
        assertTrue(TestData.campsite().hasMapLocation)
        assertFalse(TestData.campsite().copy(latitude = null).hasMapLocation)
    }
}
