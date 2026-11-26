/*
 * Auth.kt (Vaibhav)
 *
 * Signing up and in: the sign-in interface, Firebase Authentication behind it, and an
 * in-memory version for tests and the offline build.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * A signed-in Firebase Authentication account; separate from the application profile.
 * @property uid credential identity used by server access rules.
 * @property email sign-in email, not an editable profile contact field.
 * @property emailVerified verification status reported by Firebase.
 */
data class AuthAccount(val uid: String, val email: String, val emailVerified: Boolean)

/** Why signing up or in failed. */
enum class AuthError(val message: String) {
    WRONG_PASSWORD("Incorrect email or password."),
    EMAIL_TAKEN("An account already uses this email. Sign in instead."),
    WEAK_PASSWORD("Use 8 or more characters with at least one number."),
    BAD_EMAIL("Enter a valid email address."),
    NETWORK("Couldn't reach the server. Check your connection and try again."),
    PROVIDER_OFF("That sign-in option is turned off for this app. In Firebase → Authentication → Sign-in method, enable Email/Password and Anonymous."),
    TOO_MANY("Too many attempts. Wait a few minutes and try again."),
    SIGN_IN_AGAIN("For your security, log out and sign in again before changing your email."),
    OTHER("Firebase sign-in failed. Check the app's Firebase setup and try again."),
}

/** Thrown by [AuthRepository] when signing up or in fails. */
class AuthException(val error: AuthError) : Exception(error.message)

/**
 * Email and password accounts. Passwords go straight to Firebase Authentication, which stores
 * them hashed; the app and the database never see or keep them.
 */
interface AuthRepository {
    /** Emits the signed-in account, or null when signed out. */
    fun observeAccount(): Flow<AuthAccount?>

    /** Creates an account and signs in. @throws AuthException */
    suspend fun signUp(email: String, password: String): AuthAccount

    /** Signs in. @throws AuthException */
    suspend fun signIn(email: String, password: String): AuthAccount

    /**
     * Signs up with this phone only, no email or password (US 01.09.01). The phone keeps the
     * session, so opening the app again goes straight in.
     */
    suspend fun signInWithDevice(): AuthAccount

    /**
     * Starts changing the sign-in email (US 01.07.03): emails a confirmation link to [newEmail];
     * the change applies once it's opened. Does nothing for phone-only accounts.
     *
     * @return true if a link was sent, false if there's no sign-in email to change.
     * @throws AuthException
     */
    suspend fun changeEmail(newEmail: String): Boolean

    /** Emails a link that verifies the address. */
    suspend fun sendEmailVerification()

    /** Re-reads the account (e.g. after the person tapped the verification link). */
    suspend fun refresh(): AuthAccount?

    /** Signs out of this phone. */
    fun signOut()

    /**
     * Deletes the signed-in login account (Delete profile).
     * @return false if Firebase wants a fresh sign-in first.
     */
    suspend fun deleteAccount(): Boolean

    /**
     * Whether Firebase will want the password again before [deleteAccount]: an email login signed
     * in more than a few minutes ago. Phone-only logins have no password to ask.
     */
    fun needsPasswordToDelete(): Boolean = false

    /** Checks the signed-in login's password again, which counts as a fresh sign-in. @throws AuthException */
    suspend fun confirmPassword(password: String) {}
}

/**
 * In-memory accounts for tests and builds without Firebase. Passwords are only compared in
 * memory and never leave the process.
 *
 * @param signedInUid the account already signed in, or null to start signed out.
 */
class InMemoryAuth(signedInUid: String? = null) : AuthRepository {
    private data class Account(val uid: String, val email: String, val password: String, var verified: Boolean = false)

    private val accounts = mutableMapOf<String, Account>()
    private val current = MutableStateFlow(signedInUid?.let { AuthAccount(it, "", true) })

    /** Set by [requireFreshSignIn]: the email login counts as signed in long ago. */
    private var signedInLongAgo = false

