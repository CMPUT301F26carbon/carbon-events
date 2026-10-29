/*
 * AppTestHarness.kt
 *
 * Purpose: shared setup for the intent (UI) tests. Each test launches the real EventsApp
 * on an in-memory store seeded with the sample campsites, with a fixed clock and the
 * camera / notification permissions pre-granted, so tests are fast and repeatable and
 * never touch the real Firestore project.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule

/** Base class for intent tests. */
abstract class AppTestHarness {
    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = if (Build.VERSION.SDK_INT >= 33) {
        GrantPermissionRule.grant(Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant(Manifest.permission.CAMERA)
    }

    @get:Rule(order = 1)
    val compose: ComposeContentTestRule = createComposeRule()

    /** Fixed "now" for every test (Oct 16, 2026 09:00 MDT). */
    protected val clock = FixedClock(1_792_162_800_000L)

    /** The test's in-memory database, seeded with the sample campsites and lotteries. */
    protected val store = InMemoryEventsStore(SampleData.snapshot(clock.now()))

    /** Device ID the app runs as. */
    protected var deviceId = "device-test"

    /** The container the app was launched with. */
    protected lateinit var container: AppContainer

    /** Saves a profile for [deviceId] so the app skips first-launch setup. */
    protected fun givenProfile(role: Role = Role.ENTRANT, isAdmin: Boolean = false, name: String = "Adam Kent") = runBlocking {
        store.saveProfile(UserProfile(deviceId, name, "adam@example.com", activeRole = role, isAdmin = isAdmin))
    }

    /** Launches the app. */
    protected fun launch(deepLinkCampsiteId: String? = null) {
        container = AppContainer.inMemory(deviceId, clock, store)
        compose.setContent {
            EventsTheme {
                EventsApp(
                    container, deepLinkCampsiteId = deepLinkCampsiteId, showSplash = false,
                    splash = {},
                    setup = { SetupScreen(LocalAppSession.current.profileVm) },
                    alerts = {},
                    entrant = {
                        val s = LocalAppSession.current
                        EntrantShell(s.container, s.requireProfile(), s.profileVm, s.deepLinkCampsiteId, s.onDeepLinkConsumed)
                    },
                    owner = { LocalAppSession.current.let { OwnerShell(it.container, it.requireProfile(), it.profileVm) } },
                    admin = { LocalAppSession.current.let { AdminShell(it.container, it.requireProfile(), it.profileVm) } },
                )
            }
        }
        compose.waitForIdle()
    }

    /** The editable text field inside the element tagged [tag]. */
    protected fun field(tag: String): SemanticsNodeInteraction =
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))
}
