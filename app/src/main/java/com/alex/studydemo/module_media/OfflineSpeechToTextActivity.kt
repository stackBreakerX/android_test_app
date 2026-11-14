package com.alex.studydemo.module_media

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Button
import androidx.compose.material3.Text

/** 保留旧离线识别入口，并提供系统识别与本地 Whisper 的实际导航。 */
class OfflineSpeechToTextActivity : ComponentActivity() {
    /** 本页不占用麦克风，权限由实际录音页面在按下时申请。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MediaPage("离线语音转文字", { finish() }) {
                Text("已迁移为系统识别方案。系统识别优先使用离线能力，是否可用取决于设备语音服务。")
                Button(onClick = { startActivity(Intent(this@OfflineSpeechToTextActivity, SpeechRecognizerSttActivity::class.java)) }) { Text("打开系统识别") }
                Text("Whisper 需要设备中具备引擎和模型。")
                Button(onClick = { startActivity(Intent(this@OfflineSpeechToTextActivity, WhisperAndroidSttActivity::class.java)) }) { Text("打开 Whisper") }
            }
        }
    }
}
