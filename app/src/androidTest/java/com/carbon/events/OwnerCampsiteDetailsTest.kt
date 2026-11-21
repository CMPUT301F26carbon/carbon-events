/*
 * OwnerCampsiteDetailsTest.kt (Aditya)
 *
 * Purpose: Compose UI (intent) tests for the details an owner gives a campsite in the form:
 * amenities, tags (suggested and their own), the map pin and what the form refuses
 * (US 02.01.02).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 16, 2026). Prompt: "Write
 * UI (intent) tests for the campsite details an owner enters in the campsite form." Reviewed,
 * edited and run by Team Carbon.
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

/** A new owner filling in the campsite form on an in-memory store. */
@RunWith(AndroidJUnit4::class)
class OwnerCampsiteDetailsTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 },
        ).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val store = InMemoryEventsStore()
    private val owner = "owner-phone"

    private fun launch() {
        runBlocking {
            store.saveProfile(UserProfile(owner, "Bow Valley Camping", "hello@bowvalley.ca", activeRole = Role.OWNER))
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
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("owner_campsites")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithTag("owner_add").performClick()
    }

    private fun field(tag: String) = compose.onNode(hasSetTextAction() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))))
    private fun tap(tag: String) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) }
    private fun tapText(text: String) { compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick) }

    private fun save(price: String = "55") {
        tap("form_next")
        field("form_price").performTextInput(price)
        tap("form_save")
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("published_title")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun saved() = store.snapshot.campsites.values.single()

    @Test fun form_savesNameLocationAmenitiesAndPrice() {
        launch()
        field("form_name").performTextInput("Bow River Sites")
        field("form_location").performTextInput("Canmore, AB")
        field("form_amenities").performTextInput("Fire pits, potable water")
        save("55")
        val site = saved()
        assertEquals("Bow River Sites", site.name)
        assertEquals("Canmore, AB", site.location)
        assertEquals("Fire pits, potable water", site.amenities)
        assertEquals(55, site.pricePerNight)
        assertEquals("Bow Valley Camping", site.ownerName)
    }

    @Test fun form_savesTheTagsTheOwnerPicks() {
        launch()
        field("form_name").performTextInput("Bow River Sites")
        field("form_location").performTextInput("Canmore, AB")
        tapText("Lakeside")
        tapText("Fire pits")
        save()
        assertEquals(setOf("Lakeside", "Fire pits"), saved().tags.toSet())
    }

    @Test fun form_tappingATagAgain_takesItOff() {
        launch()
        field("form_name").performTextInput("Bow River Sites")
        field("form_location").performTextInput("Canmore, AB")
        tapText("Lakeside")
        tapText("Fire pits")
        tapText("Lakeside")
        save()
        assertEquals(listOf("Fire pits"), saved().tags)
    }

    @Test fun form_savesATagTheOwnerTypes() {
        launch()
        field("form_name").performTextInput("Bow River Sites")
        field("form_location").performTextInput("Canmore, AB")
        tap("form_add_tag")
        compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("Canoe launch") // the "Your own tag" field
        tapText("Add")
        compose.onNodeWithText("Canoe launch").assertExists()
        save()
        assertTrue("Canoe launch" in saved().tags)
    }

    @Test fun form_offersTheMapPinAndThePhoto() {
        launch()
        compose.onNodeWithTag("form_map").assertExists()
        compose.onNodeWithTag("form_photo").assertExists()
    }

    @Test fun form_withoutANameOrLocation_doesNotMoveOn() {
        launch()
        tap("form_next")
        compose.onNodeWithTag("form_price").assertDoesNotExist()
        compose.onNodeWithTag("form_name").assertExists()
        assertTrue(store.snapshot.campsites.isEmpty())
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
