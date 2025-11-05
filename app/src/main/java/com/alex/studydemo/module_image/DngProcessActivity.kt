package com.alex.studydemo.module_image

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.alex.studydemo.R
import com.alex.studydemo.databinding.ActivityDngProcessBinding
import com.alex.studydemo.module_media.LibWebpNative
import com.alibaba.android.arouter.facade.annotation.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

@Route(path = DngProcessActivity.PATH)
class DngProcessActivity : AppCompatActivity() {

    private lateinit var mViewBinding: ActivityDngProcessBinding
    private var selectedDngUri: Uri? = null
    private var processedBitmap: Bitmap? = null
    private val performanceMetrics = PerformanceMetrics()

    companion object {
        const val PATH = "/view/DngProcess"
        private const val TAG = "DngProcessActivity"
        private const val REQUEST_STORAGE_PERMISSION = 1004
        
        // 性能配置
        private const val MAX_OUTPUT_WIDTH = 1920
        private const val MAX_OUTPUT_HEIGHT = 1920
        private const val WEBP_QUALITY = 85f
        private const val TARGET_SIZE_MB = 2.0 // 目标文件大小（MB）
    }

    // 文件选择器
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedDngUri = uri
                mViewBinding.tvSelectedFile.text = "已选择: ${getFileName(uri)}"
                mViewBinding.btnProcess.isEnabled = true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewBinding = ActivityDngProcessBinding.inflate(layoutInflater)
        setContentView(mViewBinding.root)

