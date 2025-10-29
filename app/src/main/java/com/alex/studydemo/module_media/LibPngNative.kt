package com.alex.studydemo.module_media

import android.graphics.Bitmap
import java.io.FileDescriptor

object LibPngNative {
    @Volatile private var loaded = false

    val isAvailable: Boolean
        get() {
            if (loaded) return true
            return try {
                System.loadLibrary("png_jni")
                val ok = isPngAvailable()
                loaded = ok
                ok
            } catch (e: Throwable) {
                false
            }
        }

    external fun isPngAvailable(): Boolean

    // original method kept for compatibility
    external fun encodeBitmapToPng(bitmap: Bitmap, level: Int): ByteArray?

    // new method with dropAlpha support
    external fun encodeBitmapToPng2(bitmap: Bitmap, level: Int, dropAlpha: Boolean): ByteArray?

    // new method: stream directly to file descriptor
    external fun encodeBitmapToPngFd(bitmap: Bitmap, level: Int, dropAlpha: Boolean, fd: FileDescriptor): Boolean

    // new method: decode via AImageDecoder from file descriptor (RGBA_8888)
    external fun decodePngToBitmapFd(fd: FileDescriptor): Bitmap?

    fun tryEncode(bitmap: Bitmap, level: Int): ByteArray? {
        if (!isAvailable) return null
        return try {
            encodeBitmapToPng(bitmap, level)
        } catch (e: Throwable) {
            null
        }
    }

    fun tryEncode2(bitmap: Bitmap, level: Int, dropAlpha: Boolean): ByteArray? {
        if (!isAvailable) return null
        return try {
            encodeBitmapToPng2(bitmap, level, dropAlpha)
        } catch (e: Throwable) {
            null
        }
    }

    fun tryEncodeFd(bitmap: Bitmap, level: Int, dropAlpha: Boolean, fd: FileDescriptor): Boolean {
        if (!isAvailable) return false
        return try {
            encodeBitmapToPngFd(bitmap, level, dropAlpha, fd)
        } catch (e: Throwable) {
            false
        }
    }

    fun tryDecodeFd(fd: FileDescriptor): Bitmap? {
        if (!isAvailable) return null
        return try {
            decodePngToBitmapFd(fd)
        } catch (e: Throwable) {
            null
        }
    }
}