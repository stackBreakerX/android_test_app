package com.alex.studydemo.module_media

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** 使用现有 Whisper 适配器和模型，按住采集 16 kHz 单声道 PCM16，松手停止。 */
class WhisperAndroidSttActivity : ComponentActivity() {
    private var statusText by mutableStateOf("准备就绪")
    private var transcription by mutableStateOf("")
    // running 跨主线程和录音线程共享；录音对象的启动/停止由同一把锁保护。
    private val running = AtomicBoolean(false)
    private val recordLock = Any()
    private var record: AudioRecord? = null
    private var worker: Thread? = null
    private val requestAudio = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        statusText = if (granted) "已获得麦克风权限，请再次按住录音" else "未授予麦克风权限"
    }

    /** 手势释放或取消都停止采集；模型文件路径对使用者可见，方便部署模型。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MediaPage("Whisper（长按离线识别）", { finish() }) {
                Text("模型目录：${File(filesDir, "models/whisper").absolutePath}")
                Text("需要 whisper-tiny.tflite、filters_vocab_multilingual.bin 和兼容的 Whisper 引擎。")
                MediaHoldToRecord("按住开始离线录音，松开结束", { startIfPermitted() }, { stop() })
                Text(statusText)
                MediaResult(transcription.ifEmpty { "识别文字会显示在这里" })
            }
        }
    }

    /** 权限授予后等待下一次按下，确保用户松手后不会突然开始录音。 */
    private fun startIfPermitted() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestAudio.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        start()
    }

    /** 模型复制、加载与音频读取在专用线程串行执行；上一轮释放完成前不重复启动。 */
    private fun start() {
        if (worker?.isAlive == true || !running.compareAndSet(false, true)) return
        transcription = ""
        statusText = "准备录音"
        worker = Thread {
            var localRecord: AudioRecord? = null
            var localEngine: WhisperEngine? = null
            // 收尾可能触发引擎状态回调，失败原因要在资源释放后最后提交。
            var failureStatus: String? = null
            try {
                val modelDir = File(filesDir, "models/whisper")
                ensureModelDir(modelDir)
                if (!running.get()) return@Thread
                val activeEngine = WhisperEngineAdapter(this, modelDir,
                    { text -> updateUi { transcription = text } },
                    { text -> updateUi { transcription = text } },
                    { text -> updateUi { statusText = text } })
                localEngine = activeEngine
                activeEngine.start()
                val minBuf = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(minBuf > 0) { "设备不支持 16 kHz PCM16 录音：$minBuf" }
                // 麦克风权限可在模型加载期间被撤销，创建和启动都由下方 SecurityException 分支处理。
                val activeRecord = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2)
                localRecord = activeRecord
                check(activeRecord.state == AudioRecord.STATE_INITIALIZED) { "麦克风初始化失败" }
                synchronized(recordLock) {
                    if (!running.get()) return@Thread
                    record = activeRecord
                    activeRecord.startRecording()
                    check(activeRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "麦克风未进入录音状态" }
                }
                updateUi { if (running.get()) statusText += "\n录音中，松开结束" }
                val buf = ByteArray(minBuf)
                while (running.get()) {
                    val n = activeRecord.read(buf, 0, buf.size)
                    // read 可能只填充部分缓冲区，不能把上一次遗留数据交给引擎。
                    if (n > 0) activeEngine.writePcm16(if (n == buf.size) buf else buf.copyOf(n))
                    else if (n < 0 && running.get()) error("音频读取失败：$n")
                }
            } catch (error: SecurityException) {
                // 权限撤销不能仅报告普通失败；finally 仍需释放已创建的录音对象和引擎。
                failureStatus = "麦克风权限已撤销，请重新授权后录音"
            } catch (error: Exception) {
                failureStatus = "录音失败：${error.message}"
            } finally {
                // 读取线程拥有最终释放权，避免主线程 release 与阻塞 read 竞争。
                running.set(false)
                synchronized(recordLock) {
                    try { localRecord?.stop() } catch (_: Exception) {}
                    try { localRecord?.release() } catch (_: Exception) {}
                    record = null
                }
                // 单个资源释放失败也不能跳过剩余清理和最终错误提示。
                try { localEngine?.stop() } catch (_: Exception) {}
                failureStatus?.let { message -> updateUi { statusText = message } }
            }
        }.also { it.start() }
    }

    /** 停止底层录音以唤醒阻塞 read；释放与引擎收尾由录音线程完成。 */
    private fun stop() {
        if (!running.getAndSet(false)) return
        synchronized(recordLock) {
            try { record?.stop() } catch (_: Exception) {}
        }
        statusText = "已停止"
    }

    /** 模型和识别回调统一回到主线程；销毁后的回调不再改变页面状态。 */
    private fun updateUi(action: () -> Unit) {
        runOnUiThread { if (!isDestroyed) action() }
    }

    /** 退到后台就关闭麦克风，防止手势因系统切换而没有收到释放事件。 */
    override fun onStop() {
        stop()
        super.onStop()
    }

    /** 退出时重复停止安全；录音线程会在读取退出后释放资源。 */
    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    /** 只补齐缺失的模型文件；中途复制失败不会把半成品当成可用模型。 */
    private fun ensureModelDir(dir: File) {
        check(dir.isDirectory || dir.mkdirs()) { "无法创建模型目录：$dir" }
        val assetBase = "models/whisper"
        for (name in assets.list(assetBase).orEmpty()) {
            val target = File(dir, name)
            if (target.exists()) continue
            val temporary = File(dir, "$name.tmp")
            try {
                assets.open("$assetBase/$name").use { input -> temporary.outputStream().use { input.copyTo(it) } }
                check(temporary.renameTo(target)) { "无法保存模型：$name" }
            } finally {
                temporary.delete()
            }
        }
        Log.d("Whisper", "models directory: ${dir.absolutePath}")
    }
}
