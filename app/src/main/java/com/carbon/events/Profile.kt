/*
 * Profile.kt (Rayner)
 *
 * Profiles: first-launch setup, the profile page, editing details and switching
 * between entrant / owner / admin.
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(vm: ProfileViewModel) {
    val form by vm.form.collectAsStateWithLifecycle()
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    TwilightSky(variant = SkyVariant.SPLASH) {
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                SceneArt(SceneTheme.SUNSET, Modifier.fillMaxSize())
                LogoLockup(Modifier.statusBarsPadding().padding(start = 20.dp, top = 20.dp))
            }
            Column(
                Modifier
                    // Overlaps the hero by 28dp; extra bottom padding keeps the sheet flush with the screen edge.
                    .offset(y = (-28).dp)
                    .fillMaxWidth()
                    .heightIn(min = screenHeight - 244.dp)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(EventsColors.Surface)
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 52.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Fair Camping for Every Camper", style = EventsType.Hero, color = EventsColors.Text)
                Text(
                    "Popular campsites go to a lottery, not to whoever refreshes fastest. Tell us who you are once and this phone will remember you.",
                    style = EventsType.Body, color = EventsColors.TextSecondary,
                )
                EventsTextField(form.name, vm::onNameChange, "Full name", placeholder = "Adam Kent", error = form.errors[Field.NAME], leadingIcon = R.drawable.ic_person)
                EventsTextField(form.email, vm::onEmailChange, "Email", placeholder = "you@example.com", error = form.errors[Field.EMAIL], leadingIcon = R.drawable.ic_mail, keyboardType = KeyboardType.Email)
                EventsTextField(form.phone, vm::onPhoneChange, "Phone (optional)", placeholder = "+1 780 555 0142", error = form.errors[Field.PHONE], leadingIcon = R.drawable.ic_phone, keyboardType = KeyboardType.Phone)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EventsIcon(R.drawable.ic_smartphone, size = 16.dp, tint = EventsColors.Green)
                    Text("No username or password. Your profile is linked to this device.", style = EventsType.BodySmall, color = EventsColors.TextSecondary)
                }
                if (form.message != null) Text(form.message!!, style = EventsType.BodySmall, color = EventsColors.Danger, textAlign = TextAlign.Start)
                PrimaryButton("Continue", { vm.save() }, Modifier.fillMaxWidth().testTag("setup_continue"), loading = form.saving)
            }
        }
    }
}

private fun Role.label() = when (this) {
    Role.ENTRANT -> "Entrant"
    Role.OWNER -> "Owner"
    Role.ADMIN -> "Admin"
}

/**
 * The Profile screen.
 *
 * @param onEdit opens Edit Profile.
 */
@Composable
fun ProfileScreen(profile: UserProfile, vm: ProfileViewModel, onEdit: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = PageMargin).padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Profile", style = EventsType.Headline, color = EventsColors.Text)
        DuskCard(Modifier.fillMaxWidth(), spacing = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(profile.initials, size = 56.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(profile.name, style = EventsType.Title, color = EventsColors.Text, modifier = Modifier.testTag("profile_name"), maxLines = 1)
                    MetaItem(R.drawable.ic_shield, "Trusted device", EventsColors.TextSecondary)
                }
                SecondaryButton("Edit", onEdit, Modifier.testTag("profile_edit"), icon = R.drawable.ic_edit, size = ButtonSize.SMALL)
            }
            CardDivider()
            MetaItem(R.drawable.ic_mail, profile.email, iconTint = EventsColors.Primary)
            MetaItem(R.drawable.ic_phone, profile.phone.ifBlank { "No phone added" }, iconTint = EventsColors.Primary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Viewing as", style = EventsType.LabelSmall, color = EventsColors.TextSoft)
            val roles = profile.availableRoles
            SegmentedControl(roles.map { it.label() }, roles.indexOf(profile.activeRole).coerceAtLeast(0), { vm.switchRole(roles[it]) }, tagPrefix = "role_")
        }
        DuskCard(Modifier.fillMaxWidth(), padding = PaddingValues(6.dp)) {
            SettingsRow(R.drawable.ic_smartphone, "Identified by this device", "No username or password · ID ${vm.deviceId.take(8)}…")
        }
    }
}

/** A settings row: 36dp icon tile, SemiBold 14 title, optional supporting line. */
@Composable
private fun SettingsRow(icon: Int, title: String, supporting: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconTile(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = EventsType.BodyStrong, color = EventsColors.Text)
            if (supporting != null) Text(supporting, style = EventsType.BodySmall, color = EventsColors.TextSecondary)
        }
    }
}

/**
 * Edit Profile: update name, email and phone (US 01.07.03).
 *
 * @param onDone called after saving or when backing out.
 */
