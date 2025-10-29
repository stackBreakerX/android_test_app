package com.alex.studydemo.module_media

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.alex.studydemo.base.BaseActivity
import androidx.core.content.ContextCompat
import com.alex.studydemo.databinding.ActivityJpgConvertBinding
import java.util.Locale
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream


class JpgConvertActivity : BaseActivity<ActivityJpgConvertBinding>() {
    private val JPEG_QUALITY = 85
    // Add UI-only counters for native decode usage
    private var nativeDecodeSuccessCount = 0
    private var javaDecodeFallbackCount = 0
    private var lastNativeSuccess = false

    private val requestPermissionNativeLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startScanAndConvertNative()
            } else {
                Toast.makeText(this, "未授予图片读取权限，无法扫描图库", Toast.LENGTH_SHORT).show()
            }
        }

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityJpgConvertBinding =
        ActivityJpgConvertBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        binding.btnScanAndConvertNative.setOnClickListener {
            ensurePermissionAndStartNative()
        }
    }

    private fun ensurePermissionAndStartNative() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                startScanAndConvertNative()
            }
            else -> {
                requestPermissionNativeLauncher.launch(permission)
            }
        }
    }

    private fun log(message: String) {
        binding.tvResult.post { binding.tvResult.append("\n$message") }
    }

    private fun startScanAndConvertNative() {
        Thread {
            try {
                log("[C 库] 可用性：LibDngNative.isAvailable=${LibDngNative.isAvailable} | 设备 ABI=${Build.SUPPORTED_ABIS.joinToString()}")
                val nonJpgPngUris = queryNonJpgPngImages()
                binding.tvResult.post { binding.tvResult.text = "" }
                nonJpgPngUris.forEachIndexed { _, uri ->
                    val ok = convertSingleToJpgNative(uri)
                    log("----------------------------------------")
                }
                runOnUiThread {
                    Toast.makeText(this@JpgConvertActivity, "JPG 转换完成", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    log("[错误] ${e.message}")
                    Toast.makeText(this@JpgConvertActivity, "转换失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun queryNonJpgPngImages(): List<Uri> {
        val resolver = contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DISPLAY_NAME
        )
        val selection = "${MediaStore.MediaColumns.MIME_TYPE} IS NOT NULL AND ${MediaStore.MediaColumns.MIME_TYPE} != ? AND ${MediaStore.MediaColumns.MIME_TYPE} != ?"
        val selectionArgs = arrayOf("image/jpeg", "image/png")
        val sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC"

        val uris = mutableListOf<Uri>()
        resolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                uris.add(uri)
            }
        }
        return uris
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= 28) {
                val src = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }
            } else {
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, opts)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeBitmapPreferNative(uri: Uri): Bitmap? {
        return try {
            lastNativeSuccess = false
            val info = getMediaInfo(uri)
            val mime = info.mime?.lowercase(Locale.ROOT)
            val lowerNameCandidate = (info.displayName ?: uri.lastPathSegment ?: uri.toString()).lowercase(Locale.ROOT)
            val isJpeg = mime?.contains("jpeg") == true || mime?.contains("jpg") == true
            val isPng = mime?.contains("png") == true
            val isWebp = mime?.contains("webp") == true
            val isDng = mime?.contains("dng") == true || mime?.contains("x-adobe-dng") == true || lowerNameCandidate.endsWith(".dng")
            
            val name = info.displayName ?: uri.lastPathSegment ?: uri.toString()
            log("[C 库] 待解码：$name, mime=${mime ?: "unknown"}")
            log("[C 库] 类型判定：isDng=$isDng, isJpeg=$isJpeg, isPng=$isPng, isWebp=$isWebp, 文件名=$lowerNameCandidate")

            var decoded: Bitmap? = null
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val fd = pfd.fileDescriptor

                if (isDng) {
                    if (LibDngNative.isAvailable) {
                        log("[C 库] 尝试 DNG 解码（LibRaw）")
                        val bmp = LibDngNative.tryDecodeFd(fd)
                        val timing = try { LibDngNative.getLastDngTimingInfo() } catch (_: Throwable) { null }
                        val timingJson = try { LibDngNative.getLastDngTimingInfoJson() } catch (_: Throwable) { null }
                        if (timing != null) {
                            log("[C 库] DNG 阶段耗时（摘要）：$timing")
                        }
                        if (timingJson != null) {
                            log("[C 库] DNG 阶段耗时（JSON）：$timingJson")
                        }
                        if (bmp != null) {
                            log("[C 库] DNG 解码成功：${bmp.width}x${bmp.height}")
                            decoded = bmp
                            lastNativeSuccess = true
                        } else {
                            log("[C 库] DNG 解码失败（返回 null），不计入 C 库解码，回退 Java 解码")
                            decoded = decodeBitmap(uri)
                        }
                    } else {
                        log("[C 库] DNG 原生库不可用，回退 Java 解码")
                        decoded = decodeBitmap(uri)
                    }
                    return@use
                }

                if (isJpeg) {
                    if (LibJpegNative.isAvailable) {
                        log("[C 库] 尝试 JPEG 解码（libturbojpeg）")
                        val bmp = LibJpegNative.tryDecodeFd(fd)
                        if (bmp != null) {
                            log("[C 库] JPEG 解码成功：${bmp.width}x${bmp.height}")
                            decoded = bmp
                            lastNativeSuccess = true
                        } else {
                            log("[C 库] JPEG 解码失败（返回 null），不计入 C 库解码，回退 Java 解码")
                            decoded = decodeBitmap(uri)
                        }
                    } else {
                        log("[C 库] JPEG 原生库不可用，回退 Java 解码")
                        decoded = decodeBitmap(uri)
                    }
                    return@use
                }

                if (isPng) {
                    if (LibPngNative.isAvailable) {
                        log("[C 库] 尝试 PNG 解码（AImageDecoder）")
                        val bmp = LibPngNative.tryDecodeFd(fd)
                        if (bmp != null) {
                            log("[C 库] PNG 解码成功：${bmp.width}x${bmp.height}")
                            decoded = bmp
                            lastNativeSuccess = true
                        } else {
                            log("[C 库] PNG 解码失败（返回 null），不计入 C 库解码，回退 Java 解码")
                            decoded = decodeBitmap(uri)
                        }
                    } else {
                        log("[C 库] PNG 原生库不可用，回退 Java 解码")
                        decoded = decodeBitmap(uri)
                    }
                    return@use
                }

                if (isWebp) {
                    if (LibWebpNative.isAvailable) {
                        log("[C 库] 尝试 WebP 解码")
                        val bmp = LibWebpNative.tryDecodeFd(fd)
                        if (bmp != null) {
                            log("[C 库] WebP 解码成功：${bmp.width}x${bmp.height}")
                            decoded = bmp
                            lastNativeSuccess = true
                        } else {
                            log("[C 库] WebP 解码失败（返回 null），不计入 C 库解码，回退 Java 解码")
                            decoded = decodeBitmap(uri)
                        }
                    } else {
                        log("[C 库] WebP 原生库不可用，回退 Java 解码")
                        decoded = decodeBitmap(uri)
                    }
                    return@use
                }

                log("[C 库] 类型不匹配或未知，Java 解码")
                decoded = decodeBitmap(uri)
            } ?: run {
                log("[C 库] 无法打开文件 FD，Java 解码")
                return decodeBitmap(uri)
            }

            return decoded ?: decodeBitmap(uri)
        } catch (e: Exception) {
            log("[C 库] 原生路径异常：${e.message}，Java 解码")
            return decodeBitmap(uri)
        }
    }

    private fun flattenIfHasAlpha(src: Bitmap): Bitmap {
        if (!src.hasAlpha()) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(src, 0f, 0f, null)
        return out
    }

    private fun convertSingleToJpgNative(srcUri: Uri): Boolean {
        val tStart = System.nanoTime()
        val tDecodeStart = System.nanoTime()
        val bitmap = decodeBitmapPreferNative(srcUri) ?: return false
        val tDecodeMs = (System.nanoTime() - tDecodeStart) / 1_000_000
        val w = bitmap.width
        val h = bitmap.height
        val hasAlpha = bitmap.hasAlpha()
    
        val tFlattenStart = System.nanoTime()
        val flattened = flattenIfHasAlpha(bitmap)
        val tFlattenMs = (System.nanoTime() - tFlattenStart) / 1_000_000
    
        var outUri: Uri? = null
        var tEncodeMs = 0L
        var usedNativeEncode = false
    
        val result = try {
            val displayName = getDisplayName(srcUri) ?: System.currentTimeMillis().toString()
            val baseName = displayName.substringBeforeLast('.')
            val jpgName = "$baseName.jpg"
    
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, jpgName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/JpgConverted")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
    
            val resolver = contentResolver
            val targetCollection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            outUri = resolver.insert(targetCollection, values) ?: return false
    
            var ok = false
            var fd: ParcelFileDescriptor? = null
            try {
                val tEncodeStart = System.nanoTime()
                fd = resolver.openFileDescriptor(outUri!!, "w")
                if (!hasAlpha) {
                    flattened.setHasAlpha(false)
                }
                ok = fd?.fileDescriptor?.let { LibJpegNative.tryEncodeFd(flattened, JPEG_QUALITY, dropAlpha = hasAlpha, it) } ?: false
                usedNativeEncode = ok
                tEncodeMs = (System.nanoTime() - tEncodeStart) / 1_000_000
            } finally {
                try { fd?.close() } catch (_: IOException) {}
            }
    
            if (!ok) {
                // Fallback to Java compress if native not available or failed
                var out: OutputStream? = null
                try {
                    val tEncodeStart = System.nanoTime()
                    out = resolver.openOutputStream(outUri!!)
                    if (out == null) return false
                    BufferedOutputStream(out).use { bos ->
                        val ok2 = flattened.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bos)
                        if (!ok2) return false
                    }
                    tEncodeMs = (System.nanoTime() - tEncodeStart) / 1_000_000
                } finally {
                    try { out?.close() } catch (_: IOException) {}
                }
            }
    
            if (Build.VERSION.SDK_INT >= 29) {
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(outUri, done, null, null)
            }
            true
        } catch (e: Exception) {
            false
        } finally {
            if (flattened !== bitmap) flattened.recycle()
            bitmap.recycle()
        }
    
        val tTotalMs = (System.nanoTime() - tStart) / 1_000_000
        val srcInfo = getMediaInfo(srcUri)
        val srcName = srcInfo.displayName ?: getDisplayName(srcUri) ?: srcUri.lastPathSegment ?: srcUri.toString()
        val srcSizeStr = formatSize(srcInfo.size)
        val outSizeStr = formatSize(outUri?.let { getMediaInfo(it).size })
        val dngTimingSummary = try { LibDngNative.getLastDngTimingInfo() } catch (_: Throwable) { null }
        val decodeLine = buildString {
            append("耗时：解码=")
            append(tDecodeMs)
            append("ms")
            if (!dngTimingSummary.isNullOrEmpty()) {
                append("（C库：")
                append(dngTimingSummary)
                append("）")
            }
            append("，去Alpha=")
            append(tFlattenMs)
            append("ms，编码=")
            append(tEncodeMs)
            append("ms，总=")
            append(tTotalMs)
            append("ms")
        }
        val msg = "原图信息：${srcName} | ${srcInfo.mime ?: "-"} | ${srcSizeStr}\n" +
                  "解码后的图片信息：${w}x${h} | alpha=${hasAlpha}\n" +
                  decodeLine + "\n" +
                  "c库解码还是原生解码，c库编码还是原生编码：解码=${if (lastNativeSuccess) "C 库解码" else "原生解码"}，编码=${if (usedNativeEncode) "C 库编码" else "原生编码"}"
        runOnUiThread { log(msg) }
        return result
    }

    // 新增：媒体信息与大小格式化
    private data class MediaInfo(
        val displayName: String?,
        val mime: String?,
        val size: Long?
    )

    private fun getMediaInfo(uri: Uri): MediaInfo {
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE
        )
        return try {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                if (cursor.moveToFirst()) {
                    MediaInfo(
                        cursor.getString(nameIdx),
                        cursor.getString(mimeIdx),
                        if (!cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else null
                    )
                } else MediaInfo(null, null, null)
            } ?: MediaInfo(null, null, null)
        } catch (_: Exception) {
            MediaInfo(null, null, null)
        }
    }

    private fun formatSize(size: Long?): String {
        val s = size ?: return "-"
        return when {
            s < 1024 -> "${s}B"
            s < 1024 * 1024 -> "${s / 1024}KB"
            else -> "${s / (1024 * 1024)}MB"
        }
    }

    private fun getDisplayName(uri: Uri): String? {
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
        return try {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                if (cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (e: Exception) {
            null
        }
    }
}