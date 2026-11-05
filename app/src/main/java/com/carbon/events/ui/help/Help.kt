/*
 * Help.kt (Aditya)
 *
 * Help & support (searchable answers and contact), and Policies & guidelines (terms, privacy,
 * campsite guidelines). Reached from Profile in every role.
 *
 * Outstanding issues: the wording is a draft for the team to review before release.
 */
package com.carbon.events

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** One question and answer on Help & support. */
data class HelpArticle(val topic: String, val question: String, val answer: String)

/** Email address the Contact us button writes to. */
const val SUPPORT_EMAIL = "support@carbon-events.app"

/** Everything Help & support can answer, grouped by topic. */
val HELP_ARTICLES = listOf(
    HelpArticle("Lotteries", "How does the lottery work?", "When a lottery's registration closes, winners are drawn at random from everyone on the waiting list. Joining early doesn't help, and joining once is enough."),
    HelpArticle("Lotteries", "When is the draw?", "Right after registration closes. The draw runs on the campsite owner's phone, so it can take until the owner next opens Events."),
    HelpArticle("Lotteries", "Can I leave a waiting list?", "Yes, any time before registration closes. Open the campsite and tap Leave waitlist."),
    HelpArticle("Lotteries", "What happens if a winner turns their spot down?", "Their spot goes to someone else on the waiting list who wasn't selected, picked at random. You'll get a notification if it's you."),
    HelpArticle("Bookings", "I won. How do I book?", "Open My Lotteries and tap Book your dates. Pick nights inside the booking period, choose how many guests are coming, accept the campsite rules and confirm."),
    HelpArticle("Bookings", "How do I pay?", "Events doesn't take payments. You pay the owner at check-in. Rejected or cancelled reservations cost nothing."),
    HelpArticle("Bookings", "Can I cancel?", "Yes. Open Bookings, pick the reservation and tap Cancel reservation. Your spot goes to someone else on the waiting list."),
    HelpArticle("Bookings", "Why was my reservation rejected?", "The owner gives a reason, which is in your inbox. Message the owner from the campsite page if you have questions."),
    HelpArticle("Account", "How do I stay signed in?", "Choose Trust this device when you sign up, or tick Remember me when you sign in. Remove a phone from Profile > Trusted devices to sign it out."),
    HelpArticle("Account", "I forgot my password", "Password reset isn't available yet. Contact us from this page and we'll help you get back in."),
    HelpArticle("Account", "Can I change my email?", "Your email is your sign-in, so it can't be changed in the app. You can change your name, phone and photo in Edit Profile."),
    HelpArticle("Owners", "How do I list a campsite?", "Switch to Owner in Profile > Viewing as, then tap New campsite. Add the details, a photo and the location on the map."),
    HelpArticle("Owners", "How do I run a lottery?", "Open your campsite and tap New lottery. Set when registration opens and closes, how many winners to draw and the nights winners can book."),
    HelpArticle("Owners", "How do I contact my waiting list?", "Open the lottery's waiting list and tap Notify. You can message everyone, only winners or only people who weren't selected."),
)

/**
 * Filters [articles] to those whose topic, question or answer contains every word in [query].
 * A blank query returns everything.
 */
fun searchHelp(articles: List<HelpArticle>, query: String): List<HelpArticle> {
    val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    return articles.filter { a -> val text = "${a.topic} ${a.question} ${a.answer}".lowercase(); words.all { it in text } }
}

/** Help & support (Figma E-HELP): search, answers by topic, contact. */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val results = remember(query) { searchHelp(HELP_ARTICLES, query) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackTopBar("Help & Support", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = PageMargin).padding(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SearchBar(query, { query = it }, placeholder = "Search help", modifier = Modifier.testTag("help_search"))
            if (results.isEmpty()) EmptyState(R.drawable.ic_search, "No answers found", "Try other words, or contact us below.")
            results.groupBy { it.topic }.forEach { (topic, items) ->
                Text(topic.uppercase(), style = EventsType.Overline, color = EventsColors.TextSoft)
                items.forEach { HelpItem(it) }
            }
            DuskCard(Modifier.fillMaxWidth(), spacing = 10.dp) {
                Text("Still need help?", style = EventsType.Title, color = EventsColors.Text)
                Text("Email the Events team and we'll reply within two business days.", style = EventsType.BodySmall, color = EventsColors.TextSecondary)
                PrimaryButton("Contact us", {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL")).putExtra(Intent.EXTRA_SUBJECT, "Events help")
                    runCatching { context.startActivity(intent) }
                }, Modifier.fillMaxWidth().testTag("help_contact"), icon = R.drawable.ic_mail, size = ButtonSize.SMALL)
            }
        }
    }
}

