/*
 * AccountScreens.kt (Tausif)
 *
 * The account screens: Welcome, Sign in, Create account, Your details, Terms and Trust
 * this device.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Numbered setup steps; keep progress indicators aligned with the reachable account screens. */
private const val SIGN_UP_STEPS = 4

/**
 * The account flow shown while nobody is signed in (or sign-up is unfinished).
 *
 * @param account an account signed in without a profile yet, or null.
 */
@Composable
fun AccountFlow(container: AppContainer, account: AuthAccount?) {
    // One view model for the whole flow: creating the account midway mustn't restart it.
    val vm = containerViewModel(container, key = "account") { AccountViewModel(it, account) }
    val s by vm.state.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(account) { vm.resume(account) }
    androidx.activity.compose.BackHandler(enabled = s.step != AccountStep.WELCOME, onBack = vm::back)
    when (s.step) {
        AccountStep.WELCOME -> WelcomeScreen(vm)
        AccountStep.SIGN_IN -> SignInScreen(s, vm)
        AccountStep.ACCOUNT -> CreateAccountScreen(s, vm)
        AccountStep.DETAILS -> DetailsScreen(s, vm, askPassword = account == null && !s.deviceOnly)
        AccountStep.TERMS -> TermsScreen(s, vm)
        AccountStep.TRUST -> TrustScreen(s, vm, container.deviceName)
    }
}

/** Figma S-WELCOME: night scene, "Fair Camping for Every Camper", Create an Account / I already have an account. */
@Composable
private fun WelcomeScreen(vm: AccountViewModel) {
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    TwilightSky(variant = SkyVariant.SPLASH) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("welcome")) {
            Box(Modifier.fillMaxWidth().height(screenHeight * 0.52f)) {
                SceneArt(SceneTheme.NIGHT, Modifier.fillMaxSize())
                LogoLockup(Modifier.statusBarsPadding().padding(start = 20.dp, top = 20.dp))
            }
            Column(
                Modifier.offset(y = (-28).dp).fillMaxWidth().heightIn(min = screenHeight * 0.48f + 28.dp)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(EventsColors.Surface)
                    .navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Fair Camping for Every Camper", style = EventsType.Hero, color = EventsColors.Text, textAlign = TextAlign.Center)
                Text(
                    "Popular campsites go to a lottery - not to whoever refreshes fastest. Join a waiting list, get notified if you win, book in a tap.",
                    style = EventsType.Body, color = EventsColors.TextSecondary, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                PrimaryButton("Create an Account", { vm.go(AccountStep.ACCOUNT) }, Modifier.fillMaxWidth().testTag("welcome_create"))
                TextLinkButton("I already have an account", { vm.go(AccountStep.SIGN_IN) }, Modifier.testTag("welcome_sign_in"))
            }
        }
    }
}

