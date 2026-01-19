package com.alex.studydemo.whisper

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

class Whisper(private val context: Context) {

    private var listener: IWhisperListener? = null
    private val running = AtomicBoolean(false)

    fun loadModel(modelPath: String, vocabPath: String, multilingual: Boolean) {
        listener?.onUpdateReceived("加载模型: $modelPath")
    }

    fun setListener(l: IWhisperListener) {
        listener = l
    }

    fun start() {
        running.set(true)
        listener?.onUpdateReceived("Whisper 已启动")
    }

    fun stop() {
        running.set(false)
        listener?.onUpdateReceived("Whisper 已停止")
    }

    fun writeBuffer(samples: FloatArray) {
        if (!running.get()) return
        listener?.onUpdateReceived("音频片段: ${samples.size}")
    }
}

