package com.alex.studydemo.module_media

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.MotionEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.databinding.ActivitySpeechRecognizerSttBinding

class SpeechRecognizerSttActivity : BaseActivity<ActivitySpeechRecognizerSttBinding>() {

    private var recognizer: SpeechRecognizer? = null

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivitySpeechRecognizerSttBinding =
        ActivitySpeechRecognizerSttBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        supportActionBar?.title = "系统语音识别（长按）"

        binding.btnHoldToRecord.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startIfPermitted()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stop()
                    true
                }
                else -> false
            }
        }
    }

    private fun startIfPermitted() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 2001)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            binding.txtStatus.text = "识别服务不可用"
            return
        }
        start()
    }

    private fun start() {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { binding.txtStatus.text = "开始" }
                override fun onBeginningOfSpeech() { binding.txtStatus.text = "录音中" }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { binding.txtStatus.text = "结束" }
                override fun onError(error: Int) { binding.txtStatus.text = "错误：$error" }
                override fun onResults(results: Bundle?) {
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!list.isNullOrEmpty()) binding.txtTranscription.text = list.joinToString(" ")
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!list.isNullOrEmpty()) binding.txtTranscription.text = list.joinToString(" ")
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
        binding.txtTranscription.text = ""
        binding.txtStatus.text = "准备录音"
        recognizer?.startListening(intent)
    }

    private fun stop() {
        recognizer?.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizer?.destroy()
        recognizer = null
    }
}

