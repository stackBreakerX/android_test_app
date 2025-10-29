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
import com.alex.studydemo.base.BaseActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.alex.studydemo.databinding.ActivityWebpLibwebpBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class WebpLibwebpActivity : BaseActivity<ActivityWebpLibwebpBinding>() {
    private lateinit var pickImagesLauncher: ActivityResultLauncher<Array<String>>

    private val MAX_DECODE_DIM = 2000
    private val WEBP_QUALITY = 75f

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityWebpLibwebpBinding =
        ActivityWebpLibwebpBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {

        binding.tvHeader.text = if (LibWebpNative.isAvailable) {
            "libwebp JNI 已加载：使用原生编码"
        } else {
            "libwebp JNI 未加载：将使用 Bitmap.compress 作为降级"
        }

        pickImagesLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNullOrEmpty()) {
                Toast.makeText(this, "未选择图片", Toast.LENGTH_SHORT).show()
            } else {
                convertUris(uris)
            }
        }

        binding.btnPickAndConvert.setOnClickListener { pickImagesLauncher.launch(arrayOf("image/*")) }
    }

    private fun convertUris(uris: List<Uri>) {
        lifecycleScope.launch {
            binding.tvResult.text = "开始转换，共 ${uris.size} 张图片…"
            var success = 0
            val logs = StringBuilder()
            withContext(Dispatchers.IO) {
                for ((i, uri) in uris.withIndex()) {
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
                        binding.tvResult.text = "处理中 (${i + 1}/${uris.size})…"
                    }
                }
            }
            binding.tvResult.text = "转换完成：成功 ${success}/${uris.size}\n\n$logs"
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