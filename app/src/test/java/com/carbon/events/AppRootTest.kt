/*
 * AppRootTest.kt (Tausif)
 *
 * Purpose: Unit tests for the helpers in AppRoot.kt that no other test calls directly: the
 * messages people see when something fails, the countdown ticker, the campsite QR code
 * (US 02.01.03), and photo uploads being shrunk and checked (US 02.02.02), including on
 * Android 8, which the app supports but which has no ImageDecoder.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write
 * tests for every part of AppRoot.kt that isn't tested yet." Reviewed, edited and run by
 * Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppRootTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    // ---- What people are told when something fails ----

    @Test fun ruleErrors_sayWhatWentWrong() {
        assertEquals(WaitlistError.entries.first().message, WaitlistException(WaitlistError.entries.first()).userMessage())
        assertEquals(BookingError.TAKEN.message, BookingException(BookingError.TAKEN).userMessage())
        assertEquals(AuthError.WRONG_PASSWORD.message, AuthException(AuthError.WRONG_PASSWORD).userMessage())
        assertEquals("Closing must be after opening.", LotteryEditException("Closing must be after opening.").userMessage())
    }

    @Test fun anErrorWrappedInAnother_stillShowsTheRealReason() {
        val wrapped = RuntimeException("transaction failed", BookingException(BookingError.OUTSIDE_PERIOD))
        assertEquals(BookingError.OUTSIDE_PERIOD.message, wrapped.userMessage())
    }

    @Test fun serverRefusals_explainThemselves() {
        fun msg(code: FirebaseFirestoreException.Code) = FirebaseFirestoreException("x", code).userMessage()
        assertEquals("The server didn't allow that. If you're setting up Firebase, publish the latest security rules.", msg(FirebaseFirestoreException.Code.PERMISSION_DENIED))
        assertEquals("This list needs a Firebase index. Open Logcat for the link that creates it.", msg(FirebaseFirestoreException.Code.FAILED_PRECONDITION))
        assertEquals("Couldn't reach the server. Check your connection and try again.", msg(FirebaseFirestoreException.Code.UNAVAILABLE))
        assertEquals("Something went wrong (aborted). Try again.", msg(FirebaseFirestoreException.Code.ABORTED))
    }

    @Test fun anythingElse_getsAGeneralMessage() {
        assertEquals("Something went wrong. Try again.", IllegalStateException("boom").userMessage())
    }

    // ---- Countdown ticker ----

    @Test fun ticker_givesTheTimeNow_thenAgainEachPeriod() = runTest {
        val clock = FixedClock(1_000L)
        var calls = 0
        val ticking = object : Clock { override fun now(): Long = clock.now() + 30_000L * calls++ }
        assertEquals(listOf(1_000L, 31_000L, 61_000L), nowTicker(ticking, 30_000L).take(3).toList())
    }

    // ---- Campsite QR code ----

    private fun decode(bitmap: Bitmap): String {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))).text
    }

    @Test fun qrCode_scansBackToTheCampsiteLink() {
        val payload = QrCodes.payloadFor("two-jack")
        val bitmap = qrBitmap(payload, 300).asAndroidBitmap()
        assertEquals(300, bitmap.width)
        assertEquals(300, bitmap.height)
        assertEquals(payload, decode(bitmap))
        assertEquals("two-jack", QrCodes.parseCampsiteId(decode(bitmap)))
    }

    @Test fun qrCode_isDarkPlumOnWhite() {
        val bitmap = qrBitmap(QrCodes.payloadFor("two-jack"), 200).asAndroidBitmap()
        val colours = (0 until 200 step 5).flatMap { x -> (0 until 200 step 5).map { y -> bitmap.getPixel(x, y) } }.toSet()
        assertEquals(setOf(0xFF2A2160.toInt(), 0xFFFFFFFF.toInt()), colours)
    }

    // ---- Photo uploads ----

    private fun pngBase64(w: Int, h: Int): String {
        val out = ByteArrayOutputStream()
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF3366AA.toInt()) }.compress(Bitmap.CompressFormat.PNG, 100, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    @Test fun aRealPicture_isAValidImage() {
        assertTrue(isValidImage(pngBase64(20, 10)))
    }

    @Test fun textOrNothing_isNotAnImage() {
        assertFalse(isValidImage(""))
        assertFalse(isValidImage(Base64.encodeToString("not a picture".toByteArray(), Base64.NO_WRAP)))
        assertFalse(isValidImage("%%% not base64 %%%"))
    }

    // Shrinking a real photo needs Android's image decoder, so it is checked on a phone or
    // emulator in AppRootPhotoTest.

    @Test fun aMissingPhoto_givesNothing() {
        assertNull(compressImageToBase64(app, Uri.fromFile(File(app.cacheDir, "nope.png"))))
    }

    @Test fun aLargePhoto_onAnAndroid8Phone_isShrunkToFit() {
        // Android 8 has no ImageDecoder (it came in Android 9), and the app supports Android 8.
        val file = File(app.cacheDir, "big-on-8.png")
        file.outputStream().use { Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF22AA66.toInt()) }.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val encoded = compressImageToBase64(app, Uri.fromFile(file), withImageDecoder = false)
        assertTrue("a photo on Android 8", encoded != null && isValidImage(encoded))
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        val size = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, size)
        assertEquals("longest side", 900, maxOf(size.outWidth, size.outHeight))
        assertEquals("keeps its shape", 450, minOf(size.outWidth, size.outHeight))
    }

    @Test fun aSmallPhoto_onAnAndroid8Phone_isKeptAtItsSize() {
        val file = File(app.cacheDir, "small-on-8.png")
        file.outputStream().use { Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF22AA66.toInt()) }.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val bytes = Base64.decode(compressImageToBase64(app, Uri.fromFile(file), withImageDecoder = false)!!, Base64.DEFAULT)
        val size = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, size)
        assertEquals(300, size.outWidth)
        assertEquals(200, size.outHeight)
    }
}
