/*
 * Account.kt (Tausif)
 *
 * Signing up and in: the account flow's state and view model. Firebase Authentication
 * manages credentials; a trusted phone retains its existing session.
 *
 * Stories: US 01.09.01 identified by device ("Continue with this phone", no password).
 *
 * Outstanding issues: Google sign-in, password reset and email verification are on hold.
 */
package com.carbon.events

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val TERMS = listOf(
    "1. How the lottery works" to "Popular campsites are allocated by random draw after registration closes. Joining once is enough; duplicates don't improve your odds.",
    "2. Your account & devices" to "Signing in is handled by Google's Firebase Authentication. If you set a password, Events never sees or stores it. Name, email and phone are only shared with owners of campsites you reserve.",
    "3. Reservations" to "Winners pick their nights inside the owner's booking period. Owners can see who booked and when.",
    "4. Community guidelines" to "Be respectful, leave no trace and follow park regulations. Listings or profiles that break policy may be removed.",
    "5. Notifications" to "Events tells you about lottery results, spots that open up, messages from owners and new lotteries at campsites you follow. Turn them off any time in Profile.",
    "6. Payments" to "Events doesn't take payments. You pay the owner at check-in; a rejected or cancelled reservation costs nothing.",
)

/** Shown when "Continue with Google" is tapped while Google sign-in is on hold. */
const val GOOGLE_ON_HOLD_NOTE = "Google sign-in is coming soon. For now, continue with your email."

/** Shown when someone tries to sign up on a phone used by an account an administrator removed. */
const val PHONE_REMOVED_MESSAGE = "This phone was used by an account an administrator removed, so it can't be used to sign up."

/**
 * Reachable account screens. Signup has four numbered steps: role, details, Terms and Trust.
 * Verification and notification-preference screens are not part of this onboarding flow.
 */
enum class AccountStep { WELCOME, SIGN_IN, ACCOUNT, DETAILS, TERMS, TRUST }

/** Numbered setup steps; keep progress indicators aligned with the reachable account screens. */
private const val SIGN_UP_STEPS = 4

/**
 * State of the account flow.
 *
 * @property role whether the new account is a user (entrant) or a campsite owner.
 * @property remember the Sign in screen's "Remember me" (trust this phone).
 */
data class AccountState(
    val step: AccountStep = AccountStep.WELCOME,
    val role: Role = Role.ENTRANT,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val phone: String = "",
    val age: String = "",
    val remember: Boolean = true,
    val agreed: Boolean = false,
    val trust: Boolean = true,
    val prefs: NotificationPrefs = NotificationPrefs(),
    val errors: Map<Field, String> = emptyMap(),
    val working: Boolean = false,
    val message: String? = null,
    val note: String? = null,
    /** Signing up with this phone only: no email or password needed (US 01.09.01). */
    val deviceOnly: Boolean = false,
)

/**
 * Drives sign-up and sign-in. The login account is created when valid details are submitted;
 * the profile is written after Terms and the Trust choice. Signing in with an account that has
 * no profile resumes at Details, so interrupted setup can be completed.
 *
 * Repository work runs in viewModelScope. [state] exposes validation failures and operation
 * errors; callers should disable duplicate submission while AccountState.working is true.
 *
 * @param account an account that is signed in but has no profile yet, or null.
 */
class AccountViewModel(private val container: AppContainer, account: AuthAccount?) : ViewModel() {
    private val _state = MutableStateFlow(
        if (account == null) AccountState() else AccountState(step = AccountStep.DETAILS, email = account.email),
    )

    /** Flow state. */
    val state: StateFlow<AccountState> = _state.asStateFlow()

    /** Whether an account already exists, so Your details asks for no password. */
    private var signedIn: AuthAccount? = account

    private fun edit(vararg cleared: Field, change: (AccountState) -> AccountState) {
        _state.value = change(_state.value.copy(message = null, note = null)).let { it.copy(errors = it.errors - cleared.toSet()) }
    }

    /**
     * Called whenever the flow is shown: an account that is signed in without a profile (an
     * interrupted sign-up) resumes at Your details; otherwise the flow carries on as it is.
     */
    fun resume(account: AuthAccount?) {
        if (_state.value.working) return // a sign-up or sign-in is finishing; it sets signedIn itself
        if (account != null && signedIn == null) {
            signedIn = account
            _state.value = AccountState(step = AccountStep.DETAILS, email = account.email)
        } else if (account == null && signedIn != null) {
            // Signed out (e.g. from Profile) after a finished sign-up or sign-in: start over.
            signedIn = null
            _state.value = AccountState()
        }
    }

    /** Opens a step. */
    fun go(step: AccountStep) = edit { it.copy(step = step) }

    /** "Continue with this phone": your details without email or password; the phone is your sign-in. */
    fun useThisPhone() = edit { it.copy(deviceOnly = true, step = AccountStep.DETAILS) }

    /** "Continue with email": the usual email and password account. */
    fun useEmail() = edit { it.copy(deviceOnly = false, step = AccountStep.DETAILS) }

    /** Goes back one step. */
    fun back() = edit {
        it.copy(
            step = when (it.step) {
                AccountStep.SIGN_IN, AccountStep.ACCOUNT -> AccountStep.WELCOME
                AccountStep.DETAILS -> if (signedIn == null) AccountStep.ACCOUNT else AccountStep.DETAILS
                AccountStep.TERMS -> AccountStep.DETAILS
                AccountStep.TRUST -> AccountStep.TERMS
                AccountStep.WELCOME -> AccountStep.WELCOME
            },
        )
    }