@Composable
fun EditProfileScreen(profile: UserProfile, vm: ProfileViewModel, onDone: () -> Unit) {
    LaunchedEffect(profile.deviceId) { vm.startEditing(profile) }
    val form by vm.form.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().imePadding()) {
        BackTopBar("Edit Profile", onDone, Modifier.statusBarsPadding())
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PageMargin).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) { Avatar(profile.initials, size = 72.dp) }
            EventsTextField(form.name, vm::onNameChange, "Full name", error = form.errors[Field.NAME], leadingIcon = R.drawable.ic_person)
            EventsTextField(form.email, vm::onEmailChange, "Email", error = form.errors[Field.EMAIL], leadingIcon = R.drawable.ic_mail, keyboardType = KeyboardType.Email)
            EventsTextField(form.phone, vm::onPhoneChange, "Phone (optional)", error = form.errors[Field.PHONE], leadingIcon = R.drawable.ic_phone, keyboardType = KeyboardType.Phone)
            if (form.message != null) Text(form.message!!, style = EventsType.BodySmall, color = EventsColors.Danger)
            Box(Modifier.height(8.dp))
        }
        ActionBar {
            SecondaryButton("Cancel", onDone, Modifier.weight(1f))
            PrimaryButton("Save changes", { vm.save(onDone) }, Modifier.weight(1f).testTag("profile_save"), loading = form.saving)
        }
    }
}

/** Whether the device's profile has loaded, and if so whether it exists. */
sealed interface ProfileState {
    /** Still loading from the server. */
    data object Loading : ProfileState

    /** No profile for this device yet: show first-launch setup. */
    data object Missing : ProfileState

    /** The profile exists. */
    data class Ready(val profile: UserProfile) : ProfileState
}

/** State of the profile form (setup or edit). */
data class ProfileFormState(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val errors: Map<Field, String> = emptyMap(),
    val saving: Boolean = false,
    val message: String? = null,
)

/**
 * View model for the current device's profile.
 *
 * @param container app dependencies.
 */
class ProfileViewModel(private val container: AppContainer) : ViewModel() {

    /** The device's profile as it loads and changes. */
    val state: StateFlow<ProfileState> = container.profiles.observeProfile(container.deviceId)
        .map { if (it == null) ProfileState.Missing else ProfileState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileState.Loading)

    private val _form = MutableStateFlow(ProfileFormState())

    /** The setup / edit form. */
    val form: StateFlow<ProfileFormState> = _form.asStateFlow()

    /** The device ID shown on the profile screen ("identified by this device"). */
    val deviceId: String get() = container.deviceId

    /** Pre-fills the form from [profile] before editing. */
    fun startEditing(profile: UserProfile) {
        _form.value = ProfileFormState(profile.name, profile.email, profile.phone)
    }

    /** Updates form fields; clears the matching error. */
    fun onNameChange(v: String) { _form.value = _form.value.copy(name = v, errors = _form.value.errors - Field.NAME) }
    fun onEmailChange(v: String) { _form.value = _form.value.copy(email = v, errors = _form.value.errors - Field.EMAIL) }
    fun onPhoneChange(v: String) { _form.value = _form.value.copy(phone = v, errors = _form.value.errors - Field.PHONE) }

    /**
     * Validates and saves the form. Creates the profile on first launch, or updates the
     * existing one, keeping its role and admin flag.
     *
     * @param onSaved called after a successful save.
     */
    fun save(onSaved: () -> Unit = {}) {
        val f = _form.value
        val errors = ProfileValidator.validate(f.name, f.email, f.phone)
        if (errors.isNotEmpty()) {
            _form.value = f.copy(errors = errors)
            return
        }
        val existing = (state.value as? ProfileState.Ready)?.profile
        // In demo mode (no Firebase) new profiles may use the admin role so it can be shown;
        // on the real server admin rights are granted by hand in Firestore.
        val profile = (existing ?: UserProfile(container.deviceId, "", "", isAdmin = !container.usingServer, createdAt = container.clock.now()))
            .copy(name = f.name.trim(), email = f.email.trim(), phone = f.phone.trim())
        _form.value = f.copy(saving = true, message = null)
        viewModelScope.launch {
            runCatching { container.profiles.saveProfile(profile) }
                .onSuccess { _form.value = _form.value.copy(saving = false); onSaved() }
                .onFailure { _form.value = _form.value.copy(saving = false, message = it.userMessage()) }
        }
    }

    /** Switches the role the app is viewed as. Ignored if the profile may not use [role]. */
    fun switchRole(role: Role) {
        val profile = (state.value as? ProfileState.Ready)?.profile ?: return
        if (role !in profile.availableRoles || role == profile.activeRole) return
        viewModelScope.launch { runCatching { container.profiles.saveProfile(profile.copy(activeRole = role)) } }
    }
}
