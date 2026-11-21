/*
 * CancelErrorMessageTest.kt (Vaibhav)
 *
 * Purpose: Unit tests for the message a guest gets when cancelling doesn't go through: a booking
 * that changed in the meantime gets its own wording, and other failures keep the app's usual
 * message (US 01.07.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 18, 2026). Prompt: "Write
 * tests for showing an error when cancelling a reservation fails." Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import org.junit.Assert.assertEquals
import org.junit.Test

class CancelErrorMessageTest {
    @Test fun aBookingThatIsNoLongerWon_saysItChanged() {
        assertEquals(CANCEL_CHANGED_MESSAGE, BookingException(BookingError.NOT_A_WINNER).cancelMessage())
    }

    @Test fun aWrappedChange_saysItChangedToo() {
        assertEquals(CANCEL_CHANGED_MESSAGE, RuntimeException("transaction failed", BookingException(BookingError.NOT_A_WINNER)).cancelMessage())
    }

    @Test fun otherFailures_keepTheUsualMessage() {
        val e = IllegalStateException("boom")
        assertEquals(e.userMessage(), e.cancelMessage())
    }
}
