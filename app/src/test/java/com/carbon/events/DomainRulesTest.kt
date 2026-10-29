/*
 * DomainRulesTest.kt
 *
 * Purpose: unit tests for the waiting-list rules (US 01.02.01, US 01.02.02), keyword
 * search (US 01.01.01), form validators (US 01.07.03, US 02.01.02, US 02.04.03,
 * US 02.04.05) and promo QR payloads (US 02.02.01, US 01.12.01).
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import com.carbon.events.TestData
import com.carbon.events.TestData.DAY
import com.carbon.events.TestData.NOW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

class CampsiteSearchTest {
    private val sites = listOf(
        TestData.campsite("two-jack", "Two Jack Lakeside", "Banff, AB", listOf("Lakeside")),
        TestData.campsite("tunnel", "Tunnel Mountain Village", "Banff, AB", listOf("Mountain")),
        TestData.campsite("louise", "Lake Louise Tent Sites", "Lake Louise, AB", listOf("Riverside")),
    )

    @Test fun blankQuery_returnsAllAlphabetically() =
        assertEquals(listOf("louise", "tunnel", "two-jack"), CampsiteSearch.search(sites, "  ").map { it.id })

    @Test fun matchesNameIgnoringCase() = assertEquals(listOf("tunnel"), CampsiteSearch.search(sites, "TUNNEL").map { it.id })

    @Test fun matchesLocationAndTags() {
        assertEquals(setOf("two-jack", "tunnel"), CampsiteSearch.search(sites, "banff").map { it.id }.toSet())
        assertEquals(listOf("tunnel"), CampsiteSearch.search(sites, "mountain").map { it.id })
    }

    @Test fun everyWordMustMatch() = assertEquals(listOf("two-jack"), CampsiteSearch.search(sites, "banff lakeside").map { it.id })

    @Test fun nameMatchesRankFirst() {
        // "lake" is in Lake Louise's name but only in Two Jack's tag/name word "Lakeside".
        val ids = CampsiteSearch.search(sites, "lake").map { it.id }
        assertEquals("louise", ids.first())
    }

    @Test fun noMatchReturnsEmpty() = assertTrue(CampsiteSearch.search(sites, "jasper").isEmpty())
}

class ValidatorsTest {
    @Test fun profile_validWithoutPhone() = assertTrue(ProfileValidator.validate("Adam Kent", "adam@example.com", "").isEmpty())

    @Test fun profile_requiresNameAndValidEmail() {
        val errors = ProfileValidator.validate(" ", "not-an-email", "")
        assertTrue(Field.NAME in errors && Field.EMAIL in errors)
    }

    @Test fun profile_rejectsBadPhone() = assertTrue(Field.PHONE in ProfileValidator.validate("A", "a@b.ca", "abc"))

    @Test fun profile_acceptsFormattedPhone() = assertTrue(ProfileValidator.validate("A", "a@b.ca", "+1 (780) 555-0142").isEmpty())

    @Test fun campsite_validForm() = assertTrue(CampsiteValidator.validate("Bow River Sites", "Banff, AB", 6, 65).isEmpty())

    @Test fun campsite_requiresNameLocationAndSaneNumbers() {
        val errors = CampsiteValidator.validate("", "", 0, -5)
        assertEquals(setOf(Field.NAME, Field.LOCATION, Field.OCCUPANCY, Field.PRICE), errors.keys)
    }

    @Test fun lottery_validForm() = assertTrue(LotteryValidator.validate(NOW, NOW + 3 * DAY, 4, NOW).isEmpty())

    @Test fun lottery_closeMustFollowOpen() = assertTrue(Field.REG_END in LotteryValidator.validate(NOW + DAY, NOW + DAY, 4, NOW))

    @Test fun lottery_closeMustBeInFuture() = assertTrue(Field.REG_END in LotteryValidator.validate(NOW - 2 * DAY, NOW - DAY, 4, NOW))

    @Test fun lottery_needsDatesAndWinners() {
        assertEquals(setOf(Field.REG_START, Field.REG_END, Field.WINNERS), LotteryValidator.validate(null, null, 0, NOW).keys)
    }
}

class QrCodesTest {
    @Test fun payload_roundTrips() = assertEquals("two-jack", QrCodes.parseCampsiteId(QrCodes.payloadFor("two-jack")))
    @Test fun acceptsBareCodeForManualEntry() = assertEquals("site1001", QrCodes.parseCampsiteId("  site1001 "))
    @Test fun rejectsOtherLinks() = assertNull(QrCodes.parseCampsiteId("https://example.com/campsite"))
    @Test fun rejectsEmpty() = assertNull(QrCodes.parseCampsiteId("carbon-events://campsite/"))
}
