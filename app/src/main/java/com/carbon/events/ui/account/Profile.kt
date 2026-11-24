/*
 * Profile.kt (Tausif)
 *
 * The profile page: details, role, notifications, links, signing out and deleting the
 * profile. Signing up and in live in Account.kt; help and policies in Help.kt.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first

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
    val logOutError by vm.logOutError.collectAsStateWithLifecycle()
    val devices by vm.devices.collectAsStateWithLifecycle()
    val trusted = devices.any { it.deviceId == vm.deviceId }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var confirmSignOutAll by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
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
            MetaItem(R.drawable.ic_mail, profile.email.ifBlank { "Signed in with this phone" }, iconTint = EventsColors.Primary)
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
            ActionRow(R.drawable.ic_smartphone, "Log out of all devices", "profile_sign_out_all") { confirmSignOutAll = true }
            logOutError?.let { Text(it, style = EventsType.Caption, color = EventsColors.Danger, modifier = Modifier.testTag("profile_sign_out_all_error")) }
            if (!profile.isAdmin) ActionRow(R.drawable.ic_delete, "Delete my profile", "profile_delete", danger = true) { confirmDelete = true }
        }
    }
    if (confirmSignOut) ConfirmDialog(
        R.drawable.ic_logout, "Sign out?",
        if (profile.email.isBlank()) "This profile only lives on this phone. Add an email in Edit profile first, or you won't be able to sign back in."
        else "You'll need your email and password to sign in on this phone again.",
        "Sign out", onConfirm = { confirmSignOut = false; vm.signOut() }, onDismiss = { confirmSignOut = false },
    )
    if (confirmSignOutAll) ConfirmDialog(
        R.drawable.ic_smartphone, "Log out of all devices?",
        "Every phone signed in as you, including this one, signs out and stops being trusted. You'll need your email and password to sign in again.",
        "Log out all", onConfirm = { confirmSignOutAll = false; vm.logOutEverywhere() }, onDismiss = { confirmSignOutAll = false }, destructive = true,
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
    var typed by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    // Firebase wants the password again to delete a login signed in a while ago.
    val askPassword = remember { vm.needsPasswordToDelete }
    val form by vm.form.collectAsStateWithLifecycle()
    // Type your email to confirm, or DELETE if this is a phone-only profile with no email.
    val word = profile.email.trim().ifBlank { "DELETE" }
    val matches = typed.trim().equals(word, ignoreCase = true)
    run {
        DuskCard(Modifier.fillMaxWidth().testTag("delete_dialog"), padding = PaddingValues(20.dp), spacing = 12.dp) {
            IconTile(R.drawable.ic_delete, bg = EventsColors.DangerContainer, tint = EventsColors.Danger, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("Delete Your Profile?", style = EventsType.Title, color = EventsColors.Text, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(
                "This permanently removes your profile, waitlist entries and booking history. Campsites you own are removed with their lotteries.",
                style = EventsType.BodySmall, color = EventsColors.TextSecondary,
            )
            EventsTextField(typed, { typed = it }, if (profile.email.isBlank()) "Type DELETE to confirm" else "Type your email to confirm", placeholder = word, keyboardType = KeyboardType.Email, modifier = Modifier.testTag("delete_confirm_text"))
            if (askPassword) EventsTextField(password, { password = it }, "Password", placeholder = "Your password", isPassword = true, modifier = Modifier.testTag("delete_password"))
            form.message?.let { Text(it, style = EventsType.BodySmall, color = EventsColors.Danger) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Cancel", onDismiss, Modifier.weight(1f), size = ButtonSize.SMALL)
                DangerButton(
                    "Delete profile", { vm.deleteProfile(password.takeIf { askPassword }) }, Modifier.weight(1f).testTag("delete_profile"),
                    size = ButtonSize.SMALL, enabled = matches && (!askPassword || password.isNotBlank()), loading = form.saving,
                )
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
