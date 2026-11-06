/*
 * MainActivity.kt (Rayner)
 *
 * The one screen the app runs in. Each teammate fills in their own two functions
 * below; everything else lives in their own files.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable

/**
 * The app's single activity. It sets up the data layer, draws and notifications, then shows
 * [EventsApp]; each teammate owns two of the small functions below.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the status and nav bars so the sky reaches the edges.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setupDataLayer()
        startLotteryDraws()
        setupNotifications()
        handleDeepLink(intent)
        setContent {
            AppTheme {
                AppNavigation()
            }
        }
    }

    // A promo QR link can arrive while the app is already open.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    // ---------- Vaibhav: data + lottery rules ----------

    // Connect to Firestore (or an offline demo store that saves nothing) before anything else.
    private fun setupDataLayer() {
        AppContainer.init(this)
    }

    // Keep running the automatic draws when registration closes.
    private fun startLotteryDraws() {
        AppContainer.startDraws()
    }

    // ---------- Aditya: theme + branding ----------

    // Our Figma colours and fonts around the whole app.
    @Composable
    private fun AppTheme(content: @Composable () -> Unit) {
        EventsTheme(content = content)
    }

    // Splash while the profile loads.
    @Composable
    private fun ShowSplash() {
        SplashScreen(usingServer = AppContainer.instance.usingServer, returningName = ReturningUser.firstName(this))
    }

    // ---------- Ansh: explore + campsite details ----------

    // Entrant tabs: Explore, Lotteries, Scan, Bookings, Profile.
    @Composable
    private fun EntrantArea() {
        val s = LocalAppSession.current
        EntrantShell(s.container, s.requireProfile(), s.profileVm, s.deepLinkCampsiteId, s.onDeepLinkConsumed)
    }

    // Opening a promo QR link takes you to that campsite.
    private fun handleDeepLink(intent: Intent?) {
        PromoLinks.handle(intent)
    }

    // ---------- Suvir: lotteries + notifications + scan ----------

    // Android needs a channel before we can post win / loss notifications.
    private fun setupNotifications() {
        LocalNotifier.createChannel(this)
        // Results, messages and due draws also arrive while the app is closed (about every 15 min).
        BackgroundSync.schedule(this)
    }

    // Turn new draw results into phone notifications.
    @Composable
    private fun LotteryAlerts() {
        NotificationEffect(LocalAppSession.current.container)
    }

    // ---------- Rayner: owner side + profiles ----------

    // Owner tabs: Campsites, Lotteries, Bookings, Inbox, Profile.
    @Composable
    private fun OwnerArea() {
        val s = LocalAppSession.current
        OwnerShell(s.container, s.requireProfile(), s.profileVm)
    }

    // Signed out: Welcome, sign in or create an account, then trust this phone.
    @Composable
    private fun WelcomeSetup() {
        SetupScreen(LocalAppSession.current.profileVm)
    }

    // ---------- Tausif: app root + admin ----------

    // Splash, setup, or the area for the role you're using.
    @Composable
    private fun AppNavigation() {
        // Without Firebase nothing is saved; say so on every screen instead of pretending.
        OfflineDemoFrame(enabled = !AppContainer.instance.usingServer) {
            AppScreens()
        }
    }

    @Composable
    private fun AppScreens() {
        EventsApp(
            container = AppContainer.instance,
            deepLinkCampsiteId = PromoLinks.pending,
            onDeepLinkConsumed = PromoLinks::consume,
            splash = { ShowSplash() },
            setup = { WelcomeSetup() },
            alerts = { LotteryAlerts() },
            entrant = { EntrantArea() },
            owner = { OwnerArea() },
            admin = { AdminArea() },
        )
    }

    // Admin console: browse and remove campsites, browse profiles.
    @Composable
    private fun AdminArea() {
        val s = LocalAppSession.current
        AdminShell(s.container, s.requireProfile(), s.profileVm)
    }
}
