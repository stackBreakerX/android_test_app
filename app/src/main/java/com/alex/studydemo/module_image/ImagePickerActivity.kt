package com.alex.studydemo.module_image

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.alex.studydemo.databinding.ActivityImagePickerBinding
import com.alibaba.android.arouter.facade.annotation.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@Route(path = ImagePickerActivity.PATH)
class ImagePickerActivity : AppCompatActivity() {

    private lateinit var mViewBinding: ActivityImagePickerBinding
    private var currentPhotoPath: String? = null

    companion object {
        const val PATH = "/view/ImagePicker"
        private const val TAG = "ImagePickerActivity"
        private const val REQUEST_CAMERA_PERMISSION = 1001
        private const val REQUEST_STORAGE_PERMISSION = 1002
    }

    // 相机拍照结果处理
    private val cameraResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentPhotoPath?.let { path ->
                loadImageAsync(path, isFromCamera = true)
            }
        }
    }

    // 图库选择结果处理
    private val galleryResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                loadImageFromUriAsync(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewBinding = ActivityImagePickerBinding.inflate(layoutInflater)
        setContentView(mViewBinding.root)

        initViews()
    }

    private fun initViews() {
        mViewBinding.btnSelectFromGallery.setOnClickListener {
            if (checkStoragePermission()) {
                openGallery()
            }
        }

        mViewBinding.btnTakePhoto.setOnClickListener {
            if (checkCameraPermission()) {
                takePhoto()
            }
        }

        mViewBinding.btnClearImage.setOnClickListener {
            clearImage()
        }
        
        // 初始化加载指示器
        showLoading(false)
    }

    /**
     * 检查存储权限
     */
    private fun checkStoragePermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_STORAGE_PERMISSION
            )
            false
        } else {
            true
        }
    }

    /**
     * 检查相机权限
     */
    private fun checkCameraPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
            false
        } else {
            true
        }
    }

    /**
     * 打开图库选择图片
     */
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryResultLauncher.launch(intent)
    }

    /**
     * 拍照
     */
    private fun takePhoto() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            val photoFile = createImageFile()
            photoFile?.let { file ->
                currentPhotoPath = file.absolutePath
                val photoURI = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                cameraResultLauncher.launch(intent)
            }
        }
    }

    /**
     * 创建图片文件
     */
    private fun createImageFile(): File? {
        return try {
            val imageFileName = "JPEG_${System.currentTimeMillis()}_"
            val storageDir = getExternalFilesDir("Pictures")
            File.createTempFile(imageFileName, ".jpg", storageDir)
        } catch (ex: IOException) {
            Log.e(TAG, "Error occurred while creating the file", ex)
            null
        }
    }

    /**
     * 异步加载图片（从文件路径）
     */
    private fun loadImageAsync(imagePath: String, isFromCamera: Boolean = false) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    loadBitmapFromFile(imagePath)
                }
                bitmap?.let {
                    displayImage(it)
                    generateThumbnailAsync(it)
                } ?: run {
                    showError("图片加载失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载图片失败", e)
                showError("图片加载失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    /**
     * 异步加载图片（从URI）
     */
    private fun loadImageFromUriAsync(uri: Uri) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    loadBitmapFromUri(uri)
                }
                bitmap?.let {
                    displayImage(it)
                    generateThumbnailAsync(it)
                } ?: run {
                    showError("图片加载失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载图片失败", e)
                showError("图片加载失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    /**
     * 从文件加载Bitmap（IO线程）
     */
    private suspend fun loadBitmapFromFile(imagePath: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                // 先获取图片尺寸，避免内存溢出
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeFile(imagePath, options)
                
                // 计算合适的缩放比例
                val scale = calculateInSampleSize(options, 1024, 1024)
                options.inJustDecodeBounds = false
                options.inSampleSize = scale
                options.inPreferredConfig = Bitmap.Config.RGB_565 // 减少内存使用
                
                BitmapFactory.decodeFile(imagePath, options)
            } catch (e: Exception) {
                Log.e(TAG, "从文件加载图片失败", e)
                null
            }
        }
    }

    /**
     * 从URI加载Bitmap（IO线程）
     */
    private suspend fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                inputStream?.use { stream ->
                    // 先获取图片尺寸
                    val options = BitmapFactory.Options()
                    options.inJustDecodeBounds = true
                    BitmapFactory.decodeStream(stream, null, options)
                    
                    // 重新打开流
                    contentResolver.openInputStream(uri)?.use { newStream ->
                        // 计算缩放比例
                        val scale = calculateInSampleSize(options, 1024, 1024)
                        options.inJustDecodeBounds = false
                        options.inSampleSize = scale
                        options.inPreferredConfig = Bitmap.Config.RGB_565
                        
                        BitmapFactory.decodeStream(newStream, null, options)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "从URI加载图片失败", e)
                null
            }
        }
    }

    /**
     * 计算合适的缩放比例
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * 显示图片
     */
    private fun displayImage(bitmap: Bitmap) {
        mViewBinding.ivSelectedImage.setImageBitmap(bitmap)
        mViewBinding.tvImageInfo.text = "图片尺寸: ${bitmap.width} x ${bitmap.height}"
    }

    /**
     * 异步生成缩略图
     */
    private fun generateThumbnailAsync(originalBitmap: Bitmap) {
        lifecycleScope.launch {
            try {
                val thumbnail = withContext(Dispatchers.IO) {
                    val thumbnailSize = 200 // 缩略图尺寸
                    Bitmap.createScaledBitmap(
                        originalBitmap,
                        thumbnailSize,
                        thumbnailSize,
                        true
                    )
                }
                
                // 在主线程更新UI
                mViewBinding.ivThumbnail.setImageBitmap(thumbnail)
                mViewBinding.tvThumbnailInfo.text = "缩略图尺寸: ${thumbnail.width} x ${thumbnail.height}"
                
                // 异步保存缩略图到文件
                saveThumbnailToFileAsync(thumbnail)
            } catch (e: Exception) {
                Log.e(TAG, "生成缩略图失败", e)
                showError("生成缩略图失败: ${e.message}")
            }
        }
    }

    /**
     * 异步保存缩略图到文件
     */
    private fun saveThumbnailToFileAsync(thumbnail: Bitmap) {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val thumbnailFile = File(getExternalFilesDir("Thumbnails"), "thumbnail_${System.currentTimeMillis()}.jpg")
                    thumbnailFile.parentFile?.mkdirs()
                    
                    val outputStream = FileOutputStream(thumbnailFile)
                    thumbnail.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    outputStream.flush()
                    outputStream.close()
                    
                    thumbnailFile.absolutePath
                }
                
                Log.d(TAG, "缩略图已保存到: $result")
                Toast.makeText(this@ImagePickerActivity, "缩略图已保存", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Log.e(TAG, "保存缩略图失败", e)
                Toast.makeText(this@ImagePickerActivity, "保存缩略图失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 显示/隐藏加载指示器
     */
    private fun showLoading(show: Boolean) {
        mViewBinding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        mViewBinding.btnSelectFromGallery.isEnabled = !show
        mViewBinding.btnTakePhoto.isEnabled = !show
        mViewBinding.btnClearImage.isEnabled = !show
    }

    /**
     * 显示错误信息
     */
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.e(TAG, message)
    }

    /**
     * 清除图片
     */
    private fun clearImage() {
        mViewBinding.ivSelectedImage.setImageBitmap(null)
        mViewBinding.ivThumbnail.setImageBitmap(null)
        mViewBinding.tvImageInfo.text = ""
        mViewBinding.tvThumbnailInfo.text = ""
        currentPhotoPath = null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    takePhoto()
                } else {
                    Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_STORAGE_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openGallery()
                } else {
                    Toast.makeText(this, "需要存储权限才能访问图库", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