/** Figma S-START: email, password, Remember me, Forgot password?, Sign in, Google, Go back. */
@Composable
private fun SignInScreen(s: AccountState, vm: AccountViewModel) {
    TwilightSky(variant = SkyVariant.SCREEN) {
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = PageMargin).padding(top = 12.dp, bottom = 24.dp)) {
            LogoLockup()
            Spacer(Modifier.height(18.dp))
            Text("Welcome", style = EventsType.Headline, color = EventsColors.Text)
            Text("Create an account or sign in to continue.", style = EventsType.BodySmall, color = EventsColors.TextSecondary)
            Spacer(Modifier.height(28.dp))
            DuskCard(Modifier.fillMaxWidth().testTag("sign_in"), padding = androidx.compose.foundation.layout.PaddingValues(20.dp), spacing = 14.dp) {
                EventsTextField(s.email, vm::onEmail, "Email", placeholder = "you@example.com", keyboardType = KeyboardType.Email, modifier = Modifier.testTag("sign_in_email"))
                EventsTextField(s.password, vm::onPassword, "Password", placeholder = "Your password", isPassword = true, modifier = Modifier.testTag("sign_in_password"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TickBox(s.remember, vm::onRemember, "Remember me", Modifier.weight(1f).testTag("sign_in_remember"))
                    TextLinkButton("Forgot password?", {}, Modifier.testTag("sign_in_forgot")) // on hold: needs email codes
                }
                s.message?.let { Text(it, style = EventsType.BodySmall, color = EventsColors.Danger, modifier = Modifier.testTag("sign_in_error")) }
                PrimaryButton("Sign in", vm::signIn, Modifier.fillMaxWidth().testTag("sign_in_submit"), loading = s.working)
                GoogleButton(vm::google)
                s.note?.let { Note(it) }
                TextLinkButton("Go back", vm::back, Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

/** Figma S-ACCOUNT (step 1): continue with email (or Google, on hold). */
@Composable
private fun CreateAccountScreen(s: AccountState, vm: AccountViewModel) {
    StepScaffold(1, vm::back) {
        StatusTag("User account", TagTone.BLUE)
        IconBadge(R.drawable.ic_person)
        Text("Create Your User Account", style = EventsType.Headline, color = EventsColors.Text)
        Text("Search campsites, join lotteries and get notified the moment you win.", style = EventsType.Body, color = EventsColors.TextSecondary)
        Spacer(Modifier.height(6.dp))
        PrimaryButton("Continue with email", vm::useEmail, Modifier.fillMaxWidth().testTag("account_email"), icon = R.drawable.ic_mail)
        // US 01.09.01: entrants can use the app with just this phone, no username or password.
        SecondaryButton("Continue with this phone", vm::useThisPhone, Modifier.fillMaxWidth().testTag("account_device"), icon = R.drawable.ic_smartphone)
        Text("No password: this phone signs you in. You can add an email later in your profile.", style = EventsType.Caption, color = EventsColors.TextSecondary)
        GoogleButton(vm::google)
        s.note?.let { Note(it) }
    }
}

/** Figma S-SIGNUP (step 2): User / Campsite Owner, name, email, password, phone and age. */
@Composable
private fun DetailsScreen(s: AccountState, vm: AccountViewModel, askPassword: Boolean) {
    StepScaffold(2, vm::back, bottom = {
        PrimaryButton("Continue", vm::submitDetails, Modifier.fillMaxWidth().testTag("details_continue"), loading = s.working)
    }) {
        Text("Create Account as", style = EventsType.Headline, color = EventsColors.Text)
        SegmentedControl(listOf("User", "Campsite Owner"), if (s.role == Role.OWNER) 1 else 0, { vm.onRole(if (it == 1) Role.OWNER else Role.ENTRANT) }, tagPrefix = "details_role_")
        EventsTextField(s.name, vm::onName, "Full name", placeholder = "Adam Kent", error = s.errors[Field.NAME], modifier = Modifier.testTag("details_name"))
        // With "Continue with this phone" there is no password, and the email can be left out.
        EventsTextField(s.email, vm::onEmail, if (s.deviceOnly) "Email (optional)" else "Email", placeholder = "you@example.com", error = s.errors[Field.EMAIL], keyboardType = KeyboardType.Email, modifier = Modifier.testTag("details_email"))
        if (askPassword) {
            EventsTextField(s.password, vm::onPassword, "Password", placeholder = "Create a password", error = s.errors[Field.PASSWORD], isPassword = true, modifier = Modifier.testTag("details_password"))
            if (s.errors[Field.PASSWORD] == null) Text("8+ characters with at least one number", style = EventsType.Caption, color = EventsColors.TextSecondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EventsTextField(s.phone, vm::onPhone, "Phone (optional)", placeholder = "+1 780 555 0142", error = s.errors[Field.PHONE], keyboardType = KeyboardType.Phone, modifier = Modifier.weight(1f).testTag("details_phone"))
            EventsTextField(s.age, vm::onAge, "Age", placeholder = "21", error = s.errors[Field.AGE], keyboardType = KeyboardType.Number, modifier = Modifier.width(96.dp).testTag("details_age"))
        }
        Text("You must be 18+ to reserve a campsite", style = EventsType.Caption, color = EventsColors.TextSecondary)
        s.message?.let { Text(it, style = EventsType.BodySmall, color = EventsColors.Danger, modifier = Modifier.testTag("details_error")) }
    }
}

/** Figma S-TERMS (step 3 of 5 while Verify email is on hold). */
@Composable
private fun TermsScreen(s: AccountState, vm: AccountViewModel) {
    StepScaffold(3, vm::back, bottom = {
        TickBox(s.agreed, vm::onAgreed, "I have read and agree to the Terms of Service and Privacy Policy", Modifier.fillMaxWidth().testTag("terms_agree"))
        s.message?.let { Text(it, style = EventsType.BodySmall, color = EventsColors.Danger) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Decline", vm::declineTerms, Modifier.weight(0.75f).testTag("terms_decline"))
            PrimaryButton("Accept & continue", vm::acceptTerms, Modifier.weight(1.25f).testTag("terms_accept"), enabled = s.agreed)
        }
    }) {
        Text("Terms & Policies", style = EventsType.Headline, color = EventsColors.Text)
        Text("Please read and accept to use Events.", style = EventsType.BodySmall, color = EventsColors.TextSecondary)
        DuskCard(Modifier.fillMaxWidth(), spacing = 8.dp) {
            TERMS.forEach { (title, body) ->
                Text(title, style = EventsType.BodyStrong, color = EventsColors.Text)
                Text(body, style = EventsType.BodySmall, color = EventsColors.TextSecondary)
            }
        }
    }
}

private val TERMS = listOf(
    "1. How the lottery works" to "Popular campsites are allocated by random draw after registration closes. Joining once is enough; duplicates don't improve your odds.",
    "2. Your account & devices" to "Signing in is handled by Google's Firebase Authentication. If you set a password, Events never sees or stores it. Name, email and phone are only shared with owners of campsites you reserve.",
    "3. Reservations" to "Winners pick their nights inside the owner's booking period. Owners can see who booked and when.",
    "4. Community guidelines" to "Be respectful, leave no trace and follow park regulations. Listings or profiles that break policy may be removed.",
    "5. Notifications" to "Events tells you about lottery results, spots that open up, messages from owners and new lotteries at campsites you follow. Turn them off any time in Profile.",
    "6. Payments" to "Events doesn't take payments. You pay the owner at check-in; a rejected or cancelled reservation costs nothing.",
)

/** Figma S-TRUST / S-TRUST-OWNER (step 4): trust this phone to stay signed in, or ask for the password next time. */
@Composable
private fun TrustScreen(s: AccountState, vm: AccountViewModel, deviceName: String) {
    StepScaffold(4, vm::back, bottom = {
        s.message?.let { Text(it, style = EventsType.BodySmall, color = EventsColors.Danger) }
        PrimaryButton("Yes, trust this device", { vm.chooseTrust(true) }, Modifier.fillMaxWidth().testTag("trust_yes"), icon = R.drawable.ic_shield, loading = s.working)
        TextLinkButton("Not now", { vm.chooseTrust(false) }, Modifier.testTag("trust_no"), color = EventsColors.TextSecondary)
    }) {
        IconBadge(R.drawable.ic_smartphone)
        Text("Trust This Device?", style = EventsType.Headline, color = EventsColors.Text)
        val phone = if (deviceName == "This phone") "phone" else deviceName
        Text("Stay signed in on this $phone so Events recognises you automatically - no password next time.", style = EventsType.Body, color = EventsColors.TextSecondary)
        DuskCard(Modifier.fillMaxWidth(), spacing = 10.dp) {
            CheckLine(true, "Your profile is linked to this device ID")
            CheckLine(true, "Remove trusted devices any time in Profile")
            CheckLine(false, "Don't trust shared or public devices")
        }
    }
}

// ---- Shared pieces of the account screens ----

/** Figma step header: back button, progress bar and "n of 6", then the content and a bottom bar. */
@Composable
private fun StepScaffold(
    step: Int?,
    onBack: (() -> Unit)?,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    TwilightSky(variant = SkyVariant.SCREEN) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(Modifier.statusBarsPadding().padding(horizontal = PageMargin, vertical = 8.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) GlassIconButton(R.drawable.ic_arrow_back, "Back", onBack) else Spacer(Modifier.width(44.dp))
                if (step != null) {
                    Box(Modifier.weight(1f).padding(horizontal = 14.dp).height(6.dp).clip(CircleShape).background(EventsColors.Tint)) {
                        Box(Modifier.fillMaxWidth(step / SIGN_UP_STEPS.toFloat()).height(6.dp).clip(CircleShape).background(EventsColors.Primary))
                    }
                    Text("$step of $SIGN_UP_STEPS", style = EventsType.Caption, color = EventsColors.TextSecondary)
                }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PageMargin).padding(top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp), content = content,
            )
            if (bottom != null) Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = PageMargin).padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), content = bottom,
            )
        }
    }
}

