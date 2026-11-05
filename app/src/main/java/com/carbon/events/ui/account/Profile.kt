/*
 * Profile.kt (Rayner)
 *
 * Profiles: the profile page, editing details and photo, notification settings, trusted
 * devices, signing out, deleting the profile and switching between entrant / owner / admin.
 * Signing up and in live in Account.kt; help and policies in Help.kt.
 *
 * Outstanding issues: the email is the sign-in email, so it can't be changed here.
 */
package com.carbon.events

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.navigation.compose.composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What a signed-out phone shows: the account flow (Figma "0 · Launch & Account", US 01.09.01).
 * Kept under this name because MainActivity's WelcomeSetup() calls it.
 *
 * @param vm the profile view model (tells whether an account is signed in without a profile).
 */
@Composable
fun SetupScreen(vm: ProfileViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    AccountFlow(LocalAppSession.current.container, (state as? ProfileState.Missing)?.account)
}

private fun Role.label() = when (this) {
    Role.ENTRANT -> "Entrant"
    Role.OWNER -> "Owner"
    Role.ADMIN -> "Admin"
}

/**
 * The Profile screen (Figma E-PROFILE, O-PROFILE, A-ACCOUNT): who you are, the role you're viewing
 * as (only admins can switch, US 03.11.01; entrants and owners see the other roles greyed out),
 * a notifications switch, help, policies, trusted devices, sign out and delete.
 *
 * @param onEdit opens Edit Profile.
 */
@Composable
fun ProfileScreen(profile: UserProfile, vm: ProfileViewModel, onEdit: () -> Unit, onOpen: (ProfilePage) -> Unit = {}) {
    val devices by vm.devices.collectAsStateWithLifecycle()
    val trusted = devices.any { it.deviceId == vm.deviceId }
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val askPermission = rememberNotificationPermission {}
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = PageMargin).padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(if (profile.isAdmin) "Account" else "Profile", style = EventsType.Headline, color = EventsColors.Text)
        val saved by vm.saved.collectAsStateWithLifecycle()
        LaunchedEffect(saved) { if (saved) { kotlinx.coroutines.delay(3_000); vm.savedShown() } }
        if (saved) Row(
            Modifier.fillMaxWidth().clip(EventsShapes.Medium).background(EventsColors.GreenContainer).padding(14.dp).testTag("profile_saved"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EventsIcon(R.drawable.ic_check, tint = EventsColors.Green, size = 18.dp)
            Text("Profile updated", style = EventsType.BodyStrong, color = EventsColors.Green)
        }
        DuskCard(Modifier.fillMaxWidth(), spacing = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileAvatar(profile, 56.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(profile.name, style = EventsType.Title, color = EventsColors.Text, modifier = Modifier.testTag("profile_name"), maxLines = 1)
                    val badge = when {
                        profile.isAdmin -> "Administrator"
                        profile.activeRole == Role.OWNER -> "Campsite owner"
                        trusted -> "Trusted device"
                        else -> "Entrant"
                    }
                    MetaItem(R.drawable.ic_shield, badge, EventsColors.TextSecondary)
                }
                if (!profile.isAdmin) SecondaryButton("Edit", onEdit, Modifier.testTag("profile_edit"), icon = R.drawable.ic_edit, size = ButtonSize.SMALL)
            }
            CardDivider()
            MetaItem(R.drawable.ic_mail, profile.email, iconTint = EventsColors.Primary)
            if (profile.phone.isNotBlank()) MetaItem(R.drawable.ic_phone, profile.phone, iconTint = EventsColors.Primary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (profile.isAdmin) "View the app as" else "Viewing as", style = EventsType.LabelSmall, color = EventsColors.TextSoft)
            RoleControl(profile.activeRole, profile.availableRoles, vm::switchRole)
        }
        DuskCard(Modifier.fillMaxWidth(), padding = PaddingValues(6.dp)) {
            val on = !profile.prefs.optOut
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).testTag("profile_notifications"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IconTile(R.drawable.ic_bell)
                Text("Notifications", style = EventsType.BodyStrong, color = EventsColors.Text, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(
                    on, { now -> vm.savePrefs(profile.prefs.copy(optOut = !now)); if (now) askPermission() },
                    Modifier.testTag("profile_notifications_switch"),
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = EventsColors.OnPrimary, checkedTrackColor = EventsColors.Primary,
                        uncheckedThumbColor = EventsColors.TextSecondary, uncheckedTrackColor = EventsColors.SurfaceSoft,
                    ),
                )
            }
            if (!profile.isAdmin) {
                LinkRow(R.drawable.ic_info, "Help & contact us", "profile_help") { onOpen(ProfilePage.HELP) }
                LinkRow(R.drawable.ic_policy, "Policies & guidelines", "profile_policies") { onOpen(ProfilePage.POLICIES) }
            }
            LinkRow(R.drawable.ic_smartphone, "Trusted devices", "profile_devices") { onOpen(ProfilePage.DEVICES) }
            ActionRow(R.drawable.ic_logout, "Sign out", "profile_sign_out") { confirmSignOut = true }
            if (!profile.isAdmin) ActionRow(R.drawable.ic_delete, "Delete my profile", "profile_delete", danger = true) { confirmDelete = true }
        }
    }
    if (confirmSignOut) ConfirmDialog(
        R.drawable.ic_logout, "Sign out?", "You'll need your email and password to sign in on this phone again.",
        "Sign out", onConfirm = { confirmSignOut = false; vm.signOut() }, onDismiss = { confirmSignOut = false },
    )
    if (confirmDelete) DeleteProfileDialog(profile, vm) { confirmDelete = false }
}