        initViews()
        checkPermissions()
    }

    private fun initViews() {
        mViewBinding.btnSelectFile.setOnClickListener {
            selectDngFile()
        }

        mViewBinding.btnProcess.setOnClickListener {
            selectedDngUri?.let { processDng(it) }
        }

        mViewBinding.btnClear.setOnClickListener {
            clearAll()
        }

        // 初始化裁剪参数设置
        mViewBinding.etCropLeft.setText("0")
        mViewBinding.etCropTop.setText("0")
        mViewBinding.etCropRight.setText("100")
        mViewBinding.etCropBottom.setText("100")
        mViewBinding.etScaleFactor.setText("1.0")
        mViewBinding.etQuality.setText(WEBP_QUALITY.toString())

        mViewBinding.btnProcess.isEnabled = false
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_STORAGE_PERMISSION
            )
        }
    }

    private fun selectDngFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/x-adobe-dng"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        filePickerLauncher.launch(intent)
    }

    private fun processDng(uri: Uri) {
        showLoading(true)
        performanceMetrics.reset()

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    processDngInternal(uri)
                }

                withContext(Dispatchers.Main) {
                    if (result != null) {
                        processedBitmap?.let { bitmap ->
                            mViewBinding.ivPreview.setImageBitmap(bitmap)
                        }
                        showResult(result)
                    } else {
                        showError("处理失败")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理DNG失败", e)
                withContext(Dispatchers.Main) {
                    showError("处理失败: ${e.message}")
                }
            } finally {
                showLoading(false)
            }
        }
    }

    private suspend fun processDngInternal(uri: Uri): ProcessResult? = withContext(Dispatchers.IO) {
        try {
            // 步骤1: 加载DNG文件
            val loadStart = System.currentTimeMillis()
            val bitmap = loadDngFile(uri) ?: return@withContext null
            performanceMetrics.loadTime = System.currentTimeMillis() - loadStart
            Log.d(TAG, "加载DNG耗时: ${performanceMetrics.loadTime}ms")

            // 步骤2: 裁剪
            val cropStart = System.currentTimeMillis()
            val croppedBitmap = cropBitmap(bitmap)
            performanceMetrics.cropTime = System.currentTimeMillis() - cropStart
            Log.d(TAG, "裁剪耗时: ${performanceMetrics.cropTime}ms")
            bitmap.recycle()

            // 步骤3: 压缩
            val compressStart = System.currentTimeMillis()
            val compressedBitmap = compressBitmap(croppedBitmap)
            performanceMetrics.compressTime = System.currentTimeMillis() - compressStart
            Log.d(TAG, "压缩耗时: ${performanceMetrics.compressTime}ms")
            croppedBitmap.recycle()

            // 步骤4: 转换为WebP
            val convertStart = System.currentTimeMillis()
            val webpUri = convertToWebP(compressedBitmap, uri)
            performanceMetrics.convertTime = System.currentTimeMillis() - convertStart
            Log.d(TAG, "WebP转换耗时: ${performanceMetrics.convertTime}ms")

            processedBitmap = compressedBitmap
            val totalTime = performanceMetrics.getTotalTime()

            ProcessResult(
                webpUri = webpUri,
                originalSize = getFileSize(uri),
                outputSize = webpUri?.let { getFileSize(it) } ?: 0L,
                metrics = performanceMetrics
            )
        } catch (e: Exception) {
            Log.e(TAG, "处理DNG失败", e)
            null
        }
    }

    /**
     * 加载DNG文件
     * 注意：Android原生对DNG支持有限，优先尝试BitmapFactory，失败则尝试使用DngCreator
     */
    private fun loadDngFile(uri: Uri): Bitmap? {
        // 方法1: 尝试使用BitmapFactory直接解码（某些系统可能支持）
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                if (bitmap != null) {
                    Log.d(TAG, "使用BitmapFactory成功加载DNG")
                    return bitmap
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "BitmapFactory解码失败，尝试其他方法", e)
        }

        // 方法2: 尝试使用FileDescriptor（可能在某些设备上工作）
        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)
                if (bitmap != null) {
                    Log.d(TAG, "使用FileDescriptor成功加载DNG")
                    return bitmap
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "FileDescriptor解码失败", e)
        }

        // 方法3: 如果系统支持DngCreator，尝试使用（Android 7.0+）
        // 注意：DngCreator主要用于写入，读取能力有限
        // 这里我们尝试使用MediaStore查找对应的JPEG预览图
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // 尝试从MediaStore获取JPEG预览
                val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA)
                val selection = "${MediaStore.Images.Media.DATA} LIKE ?"
                val selectionArgs = arrayOf("%.dng")
                
                contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                        val data = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                        
                        // 查找对应的JPEG文件（通常DNG和JPEG在同一个目录）
                        val jpegPath = data.replace(".dng", ".jpg", ignoreCase = true)
                            .replace(".DNG", ".jpg", ignoreCase = true)
                        val jpegFile = File(jpegPath)
                        
                        if (jpegFile.exists()) {
                            val bitmap = BitmapFactory.decodeFile(jpegPath)
                            if (bitmap != null) {
                                Log.d(TAG, "找到对应的JPEG预览图")
                                return bitmap
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "查找JPEG预览图失败", e)
        }

        Log.e(TAG, "所有DNG加载方法都失败")
        return null
    }

    /**
     * 裁剪图片
     */
    private fun cropBitmap(bitmap: Bitmap): Bitmap {
        val left = mViewBinding.etCropLeft.text.toString().toFloatOrNull()?.div(100f) ?: 0f
        val top = mViewBinding.etCropTop.text.toString().toFloatOrNull()?.div(100f) ?: 0f
        val right = mViewBinding.etCropRight.text.toString().toFloatOrNull()?.div(100f) ?: 1f
        val bottom = mViewBinding.etCropBottom.text.toString().toFloatOrNull()?.div(100f) ?: 1f

        val width = bitmap.width
        val height = bitmap.height

        val cropRect = Rect(
            (width * left).toInt(),
            (height * top).toInt(),
            (width * right).toInt(),
            (height * bottom).toInt()
        )

        return Bitmap.createBitmap(bitmap, cropRect.left, cropRect.top, 
            cropRect.width(), cropRect.height())
    }

    /**
     * 压缩图片
     */
    private fun compressBitmap(bitmap: Bitmap): Bitmap {
        val scaleFactor = mViewBinding.etScaleFactor.text.toString().toFloatOrNull() ?: 1.0f
        
        var targetWidth = (bitmap.width * scaleFactor).toInt()
        var targetHeight = (bitmap.height * scaleFactor).toInt()

        // 限制最大尺寸
        if (targetWidth > MAX_OUTPUT_WIDTH || targetHeight > MAX_OUTPUT_HEIGHT) {
            val scale = minOf(
                MAX_OUTPUT_WIDTH.toFloat() / targetWidth,
                MAX_OUTPUT_HEIGHT.toFloat() / targetHeight
            )
            targetWidth = (targetWidth * scale).toInt()
            targetHeight = (targetHeight * scale).toInt()
        }

        if (targetWidth == bitmap.width && targetHeight == bitmap.height) {
            return bitmap
        }

        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    /**
     * 转换为WebP
     */
    private fun convertToWebP(bitmap: Bitmap, originalUri: Uri): Uri? {
        return try {
            val quality = mViewBinding.etQuality.text.toString().toFloatOrNull() ?: WEBP_QUALITY
            
            // 优先使用原生WebP编码器
            val webpBytes = if (LibWebpNative.isAvailable) {
                LibWebpNative.tryEncode(bitmap, quality)
            } else {
                null
            }

            if (webpBytes != null) {
                saveWebpBytes(webpBytes, originalUri)
            } else {
                // 降级使用平台WebP编码
                saveWebpBitmap(bitmap, quality, originalUri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "转换为WebP失败", e)
            null
        }
    }

    /**
     * 保存WebP字节数组
     */
    private fun saveWebpBytes(webpBytes: ByteArray, originalUri: Uri): Uri? {
        val name = getFileName(originalUri)
            .replace(".dng", "", ignoreCase = true)
            .replace(".DNG", "", ignoreCase = true) + ".webp"

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
            contentResolver.openOutputStream(newUri)?.use { outputStream ->
                outputStream.write(webpBytes)
                outputStream.flush()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                contentResolver.update(newUri, cv, null, null)
            }
            newUri
        } catch (e: Exception) {
            Log.e(TAG, "保存WebP失败", e)
            null
        }
    }

    /**
     * 保存WebP Bitmap（降级方案）
     */
    private fun saveWebpBitmap(bitmap: Bitmap, quality: Float, originalUri: Uri): Uri? {
        val name = getFileName(originalUri)
            .replace(".dng", "", ignoreCase = true)
            .replace(".DNG", "", ignoreCase = true) + ".webp"

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

        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }

        return try {
            contentResolver.openOutputStream(newUri)?.use { outputStream ->
                val ok = bitmap.compress(format, quality.toInt(), outputStream)
                outputStream.flush()
                if (!ok) return null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                contentResolver.update(newUri, cv, null, null)
            }
            newUri
        } catch (e: Exception) {
            Log.e(TAG, "保存WebP失败", e)
            null
        }
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path?.let { path ->
                val cut = path.lastIndexOf('/')
                if (cut != -1) {
                    path.substring(cut + 1)
                } else {
                    path
                }
            }
        }
        return result ?: "image"
    }

    private fun getFileSize(uri: Uri): Long {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                pfd.statSize
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun showLoading(show: Boolean) {
        mViewBinding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        mViewBinding.btnSelectFile.isEnabled = !show
        mViewBinding.btnProcess.isEnabled = !show && selectedDngUri != null
        mViewBinding.btnClear.isEnabled = !show
    }

    private fun showResult(result: ProcessResult) {
        val metrics = result.metrics
        val totalTime = metrics.getTotalTime()
        val compressionRatio = if (result.originalSize > 0) {
            (result.outputSize.toFloat() / result.originalSize * 100).toInt()
        } else {
            0
        }

        val info = buildString {
            appendLine("✅ 处理完成")
            appendLine("总耗时: ${totalTime}ms")
            appendLine("━━━━━━━━━━━━━━━━━━━━")
            appendLine("📊 各阶段耗时:")
            appendLine("  加载DNG: ${metrics.loadTime}ms")
            appendLine("  裁剪: ${metrics.cropTime}ms")
            appendLine("  压缩: ${metrics.compressTime}ms")
            appendLine("  WebP转换: ${metrics.convertTime}ms")
            appendLine("━━━━━━━━━━━━━━━━━━━━")
            appendLine("📁 文件信息:")
            appendLine("  原始大小: ${formatFileSize(result.originalSize)}")
            appendLine("  输出大小: ${formatFileSize(result.outputSize)}")
            appendLine("  压缩比: ${compressionRatio}%")
            appendLine("━━━━━━━━━━━━━━━━━━━━")
            appendLine("💾 保存位置:")
            appendLine("  ${result.webpUri}")
        }

        mViewBinding.tvResult.text = info
    }

    private fun showError(message: String) {
        mViewBinding.tvResult.text = "❌ $message"
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun clearAll() {
        selectedDngUri = null
        processedBitmap?.recycle()
        processedBitmap = null
        mViewBinding.ivPreview.setImageBitmap(null)
        mViewBinding.tvSelectedFile.text = "未选择文件"
        mViewBinding.tvResult.text = ""
        mViewBinding.btnProcess.isEnabled = false
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            else -> "${bytes / (1024 * 1024)}MB"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_STORAGE_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 权限已授予
                } else {
                    Toast.makeText(this, "需要存储权限才能访问文件", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        processedBitmap?.recycle()
    }
}

/**
 * 性能指标
 */
data class PerformanceMetrics(
    var loadTime: Long = 0,
    var cropTime: Long = 0,
    var compressTime: Long = 0,
    var convertTime: Long = 0
) {
    fun getTotalTime(): Long = loadTime + cropTime + compressTime + convertTime
    
    fun reset() {
        loadTime = 0
        cropTime = 0
        compressTime = 0
        convertTime = 0
    }
}

/**
 * 处理结果
 */
data class ProcessResult(
    val webpUri: Uri?,
    val originalSize: Long,
    val outputSize: Long,
    val metrics: PerformanceMetrics
)
