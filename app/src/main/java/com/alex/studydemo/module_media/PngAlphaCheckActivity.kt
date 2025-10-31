package com.alex.studydemo.module_media

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.databinding.ActivityPngAlphaCheckBinding

class PngAlphaCheckActivity : BaseActivity<ActivityPngAlphaCheckBinding>() {

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) analyze(uri)
    }

    override fun inflateBinding(inflater: LayoutInflater): ActivityPngAlphaCheckBinding =
        ActivityPngAlphaCheckBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {}

    fun pickPng(view: View) {
        openDocument.launch(arrayOf("image/png"))
    }

    private fun analyze(uri: Uri) {
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
        binding.tvResult.text = text
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