/*
 * TrustedDevices.kt (Tausif)
 *
 * Profile > Trusted devices: the phones that stay signed in, with Remove (US 01.09.01).
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