@Composable
private fun HelpItem(article: HelpArticle) {
    var open by remember { mutableStateOf(false) }
    DuskCard(Modifier.fillMaxWidth().animateContentSize(), onClick = { open = !open }, spacing = 6.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(article.question, style = EventsType.BodyStrong, color = EventsColors.Text, modifier = Modifier.weight(1f))
            EventsIcon(if (open) R.drawable.ic_remove else R.drawable.ic_add, tint = EventsColors.TextSecondary, size = 18.dp)
        }
        if (open) Text(article.answer, style = EventsType.BodySmall, color = EventsColors.TextSecondary)
    }
}

/** A policy page: a title and its sections. */
data class Policy(val title: String, val icon: Int, val sections: List<Pair<String, String>>)

/** Terms, privacy and campsite guidelines. */
val POLICIES = listOf(
    Policy("Terms of Service", R.drawable.ic_policy, listOf(
        "Fair draws" to "Lottery winners are chosen uniformly at random. Each person can join a waiting list once.",
        "Reservations" to "A reservation is between you and the campsite owner. Events doesn't take payments; you pay the owner at check-in.",
        "Cancelling" to "Winners may decline or cancel. The spot is offered to someone else who entered.",
        "Accounts" to "One person, one profile. Admins may warn, block or remove profiles and listings that break these terms.",
    )),
    Policy("Privacy Policy", R.drawable.ic_shield, listOf(
        "What we keep" to "Your name, email, phone, age, photo, trusted devices and your lottery entries and reservations.",
        "Who sees it" to "Owners see the names of people on their waiting lists and the guests who book. Admins can see profiles to keep the community safe.",
        "Passwords" to "Passwords are handled by Google Firebase Authentication. Events never sees or stores them.",
        "Location" to "If you allow it, your phone's location is only used on your phone to sort lotteries by distance. It is never uploaded.",
        "Deleting" to "Profile > Delete profile removes your profile, inbox, devices and campsites.",
    )),
    Policy("Campsite Guidelines", R.drawable.ic_tent, listOf(
        "Leave no trace" to "Pack out what you pack in and keep fires in the fire pits provided.",
        "Respect quiet hours" to "Quiet hours are 10 PM to 7 AM unless the owner's rules say otherwise.",
        "Guests" to "Bring no more guests than your reservation and the site's maximum allow.",
        "Listings" to "Owners must describe their campsite honestly and keep photos current. Misleading listings can be reported and removed.",
    )),
)

/** Policies & guidelines (Figma E-POLICY). */
@Composable
fun PoliciesScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackTopBar("Policies & Guidelines", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = PageMargin).padding(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            POLICIES.forEach { policy ->
                var open by remember { mutableStateOf(false) }
                DuskCard(Modifier.fillMaxWidth().animateContentSize().testTag("policy_${policy.title}"), spacing = 10.dp) {
                    Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconTile(policy.icon)
                        Text(policy.title, style = EventsType.TitleSmall, color = EventsColors.Text, modifier = Modifier.weight(1f))
                        EventsIcon(if (open) R.drawable.ic_remove else R.drawable.ic_add, tint = EventsColors.TextSecondary, size = 18.dp)
                    }
                    if (open) policy.sections.forEach { (title, body) ->
                        Text(title, style = EventsType.BodyStrong, color = EventsColors.Text)
                        Text(body, style = EventsType.BodySmall, color = EventsColors.TextSecondary)
                    }
                }
            }
            Text("Last updated October 2026", style = EventsType.Caption, color = EventsColors.TextTertiary)
        }
    }
}
