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

/** 沿用现有 WebSocket 协议传输 PCM16；调用方持有麦克风权限并在离开前停止。 */
class NetworkSttClient(
    private val url: String,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private var ws: WebSocket? = null
    private var record: AudioRecord? = null
    private val running = AtomicBoolean(false)
    // 关闭连接的异步回调不能覆盖录音启动失败原因，保留到下次明确启动。
    @Volatile private var recordingFailure: String? = null
    private val client = OkHttpClient()
    private val sampleRate = 16000

    /** 建立异步连接；未配置 URL 时只报告状态，不启动麦克风。 */
    fun start() {
        if (url.isEmpty()) {
            onStatus("未配置服务地址")
            return
        }
        if (running.get()) return
        recordingFailure = null
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
                onStatus(recordingFailure ?: "连接关闭")
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                onStatus(recordingFailure ?: "连接失败: ${t.message}")
                stop()
            }
        })
    }

    /** 与 stop 串行检查会话状态，防止松手后迟到的 onOpen 再次启动麦克风。 */
    @Synchronized
    private fun startRecordAndPump() {
        if (!running.get()) return
        try {
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            check(minBuf > 0) { "设备不支持 16 kHz PCM16 录音：$minBuf" }
            // 权限检查与实际创建之间仍可能撤权，因此创建和启动必须处于同一异常边界。
            val activeRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
            record = activeRecord
            check(activeRecord.state == AudioRecord.STATE_INITIALIZED) { "麦克风初始化失败" }
            activeRecord.startRecording()
            check(activeRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "麦克风未进入录音状态" }
            Thread {
                try {
                    val buf = ByteArray(minBuf)
                    while (running.get()) {
                        val n = activeRecord.read(buf, 0, buf.size)
                        if (n > 0) {
                            // read 允许短读，网络消息只发送本次采集到的有效字节。
                            val payload = JSONObject()
                                .put("type", "audio")
                                .put("format", "pcm16")
                                .put("sr", sampleRate)
                                .put("data", android.util.Base64.encodeToString(buf, 0, n, android.util.Base64.DEFAULT))
                            ws?.send(payload.toString())
                        } else if (n < 0 && running.get()) {
                            error("音频读取失败：$n")
                        }
                    }
                } catch (error: SecurityException) {
                    // 采集中撤权同样终止连接；正常停止导致的读取退出不报告为失败。
                    if (running.get()) failRecording("麦克风权限已撤销，请重新授权后录音")
                } catch (error: Exception) {
                    if (running.get()) failRecording("录音失败：${error.message}")
                }
            }.start()
        } catch (error: SecurityException) {
            failRecording("麦克风权限已撤销，请重新授权后录音")
        } catch (error: Exception) {
            failRecording("录音启动失败：${error.message}")
        }
    }

    /** 启动或读取失败时释放已创建的录音对象并关闭连接，错误状态不会被关闭回调覆盖。 */
    @Synchronized
    private fun failRecording(message: String) {
        recordingFailure = message
        stop()
        onStatus(message)
    }

    /** 与连接成功回调互斥；重复停止无副作用，并关闭录音和 WebSocket。 */
    @Synchronized
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
        onStatus(recordingFailure ?: "已停止")
    }
}