/**
 * Figma "Viewing as": Entrant · Owner · Admin. Roles the person can't use are greyed out and
 * can't be tapped, so entrants stay entrants and owners stay owners; admins can pick any.
 */
@Composable
private fun RoleControl(active: Role, allowed: List<Role>, onSelect: (Role) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(50.dp).clip(EventsShapes.Pill).background(EventsColors.Tint)
            .border(1.dp, EventsColors.TintStroke, EventsShapes.Pill).padding(4.dp).testTag("role_control"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Role.entries.forEach { role ->
            val selected = role == active
            val enabled = role in allowed
            Box(
                Modifier.weight(1f).height(40.dp).clip(EventsShapes.Pill)
                    .background(if (selected) EventsColors.Surface else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(enabled = enabled && !selected) { onSelect(role) }.testTag("role_${role.name.lowercase()}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    role.label(), style = EventsType.LabelSmall, maxLines = 1,
                    color = when { selected -> EventsColors.Text; enabled -> EventsColors.TextSoft; else -> EventsColors.TextTertiary.copy(alpha = 0.55f) },
                )
            }
        }
    }
}

/** Figma E-DELETE: delete your profile after typing your email to confirm. */
@Composable
private fun DeleteProfileDialog(profile: UserProfile, vm: ProfileViewModel, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) { DeleteProfileCard(profile, vm, onDismiss) }
}

