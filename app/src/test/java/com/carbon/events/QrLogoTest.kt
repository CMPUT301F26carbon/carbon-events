/*
 * QrLogoTest.kt
 *
 * Purpose: checks that a promo QR code (US 02.02.01) still scans with the Events logo
 * drawn over its centre, as the Figma promo QR shows. The app encodes with error
 * correction level H and covers about 22% of the width with the logo.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class QrLogoTest {
    @Test
    fun promoQr_decodesWithLogoOverCentre() {
        val size = 512
        val payload = QrCodes.payloadFor("two-jack")
        val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H)
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints)
        // Same proportions as QrCodeImage: logo is 22% of the tile, plus a small white border.
        val logo = (size * 0.24).toInt()
        val from = (size - logo) / 2
        val pixels = IntArray(size * size) { i ->
            val x = i % size
            val y = i / size
            val inLogo = x in from until from + logo && y in from until from + logo
            if (!inLogo && matrix[x, y]) 0xFF2A2160.toInt() else 0xFFFFFFFF.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size, size, pixels)))
        val decoded = QRCodeReader().decode(bitmap, mapOf(DecodeHintType.TRY_HARDER to true))
        assertEquals("two-jack", QrCodes.parseCampsiteId(decoded.text))
    }
}
