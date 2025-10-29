package com.alex.studydemo.module_media

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
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
import androidx.lifecycle.lifecycleScope
import com.alex.studydemo.databinding.ActivityPngConvertBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream

// Add native PNG
import com.alex.studydemo.module_media.LibPngNative

class PngConvertActivity : BaseActivity<ActivityPngConvertBinding>() {

    // Native PNG compression level: 0-9 (speed vs size)
    private val PNG_COMPRESSION_LEVEL = 3

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startScanAndConvert()
            } else {
                Toast.makeText(this, "未授予图片读取权限，无法扫描图库", Toast.LENGTH_SHORT).show()
            }
        }

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityPngConvertBinding =
        ActivityPngConvertBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        binding.btnScanAndConvert.setOnClickListener {
            ensurePermissionAndStart()
        }
        binding.btnScanAndConvertJpg.setOnClickListener {
            ensurePermissionAndStartJpg()
        }
    }

    private fun ensurePermissionAndStart() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                startScanAndConvert()
            }
            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun ensurePermissionAndStartJpg() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                startScanAndConvertJpg()
            }
            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun log(message: String) {
        binding.tvResult.append("\n$message")
    }

    private fun startScanAndConvert() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val nonJpgPngUris = queryNonJpgPngImages()
                withContext(Dispatchers.Main) {
                    binding.tvResult.text = "共找到 ${nonJpgPngUris.size} 张非 JPG/PNG/WEBP/GIF 图片，将开始转换为 PNG..."
                }
                val tAllStart = System.nanoTime()
                var success = 0
                var fail = 0
                nonJpgPngUris.forEachIndexed { index, uri ->
                    val name = getDisplayName(uri) ?: uri.lastPathSegment ?: uri.toString()
                    val ok = convertSingleToPng(uri)
                    if (ok) success++ else fail++
                    withContext(Dispatchers.Main) {
                        log("(${index + 1}/${nonJpgPngUris.size}) ${if (ok) "✅" else "❌"} 转换: ${name}")
                    }
                }
                val totalMs = (System.nanoTime() - tAllStart) / 1_000_000
                withContext(Dispatchers.Main) {
                    log("完成：成功 $success，失败 $fail，总耗时 ${totalMs}ms")
                    Toast.makeText(this@PngConvertActivity, "PNG 转换完成：${totalMs}ms", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    log("发生错误: ${e.message}")
                    Toast.makeText(this@PngConvertActivity, "转换失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startScanAndConvertJpg() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val nonJpgUris = queryNonJpgImages()
                withContext(Dispatchers.Main) {
                    binding.tvResult.text = "共找到 ${nonJpgUris.size} 张非 JPG 图片，将开始转换为 JPG（质量100）..."
                }
                val tAllStart = System.nanoTime()
                var success = 0
                var fail = 0
                nonJpgUris.forEachIndexed { index, uri ->
                    val name = getDisplayName(uri) ?: uri.lastPathSegment ?: uri.toString()
                    val ok = convertSingleToJpg(uri)
                    if (ok) success++ else fail++
                    withContext(Dispatchers.Main) {
                        log("(${index + 1}/${nonJpgUris.size}) ${if (ok) "✅" else "❌"} 转换: ${name}")
                    }
                }
                val totalMs = (System.nanoTime() - tAllStart) / 1_000_000
                withContext(Dispatchers.Main) {
                    log("完成：成功 $success，失败 $fail，总耗时 ${totalMs}ms")
                    Toast.makeText(this@PngConvertActivity, "JPG 转换完成：${totalMs}ms", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    log("发生错误: ${e.message}")
                    Toast.makeText(this@PngConvertActivity, "转换失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun queryNonJpgImages(): List<Uri> {
        val resolver = contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DISPLAY_NAME
        )
        val selection = "${MediaStore.MediaColumns.MIME_TYPE} IS NOT NULL AND ${MediaStore.MediaColumns.MIME_TYPE} != ?"
        val selectionArgs = arrayOf("image/jpeg")
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

    private fun queryNonJpgPngImages(): List<Uri> {
        val resolver = contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DISPLAY_NAME
        )
        val selection = "${MediaStore.MediaColumns.MIME_TYPE} IS NOT NULL AND ${MediaStore.MediaColumns.MIME_TYPE} != ? AND ${MediaStore.MediaColumns.MIME_TYPE} != ? AND ${MediaStore.MediaColumns.MIME_TYPE} != ? AND ${MediaStore.MediaColumns.MIME_TYPE} != ?"
        val selectionArgs = arrayOf("image/jpeg", "image/png", "image/webp", "image/gif")
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
                ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                    val w = info.size.width
                    val h = info.size.height
                    if (w > 0 && h > 0) {
                        // 保持原始分辨率，不进行任何缩放
                        decoder.setTargetSize(w, h)
                    }
                }
            } else {
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inSampleSize = 1
                }
                contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, opts)
                }
            }
        } catch (e: Exception) {
            null
        }
    }


    private fun convertSingleToPng(srcUri: Uri): Boolean {
        val tStart = System.nanoTime()
        val info = getMediaInfo(srcUri)
        val tDecodeStart = System.nanoTime()
        val bitmap = decodeBitmap(srcUri) ?: return false
        val tDecodeMs = (System.nanoTime() - tDecodeStart) / 1_000_000
        val w = bitmap.width
        val h = bitmap.height
        val hasAlpha = bitmap.hasAlpha()

        var result = false
        var tEncodeMs = 0L
        var outSize: Long? = null
        try {
            val displayName = info.displayName ?: getDisplayName(srcUri) ?: System.currentTimeMillis().toString()
            val baseName = displayName.substringBeforeLast('.')
            val pngName = "$baseName.png"

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, pngName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PngConverted")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = contentResolver
            val targetCollection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val outUri = resolver.insert(targetCollection, values) ?: return false

            val tEncodeStart = System.nanoTime()
            var fd: ParcelFileDescriptor? = null
            var out: OutputStream? = null
            try {
                // 尽量优先使用原生FD流式编码，减少内存拷贝与GC
                fd = resolver.openFileDescriptor(outUri, "w")
                if (!hasAlpha) {
                    bitmap.setHasAlpha(false)
                }
                val level = PNG_COMPRESSION_LEVEL

                val fdOk = try {
                    fd?.fileDescriptor?.let { LibPngNative.tryEncodeFd(bitmap, level, dropAlpha = !hasAlpha, it) } ?: false
                } catch (_: Throwable) { false }

                if (!fdOk) {
                    // 回退到字节数组路径
                    out = resolver.openOutputStream(outUri)
                    if (out == null) return false
                    BufferedOutputStream(out, 256 * 1024).use { bos ->
                        val nativeBytes2 = LibPngNative.tryEncode2(bitmap, level, dropAlpha = !hasAlpha)
                        if (nativeBytes2 != null) {
                            bos.write(nativeBytes2)
                        } else {
                            val nativeBytes = LibPngNative.tryEncode(bitmap, level)
                            if (nativeBytes != null) {
                                bos.write(nativeBytes)
                            } else {
                                val ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
                                if (!ok) return false
                            }
                        }
                    }
                }
            } finally {
                try { out?.close() } catch (_: IOException) {}
                try { fd?.close() } catch (_: IOException) {}
            }
            tEncodeMs = (System.nanoTime() - tEncodeStart) / 1_000_000

            if (Build.VERSION.SDK_INT >= 29) {
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(outUri, done, null, null)
            }
            outSize = try {
                resolver.query(outUri, arrayOf(MediaStore.Images.Media.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getLong(0) else null
                }
            } catch (_: Exception) { null }

            result = true
            val tAllMs = (System.nanoTime() - tStart) / 1_000_000
            val srcName = info.displayName ?: getDisplayName(srcUri) ?: srcUri.lastPathSegment ?: srcUri.toString()
            val srcSizeStr = formatSize(info.size)
            val outSizeStr = formatSize(outSize)
            val msg = "原图信息：${srcName} | ${info.mime ?: "-"} | ${w}x${h} | ${srcSizeStr} | alpha=${hasAlpha}\n" +
                      "转码图片信息：image/png | ${w}x${h} | ${outSizeStr} | 压缩等级=${PNG_COMPRESSION_LEVEL}\n" +
                      "耗时：解码=${tDecodeMs}ms，编码=${tEncodeMs}ms，总=${tAllMs}ms"
            runOnUiThread {
                log(msg)
            }
        } catch (e: Exception) {
            result = false
            runOnUiThread {
                binding.tvResult.text = "转换失败: ${e.message}".replace("\n", " ")
            }
        }
        return result
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

    private fun convertSingleToJpg(srcUri: Uri): Boolean {
        val tStart = System.nanoTime()
        val info = getMediaInfo(srcUri)
        val tDecodeStart = System.nanoTime()
        val bitmap = decodeBitmap(srcUri) ?: return false
        val tDecodeMs = (System.nanoTime() - tDecodeStart) / 1_000_000
        val w = bitmap.width
        val h = bitmap.height
        val hasAlpha = bitmap.hasAlpha()

        // JPEG 不支持 alpha，若有 alpha 则铺底白色
        val rgbBitmap = if (hasAlpha) flattenIfHasAlpha(bitmap) else bitmap

        var result = false
        var tEncodeMs = 0L
        var outSize: Long? = null
        try {
            val displayName = info.displayName ?: getDisplayName(srcUri) ?: System.currentTimeMillis().toString()
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
            val outUri = resolver.insert(targetCollection, values) ?: return false

            val tEncodeStart = System.nanoTime()
            resolver.openOutputStream(outUri)?.use { os ->
                BufferedOutputStream(os).use { bos ->
                    // 质量 100，尽量不丢真（仍是有损格式）
                    if (!rgbBitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos)) {
                        throw IOException("JPEG 压缩失败")
                    }
                    bos.flush()
                }
            }
            tEncodeMs = (System.nanoTime() - tEncodeStart) / 1_000_000

            if (Build.VERSION.SDK_INT >= 29) {
                val cv = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(outUri, cv, null, null)
            }

            outSize = getMediaInfo(outUri).size
            result = true

            val tTotalMs = (System.nanoTime() - tStart) / 1_000_000
            val srcName = info.displayName ?: getDisplayName(srcUri) ?: srcUri.lastPathSegment ?: srcUri.toString()
            val srcSizeStr = formatSize(info.size)
            val outSizeStr = formatSize(outSize)
            val msg = "原图信息：${srcName} | ${info.mime ?: "-"} | ${w}x${h} | ${srcSizeStr} | alpha=${hasAlpha}\n" +
                      "转码图片信息：image/jpeg | ${w}x${h} | ${outSizeStr} | 质量=100 | alphaFlatten=${hasAlpha}\n" +
                      "耗时：解码=${tDecodeMs}ms，编码=${tEncodeMs}ms，总=${tTotalMs}ms"
            runOnUiThread { log(msg) }
        } catch (e: Exception) {
            runOnUiThread { log("JPG 失败: ${e.message}") }
            result = false
        } finally {
            if (rgbBitmap !== bitmap) rgbBitmap.recycle()
            bitmap.recycle()
        }
        return result
    }

    private fun flattenIfHasAlpha(src: Bitmap): Bitmap {
        if (!src.hasAlpha()) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(src, 0f, 0f, null)
        return out
    }

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
                        cursor.getLong(sizeIdx)
                    )
                } else {
                    MediaInfo(null, null, null)
                }
            } ?: MediaInfo(null, null, null)
        } catch (e: Exception) {
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
}