package com.alex.studydemo.module_media

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.alex.studydemo.BuildConfig

/** 按住时优先使用系统中文离线识别，ERROR_CLIENT 时保留 WebSocket 降级路径。 */
class SpeechRecognizerSttActivity : ComponentActivity() {
    // 系统识别回调在主线程到达；网络回调通过 runOnUiThread 更新可观察文字。
    private var statusText by mutableStateOf("准备就绪")
    private var transcription by mutableStateOf("")
    private var holding = false
    private val requestAudio = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        statusText = if (granted) "已获得麦克风权限，请再次按住录音" else "未授予麦克风权限"
    }


    private var recognizer: SpeechRecognizer? = null
    private var fallback: NetworkSttClient? = null

    /** 重组只更新文字；按住手势的释放和取消都关闭当前录音会话。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MediaPage("系统语音识别（长按）", { finish() }) {
                MediaHoldToRecord("按住开始录音，松开结束", { holding = true; startIfPermitted() }, { holding = false; stop(); stopFallback() })
                Text(statusText)
                MediaResult(transcription.ifEmpty { "识别文字会显示在这里" })
            }
        }
    }

    /** 权限返回时不自动打开麦克风，避免用户已经松手却开始后台录音。 */
    private fun startIfPermitted() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            holding = false
            requestAudio.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText = "识别服务不可用"
            return
        }
        start()
    }

    /** 每次按下清空文字，系统回调同时接收部分结果与最终结果。 */
    private fun start() {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { statusText = "开始" }
                override fun onBeginningOfSpeech() { statusText = "录音中" }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { statusText = "结束" }
                override fun onError(error: Int) {
                    statusText = "错误：$error"
                    if (holding && error == SpeechRecognizer.ERROR_CLIENT) {
                        startFallback()
                    }
                }
                override fun onResults(results: Bundle?) {
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!list.isNullOrEmpty()) transcription = list.joinToString(" ")
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!list.isNullOrEmpty()) transcription = list.joinToString(" ")
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN")
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        transcription = ""
        statusText = "准备录音"
        try { recognizer?.startListening(intent) } catch (error: Exception) {
            statusText = "启动识别失败：${error.message}"
        }
    }

    /** 松手要求系统提交最终结果；服务异常不影响页面退出。 */
    private fun stop() {
        try { recognizer?.stopListening() } catch (_: Exception) {}
    }

    /** 网络降级只在仍然按住时启动；服务地址沿用 BuildConfig 配置。 */
    private fun startFallback() {
        if (fallback != null) return
        val url = BuildConfig.STT_WS_URL
        fallback = NetworkSttClient(
            url,
            { t -> runOnUiThread { transcription = t } },
            { t -> runOnUiThread { transcription = t } },
            { s -> runOnUiThread { statusText = s } }
        )
        try { fallback?.start() } catch (error: Exception) {
            stopFallback()
            statusText = "网络识别启动失败：${error.message}"
        }
    }

    /** 释放网络客户端的录音和连接；允许重复调用。 */
    private fun stopFallback() {
        fallback?.stop()
        fallback = null
    }

    /** 切到后台立即停止采集，避免系统打断手势后继续占用麦克风。 */
    override fun onStop() {
        holding = false
        stop()
        recognizer?.cancel()
        stopFallback()
        super.onStop()
    }

    /** 系统识别器绑定 Activity，退出时必须销毁，不能交给 Compose 重组管理。 */
    override fun onDestroy() {
        super.onDestroy()
        recognizer?.destroy()
        recognizer = null
        stopFallback()
    }
}