/** The Delete Your Profile card shown in [DeleteProfileDialog]. */
@Composable
fun DeleteProfileCard(profile: UserProfile, vm: ProfileViewModel, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val form by vm.form.collectAsStateWithLifecycle()
    val matches = typed.trim().equals(profile.email.trim(), ignoreCase = true)
    run {
        DuskCard(Modifier.fillMaxWidth().testTag("delete_dialog"), padding = PaddingValues(20.dp), spacing = 12.dp) {
            IconTile(R.drawable.ic_delete, bg = EventsColors.DangerContainer, tint = EventsColors.Danger, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("Delete Your Profile?", style = EventsType.Title, color = EventsColors.Text, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(
                "This permanently removes your profile, waitlist entries and booking history. Campsites you own are removed with their lotteries.",
                style = EventsType.BodySmall, color = EventsColors.TextSecondary,
            )
            EventsTextField(typed, { typed = it }, "Type your email to confirm", placeholder = profile.email, keyboardType = KeyboardType.Email, modifier = Modifier.testTag("delete_confirm_text"))
            form.message?.let { Text(it, style = EventsType.BodySmall, color = EventsColors.Danger) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Cancel", onDismiss, Modifier.weight(1f), size = ButtonSize.SMALL)
                DangerButton("Delete profile", vm::deleteProfile, Modifier.weight(1f).testTag("delete_profile"), size = ButtonSize.SMALL, enabled = matches, loading = form.saving)
            }
        }
    }
}

/** Pages reachable from Profile. Every role's area registers them with [profilePages]. */
enum class ProfilePage(val path: String) { EDIT("edit"), DEVICES("devices"), HELP("help"), POLICIES("policies") }

/**
 * Registers Profile and its pages under [base] (e.g. "profile", "owner/profile") so the
 * entrant, owner and admin areas share them.
 */
fun androidx.navigation.NavGraphBuilder.profilePages(
    base: String,
    nav: androidx.navigation.NavHostController,
    profile: () -> UserProfile,
    vm: ProfileViewModel,
) {
    composable(base) {
        ProfileScreen(profile(), vm, onEdit = { nav.navigate("$base/${ProfilePage.EDIT.path}") }, onOpen = { nav.navigate("$base/${it.path}") })
    }
    composable("$base/${ProfilePage.EDIT.path}") { EditProfileScreen(profile(), vm, onDone = { nav.popBackStack() }) }
    composable("$base/${ProfilePage.DEVICES.path}") { TrustedDevicesScreen(vm, onBack = { nav.popBackStack() }) }
    composable("$base/${ProfilePage.HELP.path}") { HelpScreen(onBack = { nav.popBackStack() }) }
    composable("$base/${ProfilePage.POLICIES.path}") { PoliciesScreen(onBack = { nav.popBackStack() }) }
}

/** A tappable menu row with a chevron (Figma Profile list). */
@Composable
private fun LinkRow(icon: Int, title: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(EventsShapes.Medium).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(icon)
        Text(title, style = EventsType.BodyStrong, color = EventsColors.Text, modifier = Modifier.weight(1f))
        EventsIcon(R.drawable.ic_chevron_right, tint = EventsColors.TextSecondary, size = 20.dp)
    }
}

/** A menu row without a chevron (Sign out, Delete my profile). */
@Composable
private fun ActionRow(icon: Int, title: String, tag: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(EventsShapes.Medium).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (danger) IconTile(icon, bg = EventsColors.DangerContainer, tint = EventsColors.Danger) else IconTile(icon)
        Text(title, style = EventsType.BodyStrong, color = if (danger) EventsColors.Danger else EventsColors.Text)
    }
}

/** The person's photo, or their initials when they haven't added one. */
@Composable
fun ProfileAvatar(profile: UserProfile, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    val photo = remember(profile.photoBase64) { decodeBase64Image(profile.photoBase64) }
    if (photo == null) Avatar(profile.initials, modifier, size = size)
    else Image(
        photo, "Photo of ${profile.name}",
        modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape),
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
    )
}

/**
 * Profile > Trusted devices: the phones this person trusts to stay signed in (US 01.09.01, Figma
 * S-TRUST "Remove trusted devices any time in Profile"), each with Remove. Signing in on another
 * phone with "Remember me" adds it here.
 */
