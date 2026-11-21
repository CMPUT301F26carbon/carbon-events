/*
 * TrustedDevicesTest.kt (Tausif)
 *
 * Purpose: Compose UI (intent) tests for TrustedDevices.kt: the list, removing a phone and trusting this
 * one again (US 01.09.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write UI (intent) tests for Trusted devices."
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
class TrustedDevicesTest {
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

    // ---- Trusted devices ----

    private fun openDevices() {
        compose.onNodeWithTag("profile_devices").performScrollTo()
        tap("profile_devices")
        waitFor("trusted_phones")
    }

    @Test fun trustedDevices_listsThisPhone() {
        launch()
        openDevices()
        compose.onNodeWithTag("device_$phone").assertExists()
        compose.onNodeWithText("This phone", substring = true).assertExists()
        compose.onNodeWithTag("device_trust_here").assertDoesNotExist()
    }

    @Test fun removingThisPhone_saysSo_andItCanBeTrustedAgain() {
        launch()
        openDevices()
        tap("device_remove_$phone")
        confirm("Remove")
        waitFor("device_trust_here")
        compose.onNodeWithTag("device_message").assertTextEquals("This phone is no longer trusted. You'll sign in next time.")
        assertNull(store.snapshot.devices[phone])

        tap("device_trust_here")
        waitFor("device_$phone")
        compose.onNodeWithTag("device_message").assertTextEquals("This phone is trusted. You'll stay signed in.")
        assertEquals(adam.id, store.snapshot.devices.getValue(phone).userId)
    }

    @Test fun removingAnotherPhone_saysItWillBeSignedOut() {
        launch(seed = { store.trustDevice(TrustedDevice("tablet", phone, "Galaxy Tab", NOW)) })
        openDevices()
        tap("device_remove_tablet")
        confirm("Remove")
        waitFor("device_message")
        compose.onNodeWithTag("device_message").assertTextEquals("Galaxy Tab removed. It will be signed out next time it opens.")
        assertNull(store.snapshot.devices["tablet"])
        compose.onNodeWithTag("device_$phone").assertExists()
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
