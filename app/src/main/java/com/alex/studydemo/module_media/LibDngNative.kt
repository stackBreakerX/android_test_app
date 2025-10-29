package com.alex.studydemo.module_media

import android.graphics.Bitmap
import java.io.FileDescriptor

object LibDngNative {
    @Volatile private var loaded = false

    val isAvailable: Boolean
        get() {
            if (loaded) return true
            return try {
                try { System.loadLibrary("omp") } catch (_: Throwable) { /* optional */ }
                System.loadLibrary("dng_jni")
                val ok = isDngAvailable()
                loaded = ok
                ok
            } catch (_: Throwable) {
                false
            }
        }

    // JNI: checks presence of required LibRaw C API symbols
    external fun isDngAvailable(): Boolean

    // JNI: decode DNG from file descriptor into an ARGB_8888 Bitmap
    external fun decodeDngToBitmapFd(fd: FileDescriptor): Bitmap?

    // JNI: get last DNG stage timing info summary string
    external fun getLastDngTimingInfo(): String?

    // JNI: get last DNG stage timing info as JSON
    external fun getLastDngTimingInfoJson(): String?

    fun tryDecodeFd(fd: FileDescriptor): Bitmap? {
        if (!isAvailable) return null
        return try {
            val result = decodeDngToBitmapFd(fd)
            return result
        } catch (_: Throwable) {
            null
        }
    }
}