package com.alex.studydemo.module_media

interface WhisperEngine {
    fun start()
    fun stop()
    fun writePcm16(bytes: ByteArray)
}

class WhisperUnavailableEngine(
    private val onStatus: (String) -> Unit
) : WhisperEngine {
    override fun start() { onStatus("Whisper 引擎不可用或模型缺失") }
    override fun stop() {}
    override fun writePcm16(bytes: ByteArray) {}
}