/** Round icon badge used at the top of each step (Figma 64dp circle). */
@Composable
private fun IconBadge(icon: Int) {
    Box(Modifier.size(64.dp).clip(CircleShape).background(EventsColors.PrimaryContainer), contentAlignment = Alignment.Center) {
        EventsIcon(icon, size = 28.dp, tint = EventsColors.Primary)
    }
}

/** White "Continue with Google" button (Figma); Google sign-in is on hold. */
@Composable
private fun GoogleButton(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(EventsShapes.Pill).background(Color.White).clickable(onClick = onClick).testTag("google_button"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("Continue with Google", style = EventsType.Label, color = Color(0xFF1F1F1F))
    }
}

/** Checkbox with a label (Remember me, Terms). */
@Composable
private fun TickBox(checked: Boolean, onChange: (Boolean) -> Unit, label: String, modifier: Modifier = Modifier) {
    Row(modifier.clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked, onChange,
            colors = CheckboxDefaults.colors(checkedColor = EventsColors.Primary, uncheckedColor = EventsColors.TextSecondary, checkmarkColor = EventsColors.OnPrimary),
        )
        Text(label, style = EventsType.BodySmall, color = EventsColors.Text)
    }
}

/** One line of the Trust screen's checklist. */
@Composable
private fun CheckLine(good: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(if (good) EventsColors.GreenContainer else Color.Transparent)
                .border(if (good) 0.dp else 1.dp, EventsColors.TextTertiary, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) { EventsIcon(if (good) R.drawable.ic_check else R.drawable.ic_close, size = 14.dp, tint = if (good) EventsColors.Green else EventsColors.TextTertiary) }
        Text(text, style = EventsType.BodySmall, color = if (good) EventsColors.Text else EventsColors.TextTertiary)
    }
}

/** Info note (Google on hold, reset unavailable, verification status). */
@Composable
private fun Note(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(EventsShapes.Medium).background(EventsColors.PrimaryContainer).padding(12.dp).testTag("account_note"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EventsIcon(R.drawable.ic_info, size = 18.dp, tint = EventsColors.OnPrimaryContainer)
        Text(text, style = EventsType.BodySmall, color = EventsColors.Text)
    }
}
