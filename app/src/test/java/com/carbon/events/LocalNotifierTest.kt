/*
 * LocalNotifierTest.kt (Suvir)
 *
 * Purpose: Unit tests for how a phone notification looks (US 01.03.01, US 01.03.02): the Events
 * logo in the status bar, and the logo on the right with a small picture in its bottom-right
 * corner that says who it's from (a speech bubble for messages, a bell for lottery news, a
 * warning sign for a warning from the Events team); tapping one opens the chat or campsite it's
 * about; and allowing notifications late, or opening the app after a while, doesn't bring a pile
 * of old ones at once.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write
 * tests for the app logo and the corner picture on phone notifications." Reviewed, edited and
 * run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import org.junit.Assert.assertNull
import org.junit.Rule
import androidx.compose.ui.test.junit4.createComposeRule
import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocalNotifierTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager get() = app.getSystemService(NotificationManager::class.java)

    @get:Rule val compose = createComposeRule()

    @Before fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        LocalNotifier.createChannel(app)
        app.getSharedPreferences("local_notifier", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        NotificationTaps.consume()
    }

    private fun note(id: String, type: NotificationType) =
        AppNotification(id, "adam-kent", type, "Title $id", "Body $id", "lot-1", "two-jack", TestData.NOW)

    private fun posted() = shadowOf(manager).allNotifications

    // ---- Which corner picture ----

    @Test fun messages_showTheSpeechBubble() {
        assertEquals(R.drawable.ic_chat, notificationBadge(NotificationType.MESSAGE))
    }

    @Test fun lotteryNews_showsTheBell() {
        listOf(NotificationType.LOTTERY_WON, NotificationType.LOTTERY_LOST, NotificationType.SPOT_OPENED, NotificationType.OPENED, NotificationType.REJECTED, NotificationType.CANCELLED)
            .forEach { assertEquals(it.name, R.drawable.ic_bell, notificationBadge(it)) }
    }

    @Test fun aWarningFromTheEventsTeam_showsTheWarningSign() {
        assertEquals(R.drawable.ic_warning, notificationBadge(NotificationType.WARNING))
    }

    @Test fun everyKindOfNotification_hasACornerPicture() {
        NotificationType.entries.forEach { assertTrue(it.name, notificationBadge(it) in setOf(R.drawable.ic_chat, R.drawable.ic_bell, R.drawable.ic_warning)) }
    }

    // ---- Where it sits ----

    @Test fun cornerPicture_sitsInTheBottomRightCorner() {
        val size = 192
        val corner = badgeBounds(size)
        assertTrue("right edge", corner.right <= size && corner.right >= size - size * 0.06f)
        assertTrue("bottom edge", corner.bottom <= size && corner.bottom >= size - size * 0.06f)
        assertTrue("right half", corner.left > size / 2f)
        assertTrue("bottom half", corner.top > size / 2f)
        assertEquals("round", corner.width(), corner.height(), 0.01f)
    }

    @Test fun logoPicture_hasTheBadgeColourInTheCorner_andTheLogoElsewhere() {
        val art = notificationArt(app, NotificationType.MESSAGE)
        val size = art.width
        assertEquals(size, art.height)
        val corner = badgeBounds(size)
        // Just inside the corner circle's rim: the badge colour, not the night sky.
        val rim = art.getPixel((corner.centerX() + corner.width() * 0.3f).toInt(), corner.centerY().toInt())
        assertEquals("badge colour", BADGE_COLOR and 0xFFFFFF, rim and 0xFFFFFF)
        // The top-left of the logo is night sky: dark blue, not the badge.
        val sky = art.getPixel(size / 4, size / 4)
        assertTrue("logo in the top left", Color.blue(sky) > Color.red(sky) && Color.alpha(sky) == 255 && sky != BADGE_COLOR)
    }

    @Test fun logoPicture_hasNoWhiteCorners() {
        val art = notificationArt(app, NotificationType.LOTTERY_WON)
        assertEquals("the logo's square corners are cut away", 0, Color.alpha(art.getPixel(1, 1)))
    }

    // ---- The notification itself ----

    @Test fun phoneNotification_usesTheEventsLogo_inTheStatusBar() {
        LocalNotifier.showNew(app, listOf(note("n1", NotificationType.LOTTERY_WON)), TestData.NOW)
        val n = posted().single()
        assertEquals(R.drawable.ic_notification, n.smallIcon.resId)
    }

    @Test fun phoneNotification_showsTheLogoWithItsCornerPicture_onTheRight() {
        LocalNotifier.showNew(app, listOf(note("n2", NotificationType.MESSAGE)), TestData.NOW)
        val large: Icon? = posted().single().getLargeIcon()
        assertNotNull("logo on the right", large)
        assertEquals(Icon.TYPE_BITMAP, large!!.type)
    }

    @Test fun eachNotification_getsThePictureForItsKind() {
        LocalNotifier.showNew(app, listOf(note("m", NotificationType.MESSAGE), note("w", NotificationType.LOTTERY_WON)), TestData.NOW)
        val byTitle = posted().associateBy { it.extras.getString("android.title") }
        val message = (byTitle["Title m"]!!.getLargeIcon().loadDrawable(app) as android.graphics.drawable.BitmapDrawable).bitmap
        val won = (byTitle["Title w"]!!.getLargeIcon().loadDrawable(app) as android.graphics.drawable.BitmapDrawable).bitmap
        assertTrue("a message and lottery news look different", !message.sameAs(won))
        // Android keeps its own (resized) copy: it is the message's picture, not the bell's.
        fun near(a: Bitmap, b: Bitmap): Long {
            val s = Bitmap.createScaledBitmap(b, a.width, a.height, true)
            var d = 0L
            for (x in 0 until a.width step 2) for (y in 0 until a.height step 2) {
                val p = a.getPixel(x, y); val q = s.getPixel(x, y)
                d += Math.abs(Color.red(p) - Color.red(q)) + Math.abs(Color.green(p) - Color.green(q)) + Math.abs(Color.blue(p) - Color.blue(q))
            }
            return d
        }
        assertTrue("shows the speech bubble", near(message, notificationArt(app, NotificationType.MESSAGE)) < near(message, notificationArt(app, NotificationType.LOTTERY_WON)))
    }

    @Test fun picture_isMadeOncePerKind() {
        val first: Bitmap = notificationArt(app, NotificationType.SPOT_OPENED)
        assertTrue(first === notificationArt(app, NotificationType.SPOT_OPENED))
    }

    // ---- Not a pile of old ones at once ----

    @Test fun withoutPermission_nothingPops_andAllowingItLater_doesntReplayThem() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val waiting = listOf(note("a", NotificationType.LOTTERY_WON), note("b", NotificationType.MESSAGE), note("c", NotificationType.SPOT_OPENED))
        LocalNotifier.showNew(app, waiting, TestData.NOW)
        assertTrue(posted().isEmpty())
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        LocalNotifier.showNew(app, waiting, TestData.NOW)
        assertTrue("the three from before stay in the app's list only", posted().isEmpty())
        LocalNotifier.showNew(app, waiting + note("d", NotificationType.LOTTERY_LOST), TestData.NOW)
        assertEquals(listOf("Title d"), posted().map { it.extras.getString("android.title") })
    }

    @Test fun somethingOverADayOld_staysInTheApp_withoutPoppingUp() {
        val old = note("old", NotificationType.LOTTERY_WON).copy(createdAt = TestData.NOW - LocalNotifier.MAX_AGE_MS - 1)
        val fresh = note("new", NotificationType.LOTTERY_WON).copy(createdAt = TestData.NOW - 60_000)
        LocalNotifier.showNew(app, listOf(old, fresh), TestData.NOW)
        assertEquals(listOf("Title new"), posted().map { it.extras.getString("android.title") })
    }

    // ---- Tapping one ----

    @Test fun whatATapOpens() {
        val chat = AppNotification("${CHAT_NOTICE_PREFIX}two-jack--adam-kent-1792162800000", "adam-kent", NotificationType.MESSAGE, "Message from Priya", "Hi", "", "two-jack", TestData.NOW)
        assertEquals(TapTarget.Chat("two-jack--adam-kent"), tapTarget(chat))
        assertEquals(TapTarget.Campsite("two-jack"), tapTarget(note("w", NotificationType.LOTTERY_WON)))
        assertEquals(TapTarget.App, tapTarget(note("x", NotificationType.WARNING)))
        assertEquals(TapTarget.App, tapTarget(note("y", NotificationType.LOTTERY_LOST).copy(campsiteId = "")))
    }

    @Test fun tappingALotteryResult_opensItsCampsite() {
        LocalNotifier.showNew(app, listOf(note("won", NotificationType.LOTTERY_WON)), TestData.NOW)
        val intent = shadowOf(posted().single().contentIntent).savedIntent
        PromoLinks.handle(intent)
        assertEquals("two-jack", PromoLinks.pending)
        PromoLinks.consume()
    }

    @Test fun tappingAMessage_opensItsChat() {
        val chat = AppNotification("${CHAT_NOTICE_PREFIX}two-jack--adam-kent-${TestData.NOW}", "adam-kent", NotificationType.MESSAGE, "Message from Priya", "Hi", "", "two-jack", TestData.NOW)
        LocalNotifier.showNew(app, listOf(chat), TestData.NOW)
        NotificationTaps.handle(shadowOf(posted().single().contentIntent).savedIntent)
        assertEquals("two-jack--adam-kent", NotificationTaps.pendingChat)
    }

    @Test fun eachNotification_opensItsOwnThing() {
        LocalNotifier.showNew(app, listOf(note("a", NotificationType.LOTTERY_WON), note("b", NotificationType.LOTTERY_WON).copy(campsiteId = "lake-louise")), TestData.NOW)
        val opened = posted().map { PromoLinks.handle(shadowOf(it.contentIntent).savedIntent); PromoLinks.pending }.toSet()
        PromoLinks.consume()
        assertEquals(setOf("two-jack", "lake-louise"), opened)
    }

    @Test fun theTappedChat_opensOnScreen_forSomeoneInIt() {
        val store = InMemoryEventsStore()
        val chat = Chat(Chat.idFor("two-jack", "adam-kent"), "two-jack", "Two Jack Lakeside", "owner-1", "Priya", "adam-kent", "Adam Kent")
        kotlinx.coroutines.runBlocking { store.sendMessage(chat, "owner-1", "Priya", "Gates open at 2", TestData.NOW) }
        NotificationTaps.handle(LocalNotifier.tapIntent(app, TapTarget.Chat(chat.id)))
        var opened: Chat? = null
        compose.setContent { OpenTappedChat(AppContainer.inMemory("adam-kent", FixedClock(TestData.NOW), store)) { opened = it } }
        compose.waitUntil(5_000) { opened != null }
        assertEquals(chat.id, opened!!.id)
        assertNull(NotificationTaps.pendingChat)
    }

    @Test fun aTappedChat_isntOpened_forSomeoneElse() {
        val store = InMemoryEventsStore()
        val chat = Chat(Chat.idFor("two-jack", "adam-kent"), "two-jack", "Two Jack Lakeside", "owner-1", "Priya", "adam-kent", "Adam Kent")
        kotlinx.coroutines.runBlocking { store.sendMessage(chat, "owner-1", "Priya", "Gates open at 2", TestData.NOW) }
        NotificationTaps.handle(LocalNotifier.tapIntent(app, TapTarget.Chat(chat.id)))
        var opened: Chat? = null
        compose.setContent { OpenTappedChat(AppContainer.inMemory("someone-else", FixedClock(TestData.NOW), store)) { opened = it } }
        compose.waitUntil(5_000) { NotificationTaps.pendingChat == null }
        assertNull(opened)
    }
}
