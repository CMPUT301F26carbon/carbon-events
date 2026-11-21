/*
 * WinnerChoosesSiteTest.kt (Suvir)
 *
 * Purpose: Compose UI (intent) tests for one lottery over several campsites from the entrant's
 * side: joining from one campsite enters them for all, and a winner of more than one site
 * chooses which to reserve and its dates while the others go back (US 01.04.02).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for an entrant who wins a multiple campsite lottery choosing a campsite and dates."
 * Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import android.Manifest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A lottery over Tunnel Mountain Village and Waterfowl Lakes. */
@RunWith(AndroidJUnit4::class)
class WinnerChoosesSiteTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 },
        ).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val me = UserProfile("test-phone", "Adam Kent", "adam@example.com")
    private val today = dayOf(NOW)
    private fun lot(id: String, site: String, name: String, status: LotteryStatus, regEnd: Long) =
        Lottery(id, site, name, SampleData.OWNER_ID, NOW - 10 * DAY, regEnd, 1, status = status, drawnAt = if (status == LotteryStatus.DRAWN) regEnd else null,
            bookingStartDay = today + 2, bookingEndDay = today + 16, groupId = "grp-test")

    /** Sample data plus a lottery over Tunnel Mountain and Waterfowl Lakes, open or already drawn. */
    private fun storeWith(status: LotteryStatus, entries: List<WaitlistEntry> = emptyList()) = InMemoryEventsStore(SampleData.snapshot(NOW).let { s ->
        val regEnd = if (status == LotteryStatus.DRAWN) NOW - DAY else NOW + 3 * DAY
        s.copy(
            lotteries = s.lotteries + listOf(lot(TUNNEL, "tunnel-mountain", "Tunnel Mountain Village", status, regEnd), lot(WATERFOWL, "waterfowl-lakes", "Waterfowl Lakes", status, regEnd)).associateBy { it.id },
            entries = s.entries + entries,
        )
    })
    private fun won(lotteryId: String, site: String, name: String) =
        WaitlistEntry(lotteryId, me.id, me.name, NOW - 5 * DAY, EntryStatus.WON, campsiteId = site, campsiteName = name, ownerId = SampleData.OWNER_ID)
    private var store = storeWith(LotteryStatus.OPEN)

    /** Signs [profile] in on this phone and opens the entrant app on Explore. */
    private fun launchEntrant(profile: UserProfile, seed: suspend () -> Unit = {}) {
        runBlocking {
            store.saveProfile(profile)
            store.linkDevice(profile.id, profile.id)
            seed()
        }
        val c = AppContainer.inMemory(profile.id, clock, store)
        compose.setContent {
            EventsTheme {
                EventsApp(c, showSplash = false, splash = {}, setup = {}, alerts = {},
                    entrant = { LocalAppSession.current.let { EntrantShell(it.container, it.requireProfile(), it.profileVm, null, {}) } },
                    owner = {}, admin = {},
                )
            }
        }
        waitFor("explore")
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun tap(tag: String) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) }
    private fun tapText(text: String) { compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick) }

    private fun entry(lotteryId: String) = store.snapshot.entries.firstOrNull { it.lotteryId == lotteryId && it.entrantId == me.id }

    private fun openDetails(search: String, site: String) {
        launchEntrant(me)
        compose.onNodeWithTag("explore_search").performClick()
        compose.onNodeWithTag("search_field").performTextInput(search)
        compose.onNodeWithTag("campsite_card_$site").performClick()
        waitFor("lottery_card")
    }

    private fun openBooking() {
        launchEntrant(me)
        compose.onNodeWithTag("nav_Lotteries").performClick()
        compose.onNodeWithText("Results", substring = true).performClick()
        waitFor("book_$TUNNEL")
        tap("book_$TUNNEL")
        waitFor("book_title")
    }

    private fun pick(d: Long) { compose.onNodeWithTag("day_$d").performSemanticsAction(SemanticsActions.OnClick) }

    @Test fun details_sayWhichOtherCampsitesTheLotteryCovers() {
        openDetails("tunnel", "tunnel-mountain")
        compose.onNodeWithTag("lottery_also_in").assertExists()
        compose.onNodeWithText("This lottery also covers Waterfowl Lakes", substring = true).assertExists()
    }

    @Test fun joiningOneCampsite_joinsTheWholeLottery() {
        openDetails("tunnel", "tunnel-mountain")
        tap("lottery_action")
        compose.waitUntil(10_000) { entry(TUNNEL) != null && entry(WATERFOWL) != null }
        assertEquals(1, store.snapshot.lotteries.getValue(WATERFOWL).entrantCount)
    }

    @Test fun leaving_leavesTheWholeLottery() {
        store = storeWith(LotteryStatus.OPEN)
        runBlocking { listOf(TUNNEL, WATERFOWL).forEach { store.joinWaitlist(it, me.id, me.name, NOW) } }
        openDetails("waterfowl", "waterfowl-lakes")
        tap("lottery_action") // Leave waitlist
        compose.onNode(hasText("Leave") and hasAnyAncestor(hasTestTag("confirm_dialog")) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { entry(TUNNEL) == null && entry(WATERFOWL) == null }
    }

    @Test fun winningTwoSites_asksTheWinnerToChooseOne() {
        store = storeWith(LotteryStatus.DRAWN, listOf(won(TUNNEL, "tunnel-mountain", "Tunnel Mountain Village"), won(WATERFOWL, "waterfowl-lakes", "Waterfowl Lakes")))
        openBooking()
        compose.onNodeWithTag("book_title").assertTextEquals("You Won 2 Sites!")
        compose.onNodeWithText("Choose your site").assertExists()
        compose.onNodeWithTag("site_choice_tunnel-mountain").assertExists()
        compose.onNodeWithTag("site_choice_waterfowl-lakes").assertExists()
    }

    @Test fun choosingTheOtherSite_showsThatSitesBooking() {
        store = storeWith(LotteryStatus.DRAWN, listOf(won(TUNNEL, "tunnel-mountain", "Tunnel Mountain Village"), won(WATERFOWL, "waterfowl-lakes", "Waterfowl Lakes")))
        openBooking()
        tap("site_choice_waterfowl-lakes")
        waitFor("stay_calendar")
        compose.onNodeWithText("Max 6 guests per site").assertDoesNotExist() // rules sheet stays closed
        tap("book_rules")
        compose.onNode(hasText("Waterfowl Lakes") and hasAnyAncestor(hasTestTag("rules_sheet")), useUnmergedTree = true).assertExists()
    }

    @Test fun bookingOneSite_givesTheOtherBack() {
        store = storeWith(LotteryStatus.DRAWN, listOf(won(TUNNEL, "tunnel-mountain", "Tunnel Mountain Village"), won(WATERFOWL, "waterfowl-lakes", "Waterfowl Lakes")))
        openBooking()
        tap("site_choice_waterfowl-lakes")
        waitFor("stay_calendar")
        pick(today + 3); pick(today + 5)
        tap("book_agree")
        tap("book_confirm")
        waitFor("book_done")
        compose.waitUntil(10_000) { entry(TUNNEL)?.status == EntryStatus.DECLINED }
        assertTrue(entry(WATERFOWL)!!.isBooked)
        assertEquals(today + 3, entry(WATERFOWL)!!.checkInDay)
        assertEquals(1, store.snapshot.lotteries.getValue(TUNNEL).spotsFree)
    }

    @Test fun winningJustOneSiteOfTheLottery_needsNoChoice() {
        store = storeWith(LotteryStatus.DRAWN, listOf(won(TUNNEL, "tunnel-mountain", "Tunnel Mountain Village")))
        openBooking()
        compose.onNodeWithTag("book_title").assertTextEquals("You Won Tunnel Mountain Village!")
        compose.onNodeWithTag("site_choices").assertDoesNotExist()
    }

    private companion object {
        const val NOW = 1_792_162_800_000L
        const val DAY = 86_400_000L
        const val TUNNEL = "lot-tunnel"
        const val WATERFOWL = "lot-waterfowl"
    }
}
