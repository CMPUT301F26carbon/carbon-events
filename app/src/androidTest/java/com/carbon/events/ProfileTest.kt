/*
 * ProfileTest.kt (Tausif)
 *
 * Purpose: Compose UI (intent) tests for Profile.kt: turning notifications off (US 01.04.03), "Log out of
 * all devices", and the password Delete my profile asks for when Firebase wants a fresh sign-in
 * (US 01.08.01), and the note while a new sign-in email waits for its link (US 01.07.03).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write UI (intent) tests for the profile page."
 * Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import android.Manifest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Adam Kent on his trusted phone, in the entrant app. */
@RunWith(AndroidJUnit4::class)
class ProfileTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 },
        ).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW))
    private val phone = "adam-phone"
    private val adam = UserProfile(phone, "Adam Kent", "adam@example.com", age = 30, createdAt = NOW)

    private fun launch(profiles: ProfileRepository = store, auth: AuthRepository = InMemoryAuth(phone), seed: suspend () -> Unit = {}) {
        runBlocking {
            store.saveProfile(adam)
            store.trustDevice(TrustedDevice(phone, adam.id, "Pixel 8", NOW))
            seed()
        }
        val c = AppContainer(phone, "Pixel 8", profiles, store, store, store, clock, usingServer = false, auth = auth, social = store)
        compose.setContent {
            EventsTheme {
                EventsApp(c, showSplash = false, splash = {}, setup = {}, alerts = {},
                    entrant = { LocalAppSession.current.let { EntrantShell(it.container, it.requireProfile(), it.profileVm, null, {}) } },
                    owner = {}, admin = {},
                )
            }
        }
        waitFor("explore")
        compose.onNodeWithTag("nav_Profile").performClick()
        waitFor("profile_name")
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun tap(tag: String) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) }

    /** Taps [text] on the dialog that is open, not the button that opened it. */
    private fun confirm(text: String) { compose.onNode(hasText(text) and hasAnyAncestor(isDialog())).performSemanticsAction(SemanticsActions.OnClick) }
    private fun field(tag: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)) or (hasSetTextAction() and hasTestTag(tag)))

    // ---- Notifications ----

    @Test fun notificationsSwitch_turnsEveryNotificationOff_andOnAgain() {
        launch()
        tap("profile_notifications_switch")
        compose.waitUntil(5_000) { store.snapshot.profiles.getValue(phone).prefs.optOut }
        tap("profile_notifications_switch")
        compose.waitUntil(5_000) { !store.snapshot.profiles.getValue(phone).prefs.optOut }
    }

    // ---- Log out of all devices ----

    @Test fun logOutOfAllDevices_thatCantReachTheServer_saysWhy_andKeepsYouIn() {
        val offline = object : ProfileRepository by store {
            override suspend fun removeAllDevices(userId: String) = throw AuthException(AuthError.NETWORK)
        }
        launch(profiles = offline)
        compose.onNodeWithTag("profile_sign_out_all").performScrollTo()
        tap("profile_sign_out_all")
        confirm("Log out all")
        waitFor("profile_sign_out_all_error")
        compose.onNodeWithTag("profile_sign_out_all_error").assertTextEquals(AuthError.NETWORK.message)
        compose.onNodeWithTag("profile_name").assertExists()
        assertEquals(adam.id, store.snapshot.devices.getValue(phone).userId)
    }

    @Test fun logOutOfAllDevices_signsYouOut_andNoPhoneStaysTrusted() {
        launch(seed = { store.trustDevice(TrustedDevice("tablet", phone, "Galaxy Tab", NOW)) })
        compose.onNodeWithTag("profile_sign_out_all").performScrollTo()
        tap("profile_sign_out_all")
        confirm("Log out all")
        compose.waitUntil(5_000) { store.snapshot.devices.values.none { it.userId == phone } }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("profile_name")).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
    }

    // ---- Delete my profile ----

    @Test fun deletingAfterAnOldSignIn_asksForThePassword() {
        val auth = InMemoryAuth(null)
        val email = "adam.email@example.com"
        launch(auth = auth, seed = {
            val account = auth.signUp(email, "campfire8")
            store.saveProfile(adam.copy(email = email, authUid = account.uid))
            auth.requireFreshSignIn()
        })
        compose.onNodeWithTag("profile_delete").performScrollTo()
        tap("profile_delete")
        waitFor("delete_dialog")
        compose.onNodeWithTag("delete_password").assertExists()
        field("delete_confirm_text").performTextInput(email)
        compose.onNodeWithTag("delete_profile").assertIsNotEnabled()
        field("delete_password").performTextInput("campfire8")
        compose.onNodeWithTag("delete_profile").assertIsEnabled()
        tap("delete_profile")
        compose.waitUntil(5_000) { store.snapshot.profiles[phone] == null }
        assertTrue("the login is gone too", runCatching { runBlocking { auth.signIn(email, "campfire8") } }.isFailure)
    }

    @Test fun deletingRightAfterSigningIn_needsNoPassword() {
        launch()
        compose.onNodeWithTag("profile_delete").performScrollTo()
        tap("profile_delete")
        waitFor("delete_dialog")
        compose.onNodeWithTag("delete_password").assertDoesNotExist()
    }

    // ---- Changing the sign-in email ----

    @Test fun aNewEmail_showsANoteUntilItsLinkIsOpened() {
        val auth = InMemoryAuth()
        launch(auth = auth) { store.saveProfile(adam.copy(authUid = auth.signUp("adam@example.com", "campfire8").uid)) }
        tap("profile_edit")
        waitFor("profile_email")
        field("profile_email").performTextClearance()
        field("profile_email").performTextInput("adam.kent@example.com")
        tap("profile_save")
        waitFor("profile_email_pending")
        compose.onNodeWithText("adam.kent@example.com", substring = true).assertExists()
        assertEquals("adam@example.com", store.snapshot.profiles.getValue(phone).email)
        auth.openEmailChangeLink()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("profile_email_pending").fetchSemanticsNodes().isEmpty() }
        assertEquals("adam.kent@example.com", store.snapshot.profiles.getValue(phone).email)
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
