package com.alex.studydemo.module_image

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.alex.studydemo.module_media.LibDngNative
import com.alex.studydemo.module_media.LibWebpNative
import com.alibaba.android.arouter.facade.annotation.Route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/** DNG 文件经解码、百分比裁剪、等比缩放后导出 WebP；UI 状态只在主线程提交。 */
@Route(path = DngProcessActivity.PATH)
class DngProcessActivity : ComponentActivity() {
    /** 保留现有 ARouter 路径和入口类名。 */
    companion object { const val PATH = "/view/DngProcess" }

    // 参数以文本保留编辑中间态，开始时统一校验并生成不可变快照，后台不会读取 UI。
    private var fields by mutableStateOf(listOf("0", "0", "100", "100", "1.0", "85"))
    private var selectedDngUri by mutableStateOf<Uri?>(null)
    private var selectedName by mutableStateOf("未选择文件")
    private var processedBitmap by mutableStateOf<Bitmap?>(null)
    private var busy by mutableStateOf(false)
    private var status by mutableStateOf("")
    private var outputUri by mutableStateOf<Uri?>(null)

    // 文档选择按 URI 授权，无须申请读取整个图库；允许通用类型以兼容 MIME 标记不准确的 DNG。
    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: SecurityException) { /* 提供器不支持持久授权时仍使用本次临时授权。 */ }
            selectFile(uri)
        }
    }
    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) processDng() else status = "Android 9 及以下保存到图库需要存储写入权限。"
    }

    /** 恢复表单与文档 URI；结果位图不写入 Bundle，避免超出跨进程事务大小限制。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getStringArrayList("fields")?.takeIf { it.size == 6 }?.let { fields = it }
        selectedDngUri = savedInstanceState?.getString("uri")?.let(Uri::parse)
        selectedName = savedInstanceState?.getString("name") ?: "未选择文件"
        outputUri = savedInstanceState?.getString("output")?.let(Uri::parse)
        status = savedInstanceState?.getString("status") ?: ""
        outputUri?.let { restorePreview(it) }
        setContent {
            ImageDemoTheme {
                ImageDemoScaffold("DNG 图片处理", { finish() }) { padding ->
                    Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("裁剪与 WebP 导出", style = MaterialTheme.typography.headlineSmall)
                        Text(selectedName)
                        Button(onClick = { filePicker.launch(arrayOf("image/x-adobe-dng", "image/dng", "application/octet-stream", "*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("选择 DNG 文件") }
                        Text("裁剪区域（百分比）", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Parameter(0, "左 0–100", Modifier.weight(1f))
                            Parameter(2, "右 0–100", Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Parameter(1, "上 0–100", Modifier.weight(1f))
                            Parameter(3, "下 0–100", Modifier.weight(1f))
                        }
                        Parameter(4, "缩放因子 0.1–2.0", Modifier.fillMaxWidth())
                        Parameter(5, "WebP 质量 0–100", Modifier.fillMaxWidth())
                        Text("输出最长边不超过 1920 px；使用 LibRaw 解码，不可用时尝试系统解码或文件内嵌预览。", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { processDng() }, enabled = !busy && selectedDngUri != null, modifier = Modifier.weight(1f)) { Text("开始处理") }
                            OutlinedButton(onClick = { clearAll() }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("清除") }
                        }
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        ImagePreview(processedBitmap, "处理结果")
                        SelectionContainer { Text(status) }
                        outputUri?.let { uri -> OutlinedButton(onClick = { openOutput(uri) }, enabled = !busy) { Text("打开已保存 WebP") } }
                    }
                }
            }
        }
    }

    /** 仅保存轻量状态；正在处理的任务随 Activity 销毁取消，旋转后可以再次开始。 */
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList("fields", ArrayList(fields))
        outState.putString("uri", selectedDngUri?.toString())
        outState.putString("name", selectedName)
        outState.putString("output", outputUri?.toString())
        outState.putString("status", if (busy) "处理被页面重建中断，请重新开始；若导出已完成，可在图库中查看。" else status)
        super.onSaveInstanceState(outState)
    }

    /** 重建页面后从已发布文件重载预览；失败仍保留导出 URI 和原始结果文本。 */
    private fun restorePreview(uri: Uri) {
        busy = true
        lifecycleScope.launch {
            try { processedBitmap = withContext(Dispatchers.IO) { decodeImageSampled(contentResolver, uri, 1920) } }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { status += "\n预览恢复失败：${error.localizedMessage}" }
            catch (_: OutOfMemoryError) { status += "\n内存不足，无法恢复预览，已导出文件仍然保留。" }
            finally { busy = false }
        }
    }

    /** 数字输入允许暂时为空；禁止在工作期间修改参数，提交时再报告具体错误。 */
    @Composable
    private fun Parameter(index: Int, label: String, modifier: Modifier) {
        OutlinedTextField(value = fields[index], onValueChange = { value -> fields = fields.toMutableList().also { it[index] = value } }, label = { Text(label) }, enabled = !busy, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier)
    }

    /** 文件名查询在 IO 执行；新文件清除旧结果，读取失败保留 URI 供用户重试。 */
    private fun selectFile(uri: Uri) {
        selectedDngUri = uri
        selectedName = uri.lastPathSegment ?: "DNG 文件"
        processedBitmap = null
        outputUri = null
        status = ""
        busy = true
        lifecycleScope.launch {
            try { selectedName = withContext(Dispatchers.IO) { getFileName(uri) } }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { status = "无法读取文件名：${error.localizedMessage}" }
            finally { busy = false }
        }
    }

    /** 所有数值必须有限且在规定范围，裁剪边界必须有序，避免 native 收到非法尺寸。 */
    private fun readOptions(): DngOptions {
        val numbers = fields.map { requireNotNull(it.toFloatOrNull()?.takeIf(Float::isFinite)) { "所有参数必须填写有效数字" } }
        val (left, top, right, bottom) = numbers
        require(listOf(left, top, right, bottom).all { it in 0f..100f } && left < right && top < bottom) { "裁剪范围需为 0–100，且左小于右、上小于下" }
        require(numbers[4] in 0.1f..2f) { "缩放因子必须在 0.1–2.0 之间" }
        require(numbers[5] in 0f..100f) { "WebP 质量必须在 0–100 之间" }
        return DngOptions(left / 100, top / 100, right / 100, bottom / 100, numbers[4], numbers[5])
    }

    /** 捕获参数与 URI 后串行执行；旧系统只在真正导出时申请写权限，失败不覆盖上次结果。 */
    private fun processDng() {
        if (busy) return
        val uri = selectedDngUri ?: return
        val options = try { readOptions() } catch (error: IllegalArgumentException) { status = error.message ?: "参数错误"; return }
        if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        busy = true
        status = "正在解码、裁剪、缩放并保存…"
        lifecycleScope.launch {
            try {
                val completed = withContext(Dispatchers.IO) { processDngInternal(uri, options) }
                processedBitmap = completed.bitmap
                outputUri = completed.result.webpUri
                status = resultText(completed)
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { status = "处理失败：${error.localizedMessage}" }
            catch (_: OutOfMemoryError) { status = "DNG 解码需要的内存不足，请选择较小的原始文件。" }
            finally { busy = false }
        }
    }

    /** 后台独占中间位图；裁剪和缩放可能返回原对象，只回收不再使用且未交付 UI 的实例。 */
    private suspend fun processDngInternal(uri: Uri, options: DngOptions): DngCompleted {
        val owned = mutableSetOf<Bitmap>()
        var delivered: Bitmap? = null
        try {
            val metrics = PerformanceMetrics()
            var start = SystemClock.elapsedRealtime()
            val decoded = loadDngFile(uri)
            val source = decoded.first.also { owned.add(it) }
            metrics.loadTime = SystemClock.elapsedRealtime() - start
            coroutineContext.ensureActive()
            start = SystemClock.elapsedRealtime()
            val x = (source.width * options.left).toInt().coerceIn(0, source.width - 1)
            val y = (source.height * options.top).toInt().coerceIn(0, source.height - 1)
            val right = (source.width * options.right).toInt().coerceIn(x + 1, source.width)
            val bottom = (source.height * options.bottom).toInt().coerceIn(y + 1, source.height)
            val cropped = Bitmap.createBitmap(source, x, y, right - x, bottom - y).also { owned.add(it) }
            metrics.cropTime = SystemClock.elapsedRealtime() - start
            if (source !== cropped) { source.recycle(); owned.remove(source) }
            start = SystemClock.elapsedRealtime()
            val requestedWidth = cropped.width * options.scale
            val requestedHeight = cropped.height * options.scale
            val limit = minOf(1f, 1920f / maxOf(requestedWidth, requestedHeight))
            val output = Bitmap.createScaledBitmap(cropped, (requestedWidth * limit).roundToInt().coerceAtLeast(1), (requestedHeight * limit).roundToInt().coerceAtLeast(1), true).also { owned.add(it) }
            metrics.compressTime = SystemClock.elapsedRealtime() - start
            if (cropped !== output) { cropped.recycle(); owned.remove(cropped) }
            coroutineContext.ensureActive()
            val originalSize = getFileSize(uri)
            start = SystemClock.elapsedRealtime()
            val exported = saveWebp(output, options.quality, getFileName(uri))
            metrics.convertTime = SystemClock.elapsedRealtime() - start
            val result = ProcessResult(exported.first, originalSize, getFileSize(exported.first), metrics)
            delivered = output
            return DngCompleted(output, result, decoded.second, exported.second)
        } finally {
            owned.filter { it !== delivered && !it.isRecycled }.forEach { it.recycle() }
        }
    }

    /** 优先真正的 LibRaw 解码；缺少 native 库时只读取所选文件，绝不借用其他 DNG 的 JPEG。 */
    private fun loadDngFile(uri: Uri): Pair<Bitmap, String> {
        attemptDecode {
            contentResolver.openFileDescriptor(uri, "r")?.use { LibDngNative.tryDecodeFd(it.fileDescriptor) }
        }?.let { return it to "LibRaw 原始数据解码" }
        attemptDecode {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }?.let { return it to "系统解码（可能为 DNG 内嵌预览）" }
        attemptDecode {
            contentResolver.openFileDescriptor(uri, "r")?.use { BitmapFactory.decodeFileDescriptor(it.fileDescriptor) }
        }?.let { return it to "系统文件描述符解码" }
        // 部分设备不支持直接解码 DNG，但 Exif 中存在可读的 JPEG 缩略图；明确提示预览来源。
        attemptDecode {
            contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                ExifInterface(fd.fileDescriptor).thumbnail?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
        }?.let { return it to "DNG 内嵌 JPEG 缩略图（非完整 RAW）" }
        error("无法解码此 DNG；当前设备缺少可用的 LibRaw 解码库，或文件没有系统可读的预览")
    }

    /** 提供器可能只支持流或描述符；单条解码路径异常允许继续降级，内存不足交给页面处理。 */
    private fun attemptDecode(decode: () -> Bitmap?): Bitmap? = try {
        decode()
    } catch (cancel: CancellationException) { throw cancel }
    catch (_: Exception) { null }
    catch (_: LinkageError) { null }

    /** 插入待发布媒体行后写入；编码或写入失败删除该行，避免图库出现空文件。 */
    private suspend fun saveWebp(bitmap: Bitmap, quality: Float, sourceName: String): Pair<Uri, String> {
        val nativeBytes = try { LibWebpNative.tryEncode(bitmap, quality) } catch (_: Exception) { null } catch (_: LinkageError) { null }
        // native 编码不能被协程抢占；完成后再次检查取消，避免离开页面后创建媒体文件。
        coroutineContext.ensureActive()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, sourceName.substringBeforeLast('.', sourceName) + ".webp")
            put(MediaStore.Images.Media.MIME_TYPE, "image/webp")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/StudyDemoWebP")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = requireNotNull(contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)) { "无法创建输出媒体文件" }
        try {
            requireNotNull(contentResolver.openOutputStream(uri)) { "无法打开输出文件" }.use { stream ->
                if (nativeBytes != null) stream.write(nativeBytes)
                else {
                    val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                    check(bitmap.compress(format, quality.roundToInt(), stream)) { "平台 WebP 编码失败" }
                }
                stream.flush()
            }
            coroutineContext.ensureActive()
            if (Build.VERSION.SDK_INT >= 29) {
                check(contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) > 0) { "发布输出文件失败" }
            }
            return uri to if (nativeBytes != null) "libwebp" else "Android 平台 WebP"
        } catch (error: Throwable) {
            try { contentResolver.delete(uri, null, null) } catch (_: Exception) { /* 保留原始编码异常，避免清理错误覆盖失败原因。 */ }
            throw error
        }
    }

    /** 名称只用于显示和导出命名，文档提供器缺少列时回退 URI 的最后一段。 */
    private fun getFileName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getString(0)
        }
        return uri.lastPathSegment ?: "image.dng"
    }

    /** 文件描述符可能返回未知长度，用 -1 保留该语义以免产生错误的压缩比。 */
    private fun getFileSize(uri: Uri): Long = try {
        contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
    } catch (_: Exception) { -1L }

    /** 分阶段耗时单位为毫秒，大小单位为字节；只有输入输出大小已知时显示百分比。 */
    private fun resultText(completed: DngCompleted): String {
        val result = completed.result
        val metrics = result.metrics
        val ratio = if (result.originalSize > 0 && result.outputSize >= 0) "${"%.1f".format(result.outputSize * 100.0 / result.originalSize)}%" else "未知"
        return "处理完成\n解码：${completed.decoder}\n编码：${completed.encoder}\n总耗时：${metrics.getTotalTime()} ms\n加载 DNG：${metrics.loadTime} ms\n裁剪：${metrics.cropTime} ms\n缩放：${metrics.compressTime} ms\nWebP 转换与保存：${metrics.convertTime} ms\n原始大小：${imageFileSize(result.originalSize)}\n输出大小：${imageFileSize(result.outputSize)}\n输出 / 原始：$ratio\n保存位置：${result.webpUri}"
    }

    /** 打开已发布的内容 URI；没有支持 WebP 的查看器时保留结果和错误说明。 */
    private fun openOutput(uri: Uri) {
        try { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/webp").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
        catch (error: Exception) { status += "\n无法打开查看器：${error.localizedMessage}" }
    }

    /** 清除当前会话结果，不删除已经导出的文件，也不提前回收 Compose 正在绘制的位图。 */
    private fun clearAll() {
        selectedDngUri = null; selectedName = "未选择文件"; processedBitmap = null; outputUri = null; status = ""
    }
}

