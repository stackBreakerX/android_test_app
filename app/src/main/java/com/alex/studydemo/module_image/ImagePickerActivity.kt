package com.alex.studydemo.module_image

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.alibaba.android.arouter.facade.annotation.Route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 图库和相机共用异步解码流程，原图与 200 像素缩略图均由 Compose 展示。 */
@Route(path = ImagePickerActivity.PATH)
class ImagePickerActivity : ComponentActivity() {
    /** 保留跨模块 ARouter 入口。 */
    companion object { const val PATH = "/view/ImagePicker" }

    // 仅主线程提交显示状态；工作期间禁用操作，避免旧结果覆盖新选择。
    private var original by mutableStateOf<Bitmap?>(null)
    private var thumbnail by mutableStateOf<Bitmap?>(null)
    private var busy by mutableStateOf(false)
    private var message by mutableStateOf("选择图库图片或拍照，生成并保存缩略图。")
    private var currentPhotoPath: String? = null
    private var selectedUri: Uri? = null
    // 恢复状态和外部结果可能相邻到达；只允许最后一次请求更新预览和 busy。
    private var loadJob: Job? = null
    private var loadVersion = 0

    // 文档选择器按 URI 临时授权，不需要申请整个图库的读取权限。
    private val gallery = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: SecurityException) { /* 部分提供器只支持本次授权，当前会话仍可读取。 */ }
            loadImage(uri)
        }
    }
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = currentPhotoPath
        if (success && path != null) loadImage(Uri.fromFile(File(path)))
        else {
            path?.let { File(it).delete() }
            currentPhotoPath = null
            message = "已取消拍照"
        }
    }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takePhoto() else message = "需要相机权限才能拍照，可在系统设置中授权或选择图库图片。"
    }

    /** 恢复外部相机等待期间的文件路径；位图重新解码，不放入 Bundle。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentPhotoPath = savedInstanceState?.getString("photo")
        savedInstanceState?.getString("selected")?.let { loadImage(Uri.parse(it)) }
        setContent {
            ImageDemoTheme {
                ImageDemoScaffold("图片选择与缩略图", { finish() }) { padding ->
                    Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("从原图到缩略图", style = MaterialTheme.typography.headlineSmall)
                        Button(onClick = { gallery.launch(arrayOf("image/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("从图库选择") }
                        OutlinedButton(onClick = { requestCamera() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("拍照") }
                        TextButton(onClick = { clearImage() }, enabled = !busy) { Text("清除图片") }
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(message)
                        ImagePreview(original, "原图预览（采样加载）")
                        ImagePreview(thumbnail, "200 × 200 缩略图", 160)
                    }
                }
            }
        }
    }

    /** 保留 URI 与相机输出路径，使旋转后仍可接收拍照结果。 */
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("photo", currentPhotoPath)
        outState.putString("selected", selectedUri?.toString())
        super.onSaveInstanceState(outState)
    }

    /** 仅拍照操作申请 CAMERA，拒绝不阻断文档选择器。 */
    private fun requestCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) takePhoto()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    /** 输出到既有 FileProvider 允许的 Pictures 目录；启动失败时显示原因并删除空文件。 */
    private fun takePhoto() {
        try {
            val directory = requireNotNull(getExternalFilesDir("Pictures")) { "图片目录不可用" }.apply { mkdirs() }
            val file = File.createTempFile("JPEG_", ".jpg", directory)
            currentPhotoPath = file.absolutePath
            camera.launch(FileProvider.getUriForFile(this, "$packageName.fileprovider", file))
        } catch (error: Exception) {
            currentPhotoPath?.let { File(it).delete() }
            currentPhotoPath = null
            message = "无法启动相机：${error.localizedMessage}"
        }
    }

    /** 解码、生成和保存串行完成后一次提交；取消协程不显示错误，保存失败仍保留预览。 */
    private fun loadImage(uri: Uri) {
        val version = ++loadVersion
        loadJob?.cancel()
        selectedUri = uri
        busy = true
        loadJob = lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val bitmap = requireNotNull(decodeImageSampled(contentResolver, uri, 1024)) { "无法解码图片" }
                    val small = Bitmap.createScaledBitmap(bitmap, 200, 200, true)
                    val saved = runCatching {
                        val directory = requireNotNull(getExternalFilesDir("Thumbnails")) { "缩略图目录不可用" }.apply { mkdirs() }
                        val file = File(directory, "thumbnail_${System.currentTimeMillis()}.jpg")
                        file.outputStream().use { check(small.compress(Bitmap.CompressFormat.JPEG, 90, it)) { "JPEG 编码失败" } }
                        file.absolutePath
                    }
                    Triple(bitmap, small, saved)
                }
                original = result.first
                thumbnail = result.second
                message = result.third.fold({ "缩略图已保存：$it" }, { "图片已加载，缩略图保存失败：${it.localizedMessage}" })
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { message = "图片加载失败：${error.localizedMessage}" }
            catch (_: OutOfMemoryError) { message = "图片过大，内存不足，请选择较小的图片。" }
            finally { if (version == loadVersion) busy = false }
        }
    }

    /** 清除显示引用即可释放持有关系；已保存的缩略图文件继续保留。 */
    private fun clearImage() {
        original = null
        thumbnail = null
        selectedUri = null
        currentPhotoPath = null
        message = "已清除图片"
    }
}
