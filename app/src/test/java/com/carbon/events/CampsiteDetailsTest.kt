/*
 * CampsiteDetailsTest.kt (Ansh, Vaibhav)
 *
 * Purpose: Unit tests for CampsiteDetails.kt: which lottery button the details screen shows (US 01.01.05, US 01.01.06, US 01.02.01, US 01.02.02) and promo links (US 01.12.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 6, 2026). Prompt: "Write
 * a test file for each source file that has none." Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CampsiteDetailsTest {
    private val now = 1_792_162_800_000L
    private val day = 86_400_000L
    private val open = SampleData.snapshot(now).lotteries.getValue("lot-two-jack")
    private fun entry(status: EntryStatus) = WaitlistEntry(open.id, "maya-chen-x9k2", "Maya Chen", now, status)

    @Test fun noLottery_showsNothingToJoin() = assertEquals(LotteryAction.NONE, lotteryAction(null, null, now))

    @Test fun openLottery_canBeJoinedThenLeft() {
        assertEquals(LotteryAction.CAN_JOIN, lotteryAction(open, null, now))
        assertEquals(LotteryAction.JOINED, lotteryAction(open, entry(EntryStatus.WAITING), now))
    }

    @Test fun upcomingClosedAndFullLotteries() {
        assertEquals(LotteryAction.UPCOMING, lotteryAction(open.copy(registrationStart = now + day), null, now))
        assertEquals(LotteryAction.CLOSED, lotteryAction(open.copy(registrationEnd = now - 1), null, now))
        assertEquals(LotteryAction.FULL, lotteryAction(open.copy(waitlistCap = open.entrantCount), null, now))
    }

    @Test fun drawnLottery_showsTheEntrantsResult() {
        val drawn = open.copy(status = LotteryStatus.DRAWN)
        assertEquals(LotteryAction.WON, lotteryAction(drawn, entry(EntryStatus.WON), now))
        assertEquals(LotteryAction.LOST, lotteryAction(drawn, entry(EntryStatus.LOST), now))
        assertEquals(LotteryAction.GAVE_UP, lotteryAction(drawn, entry(EntryStatus.CANCELLED), now))
        assertEquals(LotteryAction.ENDED, lotteryAction(drawn, null, now))
    }

    @Test fun spots_isSingularForOne() {
        assertEquals("1 spot", spots(1))
        assertEquals("8 spots", spots(8))
    }

    @Test fun promoLink_opensItsCampsiteOnce() {
        PromoLinks.handle(Intent(Intent.ACTION_VIEW, Uri.parse(QrCodes.payloadFor("two-jack"))))
        assertEquals("two-jack", PromoLinks.pending)
        PromoLinks.consume()
        assertNull(PromoLinks.pending)
        PromoLinks.handle(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/not-ours")))
        assertNull(PromoLinks.pending)
    }
}
