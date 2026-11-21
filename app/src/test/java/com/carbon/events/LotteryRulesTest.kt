/*
 * LotteryRulesTest.kt (Vaibhav)
 *
 * Purpose: Unit tests for the rules in LotteryRules.kt that no other test calls directly:
 * filling spots a winner gave back (US 01.05.01), the notice for a rejected reservation,
 * password and sign-up checks (US 01.02.01), the booking-period and waitlist-cap checks on the
 * new-lottery form (US 02.04.04, US 02.04.07), booked nights and overlapping stays, and editing a
 * published lottery (US 02.04.06).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write
 * tests for every rule in LotteryRules.kt that isn't tested yet." Reviewed, edited and run by
 * Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LotteryRulesTest {
    private val now = TestData.NOW
    private val today = now / TestData.DAY

    // ---- Filling spots that open up ----

    private fun drawn(winners: Int, free: Int) = TestData.lottery(winners = winners).copy(status = LotteryStatus.DRAWN, spotsFree = free)
    private fun entry(i: Int, status: EntryStatus) = WaitlistEntry("lot-1", "entrant-$i", "Entrant $i", now, status = status)

    @Test fun fillSpots_promotesOnlyAsManyAsHaveOpened() {
        val list = listOf(entry(1, EntryStatus.WON), entry(2, EntryStatus.LOST), entry(3, EntryStatus.LOST), entry(4, EntryStatus.LOST))
        val result = LotteryDrawer.fillSpots(drawn(winners = 2, free = 1), list, now, Random(7))
        assertEquals(1, result.entries.size)
        assertTrue(result.entries.all { it.status == EntryStatus.WON })
        assertTrue(result.entries.single().entrantId in setOf("entrant-2", "entrant-3", "entrant-4"))
    }

    @Test fun fillSpots_tellsEachPromotedEntrant() {
        val list = listOf(entry(2, EntryStatus.LOST), entry(3, EntryStatus.LOST))
        val result = LotteryDrawer.fillSpots(drawn(winners = 2, free = 2), list, now, Random(1))
        assertEquals(result.winnerIds, result.notifications.map { it.recipientId }.toSet())
        result.notifications.forEach {
            assertEquals(NotificationType.SPOT_OPENED, it.type)
            assertEquals("A spot opened at Two Jack Lakeside!", it.title)
            assertEquals(now, it.createdAt)
        }
    }

    @Test fun fillSpots_withEveryWinnerStillIn_promotesNobody() {
        val list = listOf(entry(1, EntryStatus.WON), entry(2, EntryStatus.WON), entry(3, EntryStatus.LOST))
        assertTrue(LotteryDrawer.fillSpots(drawn(winners = 2, free = 1), list, now).entries.isEmpty())
    }

    @Test fun fillSpots_withNobodyLeftToDraw_promotesNobody() {
        val list = listOf(entry(1, EntryStatus.WON), entry(2, EntryStatus.DECLINED))
        assertTrue(LotteryDrawer.fillSpots(drawn(winners = 2, free = 1), list, now).entries.isEmpty())
    }

    @Test fun fillSpots_neverPromotesPeopleStillWaitingOrWhoDeclined() {
        val list = listOf(entry(1, EntryStatus.WAITING), entry(2, EntryStatus.DECLINED), entry(3, EntryStatus.LOST))
        val result = LotteryDrawer.fillSpots(drawn(winners = 3, free = 3), list, now)
        assertEquals(setOf("entrant-3"), result.winnerIds)
    }

    // ---- Notices ----

    @Test fun rejectedNotice_givesTheReason() {
        val n = LotteryDrawer.rejectedNotice(TestData.lottery(), "entrant-1", "Site closed for repairs", now)
        assertEquals(NotificationType.REJECTED, n.type)
        assertEquals("entrant-1", n.recipientId)
        assertEquals("Reservation at Two Jack Lakeside rejected", n.title)
        assertTrue(n.body.contains("Site closed for repairs"))
        assertEquals("lot-1", n.lotteryId)
    }

    @Test fun rejectedNotice_withoutAReason_saysSo() {
        assertTrue(LotteryDrawer.rejectedNotice(TestData.lottery(), "entrant-1", "  ", now).body.contains("no reason given"))
    }

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

    // ---- Booked nights ----

    @Test fun stays_thatShareANight_overlap() {
        assertTrue(BookingPolicy.overlaps(10, 13, 12, 15))
        assertTrue("one inside the other", BookingPolicy.overlaps(10, 20, 12, 14))
    }

    @Test fun checkingOut_onTheDaySomeoneChecksIn_isNotAnOverlap() {
        assertFalse(BookingPolicy.overlaps(10, 13, 13, 15))
        assertFalse(BookingPolicy.overlaps(13, 15, 10, 13))
    }

    @Test fun takenNights_areEveryBookedNight_butNotCheckOutDays() {
        val a = entry(1, EntryStatus.WON).copy(checkInDay = 100, checkOutDay = 102)
        val b = entry(2, EntryStatus.WON).copy(checkInDay = 105, checkOutDay = 106)
        val notBooked = entry(3, EntryStatus.WON)
        val lost = entry(4, EntryStatus.LOST).copy(checkInDay = 110, checkOutDay = 112)
        assertEquals(setOf(100L, 101L, 105L), BookingPolicy.takenNights(listOf(a, b, notBooked, lost)))
    }

    @Test fun takenNights_withNoBookings_isEmpty() {
        assertTrue(BookingPolicy.takenNights(emptyList()).isEmpty())
    }

    // ---- Editing a published lottery ----

    private val open = TestData.lottery(start = now - TestData.DAY, end = now + 3 * TestData.DAY).copy(entrantCount = 4)

    @Test fun editing_aDrawnLottery_isRefused() {
        val drawn = open.copy(status = LotteryStatus.DRAWN)
        assertEquals("Winners have been drawn, so this lottery can no longer be changed.", LotteryEditPolicy.check(drawn, drawn.copy(winnerCount = 3), now))
    }

    @Test fun editing_canMoveTheCloseLater_andChangeWinners() {
        assertNull(LotteryEditPolicy.check(open, open.copy(registrationEnd = open.registrationEnd + TestData.DAY, winnerCount = 5), now))
    }

    @Test fun editing_cantMoveToAnotherCampsite() {
        assertEquals("A lottery can't be moved to another campsite.", LotteryEditPolicy.check(open, open.copy(campsiteId = "elsewhere"), now))
    }

    @Test fun editing_cantChangeTheStartOnceOpen() {
        assertEquals("Registration has already opened, so its start can't change.", LotteryEditPolicy.check(open, open.copy(registrationStart = now), now))
    }

    @Test fun editing_beforeItOpens_canMoveTheStart() {
        val later = TestData.lottery(start = now + TestData.DAY, end = now + 4 * TestData.DAY)
        assertNull(LotteryEditPolicy.check(later, later.copy(registrationStart = now + 2 * TestData.DAY), now))
    }

    @Test fun editing_theCloseMustStayInTheFuture_andAfterTheStart() {
        assertEquals("The closing time must be in the future.", LotteryEditPolicy.check(open, open.copy(registrationEnd = now), now))
        val later = TestData.lottery(start = now + 2 * TestData.DAY, end = now + 4 * TestData.DAY)
        assertEquals("Closing must be after opening.", LotteryEditPolicy.check(later, later.copy(registrationEnd = now + TestData.DAY), now))
    }

    @Test fun editing_needsAtLeastOneWinner() {
        assertEquals("Draw at least 1 winner.", LotteryEditPolicy.check(open, open.copy(winnerCount = 0), now))
    }

    @Test fun editing_cantCapBelowThePeopleAlreadyWaiting() {
        assertEquals("The cap can't be below the 4 people already on the waitlist.", LotteryEditPolicy.check(open, open.copy(waitlistCap = 3), now))
        assertNull(LotteryEditPolicy.check(open, open.copy(waitlistCap = 4), now))
    }

    // ---- Search ----

    @Test fun searchableText_coversNameLocationAmenitiesDescriptionAndTags_inLowerCase() {
        val text = CampsiteSearch.searchableText(TestData.campsite(tags = listOf("Lakeside", "Quiet")))
        listOf("two jack lakeside", "banff, ab", "fire pits", "mount rundle", "quiet").forEach { assertTrue(it, text.contains(it)) }
        assertEquals(text.lowercase(), text)
    }
}