@Composable
fun TrustedDevicesScreen(vm: ProfileViewModel, onBack: () -> Unit) {
    val devices by vm.devices.collectAsStateWithLifecycle()
    val message by vm.deviceMessage.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<TrustedDevice?>(null) }
    val trustedHere = devices.any { it.deviceId == vm.deviceId }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackTopBar("Trusted devices", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = PageMargin).padding(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("These stay signed in without a password. Remove one to sign it out.", style = EventsType.Body, color = EventsColors.TextSecondary)
            DuskCard(Modifier.fillMaxWidth().testTag("trusted_phones"), padding = PaddingValues(6.dp)) {
                if (devices.isEmpty()) Text("No trusted phones.", style = EventsType.BodySmall, color = EventsColors.TextTertiary, modifier = Modifier.padding(8.dp))
                devices.forEach { d ->
                    val here = d.deviceId == vm.deviceId
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).testTag("device_${d.deviceId}"),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconTile(R.drawable.ic_smartphone)
                        Column(Modifier.weight(1f)) {
                            Text(d.model.ifBlank { "Android phone" }, style = EventsType.BodyStrong, color = EventsColors.Text, maxLines = 1)
                            Text(
                                (if (here) "This phone · " else "") + (if (d.addedAt > 0) "Added ${formatDay(d.addedAt)}" else "Trusted"),
                                style = EventsType.Caption, color = EventsColors.TextTertiary,
                            )
                        }
                        TextLinkButton("Remove", { confirm = d }, Modifier.testTag("device_remove_${d.deviceId}"), color = EventsColors.Danger)
                    }
                }
            }
            if (!trustedHere) {
                Text("This phone isn't trusted, so you'll sign in again next time.", style = EventsType.BodySmall, color = EventsColors.TextSecondary)
                SecondaryButton("Trust this phone", vm::trustThisPhone, Modifier.fillMaxWidth().testTag("device_trust_here"), icon = R.drawable.ic_shield)
            }
            message?.let { Text(it, style = EventsType.BodySmall, color = EventsColors.Text, modifier = Modifier.testTag("device_message")) }
        }
    }
    confirm?.let { d ->
        val here = d.deviceId == vm.deviceId
        ConfirmDialog(
            R.drawable.ic_smartphone, if (here) "Stop trusting this phone?" else "Remove ${d.model.ifBlank { "this phone" }}?",
            if (here) "You'll stay signed in for now, but you'll need your password next time you open Events."
            else "It will be signed out the next time it opens Events.",
            "Remove", onConfirm = { confirm = null; vm.removeDevice(d) }, onDismiss = { confirm = null }, destructive = true,
        )
    }
}

/**
 * Edits name, phone and photo. The sign-in email is displayed read-only; email changes are not
 * implemented by this screen. This is partial coverage of US 01.07.03.
 *
 * @param onDone called after saving or when backing out.
 */
