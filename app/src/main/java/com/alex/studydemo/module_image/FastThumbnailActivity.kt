package com.alex.studydemo.module_image

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.R
import com.alex.studydemo.databinding.ActivityFastThumbnailBinding
import com.alibaba.android.arouter.facade.annotation.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Route(path = FastThumbnailActivity.PATH)
class FastThumbnailActivity : AppCompatActivity() {

    private lateinit var mViewBinding: ActivityFastThumbnailBinding
    private lateinit var thumbnailAdapter: ThumbnailAdapter
    private val thumbnailCache = LruCache<String, Bitmap>(100) // 缓存100张缩略图

    companion object {
        const val PATH = "/view/FastThumbnail"
        private const val TAG = "FastThumbnailActivity"
        private const val REQUEST_STORAGE_PERMISSION = 1003
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewBinding = ActivityFastThumbnailBinding.inflate(layoutInflater)
        setContentView(mViewBinding.root)

        initViews()
        checkPermissions()
    }

    private fun initViews() {
        // 设置RecyclerView
        mViewBinding.recyclerView.layoutManager = GridLayoutManager(this, 3)
        thumbnailAdapter = ThumbnailAdapter(thumbnailCache)
        mViewBinding.recyclerView.adapter = thumbnailAdapter

        mViewBinding.btnLoadThumbnails.setOnClickListener {
            loadThumbnails()
        }

        mViewBinding.btnClearCache.setOnClickListener {
            clearCache()
        }
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
        } else {
            loadThumbnails()
        }
    }

    private fun loadThumbnails() {
        lifecycleScope.launch {
            val startTime = System.currentTimeMillis()
            
            val thumbnails = withContext(Dispatchers.IO) {
                loadThumbnailsFromMediaStore()
            }
            
            val loadTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "加载${thumbnails.size}张缩略图耗时: ${loadTime}ms")
            
            thumbnailAdapter.updateThumbnails(thumbnails)
            mViewBinding.tvInfo.text = "已加载${thumbnails.size}张缩略图，耗时${loadTime}ms"
        }
    }

    /**
     * 从MediaStore快速加载缩略图
     */
    private suspend fun loadThumbnailsFromMediaStore(): List<ThumbnailItem> = withContext(Dispatchers.IO) {
        val thumbnails = mutableListOf<ThumbnailItem>()
        
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE
        )
        
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        
        val cursor: Cursor? = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )
        
        cursor?.use { c ->
            val idColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dataColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val dateColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val sizeColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            
            while (c.moveToNext()) {
                val id = c.getLong(idColumn)
                val name = c.getString(nameColumn)
                val data = c.getString(dataColumn)
                val dateAdded = c.getLong(dateColumn)
                val size = c.getLong(sizeColumn)
                
                // 检查文件是否存在
                if (File(data).exists()) {
                    val thumbnailItem = ThumbnailItem(
                        id = id,
                        name = name,
                        data = data,
                        dateAdded = dateAdded,
                        size = size
                    )
                    thumbnails.add(thumbnailItem)
                }
            }
        }
        
        thumbnails
    }

    /**
     * 快速获取缩略图Bitmap
     */
    fun getFastThumbnail(thumbnailItem: ThumbnailItem): Bitmap? {
        // 先检查缓存
        val cacheKey = thumbnailItem.id.toString()
        thumbnailCache.get(cacheKey)?.let { return it }
        
        return try {
            // 使用MediaStore的缩略图API快速获取
            val thumbnail = MediaStore.Images.Thumbnails.getThumbnail(
                contentResolver,
                thumbnailItem.id,
                MediaStore.Images.Thumbnails.MINI_KIND,
                null
            )
            
            // 如果MediaStore缩略图不存在，使用原图生成小尺寸缩略图
            thumbnail ?: generateFastThumbnail(thumbnailItem.data)
        } catch (e: Exception) {
            Log.e(TAG, "获取缩略图失败: ${thumbnailItem.name}", e)
            null
        }?.also { bitmap ->
            // 缓存缩略图
            thumbnailCache.put(cacheKey, bitmap)
        }
    }

    /**
     * 快速生成缩略图
     */
    private fun generateFastThumbnail(imagePath: String): Bitmap? {
        return try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(imagePath, options)
            
            // 计算合适的缩放比例，目标尺寸64x64
            val scale = calculateInSampleSize(options, 64, 64)
            options.inJustDecodeBounds = false
            options.inSampleSize = scale
            options.inPreferredConfig = Bitmap.Config.RGB_565
            
            BitmapFactory.decodeFile(imagePath, options)
        } catch (e: Exception) {
            Log.e(TAG, "生成缩略图失败: $imagePath", e)
            null
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

    private fun clearCache() {
        thumbnailCache.evictAll()
        thumbnailAdapter.notifyDataSetChanged()
        mViewBinding.tvInfo.text = "缓存已清除"
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
                    loadThumbnails()
                } else {
                    Toast.makeText(this, "需要存储权限才能访问相册", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

/**
 * 缩略图数据类
 */
data class ThumbnailItem(
    val id: Long,
    val name: String,
    val data: String,
    val dateAdded: Long,
    val size: Long
)

/**
 * 缩略图适配器
 */
class ThumbnailAdapter(
    private val thumbnailCache: LruCache<String, Bitmap>
) : RecyclerView.Adapter<ThumbnailViewHolder>() {

    private var thumbnails = mutableListOf<ThumbnailItem>()

    fun updateThumbnails(newThumbnails: List<ThumbnailItem>) {
        thumbnails.clear()
        thumbnails.addAll(newThumbnails)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ThumbnailViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_thumbnail, parent, false)
        return ThumbnailViewHolder(view, thumbnailCache)
    }

    override fun onBindViewHolder(holder: ThumbnailViewHolder, position: Int) {
        holder.bind(thumbnails[position])
    }

    override fun getItemCount(): Int = thumbnails.size
}

/**
 * 缩略图ViewHolder
 */
class ThumbnailViewHolder(
    itemView: android.view.View,
    private val thumbnailCache: LruCache<String, Bitmap>
) : RecyclerView.ViewHolder(itemView) {

    private val imageView: android.widget.ImageView = itemView.findViewById(R.id.iv_thumbnail)
    private val nameTextView: android.widget.TextView = itemView.findViewById(R.id.tv_name)
    private val sizeTextView: android.widget.TextView = itemView.findViewById(R.id.tv_size)

    fun bind(thumbnailItem: ThumbnailItem) {
        nameTextView.text = thumbnailItem.name
        sizeTextView.text = formatFileSize(thumbnailItem.size)
        
        // 异步加载缩略图
        loadThumbnailAsync(thumbnailItem)
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "${size}B"
            size < 1024 * 1024 -> "${size / 1024}KB"
            else -> "${size / (1024 * 1024)}MB"
        }
    }

    private fun loadThumbnailAsync(thumbnailItem: ThumbnailItem) {
        // 先检查缓存
        val cacheKey = thumbnailItem.id.toString()
        thumbnailCache.get(cacheKey)?.let { bitmap ->
            imageView.setImageBitmap(bitmap)
            return
        }

        // 异步加载
        (imageView.context as? androidx.appcompat.app.AppCompatActivity)?.lifecycleScope?.launch {
            try {
                val bitmap = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    getFastThumbnail(thumbnailItem)
                }
                bitmap?.let { imageView.setImageBitmap(it) }
            } catch (e: Exception) {
                android.util.Log.e("ThumbnailAdapter", "加载缩略图失败", e)
            }
        }
    }

    private fun getFastThumbnail(thumbnailItem: ThumbnailItem): Bitmap? {
        return try {
            // 使用MediaStore的缩略图API
            val thumbnail = MediaStore.Images.Thumbnails.getThumbnail(
                imageView.context.contentResolver,
                thumbnailItem.id,
                MediaStore.Images.Thumbnails.MINI_KIND,
                null
            )
            
            thumbnail ?: generateFastThumbnail(thumbnailItem.data)
        } catch (e: Exception) {
            android.util.Log.e("ThumbnailAdapter", "获取缩略图失败", e)
            null
        }
    }

    private fun generateFastThumbnail(imagePath: String): Bitmap? {
        return try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(imagePath, options)
            
            val scale = calculateInSampleSize(options, 64, 64)
            options.inJustDecodeBounds = false
            options.inSampleSize = scale
            options.inPreferredConfig = Bitmap.Config.RGB_565
            
            BitmapFactory.decodeFile(imagePath, options)
        } catch (e: Exception) {
            android.util.Log.e("ThumbnailAdapter", "生成缩略图失败", e)
            null
        }
    }

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
}
