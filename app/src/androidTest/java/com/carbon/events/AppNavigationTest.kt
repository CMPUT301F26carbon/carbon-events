/*
 * AppNavigationTest.kt (Ansh)
 *
 * Purpose: Compose UI (intent) tests for the main flows: search (US 01.01.01), join and leave a
 * lottery (US 01.01.06, US 01.02.01, US 01.02.02), edit profile (US 01.07.03), register a
 * campsite (US 02.01.01), active / past reservations (US 02.07.01, US 02.07.02) and admin
 * campsite removal (US 03.01.01).
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

/** Real Compose navigation and interactions, isolated from the production Firebase project. */
@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    // These tests cover app navigation; native permission-dialog acceptance has separate scope.
    // Joining a lottery now asks to allow notifications (Android 13+), so grant that too, or the
    // system dialog covers the screen mid-test.
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(
            Manifest.permission.CAMERA, Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 },
        ).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(TestDataTime.NOW)
    private val store = InMemoryEventsStore(SampleData.snapshot(clock.now()))
    private val device = "test-phone"

    private fun launch(role: Role = Role.ENTRANT, id: String = device) {
        runBlocking {
            store.saveProfile(UserProfile(id, "Adam Kent", "adam@example.com", activeRole = role, isAdmin = role == Role.ADMIN))
            store.linkDevice(id, id)
        }
        val c = AppContainer.inMemory(id, clock, store)
        compose.setContent {
            EventsTheme {
                EventsApp(c, showSplash = false, splash = {}, setup = {}, alerts = {},
                    entrant = { LocalAppSession.current.let { EntrantShell(it.container, it.requireProfile(), it.profileVm, null, {}) } },
                    owner = { LocalAppSession.current.let { OwnerShell(it.container, it.requireProfile(), it.profileVm) } },
                    admin = { LocalAppSession.current.let { AdminShell(it.container, it.requireProfile(), it.profileVm) } },
                )
            }
        }
        compose.waitForIdle()
        // Startup collects an asynchronous profile flow. Idle rendering alone need not mean
        // that the role's first screen has been emitted yet.
        val start = when (role) { Role.ENTRANT -> "explore"; Role.OWNER -> "owner_campsites"; Role.ADMIN -> "admin_campsites" }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag(start)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun field(tag: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

    @Test fun search_navigatesAndShowsMatchingResults() {
        launch()
        compose.onNodeWithTag("explore_search").performClick()
        compose.onNodeWithTag("search_field").performTextInput("banff")
        compose.onNodeWithText("Two Jack Lakeside").assertIsDisplayed()
        compose.onNodeWithText("Tunnel Mountain Village").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("explore").assertIsDisplayed()
    }

    @Test fun entrant_joinsThenLeavesLotteryFromDetails() {
        launch()
        compose.onNodeWithTag("explore").performScrollToNode(hasTestTag("campsite_card_two-jack"))
        compose.onNodeWithTag("campsite_card_two-jack").performClick()
        compose.onNodeWithText("Join lottery").performClick()
        compose.onNodeWithText("Leave waitlist").assertIsDisplayed().performClick()
        compose.onNodeWithText("Leave").performClick()
        compose.onNodeWithText("Join lottery").assertIsDisplayed()
        assertFalse(store.snapshot.entries.any { it.entrantId == device })
    }

    @Test fun profile_editSavesContactAndReturnsToProfile() {
        launch()
        compose.onNodeWithTag("nav_Profile").performClick()
        compose.onNodeWithTag("profile_edit").performClick()
        field("profile_name").performTextClearance()
        field("profile_name").performTextInput("Adam K.")
        field("profile_phone").performScrollTo().performTextInput("+1 780 555 0142")
        compose.onNodeWithTag("profile_save").performClick()
        compose.onNodeWithText("Adam K.").assertIsDisplayed()
        assertEquals("+1 780 555 0142", store.snapshot.profiles.getValue(device).phone)
    }

    @Test fun owner_createsListingThroughTheForm() {
        launch(Role.OWNER)
        compose.onNodeWithTag("owner_add").performClick()
        field("form_name").performTextInput("Bow River Sites")
        field("form_location").performTextInput("Banff, AB")
        compose.onNodeWithTag("form_next").performClick()
        field("form_price").performTextInput("65")
        compose.onNodeWithTag("form_save").performClick()
        compose.onNodeWithTag("published_title").assertIsDisplayed()
        compose.onNodeWithTag("qr_code").assertIsDisplayed()
        val site = store.snapshot.campsites.values.single { it.name == "Bow River Sites" }
        assertEquals(device, site.ownerId)
        assertEquals("Banff, AB", site.location)
    }

    @Test fun owner_canSwitchBetweenActiveAndPastReservations() {
        launch(Role.OWNER, SampleData.OWNER_ID)
        compose.onNodeWithTag("nav_Bookings").performClick()
        compose.onNodeWithTag("owner_bookings").assertIsDisplayed()
        compose.onNodeWithTag("bookings_tab_1").performClick()
        compose.onNodeWithText("Maya Chen").assertExists()
        compose.onNodeWithTag("bookings_tab_0").performClick()
        compose.onNodeWithText("Liam Okafor").assertExists()
    }

    @Test fun admin_browsesAndConfirmsCampsiteRemoval() {
        launch(Role.ADMIN)
        compose.onNodeWithTag("campsite_card_two-jack").performClick()
        compose.onNodeWithTag("admin_remove").performClick()
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithTag("removed_banner").assertIsDisplayed()
        assertFalse(store.snapshot.campsites.containsKey("two-jack"))
    }

    /** Fixed time shared with the realistic fixture; kept independent of JVM-only helpers. */
    private object TestDataTime { const val NOW = 1_792_162_800_000L }
}
