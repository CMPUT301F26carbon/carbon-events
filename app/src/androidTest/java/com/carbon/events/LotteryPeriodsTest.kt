/*
 * LotteryPeriodsTest.kt (Vaibhav)
 *
 * Purpose: Compose UI (intent) tests for setting a lottery's registration period (US 02.04.03),
 * booking period (US 02.04.04) and number of winners (US 02.04.05), and for viewing the
 * currently booked dates of a campsite (US 02.06.01).
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The sample owner, Parks Lodge, setting up a new lottery for Tunnel Mountain Village. */
@RunWith(AndroidJUnit4::class)
class LotteryPeriodsTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 }).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW))
    private val owner = SampleData.OWNER_ID

    private fun launch() {
        runBlocking {
            store.saveProfile(SampleData.owner(NOW).copy(activeRole = Role.OWNER))
            store.linkDevice(owner, owner)
        }
        val c = AppContainer.inMemory(owner, clock, store)
        compose.setContent {
            EventsTheme {
                EventsApp(c, showSplash = false, splash = {}, setup = {}, alerts = {},
                    entrant = {},
                    owner = { LocalAppSession.current.let { OwnerShell(it.container, it.requireProfile(), it.profileVm) } },
                    admin = {},
                )
            }
        }
        waitFor("owner_campsites")
    }

    /** Taps a chip; these sit in sideways-scrolling rows, so tap it by its click action. */
    private fun choose(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    /** Opens New Lottery for Tunnel Mountain Village, which has no lottery yet. */
    private fun startNewLottery() {
        compose.onNodeWithTag("nav_Lotteries").performClick()
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasTestTag("owner_new_lottery"))
        compose.onNodeWithTag("owner_new_lottery").performClick()
        choose("lottery_site_tunnel-mountain")
    }

    /** Publishes the form and returns the saved lottery. */
    private fun publish(): Lottery {
        compose.onNodeWithTag("lottery_publish").performClick()
        compose.waitUntil(10_000) { store.snapshot.lotteries.values.any { it.campsiteId == "tunnel-mountain" } }
        return store.snapshot.lotteries.values.single { it.campsiteId == "tunnel-mountain" }
    }

    /** US 02.04.03: the waitlist opens and closes at the times the owner picks. */
    @Test fun owner_setsTheRegistrationPeriod() {
        launch()
        startNewLottery()
        choose("preset_start_Tomorrow")
        choose("preset_end_In 2 weeks")
        choose("preset_booking_1 week")
        val lot = publish()
        assertEquals(NOW + DAY, lot.registrationStart)
        assertEquals(NOW + 14 * DAY, lot.registrationEnd)
        assertTrue(lot.isUpcoming(NOW))
    }

    /** US 02.04.04: the owner sets the nights winners can book, starting after the draw. */
    @Test fun owner_setsTheBookingPeriod() {
        launch()
        startNewLottery()
        choose("preset_start_Now")
        choose("preset_end_In 3 days")
        choose("preset_booking_2 weeks")
        val lot = publish()
        val drawDay = dayOf(NOW + 3 * DAY)
        assertEquals(drawDay + 1, lot.bookingStartDay)
        assertEquals(drawDay + 1 + 14, lot.bookingEndDay)
        assertTrue(lot.hasBookingPeriod)
    }

    /** US 02.04.05: the owner sets how many winners are drawn. */
    @Test fun owner_setsTheNumberOfWinners() {
        launch()
        startNewLottery()
        choose("preset_start_Now")
        choose("preset_end_In 1 week")
        choose("preset_booking_1 week")
        val increase = compose.onNodeWithContentDescription("Increase Number of winners").performScrollTo()
        increase.performClick()
        increase.performClick()
        compose.onNodeWithContentDescription("Decrease Number of winners").performClick()
        val lot = publish()
        assertEquals(5, lot.winnerCount)
    }

    /** US 02.06.01: the owner sees the dates already booked at a campsite. */
    @Test fun owner_viewsTheCurrentlyBookedDates() {
        launch()
        compose.onNodeWithTag("owner_campsites").performScrollToNode(hasText("Johnston Canyon"))
        compose.onNodeWithText("Johnston Canyon").performClick()
        waitFor("manage_name")
        compose.waitForIdle()
        compose.onNodeWithTag("manage_bookings").performScrollTo().performClick()
        waitFor("booked_dates")
        compose.onNodeWithTag("booked_dates").assertIsDisplayed()
        compose.onNode(hasText("Booked dates", substring = true)).assertIsDisplayed()
        // The sample's active stay (Liam Okafor) covers yesterday to two days from now.
        compose.onNode(hasText("of 14 nights", substring = true)).assertTextContains("2 of 14 nights")
        compose.onNodeWithText("Liam Okafor").assertExists()
    }

    private companion object {
        /** Fixed time shared with the realistic fixture. */
        const val NOW = 1_792_162_800_000L
        const val DAY = 24 * 60 * 60_000L
    }
}
