package com.alex.studydemo.module_media

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.databinding.ActivityOfflineSttBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.vosk.Model
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File

class OfflineSpeechToTextActivity : BaseActivity<ActivityOfflineSttBinding>() {

    private var speechService: SpeechService? = null
    private var model: Model? = null
    private val sampleRate = 16000.0f

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityOfflineSttBinding =
        ActivityOfflineSttBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        supportActionBar?.title = "离线语音转文字"

        binding.btnHoldToRecord.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRecordingIfPermitted()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRecording()
                    true
                }
                else -> false
            }
        }
    }

    private fun startRecordingIfPermitted() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
            return
        }
        ensureModelAndStart()
    }

    private fun ensureModelAndStart() {
        if (model != null) {
            startService()
            return
        }
        val dir = File(filesDir, "models/vosk-model-small-cn")
        if (!dir.exists()) {
            binding.txtStatus.text = "模型缺失：请将离线模型目录拷贝到 ${dir.absolutePath}"
            return
        }
        binding.txtStatus.text = "加载离线模型..."
        CoroutineScope(Dispatchers.Default).launch {
            model = Model(dir.absolutePath)
            runOnUiThread {
                binding.txtStatus.text = "模型已加载"
                startService()
            }
        }
    }

    private fun startService() {
        if (speechService != null) return
        val recognizer = org.vosk.Recognizer(model, sampleRate)
        speechService = SpeechService(recognizer, sampleRate)
        binding.txtTranscription.text = ""
        binding.txtStatus.text = "录音中...长按保持，松开结束"
        speechService?.startListening(object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) {
                hypothesis?.let { binding.txtTranscription.text = it }
            }
            override fun onResult(hypothesis: String?) {
                hypothesis?.let { binding.txtTranscription.text = it }
            }
            override fun onFinalResult(hypothesis: String?) {
                hypothesis?.let { binding.txtTranscription.text = it }
            }
            override fun onError(e: Exception?) {
                binding.txtStatus.text = "错误：${e?.message ?: "unknown"}"
            }
            override fun onTimeout() {
                binding.txtStatus.text = "超时"
            }
        })
    }

    private fun stopRecording() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        binding.txtStatus.text = "已停止"
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        model?.close()
        model = null
    }
}
