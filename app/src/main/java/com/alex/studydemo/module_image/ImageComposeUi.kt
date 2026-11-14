package com.alex.studydemo.module_image

import com.alex.studydemo.ui.StudyScaffold
import com.alex.studydemo.ui.StudyTheme
import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/** 图片实验跟随应用主题，位图内容保持原始颜色，不随明暗主题重新解码。 */
@Composable
internal fun ImageDemoTheme(content: @Composable () -> Unit) {
    StudyTheme(content = content)
}

/** 独立页面通过 finish 返回调用方；内容接收 Scaffold 内边距，避免遮住系统栏。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImageDemoScaffold(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    StudyScaffold(title = title, onBack = onBack, content = content)
}

/** 使用 Compose 绘制位图；不主动 recycle，避免渲染线程继续读取已经释放的像素。 */
@Composable
internal fun ImagePreview(bitmap: Bitmap?, title: String, height: Int = 240) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (bitmap != null) {
                Image(bitmap.asImageBitmap(), title, Modifier.fillMaxWidth().height(height.dp), contentScale = ContentScale.Fit)
                Text("${bitmap.width} × ${bitmap.height} px", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("暂无图片", Modifier.padding(vertical = 24.dp))
            }
        }
    }
}

/** 先读尺寸再重新打开流采样；调用方须在 IO 线程执行，无法解码时返回 null。目标值单位为像素。 */
internal fun decodeImageSampled(resolver: ContentResolver, uri: Uri, target: Int): Bitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    options.inSampleSize = 1
    // 按最长边限制内存；超长全景图也必须采样，不能只检查较短的一边。
    while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > target * 2) options.inSampleSize *= 2
    options.inJustDecodeBounds = false
    options.inPreferredConfig = Bitmap.Config.ARGB_8888
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

/** 文件长度未知时直接说明，避免把文档提供器返回的 -1 显示成负数。 */
internal fun imageFileSize(bytes: Long): String = when {
    bytes < 0 -> "未知"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.2f".format(bytes / (1024.0 * 1024.0))} MB"
}
