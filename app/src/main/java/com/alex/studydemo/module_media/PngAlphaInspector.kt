package com.alex.studydemo.module_media

import android.content.ContentResolver
import android.net.Uri
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlin.math.min

data class PngAlphaInfo(
    val hasAlphaChannel: Boolean,
    val hasTransparencyChunk: Boolean,
    val colorType: Int,
    val bitDepth: Int,
    val paletteAlphaEntries: Int,
    val transparencyKind: TransparencyKind
)

enum class TransparencyKind { None, PerPixelAlpha, PaletteAlpha, SingleTransparentColor }

object PngAlphaInspector {
    private val SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    fun inspect(cr: ContentResolver, uri: Uri): PngAlphaInfo {
        cr.openInputStream(uri).use { inStream ->
            return inspect(inStream ?: throw IllegalArgumentException("Cannot open stream"))
        }
    }

    fun inspect(input: InputStream): PngAlphaInfo {
        val dis = DataInputStream(BufferedInputStream(input))
        val sig = ByteArray(8)
        dis.readFully(sig)
        if (!sig.contentEquals(SIGNATURE)) throw IllegalArgumentException("Not a PNG")

        var colorType = -1
        var bitDepth = -1
        var hasTRNS = false
        var paletteAlphaCount = 0

        while (true) {
            val length = dis.readInt()
            val typeBytes = ByteArray(4)
            dis.readFully(typeBytes)
            val type = String(typeBytes, StandardCharsets.US_ASCII)

            if (type == "IHDR") {
                val ihdr = ByteArray(13)
                dis.readFully(ihdr)
                bitDepth = ihdr[8].toInt() and 0xFF
                colorType = ihdr[9].toInt() and 0xFF
                skipFully(dis, 4)
            } else if (type == "tRNS") {
                val data = ByteArray(length)
                dis.readFully(data)
                hasTRNS = true
                paletteAlphaCount = when (colorType) {
                    3 -> data.count { (it.toInt() and 0xFF) != 0xFF }
                    else -> 1
                }
                skipFully(dis, 4)
            } else if (type == "IDAT") {
                skipFully(dis, length)
                skipFully(dis, 4)
                break
            } else {
                skipFully(dis, length)
                skipFully(dis, 4)
                if (type == "IEND") break
            }
        }

        val hasAlphaChannel = colorType == 4 || colorType == 6
        val kind = when {
            hasAlphaChannel -> TransparencyKind.PerPixelAlpha
            hasTRNS && colorType == 3 -> TransparencyKind.PaletteAlpha
            hasTRNS && (colorType == 0 || colorType == 2) -> TransparencyKind.SingleTransparentColor
            else -> TransparencyKind.None
        }
        return PngAlphaInfo(
            hasAlphaChannel = hasAlphaChannel,
            hasTransparencyChunk = hasTRNS,
            colorType = colorType,
            bitDepth = bitDepth,
            paletteAlphaEntries = paletteAlphaCount,
            transparencyKind = kind
        )
    }

    private fun skipFully(dis: DataInputStream, bytes: Int) {
        var remaining = bytes
        while (remaining > 0) {
            val skipped = dis.skipBytes(min(remaining, 8192))
            if (skipped <= 0) throw EOFException()
            remaining -= skipped
        }
    }
}