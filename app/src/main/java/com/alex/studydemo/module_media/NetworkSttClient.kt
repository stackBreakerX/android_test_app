package com.alex.studydemo.module_media

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class NetworkSttClient(
    private val url: String,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private var ws: WebSocket? = null
    private var record: AudioRecord? = null
    private val running = AtomicBoolean(false)
    private val client = OkHttpClient()
    private val sampleRate = 16000

    fun start() {
        if (url.isEmpty()) {
            onStatus("未配置服务地址")
            return
        }
        if (running.get()) return
        running.set(true)
        val request = Request.Builder().url(url).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                onStatus("连接成功")
                webSocket.send(JSONObject().put("type", "start").toString())
                startRecordAndPump()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val obj = JSONObject(text)
                    val t = obj.optString("type")
                    val data = obj.optString("text")
                    when (t) {
                        "partial" -> onPartial(data)
                        "final" -> onFinal(data)
                    }
                } catch (_: Exception) {}
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {}
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onStatus("连接关闭")
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                onStatus("连接失败: ${t.message}")
                stop()
            }
        })
    }

    private fun startRecordAndPump() {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2
        )
        record?.startRecording()
        Thread {
            val buf = ByteArray(minBuf)
            while (running.get()) {
                val n = record?.read(buf, 0, buf.size) ?: -1
                if (n > 0) {
                    val payload = JSONObject()
                        .put("type", "audio")
                        .put("format", "pcm16")
                        .put("sr", sampleRate)
                        .put("data", android.util.Base64.encodeToString(buf, 0))
                    ws?.send(payload.toString())
                }
            }
        }.start()
    }

    fun stop() {
        if (!running.get()) return
        running.set(false)
        try {
            record?.stop()
        } catch (_: Exception) {}
        try {
            record?.release()
        } catch (_: Exception) {}
        record = null
        try {
            ws?.send(JSONObject().put("type", "stop").toString())
        } catch (_: Exception) {}
        try {
            ws?.close(1000, null)
        } catch (_: Exception) {}
        ws = null
        onStatus("已停止")
    }
}

