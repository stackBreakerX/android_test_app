package com.alex.studydemo.module_media

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 读取 PNG 文件结构，区分 Alpha 通道与 tRNS 透明信息，不修改原文件。 */
class PngAlphaCheckActivity : ComponentActivity() {
    private val output = MediaOutput()

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) analyze(uri)
    }

    /** 文档选择器仅授予所选 PNG 的读取能力，不需要扫描整个图库。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MediaPage("PNG 透明通道检测", { finish() }) {
                Button(onClick = { openDocument.launch(arrayOf("image/png")) }, enabled = !output.running) { Text("选择 PNG 并检测透明通道") }
                if (output.running) LinearProgressIndicator()
                MediaResult(output.text)
            }
        }
    }

    /** 保留旧的公开点击接口，供其他源代码调用；Compose 直接启动同一个选择器。 */
    fun pickPng(view: View) {
        openDocument.launch(arrayOf("image/png"))
    }

    /** 文件解析在 IO 线程执行；格式损坏、权限或读取失败由结果区显示。 */
    private fun analyze(uri: Uri) {
        output.launch(this) {
            val text = withContext(Dispatchers.IO) {
                buildAnalysis(uri)
            }
            output.text = text
        }
    }

    /** 保留每种透明类型与调色板透明项统计，不通过位图解码猜测透明性。 */
    private fun buildAnalysis(uri: Uri): String {
        val info = PngAlphaInspector.inspect(contentResolver, uri)
        val name = resolveDisplayName(uri)
        val colorTypeName = when (info.colorType) {
            0 -> "Grayscale"
            2 -> "Truecolor"
            3 -> "Indexed"
            4 -> "Grayscale+Alpha"
            6 -> "Truecolor+Alpha"
            else -> "Unknown"
        }
        val kindName = when (info.transparencyKind) {
            TransparencyKind.PerPixelAlpha -> "Per-pixel Alpha"
            TransparencyKind.PaletteAlpha -> "Palette Alpha"
            TransparencyKind.SingleTransparentColor -> "Single Transparent Color"
            TransparencyKind.None -> "None"
        }
        val text = buildString {
            append("文件: "); append(name ?: uri.toString()); append('\n')
            append("位深: "); append(info.bitDepth); append('\n')
            append("颜色类型: "); append(colorTypeName); append(" ("); append(info.colorType); append(")"); append('\n')
            append("含Alpha通道: "); append(info.hasAlphaChannel); append('\n')
            append("含tRNS透明信息: "); append(info.hasTransparencyChunk); append('\n')
            append("透明类型: "); append(kindName); append('\n')
            if (info.paletteAlphaEntries > 0) {
                append("调色板含透明项: "); append(info.paletteAlphaEntries); append('\n')
            }
        }
        return text
    }

    private fun resolveDisplayName(uri: Uri): String? {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(idx)
            }
        }
        return null
    }
}
