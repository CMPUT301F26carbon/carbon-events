/*
 * ValidatorsTest.kt (Suvir, Vaibhav)
 *
 * Purpose: Unit tests for Validators.kt: profile, sign-up and password checks (US 01.09.01,
 * US 01.07.03), the campsite form (US 02.01.02), and the lottery form's registration period,
 * winners, booking period and waitlist cap (US 02.04.03 to 02.04.05, US 02.04.07).
 *
 * AI use: the tests added on November 20, 2026 were written with AI assistance (Claude Opus 5.5, Anthropic). Prompt: "Write tests for every part of Validators.kt that isn't tested yet."
 * Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import com.carbon.events.TestData.NOW
import com.carbon.events.TestData.DAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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

    private val now = TestData.NOW
    private val today = now / TestData.DAY

    // ---- Sign-up checks ----

    @Test fun strongPassword_needsEightCharactersAndANumber() {
        assertTrue(ProfileValidator.isStrongPassword("campfire8"))
        assertFalse("too short", ProfileValidator.isStrongPassword("camp8"))
        assertFalse("no number", ProfileValidator.isStrongPassword("campfires"))
        assertTrue("exactly eight", ProfileValidator.isStrongPassword("abcdefg1"))
    }

    @Test fun signUp_withEverythingRight_hasNoErrors() {
        assertTrue(ProfileValidator.validateSignUp("Adam Kent", "adam@example.com", "campfire8", "", "30").isEmpty())
    }

    @Test fun signUp_flagsAWeakPasswordAndAMissingAge() {
        val errors = ProfileValidator.validateSignUp("Adam Kent", "adam@example.com", "short", "", "")
        assertEquals("Use 8 or more characters with at least one number", errors[Field.PASSWORD])
        assertEquals("Enter your age", errors[Field.AGE])
    }

    @Test fun signUp_withAnExistingLogin_doesNotAskForAPassword() {
        assertNull(ProfileValidator.validateSignUp("Adam Kent", "adam@example.com", null, "", "30")[Field.PASSWORD])
    }

    @Test fun signUp_acceptsAgesFrom18To120() {
        assertEquals("You must be 18+ to use Events", ProfileValidator.validateSignUp("A", "a@b.co", null, "", "17")[Field.AGE])
        assertNull(ProfileValidator.validateSignUp("A", "a@b.co", null, "", "18")[Field.AGE])
        assertNull(ProfileValidator.validateSignUp("A", "a@b.co", null, "", "120")[Field.AGE])
        assertEquals("You must be 18+ to use Events", ProfileValidator.validateSignUp("A", "a@b.co", null, "", "121")[Field.AGE])
        assertEquals("Enter your age", ProfileValidator.validateSignUp("A", "a@b.co", null, "", "thirty")[Field.AGE])
    }

    // ---- New-lottery form ----

    @Test fun bookingPeriod_isNeeded() {
        assertEquals("Pick the stay dates winners can book", LotteryValidator.validateBooking(today, null, null)[Field.BOOK_START])
        assertEquals("Pick the last check-out day", LotteryValidator.validateBooking(today, today + 5, null)[Field.BOOK_END])
    }

    @Test fun bookingPeriod_checkOutMustComeAfterTheFirstNight() {
        assertEquals("Check-out must be after the first night", LotteryValidator.validateBooking(today, today + 5, today + 5)[Field.BOOK_END])
        assertEquals("Check-out must be after the first night", LotteryValidator.validateBooking(today, today + 5, today + 4)[Field.BOOK_END])
    }

    @Test fun bookingPeriod_startsOnOrAfterTheDayRegistrationCloses() {
        assertEquals("Stays must start after registration closes", LotteryValidator.validateBooking(today + 3, today + 2, today + 6)[Field.BOOK_START])
        assertTrue(LotteryValidator.validateBooking(today + 3, today + 3, today + 6).isEmpty())
        assertTrue("registration close not picked yet", LotteryValidator.validateBooking(null, today + 1, today + 6).isEmpty())
    }

    @Test fun waitlistCap_isOptional_butAtLeastOne() {
        assertNull(LotteryValidator.validateCap(null))
        assertNull(LotteryValidator.validateCap(1))
        assertEquals("The cap must be at least 1", LotteryValidator.validateCap(0))
        assertEquals("The cap must be at least 1", LotteryValidator.validateCap(-4))
    }
}
