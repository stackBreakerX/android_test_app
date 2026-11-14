package com.alex.studydemo.module_media

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException

/** 保留媒体扫描、编解码和输出协议，由 Compose 状态展示进度与结果。 */
class WebpLibwebpActivity : ComponentActivity() {

    // 日志和忙碌状态驱动 Compose；任务结束前禁用再次提交。
    private val output = MediaOutput()

    private lateinit var pickImagesLauncher: ActivityResultLauncher<Array<String>>

    private val MAX_DECODE_DIM = 2000
    private val WEBP_QUALITY = 75f

    /** 在创建阶段注册文档选择回调；重组只读取状态，不重复注册或启动转换。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickImagesLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNullOrEmpty()) {
                Toast.makeText(this, "未选择图片", Toast.LENGTH_SHORT).show()
            } else {
                convertUris(uris)
            }
        }
        setContent {
            MediaPage("libwebp 转换", { finish() }) {
                Text("原分辨率 · 质量 75 · 输出 Pictures/StudyDemoWebP")
                Text(if (LibWebpNative.isAvailable) "libwebp JNI 已加载：使用原生编码" else "libwebp JNI 未加载：使用 Bitmap.compress 降级")
                Button(onClick = { pickImagesLauncher.launch(arrayOf("image/*")) }, enabled = !output.running, modifier = Modifier.fillMaxWidth()) { Text("选择 JPG/PNG 并转换") }
                if (output.running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                MediaResult(output.text)
            }
        }
    }

    /** 串行处理所选 JPG/PNG；保留 JNI 优先、平台降级与每项保存结果。 */
    private fun convertUris(uris: List<Uri>) {
        output.launch(this) {
            output.text = "开始转换，共 ${uris.size} 张图片…"
            var success = 0
            val logs = StringBuilder()
            withContext(Dispatchers.IO) {
                for ((i, uri) in uris.withIndex()) {
                    ensureActive()
                    val mime = contentResolver.getType(uri) ?: guessMimeFromUri(uri)
                    if (mime != null && !isJpgOrPng(mime)) {
                        logs.append("跳过非 JPG/PNG：").append(mime).append('\n')
                        continue
                    }
                    val bmp = decodeBitmap(uri)
                    if (bmp == null) {
                        logs.append("解码失败：").append(uri).append('\n')
                        continue
                    }
                    val nativeBytes = LibWebpNative.tryEncode(bmp, WEBP_QUALITY)
                    val savedUri = if (nativeBytes != null) {
                        saveWebpBytes(nativeBytes, uri)
                    } else {
                        // 降级：使用平台 WebP
                        saveWebpBitmap(bmp, uri)
                    }
                    bmp.recycle()
                    if (savedUri != null) {
                        success++
                        logs.append("转换成功 -> ").append(savedUri).append('\n')
                    } else {
                        logs.append("保存失败：").append(uri).append('\n')
                    }
                    withContext(Dispatchers.Main) {
                        output.text = "处理中 (${i + 1}/${uris.size})…"
                    }
                }
            }
            output.text = "转换完成：成功 ${success}/${uris.size}\n\n$logs"
            Toast.makeText(this@WebpLibwebpActivity, "转换完成：成功 ${success}/${uris.size}", Toast.LENGTH_LONG).show()
        }
    }

    private fun isJpgOrPng(mime: String): Boolean {
        val m = mime.lowercase()
        return m.contains("jpeg") || m.contains("jpg") || m.contains("png")
    }

    private fun guessMimeFromUri(uri: Uri): String? {
        val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        return if (ext.isNullOrEmpty()) null else MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    // 保持原分辨率，不进行 setTargetSize
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }
            } else {
                val opts = BitmapFactory.Options().apply {
                    // 不降采样，保持 ARGB_8888 原始分辨率
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, opts)
                }
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun saveWebpBytes(data: ByteArray, originalUri: Uri): Uri? {
        val name = (getDisplayName(originalUri) ?: "image")
            .replace(".jpeg", "").replace(".jpg", "").replace(".png", "") + ".webp"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/webp")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/StudyDemoWebP")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val newUri = contentResolver.insert(collection, values) ?: return null
        return try {
            contentResolver.openOutputStream(newUri)?.use { out ->
                out.write(data)
                out.flush()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                contentResolver.update(newUri, cv, null, null)
            }
            newUri
        } catch (e: Exception) {
            null
        }
    }

    private fun saveWebpBitmap(bitmap: Bitmap, originalUri: Uri): Uri? {
        val name = (getDisplayName(originalUri) ?: "image")
            .replace(".jpeg", "").replace(".jpg", "").replace(".png", "") + ".webp"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/webp")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/StudyDemoWebP")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val newUri = contentResolver.insert(collection, values) ?: return null
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        return try {
            contentResolver.openOutputStream(newUri)?.use { out ->
                val ok = bitmap.compress(format, WEBP_QUALITY.toInt(), out)
                if (!ok) return null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                contentResolver.update(newUri, cv, null, null)
            }
            newUri
        } catch (e: Exception) {
            null
        }
    }

    private fun getDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
