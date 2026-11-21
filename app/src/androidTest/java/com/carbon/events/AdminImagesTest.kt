/*
 * AdminImagesTest.kt (Tausif)
 *
 * Purpose: Compose UI (intent) tests for the admin console's Images tab: every uploaded campsite
 * and profile photo is listed (US 03.06.01) and the admin can remove one (US 03.03.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for the admin browsing and removing uploaded images."
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

/** The admin console's Images tab, with two campsite photos and one profile photo uploaded. */
@RunWith(AndroidJUnit4::class)
class AdminImagesTest {
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

    /** A 1x1 PNG, as the app stores photos. */
    private val photo = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
    private val maya = UserProfile("sample-entrant-0", "Maya Chen", "maya.chen@example.com", photoBase64 = photo)

    private fun launch() = launchAdmin("Images", "admin_images") {
        listOf("two-jack", "lake-louise").forEach { id -> store.updateCampsite(store.snapshot.campsites.getValue(id).copy(imageBase64 = photo)) }
        store.saveProfile(maya)
    }

    @Test fun images_listEveryUploadedCampsitePhoto() {
        launch()
        compose.onNodeWithText("3 TOTAL", ignoreCase = true).assertExists()
        compose.onNodeWithText("Campsites · 2").assertExists()
        compose.onNodeWithTag("image_c-two-jack").assertExists()
        compose.onNodeWithTag("image_c-lake-louise").assertExists()
        compose.onNodeWithTag("image_c-moraine-lake").assertDoesNotExist() // no photo uploaded
    }

    @Test fun images_profilesTab_listsProfilePhotos() {
        launch()
        compose.onNodeWithText("Profiles · 1").assertExists()
        tap("images_tab_1")
        compose.onNodeWithTag("image_p-${maya.id}").assertExists()
        compose.onNodeWithTag("image_c-two-jack").assertDoesNotExist()
    }

    @Test fun tappingAnImage_showsWhoseItIs() {
        launch()
        tap("image_c-two-jack")
        compose.onNodeWithTag("image_dialog").assertExists()
        compose.onNode(hasText("Campsite · Parks Lodge") and hasAnyAncestor(hasTestTag("image_dialog")), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("image_remove").assertExists()
    }

    @Test fun keep_leavesTheImage() {
        launch()
        tap("image_c-two-jack")
        tapText("Keep")
        compose.onNodeWithTag("image_dialog").assertDoesNotExist()
        assertEquals(photo, store.snapshot.campsites.getValue("two-jack").imageBase64)
    }

    @Test fun removeImage_takesThePhotoOffTheCampsite_andKeepsTheCampsite() {
        launch()
        tap("image_c-two-jack")
        tap("image_remove")
        compose.waitUntil(10_000) { store.snapshot.campsites.getValue("two-jack").imageBase64 == null }
        compose.onNodeWithTag("image_c-two-jack").assertDoesNotExist()
        compose.onNodeWithTag("image_c-lake-louise").assertExists()
        assertEquals("Two Jack Lakeside", store.snapshot.campsites.getValue("two-jack").name)
    }

    @Test fun removeImage_takesThePhotoOffTheProfile_andKeepsTheProfile() {
        launch()
        tap("images_tab_1")
        tap("image_p-${maya.id}")
        tap("image_remove")
        compose.waitUntil(10_000) { store.snapshot.profiles.getValue(maya.id).photoBase64 == null }
        assertEquals("Maya Chen", store.snapshot.profiles.getValue(maya.id).name)
        compose.onNodeWithText("No images").assertExists()
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
