/*
 * DeleteProfileTest.kt (Rayner)
 *
 * Purpose: Compose UI (intent) tests for deleting your own profile: Profile > Delete my profile
 * asks you to type your email, then removes the profile, signs the phone out and takes you off
 * the waiting lists you were on (US 01.08.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for an entrant deleting their profile."
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

/** Maya, who is on two open waiting lists in the sample data, deleting her profile. */
@RunWith(AndroidJUnit4::class)
class DeleteProfileTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 },
        ).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW))

    /** Signs [profile] in on this phone and opens the whole app, whichever role it is using. */
    private fun launchApp(profile: UserProfile, firstScreen: String, seed: suspend () -> Unit = {}) {
        runBlocking {
            store.saveProfile(profile)
            store.linkDevice(profile.id, profile.id)
            seed()
        }
        val c = AppContainer.inMemory(profile.id, clock, store)
        compose.setContent {
            EventsTheme {
                EventsApp(c, showSplash = false, splash = {},
                    setup = { SetupScreen(LocalAppSession.current.profileVm) }, alerts = {},
                    entrant = { LocalAppSession.current.let { EntrantShell(it.container, it.requireProfile(), it.profileVm, null, {}) } },
                    owner = { LocalAppSession.current.let { OwnerShell(it.container, it.requireProfile(), it.profileVm) } },
                    admin = { LocalAppSession.current.let { AdminShell(it.container, it.requireProfile(), it.profileVm) } },
                )
            }
        }
        waitFor(firstScreen)
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun tap(tag: String) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) }
    private fun tapText(text: String) { compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick) }

    private val maya = UserProfile("sample-entrant-0", "Maya Chen", "maya.chen@example.com")

    private fun openDeleteDialog() {
        launchApp(maya, "explore")
        compose.onNodeWithTag("nav_Profile").performClick()
        waitFor("profile_delete")
        tap("profile_delete")
        waitFor("delete_dialog")
    }

    private fun type(text: String) = compose.onNode(hasSetTextAction() and (hasTestTag("delete_confirm_text") or hasAnyAncestor(hasTestTag("delete_confirm_text")))).performTextInput(text)

    @Test fun deleteMyProfile_asksYouToTypeYourEmailFirst() {
        openDeleteDialog()
        compose.onNodeWithText("Delete Your Profile?").assertExists()
        compose.onNodeWithTag("delete_profile").assertIsNotEnabled()
        assertTrue(store.snapshot.profiles.containsKey(maya.id))
    }

    @Test fun typingTheWrongEmail_keepsDeleteDisabled() {
        openDeleteDialog()
        type("someone.else@example.com")
        compose.onNodeWithTag("delete_profile").assertIsNotEnabled()
    }

    @Test fun cancel_keepsTheProfile() {
        openDeleteDialog()
        type(maya.email)
        tapText("Cancel")
        compose.onNodeWithTag("delete_dialog").assertDoesNotExist()
        assertTrue(store.snapshot.profiles.containsKey(maya.id))
    }

    @Test fun confirming_deletesTheProfile_andGoesBackToWelcome() {
        openDeleteDialog()
        type(maya.email)
        compose.onNodeWithTag("delete_profile").assertIsEnabled()
        tap("delete_profile")
        compose.waitUntil(10_000) { !store.snapshot.profiles.containsKey(maya.id) }
        waitFor("welcome")
        assertTrue("this phone is no longer trusted for anyone", store.snapshot.devices.values.none { it.userId == maya.id })
    }

    @Test fun confirming_takesYouOffOpenWaitingLists() {
        val before = store.snapshot.lotteries.getValue("lot-two-jack").entrantCount
        assertTrue(store.snapshot.entries.any { it.lotteryId == "lot-two-jack" && it.entrantId == maya.id })
        openDeleteDialog()
        type(maya.email)
        tap("delete_profile")
        compose.waitUntil(10_000) { !store.snapshot.profiles.containsKey(maya.id) }
        assertTrue(store.snapshot.entries.none { it.lotteryId == "lot-two-jack" && it.entrantId == maya.id })
        assertEquals(before - 1, store.snapshot.lotteries.getValue("lot-two-jack").entrantCount)
        assertTrue("other people stay on the list", store.snapshot.entries.any { it.lotteryId == "lot-two-jack" })
    }

    @Test fun phoneOnlyProfile_typesDeleteInstead() {
        launchApp(UserProfile("phone-only", "Noah Singh", ""), "explore")
        compose.onNodeWithTag("nav_Profile").performClick()
        waitFor("profile_delete")
        tap("profile_delete")
        waitFor("delete_dialog")
        type("delete")
        tap("delete_profile")
        compose.waitUntil(10_000) { !store.snapshot.profiles.containsKey("phone-only") }
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