@Composable
fun EditProfileScreen(profile: UserProfile, vm: ProfileViewModel, onDone: () -> Unit) {
    LaunchedEffect(profile.id) { vm.startEditing(profile) }
    val form by vm.form.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().imePadding()) {
        BackTopBar("Edit Profile", onDone, Modifier.statusBarsPadding())
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PageMargin).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) vm.onPhoto(compressImageToBase64(context, uri, maxSide = 320, quality = 70))
            }
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ProfileAvatar(profile.copy(photoBase64 = form.photoBase64), 72.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextLinkButton(if (form.photoBase64 == null) "Add photo" else "Change photo", {
                        pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, Modifier.testTag("profile_photo"))
                    if (form.photoBase64 != null) TextLinkButton("Remove", { vm.onPhoto(null) }, color = EventsColors.Danger)
                }
            }
            EventsTextField(form.name, vm::onNameChange, "Full name", Modifier.testTag("profile_name"), error = form.errors[Field.NAME], leadingIcon = R.drawable.ic_person)
            EventsTextField(
                profile.email, {}, "Email", leadingIcon = R.drawable.ic_mail, readOnly = true,
                supporting = "This is your sign-in email, so it can't be changed.", modifier = Modifier.testTag("profile_email"),
            )
            EventsTextField(form.phone, vm::onPhoneChange, "Phone (optional)", Modifier.testTag("profile_phone"), error = form.errors[Field.PHONE], leadingIcon = R.drawable.ic_phone, keyboardType = KeyboardType.Phone)
            if (form.message != null) Text(form.message!!, style = EventsType.BodySmall, color = EventsColors.Danger)
            Box(Modifier.height(8.dp))
        }
        ActionBar {
            SecondaryButton("Cancel", onDone, Modifier.weight(1f))
            PrimaryButton("Save changes", { vm.save(onDone) }, Modifier.weight(1f).testTag("profile_save"), loading = form.saving)
        }
    }
}

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
    val phone: String = "",
    val photoBase64: String? = null,
    val errors: Map<Field, String> = emptyMap(),
    val saving: Boolean = false,
    val message: String? = null,
)

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
            // Launch check: a phone that isn't trusted for the signed-in person starts signed out.
            runCatching {
                val account = container.auth.observeAccount().first()
                if (account != null) {
                    val profile = container.profiles.observeProfileForAccount(account.uid).first()
                    val trustedFor = container.profiles.observeUserIdForDevice(container.deviceId).first()
                    if (profile != null && trustedFor != profile.id) container.auth.signOut()
                }
            }
            trustChecked.value = true
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
        _form.value = ProfileFormState(profile.name, profile.phone, profile.photoBase64)
    }

    /** Updates the name field and clears its error. */
    fun onNameChange(v: String) { _form.value = _form.value.copy(name = v, errors = _form.value.errors - Field.NAME) }

    /** Sets (or with null removes) the new profile photo. */
    fun onPhoto(base64: String?) { _form.value = _form.value.copy(photoBase64 = base64) }

    /** Updates the phone field and clears its error. */
    fun onPhoneChange(v: String) { _form.value = _form.value.copy(phone = v, errors = _form.value.errors - Field.PHONE) }

    /**
     * Validates and saves the edited details, keeping role, admin flag and account (US 01.07.03).
     *
     * @param onSaved called after a successful save.
     */
    fun save(onSaved: () -> Unit = {}) {
        val f = _form.value
        val existing = (state.value as? ProfileState.Ready)?.profile ?: return
        // The email is the sign-in email, so only name, phone and photo change here.
        val errors = ProfileValidator.validate(f.name, existing.email, f.phone)
        if (errors.isNotEmpty()) {
            _form.value = f.copy(errors = errors)
            return
        }
        val profile = existing.copy(name = f.name.trim(), phone = f.phone.trim(), photoBase64 = f.photoBase64)
        _form.value = f.copy(saving = true, message = null)
        viewModelScope.launch {
            runCatching { container.profiles.saveProfile(profile) }
                .onSuccess { _form.value = _form.value.copy(saving = false); _saved.value = true; onSaved() }
                .onFailure { _form.value = _form.value.copy(saving = false, message = it.userMessage()) }
        }
    }

    /** Saves which notifications pop up on this phone. */
    fun savePrefs(prefs: NotificationPrefs) {
        val profile = (state.value as? ProfileState.Ready)?.profile ?: return
        viewModelScope.launch { runCatching { container.profiles.saveProfile(profile.copy(prefs = prefs)) } }
    }

    /** Deletes the profile, everything only it owns and its sign-in account, then signs out. */
    fun deleteProfile() {
        val profile = (state.value as? ProfileState.Ready)?.profile ?: return
        _form.value = _form.value.copy(saving = true, message = null)
        viewModelScope.launch {
            runCatching { container.profiles.deleteProfile(profile) }
                .onSuccess {
                    // Firebase may want a fresh sign-in before deleting the login; the profile is gone either way.
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

    /** Result of the last Trusted devices action, or an error. */
    val deviceMessage: StateFlow<String?> = _deviceMessage.asStateFlow()

    /**
     * Stops trusting a phone. Another phone is signed out next time it starts; this phone stays
     * signed in now but asks for the password next launch.
     */
    fun removeDevice(device: TrustedDevice) {
        viewModelScope.launch {
            runCatching { container.profiles.removeDevice(device.deviceId) }
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

    /** Signs out of this phone and stops trusting it (Figma E-SIGNOUT). */
    fun signOut() {
        viewModelScope.launch {
            runCatching { container.profiles.removeDevice(container.deviceId) }
            container.auth.signOut()
        }
    }
}
