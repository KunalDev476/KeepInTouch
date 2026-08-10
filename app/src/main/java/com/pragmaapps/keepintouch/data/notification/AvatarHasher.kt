package com.pragmaapps.keepintouch.data.notification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import com.pragmaapps.keepintouch.data.logging.AppLogger
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Hashes a notification's largeIcon so a later call's avatar can be compared to a previously
 * "learned" one for the same contact. Never touches the address-book photo - the learned hash
 * only ever comes from a WhatsApp call notification icon.
 */
object AvatarHasher {
    private const val TAG = "AvatarHasher"
    private const val TARGET_SIZE = 32

    fun hash(context: Context, icon: Icon?): String? {
        if (icon == null) return null
        return try {
            val drawable: Drawable = icon.loadDrawable(context) ?: return null
            val bitmap = drawableToBitmap(drawable) ?: return null
            hashBitmap(bitmap)
        } catch (t: Throwable) {
            // Crash-proof: a bad/unavailable icon must never take down notification processing.
            AppLogger.e(TAG, "Failed to hash avatar icon", t)
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createScaledBitmap(drawable.bitmap, TARGET_SIZE, TARGET_SIZE, true)
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: TARGET_SIZE
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: TARGET_SIZE
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return Bitmap.createScaledBitmap(bitmap, TARGET_SIZE, TARGET_SIZE, true)
    }

    private fun hashBitmap(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (pixel in pixels) {
            stream.write(pixel ushr 24 and 0xFF)
            stream.write(pixel ushr 16 and 0xFF)
            stream.write(pixel ushr 8 and 0xFF)
            stream.write(pixel and 0xFF)
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(stream.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
