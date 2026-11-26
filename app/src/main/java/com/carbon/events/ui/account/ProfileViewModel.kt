/*
 * ProfileViewModel.kt (Tausif)
 *
 * The signed-in person: their profile, editing it (US 01.07.03), the phones they trust
 * (US 01.09.01), signing out and deleting the profile.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Whether the person's profile has loaded, and if so whether they're signed in and set up. */
sealed interface ProfileState {
    /** Still loading. */
    data object Loading : ProfileState

    /**
     * Signed out ([account] is null), or signed in to an account that has no profile yet
     * (sign-up was interrupted): show the Welcome / Create Account flow.
     */
    data class Missing(val account: AuthAccount? = null) : ProfileState

    /** Signed in with a profile. */
    data class Ready(val profile: UserProfile) : ProfileState
}

/** State of the edit-profile form. */
data class ProfileFormState(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val photoBase64: String? = null,
    val errors: Map<Field, String> = emptyMap(),
    val saving: Boolean = false,
    val message: String? = null,
)

/** How long opening the app waits to learn whether this phone is still trusted. */
private const val LAUNCH_CHECK_MS = 10_000L

/** How long signing out waits for the server before saying there's no connection. */
private const val SERVER_WAIT_MS = 15_000L

/**
 * View model for the signed-in person: follows their login account to their profile (one
 * indexed lookup), edits it (US 01.07.03), manages the phones they trust (US 01.09.01) and signs
 * out. When the app starts on a phone that isn't trusted, it signs out, so "Not now" on the
 * Trust This Device screen means "ask for my password next time".
 *
 * @param container app dependencies.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProfileViewModel(private val container: AppContainer) : ViewModel() {
    private val trustChecked = MutableStateFlow(false)

    /** Set while this person removes this phone in Trusted devices, so that doesn't sign them out. */
    private var removingThisPhone = false

    /** Set while "Log out of all devices" runs here, so its own steps don't sign out half-way. */
    private var loggingOutEverywhere = false

    private val _pendingEmail = MutableStateFlow<String?>(null)

    /** The new sign-in email waiting for its confirmation link to be opened, or null. */
    val pendingEmail: StateFlow<String?> = _pendingEmail.asStateFlow()

    /** The person's profile as it loads and changes. */
    val state: StateFlow<ProfileState> = trustChecked
        .flatMapLatest { checked -> if (!checked) flowOf(null) else container.auth.observeAccount() }
        .flatMapLatest { account ->
            when {
                !trustChecked.value -> flowOf(ProfileState.Loading)
                account == null -> flowOf(ProfileState.Missing(null))
                else -> container.profiles.observeProfileForAccount(account.uid)
                    .map { if (it == null) ProfileState.Missing(account) else ProfileState.Ready(it) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileState.Loading)

    init {
        viewModelScope.launch {
            // Launch check: a phone that isn't trusted for the signed-in person starts signed out,
            // on Welcome. Trust is asked of the server, not an old saved copy, so a phone removed
            // on another phone can't get back in; if the server doesn't answer in time, the
            // session is kept (the app can still open offline).
            runCatching {
                withTimeoutOrNull(LAUNCH_CHECK_MS) {
                    val account = container.auth.observeAccount().first()
                    if (account != null) {
                        val profile = container.profiles.observeProfileForAccount(account.uid).first()
                        val trustedFor = container.profiles.trustedUserIdNow(container.deviceId)
                        if (profile != null && trustedFor != profile.id) container.auth.signOut()
                    }
                }
            }
            trustChecked.value = true
        }
        // Once Firebase has moved the sign-in email (its link was opened), the profile follows.
        viewModelScope.launch {
            combine(container.auth.observeAccount(), state) { account, s -> account to s }.collect { (account, s) ->
                val profile = (s as? ProfileState.Ready)?.profile ?: return@collect
                val email = account?.email.orEmpty()
                if (email.isNotBlank() && !email.equals(profile.email, ignoreCase = true)) {
                    runCatching { container.profiles.saveProfile(profile.copy(email = email)) }
                    if (email.equals(_pendingEmail.value, ignoreCase = true)) _pendingEmail.value = null
                }
            }
        }
        // While the app is open, sign out here too if the profile is removed (by an admin or on
        // another phone), if this trusted phone is removed, or if "Log out of all devices" is used
        // on any phone (the profile's signedOutAt changes).
        viewModelScope.launch {
            var signedInAs: String? = null
            var trustedHere = false
            var signOutMark = 0L
            state.collectLatest { s ->
                when (s) {
                    is ProfileState.Ready -> {
                        if (signedInAs != s.profile.id) {
                            signedInAs = s.profile.id; trustedHere = false; signOutMark = s.profile.signedOutAt
                        } else if (s.profile.signedOutAt != signOutMark) {
                            signOutMark = s.profile.signedOutAt
                            if (!loggingOutEverywhere) { container.auth.signOut(); return@collectLatest }
                        }
                        container.profiles.observeUserIdForDevice(container.deviceId).collect { trustedFor ->
                            if (trustedFor == s.profile.id) trustedHere = true
                            else if (trustedHere) {
                                trustedHere = false
                                when {
                                    removingThisPhone -> removingThisPhone = false // stays in until next launch
                                    !loggingOutEverywhere -> container.auth.signOut()
                                }
                            }
                        }
                    }
                    is ProfileState.Missing -> {
                        if (signedInAs != null && s.account != null && !loggingOutEverywhere) container.auth.signOut()
                        signedInAs = null; trustedHere = false
                    }
                    else -> Unit
                }
            }
        }
    }

    /** The phones this person trusts (empty until the profile loads). */
    val devices: StateFlow<List<TrustedDevice>> = state
        .flatMapLatest { s -> if (s is ProfileState.Ready) container.profiles.observeDevices(s.profile.id) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _form = MutableStateFlow(ProfileFormState())

    /** The edit form. */
    val form: StateFlow<ProfileFormState> = _form.asStateFlow()

    private val _saved = MutableStateFlow(false)

    /** True briefly after the profile was saved (Figma E-PROFILE-SAVED). */
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    /** Hides the "profile updated" message. */
    fun savedShown() { _saved.value = false }

    /** This phone's ID, so the trusted-phones list can mark it. */
    val deviceId: String get() = container.deviceId

    /** Pre-fills the form from [profile] before editing. */
    fun startEditing(profile: UserProfile) {
        _form.value = ProfileFormState(profile.name, profile.email, profile.phone, profile.photoBase64)
    }

    /** Updates the name field and clears its error. */
    fun onNameChange(v: String) { _form.value = _form.value.copy(name = v, errors = _form.value.errors - Field.NAME) }

    /** Sets (or with null removes) the new profile photo. */
    fun onPhoto(base64: String?) { _form.value = _form.value.copy(photoBase64 = base64) }

    /** Updates the phone field and clears its error. */
    fun onEmailChange(v: String) { _form.value = _form.value.copy(email = v, errors = _form.value.errors - Field.EMAIL) }

    fun onPhoneChange(v: String) { _form.value = _form.value.copy(phone = v, errors = _form.value.errors - Field.PHONE) }

    /**
     * Validates and saves the edited details, keeping role, admin flag and account (US 01.07.03).
     *
     * @param onSaved called after a successful save.
     */
    fun save(onSaved: () -> Unit = {}) {
        val f = _form.value
        val existing = (state.value as? ProfileState.Ready)?.profile ?: return
        // US 01.07.03: name, email, phone and photo can all change. A phone-only account may leave email blank.
        val errors = ProfileValidator.validate(f.name, f.email, f.phone)
            .let { e -> if (f.email.isBlank() && existing.email.isBlank()) e - Field.EMAIL else e }
        if (errors.isNotEmpty()) {
            _form.value = f.copy(errors = errors)
            return
        }
        val profile = existing.copy(name = f.name.trim(), email = f.email.trim(), phone = f.phone.trim(), photoBase64 = f.photoBase64)
        val emailChanged = !f.email.trim().equals(existing.email.trim(), ignoreCase = true)
        _form.value = f.copy(saving = true, message = null)
        viewModelScope.launch {
            runCatching {
                // A sign-in email only moves once Firebase confirms it (the link it emails is opened),
                // so until then the profile keeps the email the person still signs in with.
                val linkSent = emailChanged && profile.email.isNotBlank() && container.auth.changeEmail(profile.email)
                container.profiles.saveProfile(if (linkSent) profile.copy(email = existing.email) else profile)
                _pendingEmail.value = if (linkSent) profile.email else null
            }
                .onSuccess { _form.value = _form.value.copy(saving = false); _saved.value = true; onSaved() }
                .onFailure { _form.value = _form.value.copy(saving = false, message = it.userMessage()) }
        }
    }

    /** Saves which notifications pop up on this phone. */
    fun savePrefs(prefs: NotificationPrefs) {
        val profile = (state.value as? ProfileState.Ready)?.profile ?: return
        viewModelScope.launch { runCatching { container.profiles.saveProfile(profile.copy(prefs = prefs)) } }
    }

    /** Whether deleting asks for the password first: Firebase wants a fresh sign-in to delete the login. */
    val needsPasswordToDelete: Boolean get() = container.auth.needsPasswordToDelete()

    /**
     * Deletes the profile, everything only it owns and its sign-in login, then signs out. When
     * Firebase wants a fresh sign-in, [password] is checked before anything is deleted, so the
     * login isn't left behind in Firebase Authentication.
     */
    fun deleteProfile(password: String? = null) {
        val profile = (state.value as? ProfileState.Ready)?.profile ?: return
        val askPassword = container.auth.needsPasswordToDelete()
        if (askPassword && password.isNullOrBlank()) {
            _form.value = _form.value.copy(message = "Enter your password to delete your profile.")
            return
        }
        _form.value = _form.value.copy(saving = true, message = null)
        viewModelScope.launch {
            runCatching {
                if (askPassword) container.auth.confirmPassword(password.orEmpty())
                container.profiles.deleteProfile(profile, container.clock.now())
            }
                .onSuccess {
                    runCatching { container.auth.deleteAccount() }
                    container.auth.signOut()
                    _form.value = ProfileFormState()
                }
                .onFailure { _form.value = _form.value.copy(saving = false, message = it.userMessage()) }
        }
    }

    /** Switches the role the app is viewed as. Ignored if the profile may not use [role]. */
    fun switchRole(role: Role) {
        val profile = (state.value as? ProfileState.Ready)?.profile ?: return
        if (role !in profile.availableRoles || role == profile.activeRole) return
        viewModelScope.launch { runCatching { container.profiles.saveProfile(profile.copy(activeRole = role)) } }
    }

    private val _deviceMessage = MutableStateFlow<String?>(null)

    private val _logOutError = MutableStateFlow<String?>(null)

    /** Why "Log out of all devices" didn't go through (shown on Profile), or null. */
    val logOutError: StateFlow<String?> = _logOutError.asStateFlow()

    /** Result of the last Trusted devices action, or an error. */
    val deviceMessage: StateFlow<String?> = _deviceMessage.asStateFlow()

    /**
     * Stops trusting a phone. Another phone is signed out next time it starts; this phone stays
     * signed in now but asks for the password next launch.
     */
    fun removeDevice(device: TrustedDevice) {
        if (device.deviceId == container.deviceId) removingThisPhone = true
        viewModelScope.launch {
            runCatching { container.profiles.removeDevice(device.deviceId) }
                .onFailure { removingThisPhone = false }
                .onSuccess {
                    _deviceMessage.value = if (device.deviceId == container.deviceId) "This phone is no longer trusted. You'll sign in next time."
                    else "${device.model.ifBlank { "That phone" }} removed. It will be signed out next time it opens."
                }
                .onFailure { _deviceMessage.value = it.userMessage() }
        }
    }

    /** Trusts this phone again so it stays signed in. */
    fun trustThisPhone() {
        val profile = (state.value as? ProfileState.Ready)?.profile ?: return
        viewModelScope.launch {
            runCatching { container.profiles.trustDevice(TrustedDevice(container.deviceId, profile.id, container.deviceName, container.clock.now())) }
                .onSuccess { _deviceMessage.value = "This phone is trusted. You'll stay signed in." }
                .onFailure { _deviceMessage.value = it.userMessage() }
        }
    }

    /**
     * "Log out of all devices": first stops trusting every phone for this person, including this
     * one, then marks the profile so every phone that has the app open signs out, trusted or not,
     * then signs out here. Phones that are closed start on Welcome next time, since none is
     * trusted any more. If the phones can't all be removed (e.g. no connection), nobody is signed
     * out and [logOutError] says why.
     */
    fun logOutEverywhere() {
        val profile = (state.value as? ProfileState.Ready)?.profile ?: return
        if (loggingOutEverywhere) return
        loggingOutEverywhere = true
        _logOutError.value = null
        viewModelScope.launch {
            runCatching {
                withTimeoutOrNull(SERVER_WAIT_MS) {
                    container.profiles.removeAllDevices(profile.id)
                    container.profiles.signOutEverywhere(profile.id, container.clock.now())
                } ?: throw AuthException(AuthError.NETWORK)
            }
                .onSuccess { container.auth.signOut() }
                .onFailure { _logOutError.value = it.userMessage() }
            loggingOutEverywhere = false
        }
    }

    /** Signs out of this phone and stops trusting it (Figma E-SIGNOUT). */
    fun signOut() {
        viewModelScope.launch {
            // Without a connection, don't keep the person waiting: the phone is forgotten when the
            // server is reached, and this phone starts on Welcome either way.
            runCatching { withTimeoutOrNull(SERVER_WAIT_MS) { container.profiles.removeDevice(container.deviceId) } }
            container.auth.signOut()
        }
    }
}
