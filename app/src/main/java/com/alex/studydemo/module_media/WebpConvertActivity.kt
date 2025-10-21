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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.alex.studydemo.databinding.ActivityWebpConvertBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.min
import java.io.BufferedOutputStream
import java.io.IOException

class WebpConvertActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebpConvertBinding

    private lateinit var pickImagesLauncher: ActivityResultLauncher<Array<String>>

    private val MAX_DECODE_DIM = 2000
    private val WEBP_QUALITY = 75
    private val PARALLELISM = min(4, Runtime.getRuntime().availableProcessors())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebpConvertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pickImagesLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNullOrEmpty()) {
                Toast.makeText(this, "未选择图片", Toast.LENGTH_SHORT).show()
            } else {
                convertUrisToWebp(uris)
            }
        }

        binding.btnPickAndConvert.setOnClickListener {
            // 使用系统图库选择多张图片
            pickImages()
        }
    }

    private fun pickImages() {
        pickImagesLauncher.launch(arrayOf("image/*"))
    }

    private fun convertUrisToWebp(uris: List<Uri>) {
        lifecycleScope.launch {
            binding.tvResult.text = "开始转换，共 ${uris.size} 张图片…"

            val semaphore = Semaphore(PARALLELISM)
            val results = uris.mapIndexed { index, uri ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        try {
                            val mime = contentResolver.getType(uri) ?: guessMimeFromUri(uri)
                            if (mime != null && !isJpgOrPng(mime)) {
                                return@withPermit false to "跳过非 JPG/PNG：$mime"
                            }

                            val bitmap = decodeBitmap(uri) ?: return@withPermit false to "解码失败：$uri"

                            val saved = saveWebp(bitmap, uri)
                            bitmap.recycle()
                            return@withPermit if (saved != null) {
                                true to "转换成功 -> $saved"
                            } else {
                                false to "保存失败：$uri"
                            }
                        } catch (e: Exception) {
                            return@withPermit false to "异常：${e.message}"
                        } finally {
                            withContext(Dispatchers.Main) {
                                binding.tvResult.text = "处理中 (${index + 1}/${uris.size})…" 
                            }
                        }
                    }
                }
            }.awaitAll()

            val successCount = results.count { it.first }
            val logs = results.joinToString("\n") { it.second }
            binding.tvResult.text = "转换完成：成功 ${successCount}/${uris.size}\n\n$logs"
            Toast.makeText(this@WebpConvertActivity, "转换完成：成功 ${successCount}/${uris.size}", Toast.LENGTH_LONG).show()
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
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val maxW = MAX_DECODE_DIM
                    val maxH = MAX_DECODE_DIM
                    val scale = kotlin.math.min(maxW.toFloat() / w, maxH.toFloat() / h)
                    if (scale < 1f) {
                        decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }
            } else {
                // 旧版使用 inSampleSize 降采样，避免解码超大图导致耗时/内存飙升
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, bounds)
                }
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(bounds, MAX_DECODE_DIM, MAX_DECODE_DIM)
                    inPreferredConfig = Bitmap.Config.RGB_565
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

    private fun saveWebp(bitmap: Bitmap, originalUri: Uri): Uri? {
        val name = (getDisplayName(originalUri) ?: "image")
            .replace(".jpeg", "")
            .replace(".jpg", "")
            .replace(".png", "") + ".webp"

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

//        var format = Bitmap.CompressFormat.WEBP
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }

        return try {
            contentResolver.openOutputStream(newUri)?.use { raw ->
                BufferedOutputStream(raw).use { out ->
                    val ok = bitmap.compress(format, WEBP_QUALITY, out)
                    out.flush()
                    if (!ok) return null
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                contentResolver.update(newUri, cv, null, null)
            }
            newUri
        } catch (e: Exception) {
            println("error = $e")
            return null
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