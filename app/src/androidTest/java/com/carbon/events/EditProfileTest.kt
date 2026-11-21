/*
 * EditProfileTest.kt (Tausif)
 *
 * Purpose: Compose UI (intent) tests for EditProfile.kt: saving shows "Profile updated", and the email
 * and photo are there (US 01.07.03).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write UI (intent) tests for Edit profile."
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
class EditProfileTest {
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

    private fun field(tag: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)) or (hasSetTextAction() and hasTestTag(tag)))

    // ---- Edit profile ----

    @Test fun savingAnEdit_showsProfileUpdated() {
        launch()
        tap("profile_edit")
        waitFor("profile_name")
        field("profile_name").performTextClearance()
        field("profile_name").performTextInput("Adam K.")
        tap("profile_save")
        waitFor("profile_saved")
        compose.onNodeWithText("Profile updated").assertExists()
        assertEquals("Adam K.", store.snapshot.profiles.getValue(phone).name)
    }

    @Test fun editProfile_showsTheEmail_andAPhotoButton() {
        launch()
        tap("profile_edit")
        waitFor("profile_email")
        field("profile_email").assertTextContains("adam@example.com")
        compose.onNodeWithTag("profile_photo").assertExists()
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
