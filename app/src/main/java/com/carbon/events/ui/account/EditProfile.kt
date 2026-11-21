/*
 * EditProfile.kt (Tausif)
 *
 * Edit profile: name, email, phone and photo (US 01.07.03).
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

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
                form.email, vm::onEmailChange, "Email", Modifier.testTag("profile_email"), error = form.errors[Field.EMAIL],
                leadingIcon = R.drawable.ic_mail, keyboardType = KeyboardType.Email,
                supporting = if (profile.email.isBlank()) "Optional. You sign in with this phone."
                    else "If you change it, we'll email a link to the new address. You'll sign in with it after you open the link.",
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
