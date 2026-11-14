package com.alex.studydemo.module_media

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier

/** 多媒体工作台按转换、图片工具和语音分组；保留所有原有 Activity 入口。 */
class MediaEntryActivity : ComponentActivity() {
    /** 页面只有导航状态；点击入口直接使用原 Activity 类，兼容外部调用。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val groups = listOf(
            "格式转换" to listOf(
                "WebP 转换" to WebpConvertActivity::class.java,
                "libwebp 转换" to WebpLibwebpActivity::class.java,
                "PNG / JPG 批量转换" to PngConvertActivity::class.java,
                "JPG 转换（C 库）" to JpgConvertActivity::class.java,
                "PNG 透明通道检测" to PngAlphaCheckActivity::class.java
            ),
            "图片工具" to listOf(
                "图片选择器" to com.alex.studydemo.module_image.ImagePickerActivity::class.java,
                "快速缩略图" to com.alex.studydemo.module_image.FastThumbnailActivity::class.java,
                "DNG 处理转 WebP" to com.alex.studydemo.module_image.DngProcessActivity::class.java
            ),
            "语音识别" to listOf(
                "系统语音识别（长按）" to SpeechRecognizerSttActivity::class.java,
                "Whisper（离线识别，需模型）" to WhisperAndroidSttActivity::class.java,
                "离线语音转文字" to OfflineSpeechToTextActivity::class.java
            )
        )
        setContent {
            MediaPage("多媒体", { finish() }) {
                groups.forEach { (title, entries) ->
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    entries.forEach { (label, target) ->
                        OutlinedButton(onClick = { startActivity(Intent(this@MediaEntryActivity, target)) }, modifier = Modifier.fillMaxWidth()) {
                            Text(label)
                        }
                    }
                }
            }
        }
    }
}
