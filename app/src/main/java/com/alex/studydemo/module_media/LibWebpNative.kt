package com.alex.studydemo.module_media

import android.graphics.Bitmap
import java.io.FileDescriptor

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

    // new method: decode WebP from file descriptor to Bitmap (ARGB_8888)
    external fun decodeWebpToBitmapFd(fd: FileDescriptor): Bitmap?

    fun tryEncode(bitmap: Bitmap, quality: Float): ByteArray? {
        if (!isAvailable) return null
        return encodeBitmapToWebp(bitmap, quality)
    }

    fun tryDecodeFd(fd: FileDescriptor): Bitmap? {
        if (!isAvailable) return null
        return try {
            decodeWebpToBitmapFd(fd)
        } catch (e: Throwable) {
            null
        }
    }
}