    /** Updates the display name and clears its validation error. */
    fun onName(v: String) = edit(Field.NAME) { it.copy(name = v) }
    /** Updates the sign-in email entered during setup and clears its validation error. */
    fun onEmail(v: String) = edit(Field.EMAIL) { it.copy(email = v) }
    /** Keeps the password only in the transient form until account creation completes. */
    fun onPassword(v: String) = edit(Field.PASSWORD) { it.copy(password = v) }
    /** Updates the optional contact number and clears its validation error. */
    fun onPhone(v: String) = edit(Field.PHONE) { it.copy(phone = v) }
    /** Keeps up to three age digits; submitDetails validates the supported age range. */
    fun onAge(v: String) = edit(Field.AGE) { it.copy(age = v.filter(Char::isDigit).take(3)) }
    /** Selects the requested signup role; server rules independently protect admin privileges. */
    fun onRole(role: Role) = edit { it.copy(role = role) }
    /** Controls whether successful sign-in trusts this phone for future launches. */
    fun onRemember(v: Boolean) = edit { it.copy(remember = v) }
    /** Records whether the user accepted the Terms before advancing. */
    fun onAgreed(v: Boolean) = edit { it.copy(agreed = v) }

    /** Trust this device (Figma S-TRUST, last step): finishes sign-up, trusting this phone or not. */
    fun chooseTrust(trust: Boolean) {
        edit { it.copy(trust = trust) }
        finish()
    }

    /** "Continue with Google" while Google sign-in is on hold. */
    fun google() = edit { it.copy(note = GOOGLE_ON_HOLD_NOTE) }

    private fun run(block: suspend () -> Unit) {
        _state.value = _state.value.copy(working = true, message = null, note = null)
        viewModelScope.launch {
            runCatching { block() }.onFailure { e -> _state.value = _state.value.copy(message = e.userMessage()) }
            _state.value = _state.value.copy(working = false)
        }
    }

    /** Sign in (Figma S-START). With "Remember me", this phone is trusted to stay signed in. */
    fun signIn() {
        val s = _state.value
        if (s.email.isBlank() || s.password.isBlank()) { _state.value = s.copy(message = "Enter your email and password."); return }
        run {
            val account = container.auth.signIn(s.email, s.password)
            signedIn = account
            val profile = container.profiles.observeProfileForAccount(account.uid).first()
            if (profile == null) {
                // The account exists but sign-up didn't finish: pick up at Your details.
                _state.value = _state.value.copy(step = AccountStep.DETAILS, password = "")
            } else if (s.remember && !container.profiles.isPhoneBlocked(container.deviceId)) {
                // A phone of someone an admin removed can't be remembered, for anyone.
                container.profiles.trustDevice(TrustedDevice(container.deviceId, profile.id, container.deviceName, container.clock.now()))
            }
        }
    }

    /** Your details' Continue (Figma S-SIGNUP): creates the login account, then shows the Terms. */
    fun submitDetails() {
        val s = _state.value
        val errors = ProfileValidator.validateSignUp(s.name, s.email, if (signedIn == null && !s.deviceOnly) s.password else null, s.phone, s.age)
            // With this phone as the sign-in, email is optional contact info.
            .let { e -> if (s.deviceOnly && s.email.isBlank()) e - Field.EMAIL else e }
        if (errors.isNotEmpty()) { _state.value = s.copy(errors = errors); return }
        run {
            if (signedIn == null) signedIn = if (s.deviceOnly) container.auth.signInWithDevice() else container.auth.signUp(s.email, s.password)
            // Someone an admin removed can't come back on the same phone: undo the new login and stop.
            if (container.profiles.isPhoneBlocked(container.deviceId)) {
                runCatching { container.auth.deleteAccount() }
                container.auth.signOut()
                signedIn = null
                _state.value = _state.value.copy(message = PHONE_REMOVED_MESSAGE, password = "")
                return@run
            }
            _state.value = _state.value.copy(step = AccountStep.TERMS, password = "")
        }
    }

    /** Terms' Accept & continue. */
    fun acceptTerms() {
        if (!_state.value.agreed) { _state.value = _state.value.copy(message = "Tick the box to accept the terms."); return }
        // A phone-only account is this phone, so it's trusted without asking.
        if (_state.value.deviceOnly) { edit { it.copy(trust = true) }; finish() } else edit { it.copy(step = AccountStep.TRUST) }
    }

    /** Terms' Decline: signs out and returns to Welcome. */
    fun declineTerms() {
        container.auth.signOut()
        signedIn = null
        _state.value = AccountState()
    }

    /**
     * Persists the completed profile and default/current notification preferences. [chooseTrust]
     * records whether to link this phone before calling this operation. Does nothing until a
     * login account exists; repository failures are exposed through AccountState.message.
     */
    fun finish() {
        val s = _state.value
        val account = signedIn ?: return
        val now = container.clock.now()
        run {
            container.profiles.createProfile(
                UserProfile(
                    id = "", name = s.name.trim(), email = s.email.trim().ifBlank { account.email }, phone = s.phone.trim(),
                    activeRole = s.role, createdAt = now, authUid = account.uid, age = s.age.trim().toIntOrNull(),
                    prefs = s.prefs,
                ),
                if (s.trust) TrustedDevice(container.deviceId, "", container.deviceName, now) else null,
            )
        }
    }
}

/**
 * Returns a function that asks Android 13+ for permission to post notifications, then calls
 * [then] whatever the answer (older phones call it straight away).
 */
@Composable
fun rememberNotificationPermission(then: () -> Unit): () -> Unit {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { then() }
    val context = androidx.compose.ui.platform.LocalContext.current
    return {
        if (android.os.Build.VERSION.SDK_INT >= 33 && !LocalNotifier.hasPermission(context)) {
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else then()
    }
}
