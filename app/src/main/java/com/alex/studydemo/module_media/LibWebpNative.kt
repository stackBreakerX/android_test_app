package com.alex.studydemo.module_media

import android.graphics.Bitmap

object LibWebpNative {
    @Volatile
    private var loaded = false
    val isAvailable: Boolean
        get() {
            if (loaded) return true
            return try {
                System.loadLibrary("webp_jni")
                // verify encoder symbol availability via JNI
                val ok = isLibwebpAvailable()
                loaded = ok
                ok
            } catch (e: Throwable) {
                false
            }
        }

    external fun encodeRgbaToWebp(
        rgba: ByteArray,
        width: Int,
        height: Int,
        stride: Int,
        quality: Float
    ): ByteArray?

    external fun encodeBitmapToWebp(
        bitmap: Bitmap,
        quality: Float
    ): ByteArray?

    external fun isLibwebpAvailable(): Boolean

    fun tryEncode(bitmap: Bitmap, quality: Float): ByteArray? {
        if (!isAvailable) return null
        return encodeBitmapToWebp(bitmap, quality)
    }
}