/** 校验后的参数快照；裁剪边界为 0–1，缩放与编码质量分别使用倍率和 0–100 数值。 */
private data class DngOptions(val left: Float, val top: Float, val right: Float, val bottom: Float, val scale: Float, val quality: Float)

/** 后台返回的位图交给 UI 持有，同时保留实际解码和编码路径供实验结果说明。 */
private data class DngCompleted(val bitmap: Bitmap, val result: ProcessResult, val decoder: String, val encoder: String)

/** 保留公开性能模型；四个阶段均使用单调时钟测量，单位为毫秒。 */
data class PerformanceMetrics(var loadTime: Long = 0, var cropTime: Long = 0, var compressTime: Long = 0, var convertTime: Long = 0) {
    /** 合计仅包含解码、裁剪、缩放、编码保存，不包含结果展示耗时。 */
    fun getTotalTime(): Long = loadTime + cropTime + compressTime + convertTime

    /** 允许原有调用方复用指标对象；调用方负责串行访问。 */
    fun reset() { loadTime = 0; cropTime = 0; compressTime = 0; convertTime = 0 }
}

/** 保留公开结果模型；长度为字节，未知时为 -1，兼容历史可空输出 URI。 */
data class ProcessResult(val webpUri: Uri?, val originalSize: Long, val outputSize: Long, val metrics: PerformanceMetrics)
