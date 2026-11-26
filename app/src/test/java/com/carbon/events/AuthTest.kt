/*
 * AuthTest.kt (Vaibhav)
 *
 * Purpose: Unit tests for Auth.kt, through the in-memory sign-in used by tests and the offline
 * build: signing up and in by email (US 01.09.01), signing in with this phone (US 01.09.01),
 * and the password check before deleting a login.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write tests for the sign-in layer in Auth.kt."
 * Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthTest {
    private val auth = InMemoryAuth(null)

    private fun error(block: suspend () -> Unit): AuthError? = runCatching { kotlinx.coroutines.runBlocking { block() } }.exceptionOrNull().let { (it as? AuthException)?.error }

    @Test fun signUp_signsIn_withTheEmailInLowerCase() = runTest {
        val account = auth.signUp(" Adam@Example.com ", "campfire8")
        assertEquals("adam@example.com", account.email)
        assertEquals(account, auth.observeAccount().first())
        assertFalse(account.emailVerified)
    }

    @Test fun signUp_refusesATakenEmail_andAWeakPassword() {
        kotlinx.coroutines.runBlocking { auth.signUp("adam@example.com", "campfire8") }
        assertEquals(AuthError.EMAIL_TAKEN, error { auth.signUp("ADAM@example.com", "campfire9") })
        assertEquals(AuthError.WEAK_PASSWORD, error { auth.signUp("maya@example.com", "short") })
    }

    @Test fun signIn_needsTheRightPassword() = runTest {
        auth.signUp("adam@example.com", "campfire8"); auth.signOut()
        assertEquals(AuthError.WRONG_PASSWORD, error { auth.signIn("adam@example.com", "wrong-pass1") })
        assertEquals(AuthError.WRONG_PASSWORD, error { auth.signIn("nobody@example.com", "campfire8") })
        assertEquals("adam@example.com", auth.signIn("adam@example.com", "campfire8").email)
    }

    @Test fun signOut_leavesNobodySignedIn() = runTest {
        auth.signUp("adam@example.com", "campfire8")
        auth.signOut()
        assertNull(auth.observeAccount().first())
    }

    @Test fun eachPhoneSignIn_isItsOwnLogin_withNoEmail() = runTest {
        val a = auth.signInWithDevice(); auth.signOut()
        val b = auth.signInWithDevice()
        assertNotEquals(a.uid, b.uid)
        assertEquals("", b.email)
    }

    @Test fun verifyingTheEmail_showsAfterARefresh() = runTest {
        auth.signUp("adam@example.com", "campfire8")
        auth.verify("adam@example.com")
        assertTrue(auth.refresh()!!.emailVerified)
    }

    @Test fun anOldSignIn_needsThePasswordBeforeTheLoginIsDeleted() = runTest {
        auth.signUp("adam@example.com", "campfire8")
        assertFalse(auth.needsPasswordToDelete())
        auth.requireFreshSignIn()
        assertTrue(auth.needsPasswordToDelete())
        assertFalse("refused, like Firebase", auth.deleteAccount())
        assertEquals(AuthError.WRONG_PASSWORD, error { auth.confirmPassword("wrong-pass1") })
        auth.confirmPassword("campfire8")
        assertTrue(auth.deleteAccount())
        assertNull(auth.observeAccount().first())
        assertEquals(AuthError.WRONG_PASSWORD, error { auth.signIn("adam@example.com", "campfire8") })
    }

    @Test fun aPhoneLogin_neverNeedsAPassword() = runTest {
        auth.signInWithDevice()
        auth.requireFreshSignIn()
        assertFalse(auth.needsPasswordToDelete())
    }

    @Test fun signInErrors_haveMessagesForPeople() {
        AuthError.entries.forEach { assertTrue(it.name, it.message.isNotBlank()) }
        assertThrows(AuthException::class.java) { throw AuthException(AuthError.NETWORK) }
    }
}
