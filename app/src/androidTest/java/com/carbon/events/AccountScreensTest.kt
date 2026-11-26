/*
 * AccountScreensTest.kt (Tausif)
 *
 * Purpose: Compose UI (intent) tests for the account screens in AccountScreens.kt that no other test
 * covers: signing in with email and password (US 01.09.01) with and without "Remember me"
 * (US 01.09.01), Google sign-in being on hold, creating an account by email as a user or a
 * campsite owner and going straight in, "Trust this device" and "Not now", declining the terms,
 * and an email that is already used.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write
 * UI (intent) tests for every part of the account screens that isn't tested yet." Reviewed, edited
 * and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import android.Manifest
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A phone on Welcome, with Adam Kent's email account already on the server. */
@RunWith(AndroidJUnit4::class)
class AccountScreensTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *listOfNotNull(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS.takeIf { android.os.Build.VERSION.SDK_INT >= 33 },
        ).toTypedArray(),
    )
    @get:Rule(order = 1) val compose = createComposeRule()
    private val clock = FixedClock(NOW)
    private val store = InMemoryEventsStore(SampleData.snapshot(NOW))
    private val auth = InMemoryAuth(null)
    private val phone = "pixel-8-account"
    private val adamEmail = "adam@example.com"
    private val password = "campfire8"

    /** Each change of [launches] is a fresh app start on the same phone, with the same saved data. */
    private var launches by mutableIntStateOf(0)

    init {
        runBlocking {
            val account = auth.signUp(adamEmail, password)
            store.createProfile(UserProfile("", "Adam Kent", adamEmail, authUid = account.uid, age = 30, createdAt = NOW), null)
            auth.signOut()
        }
    }

    private fun launch(expect: String = "welcome") {
        compose.setContent {
            key(launches) {
                val screens = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
                val c = remember { AppContainer.inMemory(phone, clock, store, auth) }
                CompositionLocalProvider(LocalViewModelStoreOwner provides screens) {
                    EventsTheme {
                        EventsApp(c, showSplash = false, splash = {},
                            setup = { SetupScreen(LocalAppSession.current.profileVm) }, alerts = {},
                            entrant = { Box(Modifier.testTag("entrant_area")) },
                            owner = { Box(Modifier.testTag("owner_area")) },
                            admin = {},
                        )
                    }
                }
            }
        }
        waitFor(expect)
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun tap(tag: String) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) }
    private fun field(tag: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)) or (hasSetTextAction() and hasTestTag(tag)))
    private fun trustedFor(): String? = store.snapshot.devices[phone]?.userId
    private fun adam() = store.snapshot.profiles.values.single { it.email == adamEmail }

    private fun signIn(email: String, pw: String, remember: Boolean = true) {
        tap("welcome_sign_in")
        waitFor("sign_in")
        field("sign_in_email").performTextInput(email)
        field("sign_in_password").performTextInput(pw)
        if (!remember) tap("sign_in_remember")
        tap("sign_in_submit")
    }

    private fun signUpByEmail(name: String, email: String, owner: Boolean = false) {
        tap("welcome_create")
        tap("account_email")
        waitFor("details_name")
        if (owner) tap("details_role_1")
        field("details_name").performTextInput(name)
        field("details_email").performTextInput(email)
        field("details_password").performTextInput(password)
        field("details_age").performTextInput("35")
        tap("details_continue")
    }

    private fun acceptTerms() {
        waitFor("terms_agree")
        tap("terms_agree")
        tap("terms_accept")
    }

    // ---- Signing in ----

    @Test fun iAlreadyHaveAnAccount_opensSignIn() {
        launch()
        tap("welcome_sign_in")
        waitFor("sign_in")
        compose.onNodeWithTag("sign_in_email").assertExists()
        compose.onNodeWithTag("sign_in_password").assertExists()
        compose.onNodeWithTag("sign_in_remember").assertExists()
    }

    @Test fun aWrongPassword_saysSo_andStaysOnSignIn() {
        launch()
        signIn(adamEmail, "not-my-password1")
        waitFor("sign_in_error")
        compose.onNodeWithTag("sign_in_error").assertTextEquals(AuthError.WRONG_PASSWORD.message)
        compose.onNodeWithTag("entrant_area").assertDoesNotExist()
    }

    @Test fun emptyFields_askForEmailAndPassword() {
        launch()
        tap("welcome_sign_in")
        waitFor("sign_in")
        tap("sign_in_submit")
        compose.onNodeWithTag("sign_in_error").assertTextEquals("Enter your email and password.")
    }

    @Test fun signingIn_withRememberMe_trustsThisPhone_andTheNextLaunchGoesStraightIn() {
        launch()
        signIn(adamEmail, password)
        waitFor("entrant_area")
        assertEquals(adam().id, trustedFor())
        launches++
        waitFor("entrant_area")
        compose.onNodeWithTag("welcome").assertDoesNotExist()
    }

    @Test fun signingIn_withoutRememberMe_asksAgainNextLaunch() {
        launch()
        signIn(adamEmail, password, remember = false)
        waitFor("entrant_area")
        assertNull(trustedFor())
        launches++
        waitFor("welcome")
        compose.onNodeWithTag("entrant_area").assertDoesNotExist()
    }

    @Test fun forgotPassword_isOnHold_andLeavesYouOnSignIn() {
        launch()
        tap("welcome_sign_in")
        waitFor("sign_in")
        tap("sign_in_forgot")
        compose.onNodeWithTag("sign_in").assertExists()
    }

    @Test fun google_isOnHold_andSaysSo() {
        launch()
        tap("welcome_sign_in")
        waitFor("google_button")
        tap("google_button")
        waitFor("account_note")
        compose.onNodeWithText(GOOGLE_ON_HOLD_NOTE).assertExists()
    }

    // ---- Creating an account by email ----

    @Test fun emailSignUp_asAnOwner_withTrust_goesStraightToTheOwnerApp() {
        launch()
        signUpByEmail("Lakeview Camps", "hello@lakeviewcamps.ca", owner = true)
        acceptTerms()
        waitFor("trust_yes")
        tap("trust_yes")
        waitFor("owner_area")
        val owner = store.snapshot.profiles.values.single { it.name == "Lakeview Camps" }
        assertEquals(Role.OWNER, owner.activeRole)
        assertEquals(owner.id, trustedFor())
    }

    @Test fun emailSignUp_notNow_goesStraightIn_butAsksForThePasswordNextLaunch() {
        launch()
        signUpByEmail("Maya Chen", "maya@example.com")
        acceptTerms()
        waitFor("trust_no")
        tap("trust_no")
        waitFor("entrant_area")
        assertNull("the phone isn't trusted", trustedFor())
        launches++
        waitFor("welcome")
    }

    @Test fun decliningTheTerms_goesBackToWelcome_withNoProfile() {
        launch()
        signUpByEmail("Maya Chen", "maya@example.com")
        waitFor("terms_decline")
        tap("terms_decline")
        waitFor("welcome")
        assertTrue(store.snapshot.profiles.values.none { it.name == "Maya Chen" })
    }

    @Test fun anEmailAlreadyUsed_saysToSignInInstead() {
        launch()
        signUpByEmail("Adam Again", adamEmail)
        waitFor("details_error")
        compose.onNodeWithTag("details_error").assertTextEquals(AuthError.EMAIL_TAKEN.message)
        compose.onNodeWithTag("terms_agree").assertDoesNotExist()
    }

    @Test fun aPhoneNumber_isOptional_butKeptWhenGiven() {
        launch()
        tap("welcome_create")
        tap("account_email")
        waitFor("details_name")
        field("details_name").performTextInput("Liam Okafor")
        field("details_email").performTextInput("liam@example.com")
        field("details_password").performTextInput(password)
        field("details_phone").performTextInput("+1 780 555 0142")
        field("details_age").performTextInput("29")
        tap("details_continue")
        acceptTerms()
        tap("trust_yes")
        waitFor("entrant_area")
        assertEquals("+1 780 555 0142", store.snapshot.profiles.values.single { it.name == "Liam Okafor" }.phone)
    }

    private companion object { const val NOW = 1_792_162_800_000L }
}
