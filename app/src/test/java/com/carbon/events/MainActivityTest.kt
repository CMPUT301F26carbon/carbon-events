/*
 * MainActivityTest.kt (Rayner)
 *
 * Purpose: Unit tests for how the app is opened (MainActivity.kt): from the launcher, and from a
 * campsite's promo QR link scanned with any camera app (US 01.12.01), which opens that campsite;
 * other links don't open the app, and turning the phone doesn't open that campsite again.
 * Launching the whole app is checked by StartupTest.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write
 * tests for how MainActivity is opened from the launcher and from a QR link." Reviewed, edited
 * and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @After fun tearDown() = PromoLinks.consume()

    /** The screen [intent] opens in this app; links must be default handlers, the launcher needn't be. */
    private fun opens(intent: Intent, flags: Int = PackageManager.MATCH_DEFAULT_ONLY): String? =
        app.packageManager.resolveActivity(intent.setPackage(app.packageName), flags)?.activityInfo?.name

    @Test fun theLauncherIcon_opensMainActivity() {
        assertEquals(MainActivity::class.java.name, opens(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), flags = 0))
    }

    @Test fun aCampsiteQrLink_opensMainActivity() {
        val link = Intent(Intent.ACTION_VIEW, Uri.parse(QrCodes.payloadFor("two-jack"))).addCategory(Intent.CATEGORY_BROWSABLE)
        assertEquals(MainActivity::class.java.name, opens(link))
    }

    @Test fun aWebsiteLink_doesNotOpenTheApp() {
        assertNull(opens(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/campsite/two-jack")).addCategory(Intent.CATEGORY_BROWSABLE)))
    }

    @Test fun theLinkTheAppOpensWith_opensThatCampsite_once() {
        PromoLinks.handle(Intent(Intent.ACTION_VIEW, Uri.parse(QrCodes.payloadFor("johnston-canyon"))))
        assertEquals("johnston-canyon", PromoLinks.pending)
        PromoLinks.consume()
        assertNull("not opened again on the next screen", PromoLinks.pending)
    }

    @Test fun openingTheAppWithoutALink_opensNoCampsite() {
        PromoLinks.handle(Intent(Intent.ACTION_MAIN))
        assertNull(PromoLinks.pending)
        PromoLinks.handle(null)
        assertNull(PromoLinks.pending)
    }

    @Test fun aLinkThatIsNotACampsite_isIgnored() {
        PromoLinks.handle(Intent(Intent.ACTION_VIEW, Uri.parse("carbon-events://campsite/../../etc")))
        assertNull(PromoLinks.pending)
    }

    /** Turning the phone recreates the activity with the same link; that campsite was already opened. */
    @Test fun rotatingAfterOpeningALink_doesNotOpenThatCampsiteAgain() {
        val link = Intent(app, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(QrCodes.payloadFor("two-jack")))
        ActivityScenario.launch<MainActivity>(link).use { scenario ->
            assertEquals("two-jack", PromoLinks.pending)
            PromoLinks.consume() // the details screen opened it
            scenario.recreate()
            assertNull("rotating must not open the campsite a second time", PromoLinks.pending)
        }
    }
}