    /** Makes the current email login count as signed in long ago, as Firebase sees it after a while (tests). */
    fun requireFreshSignIn() { signedInLongAgo = true }

    override fun needsPasswordToDelete(): Boolean = signedInLongAgo && current.value?.email?.isNotBlank() == true

    override suspend fun confirmPassword(password: String) {
        val now = current.value ?: return
        val account = accounts.values.firstOrNull { it.uid == now.uid } ?: return
        if (account.password != password) throw AuthException(AuthError.WRONG_PASSWORD)
        signedInLongAgo = false
    }

    override fun observeAccount(): Flow<AuthAccount?> = current

    private var deviceLogins = 0

    // Every "Continue with this phone" is a new login, as it is on the server.
    override suspend fun signInWithDevice(): AuthAccount =
        AuthAccount("uid-device-${++deviceLogins}", "", false).also { current.value = it }

    /** Sign-in emails waiting for their link to be opened, by account. */
    private val pendingEmails = mutableMapOf<String, String>()

    override suspend fun changeEmail(newEmail: String): Boolean {
        val now = current.value ?: return false
        val account = accounts.values.firstOrNull { it.uid == now.uid } ?: return false
        val key = newEmail.trim().lowercase()
        if (key == account.email) return false
        if (key in accounts) throw AuthException(AuthError.EMAIL_TAKEN)
        pendingEmails[account.uid] = key
        return true
    }

    /** Opens the link a pending email change sent, as Firebase does: the login moves to the new address (tests). */
    fun openEmailChangeLink() {
        val now = current.value ?: return
        val newEmail = pendingEmails.remove(now.uid) ?: return
        val account = accounts.values.firstOrNull { it.uid == now.uid } ?: return
        accounts.remove(account.email)
        accounts[newEmail] = account.copy(email = newEmail, verified = true)
        current.value = now.copy(email = newEmail, emailVerified = true)
    }

    override suspend fun signUp(email: String, password: String): AuthAccount {
        val key = email.trim().lowercase()
        if (key in accounts) throw AuthException(AuthError.EMAIL_TAKEN)
        if (!ProfileValidator.isStrongPassword(password)) throw AuthException(AuthError.WEAK_PASSWORD)
        val account = Account("uid-${accounts.size + 1}", key, password)
        accounts[key] = account
        signedInLongAgo = false
        return AuthAccount(account.uid, key, false).also { current.value = it }
    }

    override suspend fun signIn(email: String, password: String): AuthAccount {
        val account = accounts[email.trim().lowercase()]?.takeIf { it.password == password } ?: throw AuthException(AuthError.WRONG_PASSWORD)
        signedInLongAgo = false
        return AuthAccount(account.uid, account.email, account.verified).also { current.value = it }
    }

    override suspend fun sendEmailVerification() {}

    /** Marks [email]'s address verified, as if its link was tapped (tests). */
    fun verify(email: String) { accounts[email.lowercase()]?.verified = true }

    override suspend fun refresh(): AuthAccount? {
        val now = current.value ?: return null
        val account = accounts.values.firstOrNull { it.uid == now.uid } ?: return now
        return now.copy(emailVerified = account.verified).also { current.value = it }
    }

    override fun signOut() { current.value = null }

    override suspend fun deleteAccount(): Boolean {
        val uid = current.value?.uid ?: return true
        if (needsPasswordToDelete()) return false // as Firebase: an old sign-in can't delete the login
        accounts.values.removeAll { it.uid == uid }
        current.value = null
        return true
    }
}

/** Firebase lets a login be deleted only within about five minutes of signing in; this leaves a margin. */
private const val FRESH_SIGN_IN_MS = 4 * 60_000L

/**
 * Accounts on Firebase Authentication (email and password). Google hashes and stores the
 * passwords; nothing about them reaches Firestore or this app's storage.
 *
 * @param auth the FirebaseAuth instance.
 */
