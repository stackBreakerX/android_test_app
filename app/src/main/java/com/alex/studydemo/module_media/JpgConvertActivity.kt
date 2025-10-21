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
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.alex.studydemo.databinding.ActivityJpgConvertBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream

class JpgConvertActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJpgConvertBinding
    private val JPEG_QUALITY = 85

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startScanAndConvert()
            } else {
                Toast.makeText(this, "未授予图片读取权限，无法扫描图库", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJpgConvertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnScanAndConvert.setOnClickListener {
            ensurePermissionAndStart()
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

    private fun log(message: String) {
        binding.tvResult.append("\n$message")
    }

    private fun startScanAndConvert() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val nonJpgPngUris = queryNonJpgPngImages()
                withContext(Dispatchers.Main) {
                    binding.tvResult.text = "共找到 ${nonJpgPngUris.size} 张非 JPG/PNG 图片，将开始转换为 JPG..."
                }
                val tAllStart = System.nanoTime()
                var success = 0
                var fail = 0
                nonJpgPngUris.forEachIndexed { index, uri ->
                    val ok = convertSingleToJpg(uri)
                    if (ok) success++ else fail++
                    val tProgress = (System.nanoTime() - tAllStart) / 1_000_000
                    withContext(Dispatchers.Main) {
                        log("(${index + 1}/${nonJpgPngUris.size}) ${if (ok) "✅" else "❌"} 转换: $uri，累计 ${tProgress}ms")
                    }
                }
                val totalMs = (System.nanoTime() - tAllStart) / 1_000_000
                withContext(Dispatchers.Main) {
                    log("完成：成功 $success，失败 $fail，总耗时 ${totalMs}ms")
                    Toast.makeText(this@JpgConvertActivity, "JPG 转换完成", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    log("发生错误: ${e.message}")
                    Toast.makeText(this@JpgConvertActivity, "转换失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
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

    private fun flattenIfHasAlpha(src: Bitmap): Bitmap {
        if (!src.hasAlpha()) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(src, 0f, 0f, null)
        return out
    }

    private fun convertSingleToJpg(srcUri: Uri): Boolean {
        val tStart = System.nanoTime()
        val tDecodeStart = System.nanoTime()
        val bitmap = decodeBitmap(srcUri) ?: return false
        val tDecodeMs = (System.nanoTime() - tDecodeStart) / 1_000_000

        val tFlattenStart = System.nanoTime()
        val flattened = flattenIfHasAlpha(bitmap)
        val tFlattenMs = (System.nanoTime() - tFlattenStart) / 1_000_000

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
            val outUri = resolver.insert(targetCollection, values) ?: return false

            var out: OutputStream? = null
            try {
                out = resolver.openOutputStream(outUri)
                if (out == null) return false
                BufferedOutputStream(out).use { bos ->
                    val ok = flattened.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bos)
                    if (!ok) return false
                }
            } finally {
                try { out?.close() } catch (_: IOException) {}
            }

            if (Build.VERSION.SDK_INT >= 29) {
                val done = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
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
        runOnUiThread {
            log("JPG: 解码 ${tDecodeMs}ms，平铺 ${tFlattenMs}ms，总 ${tTotalMs}ms")
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
}