package com.alex.studydemo.module_image

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.R
import com.alibaba.android.arouter.facade.annotation.Route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 按图库授权范围列出图片，惰性加载缩略图，并支持比较系统缩略图与采样解码。 */
@Route(path = FastThumbnailActivity.PATH)
class FastThumbnailActivity : ComponentActivity() {
    /** 保持原有路由常量，其他模块无需调整入口。 */
    companion object { const val PATH = "/view/FastThumbnail" }

    // 列表、提示与代次只在主线程更新；LruCache 按像素字节计费，最多持有约 16 MB。
    private var thumbnails by mutableStateOf<List<ThumbnailItem>>(emptyList())
    private var busy by mutableStateOf(false)
    private var info by mutableStateOf("授权后加载图库缩略图")
    private var revision by mutableIntStateOf(0)
    private var comparison by mutableStateOf<ThumbnailItem?>(null)
    @Volatile private var cacheGeneration = 0
    private val thumbnailCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        /** 缓存计量使用字节；淘汰时不 recycle，当前 Compose 单元格可能仍在显示。 */
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadThumbnails() else {
            thumbnails = emptyList()
            info = "未获得图库权限，请点击加载重试；也可在系统设置中调整允许访问的照片。"
        }
    }

    /** 首次进入申请匹配系统版本的权限；网格及比较对话框完全由 Compose 构建。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ImageDemoTheme {
                ImageDemoScaffold("快速缩略图", { finish() }) { padding ->
                    Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("图库缩略图实验", style = MaterialTheme.typography.headlineSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { checkPermissions() }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("加载缩略图") }
                            OutlinedButton(onClick = { clearCache() }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("清除缓存") }
                        }
                        Text(info)
                        Text("点击图片比较系统缩略图与原图采样。Android 14 部分授权时仅显示允许的图片。", style = MaterialTheme.typography.bodySmall)
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        LazyVerticalGrid(columns = GridCells.Adaptive(104.dp), modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                            items(thumbnails, key = { it.id }) { item -> ThumbnailCard(item, revision) { comparison = item } }
                        }
                    }
                    comparison?.let { item -> ThumbnailComparison(item) { comparison = null } }
                }
            }
        }
        checkPermissions()
    }

    /** Android 13 起读取图库使用 READ_MEDIA_IMAGES；文档选择器不参与这里的批量查询。 */
    private fun checkPermissions() {
        val name = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, name) == PackageManager.PERMISSION_GRANTED) loadThumbnails()
        else permission.launch(name)
    }

    /** 查询在 IO 执行；不依赖 DATA 文件路径存在性，兼容作用域存储。空图库和查询错误分别反馈。 */
    private fun loadThumbnails() {
        if (busy) return
        busy = true
        lifecycleScope.launch {
            try {
                val start = SystemClock.elapsedRealtime()
                val loaded = withContext(Dispatchers.IO) {
                    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.SIZE)
                    val cursor = requireNotNull(contentResolver.query(collection, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")) { "图库查询不可用" }
                    cursor.use { c ->
                        buildList {
                            while (c.moveToNext()) {
                                val id = c.getLong(0)
                                add(ThumbnailItem(id, c.getString(1) ?: "未命名图片", ContentUris.withAppendedId(collection, id).toString(), c.getLong(2), c.getLong(3)))
                            }
                        }
                    }
                }
                thumbnails = loaded
                info = "已加载 ${loaded.size} 张图片，查询耗时 ${SystemClock.elapsedRealtime() - start} ms" + if (loaded.isEmpty()) "；当前授权范围内没有图片。" else ""
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { thumbnails = emptyList(); info = "图库加载失败：${error.localizedMessage}" }
            finally { busy = false }
        }
    }

    /** 同步兼容 API：应在后台调用；优先系统缩略图，失败时从内容 URI 采样，最后兼容旧路径。 */
    fun getFastThumbnail(thumbnailItem: ThumbnailItem): Bitmap? {
        val generation = cacheGeneration
        val key = thumbnailItem.id.toString()
        thumbnailCache.get(key)?.let { return it }
        val bitmap = try {
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, thumbnailItem.id)
            val system = try {
                if (Build.VERSION.SDK_INT >= 29) contentResolver.loadThumbnail(uri, Size(128, 128), null)
                else MediaStore.Images.Thumbnails.getThumbnail(contentResolver, thumbnailItem.id, MediaStore.Images.Thumbnails.MINI_KIND, null)
            } catch (_: Exception) { null }
            system ?: try { decodeImageSampled(contentResolver, uri, 64) } catch (_: Exception) { null }
                ?: try {
                    // 保留旧调用方传入绝对路径的能力，仍先读取边界，避免固定采样倍率导致大图溢出。
                    val legacyUri = if (thumbnailItem.data.startsWith("content:")) Uri.parse(thumbnailItem.data) else Uri.fromFile(File(thumbnailItem.data))
                    decodeImageSampled(contentResolver, legacyUri, 64)
                } catch (_: Exception) { null }
        } catch (_: Exception) { null }
        // 清理缓存后，已在后台运行的旧请求不能重新填充旧代缓存。
        synchronized(thumbnailCache) {
            if (generation == cacheGeneration && bitmap != null) thumbnailCache.put(key, bitmap)
        }
        return bitmap
    }

    /** 代次使可见单元格重新读取；清理不释放渲染线程持有的位图。 */
    private fun clearCache() {
        synchronized(thumbnailCache) { cacheGeneration++; thumbnailCache.evictAll() }
        revision++
        info = "缓存已清除，可见图片将重新加载"
    }

    /** 每个单元格按 ID 和缓存代次启动解码，离开组合时取消提交，避免复用时显示错图。 */
    @Composable
    private fun ThumbnailCard(item: ThumbnailItem, generation: Int, onClick: () -> Unit) {
        var bitmap by remember(item.id, generation) { mutableStateOf<Bitmap?>(null) }
        var loading by remember(item.id, generation) { mutableStateOf(true) }
        LaunchedEffect(item.id, generation) {
            try { bitmap = withContext(Dispatchers.IO) { getFastThumbnail(item) } }
            catch (_: OutOfMemoryError) { bitmap = null }
            finally { loading = false }
        }
        Card(onClick = onClick) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (bitmap != null) Image(bitmap!!.asImageBitmap(), item.name, Modifier.fillMaxWidth().height(100.dp), contentScale = ContentScale.Crop)
                else Text(if (loading) "加载中…" else "无法读取", Modifier.height(100.dp))
                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                Text(imageFileSize(item.size), style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    /** 比较同一 URI 的两种读取路径，分别计时；错误保留在弹窗内，不中断整个图库。 */
    @Composable
    private fun ThumbnailComparison(item: ThumbnailItem, onDismiss: () -> Unit) {
        var fast by remember(item.id) { mutableStateOf<Bitmap?>(null) }
        var sampled by remember(item.id) { mutableStateOf<Bitmap?>(null) }
        var summary by remember(item.id) { mutableStateOf("正在比较…") }
        LaunchedEffect(item.id) {
            try {
                val result = withContext(Dispatchers.IO) {
                    val start = SystemClock.elapsedRealtime()
                    val a = getFastThumbnail(item)
                    val mid = SystemClock.elapsedRealtime()
                    val b = decodeImageSampled(contentResolver, ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, item.id), 64)
                    Triple(a, b, "系统/缓存 ${mid - start} ms；原图采样 ${SystemClock.elapsedRealtime() - mid} ms")
                }
                fast = result.first; sampled = result.second; summary = result.third
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { summary = "比较失败：${error.localizedMessage}" }
            catch (_: OutOfMemoryError) { summary = "内存不足，无法比较此图片" }
        }
        AlertDialog(onDismissRequest = onDismiss, title = { Text(item.name, maxLines = 2) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(summary)
                ImagePreview(fast, "系统缩略图 / 缓存", 100)
                ImagePreview(sampled, "原图采样缩略图", 100)
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
    }
}

/**
 * 保留缩略图公开模型：id 为 MediaStore ID，data 可为内容 URI 或历史绝对路径，
 * dateAdded 单位为秒，size 单位为字节；Compose 页面优先使用 id 构造授权范围内的 URI。
 */
data class ThumbnailItem(
    val id: Long,
    val name: String,
    val data: String,
    val dateAdded: Long,
    val size: Long
)

/**
 * 保留旧调用方使用的 RecyclerView 适配器；本模块三个 Activity 的 Compose 页面不使用它。
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
