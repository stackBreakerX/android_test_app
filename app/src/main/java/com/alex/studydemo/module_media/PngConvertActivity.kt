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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream

// 原生 PNG 编码在不可用时由平台压缩器接管。
import com.alex.studydemo.module_media.LibPngNative

/** 保留媒体扫描、编解码和输出协议，由 Compose 状态展示进度与结果。 */
class PngConvertActivity : ComponentActivity() {

    // 日志和忙碌状态驱动 Compose；任务结束前禁用再次提交。
    private val output = MediaOutput()


    // PNG 压缩等级范围 0～9；等级 3 在速度与体积之间折中。
    private val PNG_COMPRESSION_LEVEL = 3

    // 权限弹窗返回后恢复用户选择的输出格式，不能默认切换成 PNG。
    private var pendingJpg = false

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                if (pendingJpg) startScanAndConvertJpg() else startScanAndConvert()
            } else {
                Toast.makeText(this, "未授予图片读取权限，无法扫描图库", Toast.LENGTH_SHORT).show()
            }
        }

    /** 创建扫描操作与日志界面；重组只读取状态，不重复启动转换。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingJpg = savedInstanceState?.getBoolean("media_pending_jpg") ?: false
        setContent {
            MediaPage("PNG / JPG 批量转换", { finish() }) {
                Text("PNG 压缩等级 3；PNG 扫描排除 JPG/PNG/WebP/GIF。输出 Pictures/PngConverted 或 JpgConverted。")
                Button(onClick = { ensurePermissionAndStart() }, enabled = !output.running, modifier = Modifier.fillMaxWidth()) { Text("扫描并转换为 PNG") }
                Button(onClick = { ensurePermissionAndStartJpg() }, enabled = !output.running, modifier = Modifier.fillMaxWidth()) { Text("扫描并转换为 JPG（质量 100）") }
                if (output.running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                MediaResult(output.text)
            }
        }
    }

    /** 系统在权限弹窗期间重建页面时，仍恢复到用户请求的转换格式。 */
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("media_pending_jpg", pendingJpg)
        super.onSaveInstanceState(outState)
    }

    /** 按 Android 版本选择图库读取权限；已授权立即转换，否则等待权限回调。 */
    private fun ensurePermissionAndStart() {
        pendingJpg = false
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

    /** 记录 JPG 输出意图，再按 Android 版本请求图库读取权限。 */
    private fun ensurePermissionAndStartJpg() {
        pendingJpg = true
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

    /** 在主线程追加转换日志，保留已有输出以便比较每张图片的处理结果。 */
    private fun log(message: String) {
        output.text += "\n$message"
    }

    /** 在 IO 线程扫描并转换非 JPG/PNG/WebP/GIF 图片；逐项进度与失败原因回到主线程。 */
    private fun startScanAndConvert() {
        output.launch(this, Dispatchers.IO) {
            try {
                val nonJpgPngUris = queryNonJpgPngImages()
                withContext(Dispatchers.Main) {
                    output.text = "共找到 ${nonJpgPngUris.size} 张非 JPG/PNG/WEBP/GIF 图片，将开始转换为 PNG..."
                }
                val tAllStart = System.nanoTime()
                var success = 0
                var fail = 0
                nonJpgPngUris.forEachIndexed { index, uri ->
                    ensureActive()
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

    /** 在 IO 线程把非 JPG 图片转换成质量 100 的 JPG；逐项计数并保留失败日志。 */
    private fun startScanAndConvertJpg() {
        output.launch(this, Dispatchers.IO) {
            try {
                val nonJpgUris = queryNonJpgImages()
                withContext(Dispatchers.Main) {
                    output.text = "共找到 ${nonJpgUris.size} 张非 JPG 图片，将开始转换为 JPG（质量100）..."
                }
                val tAllStart = System.nanoTime()
                var success = 0
                var fail = 0
                nonJpgUris.forEachIndexed { index, uri ->
                    ensureActive()
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


    /** 按原分辨率编码 PNG，依次尝试原生 FD、字节数组与平台编码；返回保存是否成功。 */
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
                output.text = "转换失败: ${e.message}".replace("\n", " ")
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

    /** 按原分辨率输出质量 100 的 JPG；透明像素铺白底，结束时释放位图。 */
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
