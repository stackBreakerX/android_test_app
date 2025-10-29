package com.alex.studydemo.module_media

import android.graphics.Bitmap
import java.io.FileDescriptor

object LibJpegNative {
    @Volatile private var loaded = false

    val isAvailable: Boolean
        get() {
            if (loaded) return true
            return try {
                System.loadLibrary("jpeg_jni")
                val ok = isJpegAvailable()
                loaded = ok
                ok
            } catch (e: Throwable) {
                false
            }
        }

    external fun isJpegAvailable(): Boolean

    // Stream JPEG encoded bytes directly to a file descriptor
    external fun encodeBitmapToJpegFd(bitmap: Bitmap, quality: Int, dropAlpha: Boolean, fd: FileDescriptor): Boolean

    // Decode JPEG from file descriptor to a Bitmap (ARGB_8888). Returns null on failure.
    external fun decodeJpegToBitmapFd(fd: FileDescriptor): Bitmap?

    fun tryEncodeFd(bitmap: Bitmap, quality: Int, dropAlpha: Boolean, fd: FileDescriptor): Boolean {
        if (!isAvailable) return false
        return try {
            encodeBitmapToJpegFd(bitmap, quality, dropAlpha, fd)
        } catch (e: Throwable) {
            false
        }
    }

    fun tryDecodeFd(fd: FileDescriptor): Bitmap? {
        if (!isAvailable) return null
        return try {
            decodeJpegToBitmapFd(fd)
        } catch (e: Throwable) {
            null
        }
    }
}