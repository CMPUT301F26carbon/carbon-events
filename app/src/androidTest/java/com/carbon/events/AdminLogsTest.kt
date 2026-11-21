/*
 * AdminLogsTest.kt (Aditya, Tausif)
 *
 * Purpose: Compose UI (intent) tests for the admin console's Logs tab: the log of every
 * notification sent (US 03.08.01) and the read-only log of messages between owners and the
 * guests who reserved their campsites (US 03.12.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for the admin reviewing notification logs and owner message logs."
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

/** The admin console's Logs tab after an owner notified a waitlist and messaged a guest. */
@RunWith(AndroidJUnit4::class)
class AdminLogsTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 }).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW))
    private val admin = "admin-phone"

    private fun launchAdmin(tab: String, screen: String, seed: suspend () -> Unit = {}) {
        runBlocking {
            store.saveProfile(SampleData.owner(NOW).copy(activeRole = Role.OWNER))
            store.saveProfile(UserProfile(admin, "Alex Admin", "admin@carbon.ca", activeRole = Role.ADMIN, isAdmin = true))
            store.linkDevice(admin, admin)
            seed()
        }
        val c = AppContainer.inMemory(admin, clock, store)
        compose.setContent {
            EventsTheme {
                EventsApp(c, showSplash = false, splash = {}, setup = {}, alerts = {},
                    entrant = {}, owner = {},
                    admin = { LocalAppSession.current.let { AdminShell(it.container, it.requireProfile(), it.profileVm) } },
                )
            }
        }
        waitFor("admin_campsites")
        if (tab != "Campsites") compose.onNodeWithTag("nav_$tab").performClick()
        waitFor(screen)
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun tap(tag: String) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) }
    private fun tapText(text: String) { compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick) }

    private val maya = UserProfile("sample-entrant-0", "Maya Chen", "maya.chen@example.com")
    private val chat = Chat(Chat.idFor("johnston-canyon", maya.id), "johnston-canyon", "Johnston Canyon", SampleData.OWNER_ID, "Parks Lodge", maya.id, "Maya Chen")

    private fun launch(seed: suspend () -> Unit = {}) = launchAdmin("Logs", "admin_logs") {
        store.saveProfile(maya)
        store.sendAnnouncement(store.snapshot.lotteries.getValue("lot-two-jack"), Audience.ALL, "Gates open at 2 PM on check-in day.", NOW - 3_600_000)
        store.sendMessage(chat, SampleData.OWNER_ID, "Parks Lodge", "Your site is number 14.", NOW - 1_800_000)
        store.sendMessage(chat, maya.id, "Maya Chen", "Thanks, see you Friday.", NOW - 900_000)
        seed()
    }

    @Test fun notificationsLog_listsWhatWasSent_whoSentIt_andToHowMany() {
        launch()
        compose.onAllNodesWithTag("log_row").assertCountEquals(1) // one line per send, however many people got it
        compose.onNodeWithText("Gates open at 2 PM on check-in day.").assertExists()
        compose.onNodeWithText("Parks Lodge → 6 people · Two Jack Lakeside", substring = true).assertExists()
    }

    @Test fun notificationsLog_hidesOldEntries_untilThePeriodIsWidened() {
        launch { store.warn(maya, "Please keep messages polite.", NOW - 10L * 86_400_000) }
        compose.onNodeWithText("Admin → Maya Chen", substring = true).assertDoesNotExist()
        tap("logs_days") // last 14 days
        compose.onNodeWithText("Admin → Maya Chen", substring = true).assertExists()
    }

    @Test fun notificationsLog_listsLotteryResultsFromTheSystem() {
        launch { store.runDrawIfDue("lot-lake-louise", NOW + 5 * 60_000) }
        compose.onNodeWithText("Lottery results").assertExists()
        compose.onNodeWithText("System → ", substring = true).assertExists()
    }

    @Test fun notificationsLog_canBeNarrowedToOneOwner() {
        launch {
            store.createCampsite(Campsite("elk-meadow", "owner-2", "Rocky Ridge Camping", "Elk Meadow", "Jasper, AB", maxOccupancy = 4, createdAt = NOW))
        }
        tap("logs_owner")
        tapText("Rocky Ridge Camping")
        compose.onNodeWithText("Nothing sent").assertExists()
        compose.onNodeWithText("Gates open at 2 PM on check-in day.").assertDoesNotExist()
    }

    @Test fun notificationsLog_withNothingSent_saysSo() {
        launchAdmin("Logs", "admin_logs")
        compose.onNodeWithText("Nothing sent").assertExists()
    }

    @Test fun messagesLog_listsEachConversationWithBothNames() {
        launch()
        tap("logs_tab_1")
        compose.onNodeWithTag("log_chat_${chat.id}").assertExists()
        compose.onNodeWithText("Thanks, see you Friday.").assertExists()
        compose.onNode(hasText("Parks Lodge ↔ Maya Chen", substring = true) and hasAnyAncestor(hasTestTag("log_chat_${chat.id}")), useUnmergedTree = true).assertExists()
    }

    @Test fun messagesLog_opensTheConversation_readOnly() {
        launch()
        tap("logs_tab_1")
        tap("log_chat_${chat.id}")
        compose.onNodeWithText("Johnston Canyon · read-only").assertExists()
        compose.onNodeWithText("Your site is number 14.").assertExists()
        compose.onNodeWithText("Thanks, see you Friday.").assertExists()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0) // nowhere to type a reply
    }

    @Test fun messagesLog_withNoConversations_saysSo() {
        launchAdmin("Logs", "admin_logs")
        tap("logs_tab_1")
        compose.onNodeWithText("No messages").assertExists()
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
