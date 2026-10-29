/*
 * MainActivity.kt
 *
 * The one screen the app runs in. Each teammate fills in their own two functions
 * below; everything else lives in their own files.
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

    // Connect to Firestore (or the sample data) before anything else.
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
	SplashScreen(usingServer = AppContainer.instance.usingServer)
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
    }

    // Turn new draw results into phone notifications.
    @Composable
    private fun LotteryAlerts() {
    }

    // ---------- Rayner: owner side + profiles ----------

    // Owner tabs: Campsites, Lotteries, Bookings, Inbox, Profile.
    @Composable
    private fun OwnerArea() {
    }

    // First launch: name, email and phone, tied to this device.
    @Composable
    private fun WelcomeSetup() {
    }

    // ---------- Tausif: app root + admin ----------

    // Splash, setup, or the area for the role you're using.
    @Composable
    private fun AppNavigation() {
    }

    // Admin console: browse and remove campsites.
    @Composable
    private fun AdminArea() {
    }
}
