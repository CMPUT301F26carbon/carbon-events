/*
 * AccountTest.kt (Tausif)
 *
 * Purpose: Unit tests for Account.kt, the account flow's view model: moving between the steps
 * and back, the checks on Your details (US 01.09.01), declining the terms, Google sign-in being
 * on hold, an unfinished sign-up picking up where it stopped, and signing out starting over.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write
 * tests for the account flow's view model." Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = ViewModelStore()
    private val store = InMemoryEventsStore()
    private val auth = InMemoryAuth(null)
    private val container = AppContainer.inMemory("pixel", FixedClock(TestData.NOW), store, auth)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }

    private fun vm(account: AuthAccount? = null) = AccountViewModel(container, account).also { models.put(System.identityHashCode(it).toString(), it) }
    private fun AccountViewModel.step() = state.value.step

    @Test fun startsOnWelcome() {
        assertEquals(AccountStep.WELCOME, vm().step())
    }

    @Test fun back_walksTheStepsInReverse() {
        val vm = vm()
        vm.go(AccountStep.ACCOUNT); vm.useEmail()
        assertEquals(AccountStep.DETAILS, vm.step())
        vm.back(); assertEquals(AccountStep.ACCOUNT, vm.step())
        vm.back(); assertEquals(AccountStep.WELCOME, vm.step())
        vm.back(); assertEquals(AccountStep.WELCOME, vm.step())
        vm.go(AccountStep.SIGN_IN); vm.back(); assertEquals(AccountStep.WELCOME, vm.step())
    }

    @Test fun details_showEveryProblem_andStay() {
        val vm = vm()
        vm.useEmail()
        vm.onEmail("not-an-email"); vm.onPassword("short"); vm.onPhone("abc"); vm.onAge("12")
        vm.submitDetails()
        val errors = vm.state.value.errors
        assertEquals(setOf(Field.NAME, Field.EMAIL, Field.PASSWORD, Field.PHONE, Field.AGE), errors.keys)
        assertEquals(AccountStep.DETAILS, vm.step())
        vm.onName("Adam Kent")
        assertNull("typing clears that field's error", vm.state.value.errors[Field.NAME])
    }

    @Test fun age_keepsOnlyUpToThreeDigits() {
        val vm = vm()
        vm.onAge("4a2b7c9")
        assertEquals("427", vm.state.value.age)
    }

    @Test fun phoneOnly_needsNoEmail() {
        val vm = vm()
        vm.useThisPhone()
        vm.onName("Maya Chen"); vm.onAge("30")
        vm.submitDetails()
        assertEquals(AccountStep.TERMS, vm.step())
    }

    @Test fun terms_mustBeTicked() {
        val vm = vm()
        vm.useThisPhone(); vm.onName("Maya Chen"); vm.onAge("30"); vm.submitDetails()
        vm.acceptTerms()
        assertEquals("Tick the box to accept the terms.", vm.state.value.message)
        assertEquals(AccountStep.TERMS, vm.step())
    }

    @Test fun decliningTheTerms_signsOut_andStartsOver() = runTest(dispatcher) {
        val vm = vm()
        vm.useEmail(); vm.onName("Adam Kent"); vm.onEmail("adam@example.com"); vm.onPassword("campfire8"); vm.onAge("30")
        vm.submitDetails()
        assertEquals(AccountStep.TERMS, vm.step())
        vm.declineTerms()
        assertNull(auth.observeAccount().first())
        assertEquals(AccountState(), vm.state.value)
    }

    @Test fun google_isOnHold() {
        val vm = vm()
        vm.google()
        assertEquals(GOOGLE_ON_HOLD_NOTE, vm.state.value.note)
    }

    @Test fun aLoginWithNoProfile_picksUpAtYourDetails() {
        val vm = vm(AuthAccount("uid-9", "adam@example.com", false))
        assertEquals(AccountStep.DETAILS, vm.step())
        assertEquals("adam@example.com", vm.state.value.email)
    }

    @Test fun signingOutElsewhere_startsTheFlowOver() {
        val vm = vm()
        vm.resume(AuthAccount("uid-9", "adam@example.com", false))
        assertEquals(AccountStep.DETAILS, vm.step())
        vm.resume(null)
        assertEquals(AccountStep.WELCOME, vm.step())
    }

    @Test fun signIn_withNothingTyped_asksForBoth() {
        val vm = vm()
        vm.go(AccountStep.SIGN_IN)
        vm.signIn()
        assertEquals("Enter your email and password.", vm.state.value.message)
        assertTrue(vm.state.value.remember)
    }
}
