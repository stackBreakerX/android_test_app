package com.alex.studydemo.module_media

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.databinding.ActivitySpeechRecognizerSttBinding
import java.util.concurrent.atomic.AtomicBoolean
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

class WhisperAndroidSttActivity : BaseActivity<ActivitySpeechRecognizerSttBinding>() {

    private var record: AudioRecord? = null
    private val running = AtomicBoolean(false)
    private var engine: WhisperEngine? = null

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivitySpeechRecognizerSttBinding =
        ActivitySpeechRecognizerSttBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        supportActionBar?.title = "Whisper（长按离线识别）"

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
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 3001)
            return
        }
        start()
    }

    private fun start() {
        if (running.get()) return
        running.set(true)
        binding.txtTranscription.text = ""
        binding.txtStatus.text = "准备录音"

        val modelDir = java.io.File(filesDir, "models/whisper")
        ensureModelDir(modelDir)
        engine = WhisperEngineAdapter(
            this,
            modelDir,
            { t -> runOnUiThread { binding.txtTranscription.text = t } },
            { t -> runOnUiThread { binding.txtTranscription.text = t } },
            { s -> runOnUiThread { binding.txtStatus.text = s } }
        )
        engine?.start()

        val sampleRate = 16000
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        record = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2)
        record?.startRecording()
        binding.txtStatus.text = "录音中..."

        Thread {
            val buf = ByteArray(minBuf)
            while (running.get()) {
                val n = record?.read(buf, 0, buf.size) ?: -1
                if (n > 0) {
                    engine?.writePcm16(buf)
                }
            }
        }.start()
    }

    private fun stop() {
        if (!running.get()) return
        running.set(false)
        try { record?.stop() } catch (_: Exception) {}
        try { record?.release() } catch (_: Exception) {}
        record = null
        engine?.stop()
        binding.txtStatus.text = "已停止"
    }

    override fun onDestroy() {
        super.onDestroy()
        stop()
    }

    private fun ensureModelDir(dir: java.io.File) {
        try {
            if (dir.exists()) return
            val parent = dir.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()
            dir.mkdirs()
            val assetBase = "models/whisper"
            val list = assets.list(assetBase) ?: emptyArray()
            for (name in list) {
                val `in` = assets.open("$assetBase/$name")
                val outFile = java.io.File(dir, name)
                val out = java.io.FileOutputStream(outFile)
                `in`.copyTo(out)
                out.flush()
                out.close()
                `in`.close()
            }
            Log.d("Whisper", "copied assets to ${dir.absolutePath}")
        } catch (e: Exception) {
            Log.e("Whisper", "ensureModelDir failed", e)
        }
    }
}
