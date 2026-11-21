/*
 * DomainRulesTest.kt (Suvir)
 *
 * Purpose: unit tests for the waiting-list rules (US 01.02.01, US 01.02.02). Search, form
 * validators and promo QR payloads have their own test files (CampsiteSearchTest,
 * ValidatorsTest, QrCodesTest).
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import com.carbon.events.TestData.DAY
import com.carbon.events.TestData.NOW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WaitlistPolicyTest {
    private val open = TestData.lottery()

    @Test fun join_allowedWhileRegistrationOpen() = assertNull(WaitlistPolicy.checkJoin(open, false, 3, NOW))
    @Test fun join_refusedTwice() = assertEquals(WaitlistError.ALREADY_JOINED, WaitlistPolicy.checkJoin(open, true, 3, NOW))
    @Test fun join_refusedBeforeOpening() = assertEquals(WaitlistError.NOT_OPEN_YET, WaitlistPolicy.checkJoin(TestData.lottery(start = NOW + DAY), false, 0, NOW))
    @Test fun join_refusedAfterClosing() = assertEquals(WaitlistError.CLOSED, WaitlistPolicy.checkJoin(open, false, 0, open.registrationEnd))
    @Test fun join_refusedWhenCapReached() = assertEquals(WaitlistError.FULL, WaitlistPolicy.checkJoin(TestData.lottery(cap = 3), false, 3, NOW))
    @Test fun leave_allowedWhileOpen() = assertNull(WaitlistPolicy.checkLeave(open, true, NOW))
    @Test fun leave_refusedIfNotJoined() = assertEquals(WaitlistError.NOT_JOINED, WaitlistPolicy.checkLeave(open, false, NOW))
    @Test fun leave_refusedAfterClosing() = assertEquals(WaitlistError.CLOSED, WaitlistPolicy.checkLeave(open, true, open.registrationEnd + 1))
}

