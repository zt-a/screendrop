package com.screendrop.util

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** Generates the QR bitmap for the sender URL (ZXing, no extra app dependencies). */
object QrGenerator {
    fun generate(content: String, size: Int = 512): Bitmap {
        val matrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
        )
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                pixels[y * w + x] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Generates a WIFI Share QR code: WIFI:S:ssid;T:WPA;P:password;; 
      If password is empty or null, omits the P: part. */
    fun generateWifiQr(ssid: String, password: String? = null, size: Int = 512): Bitmap {
        val text = buildString {
            append("WIFI:S:$ssid")
            password?.let { if (it.isNotEmpty()) append(";T:WPA;P:$it") }
            append(";;")
        }
        return generate(text, size)
    }
}

object Fmt {
    fun size(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> "%.1f ГБ".format(bytes / 1024.0 / 1024 / 1024)
        bytes >= 1024L * 1024 -> "%.1f МБ".format(bytes / 1024.0 / 1024)
        bytes >= 1024L -> "%.1f КБ".format(bytes / 1024.0)
        else -> "$bytes Б"
    }

    fun time(epochMs: Long): String {
        val fmt = java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault())
        return fmt.format(java.util.Date(epochMs))
    }
}
