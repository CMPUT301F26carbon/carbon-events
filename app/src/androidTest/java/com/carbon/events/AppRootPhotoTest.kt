/*
 * AppRootPhotoTest.kt (Tausif)
 *
 * Purpose: Instrumented test for photo uploads (US 02.02.02): a large photo picked for a campsite
 * or a profile is shrunk so its longest side is 900 pixels, keeps its shape, and is still a
 * picture. Needs Android's image decoder, so it runs on a phone or emulator only.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write
 * tests for every part of AppRoot.kt that isn't tested yet." Reviewed, edited and run by
 * Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A 2400 × 1200 photo picked from the phone. */
@RunWith(AndroidJUnit4::class)
class AppRootPhotoTest {
    @Test fun aLargePhoto_isShrunkToFit_andStillAPicture() {
        assumeTrue("needs Android's image decoder", Build.FINGERPRINT != "robolectric")
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "big-photo.png")
        file.outputStream().use { Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF22AA66.toInt()) }.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val encoded = compressImageToBase64(context, Uri.fromFile(file))

        assertNotNull(encoded)
        assertTrue(isValidImage(encoded!!))
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, size)
        assertEquals("longest side", 900, maxOf(size.outWidth, size.outHeight))
        assertEquals("keeps its shape", 450, minOf(size.outWidth, size.outHeight))
    }
}