class FirebaseAuthStore(private val auth: FirebaseAuth) : AuthRepository {
    private fun com.google.firebase.auth.FirebaseUser.toAccount() = AuthAccount(uid, email.orEmpty(), isEmailVerified)

    override fun observeAccount(): Flow<AuthAccount?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAccount()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    private suspend fun <T> mapped(block: suspend () -> T): T = try {
        block()
    } catch (e: FirebaseAuthRecentLoginRequiredException) {
        throw AuthException(AuthError.SIGN_IN_AGAIN)
    } catch (e: FirebaseAuthWeakPasswordException) {
        throw AuthException(AuthError.WEAK_PASSWORD)
    } catch (e: FirebaseAuthUserCollisionException) {
        throw AuthException(AuthError.EMAIL_TAKEN)
    } catch (e: FirebaseAuthInvalidUserException) {
        throw AuthException(AuthError.WRONG_PASSWORD)
    } catch (e: FirebaseAuthInvalidCredentialsException) {
        throw AuthException(if (e.errorCode == "ERROR_INVALID_EMAIL") AuthError.BAD_EMAIL else AuthError.WRONG_PASSWORD)
    } catch (e: FirebaseNetworkException) {
        throw AuthException(AuthError.NETWORK)
    } catch (e: com.google.firebase.FirebaseTooManyRequestsException) {
        throw AuthException(AuthError.TOO_MANY)
    } catch (e: com.google.firebase.auth.FirebaseAuthException) {
        android.util.Log.w("Events", "Firebase Auth error ${e.errorCode}", e)
        throw AuthException(if (e.errorCode == "ERROR_OPERATION_NOT_ALLOWED") AuthError.PROVIDER_OFF else AuthError.OTHER)
    }

    override suspend fun signUp(email: String, password: String): AuthAccount =
        mapped { auth.createUserWithEmailAndPassword(email.trim(), password).await().user!!.toAccount() }

    // Needs "Anonymous" turned on in Firebase → Authentication → Sign-in method (free).
    override suspend fun signInWithDevice(): AuthAccount = mapped {
        // A login someone left on this phone is not the new person's: they get their own.
        if (auth.currentUser != null) auth.signOut()
        auth.signInAnonymously().await().user!!.toAccount()
    }

    override suspend fun changeEmail(newEmail: String): Boolean {
        val user = auth.currentUser ?: return false
        if (user.isAnonymous || user.email.isNullOrBlank() || user.email.equals(newEmail.trim(), ignoreCase = true)) return false
        mapped { user.verifyBeforeUpdateEmail(newEmail.trim()).await() }
        return true
    }

    override suspend fun signIn(email: String, password: String): AuthAccount =
        mapped { auth.signInWithEmailAndPassword(email.trim(), password).await().user!!.toAccount() }

    override suspend fun sendEmailVerification() {
        auth.currentUser?.sendEmailVerification()?.await()
    }

    override suspend fun refresh(): AuthAccount? {
        val user = auth.currentUser ?: return null
        user.reload().await()
        return auth.currentUser?.toAccount()
    }

    override fun signOut() = auth.signOut()

    override fun needsPasswordToDelete(): Boolean {
        val user = auth.currentUser ?: return false
        if (user.isAnonymous || user.email.isNullOrBlank()) return false
        val signedInAt = user.metadata?.lastSignInTimestamp ?: return true
        return System.currentTimeMillis() - signedInAt > FRESH_SIGN_IN_MS
    }

    override suspend fun confirmPassword(password: String) = mapped {
        val user = auth.currentUser ?: return@mapped
        user.reauthenticate(com.google.firebase.auth.EmailAuthProvider.getCredential(user.email.orEmpty(), password)).await()
    }

    override suspend fun deleteAccount(): Boolean {
        val user = auth.currentUser ?: return true
        return try {
            user.delete().await(); true
        } catch (e: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
            auth.signOut(); false
        }
    }
}
