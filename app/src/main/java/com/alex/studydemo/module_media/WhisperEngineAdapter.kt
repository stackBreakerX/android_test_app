package com.alex.studydemo.module_media

import android.content.Context
import java.io.File
import java.lang.reflect.Proxy
import android.util.Log
import com.alex.studydemo.BuildConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WhisperEngineAdapter(
    private val context: Context,
    private val modelDir: File,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onStatus: (String) -> Unit
) : WhisperEngine {

    private var whisperObj: Any? = null
    private var listenerObj: Any? = null

    override fun start() {
        try {
            val whisperClassCandidates = listOfNotNull(
                BuildConfig.WHISPER_CLASS.takeIf { it.isNotBlank() },
                "com.app.whisper.native.Whisper",
                "com.vilassn.whisper.Whisper",
                "com.whisper.android.Whisper",
                "com.whispercpp.android.Whisper"
            )
            val whisperClass = whisperClassCandidates.mapNotNull { runCatching { Class.forName(it) }.getOrNull() }.firstOrNull()
            if (whisperClass == null) {
                onStatus("未找到 whisper_android 引擎类")
                return
            }

            val ctor = whisperClass.getConstructor(Context::class.java)
            whisperObj = ctor.newInstance(context)

            val modelPath = File(modelDir, "whisper-tiny.tflite")
            val vocabPath = File(modelDir, "filters_vocab_multilingual.bin")
            if (!modelPath.exists() || !vocabPath.exists()) {
                onStatus("模型或词表缺失：${modelPath.name}/${vocabPath.name}")
                return
            }

            val loadMethod = whisperClass.getMethod("loadModel", String::class.java, String::class.java, Boolean::class.javaPrimitiveType)
            loadMethod.invoke(whisperObj, modelPath.absolutePath, vocabPath.absolutePath, true)

            val listenerInterfaceCandidates = listOfNotNull(
                BuildConfig.WHISPER_LISTENER.takeIf { it.isNotBlank() },
                "com.app.whisper.native.IWhisperListener",
                "com.vilassn.whisper.IWhisperListener",
                "com.whisper.android.IWhisperListener"
            )
            val listenerInterface = listenerInterfaceCandidates.mapNotNull { runCatching { Class.forName(it) }.getOrNull() }.firstOrNull()
            if (listenerInterface == null) {
                onStatus("未找到 Whisper 监听接口")
                return
            }

            listenerObj = Proxy.newProxyInstance(listenerInterface.classLoader, arrayOf(listenerInterface)) { _, method, args ->
                when (method.name) {
                    "onUpdateReceived" -> onStatus(args?.getOrNull(0)?.toString() ?: "")
                    "onResultReceived" -> onPartial(args?.getOrNull(0)?.toString() ?: "")
                }
                null
            }

            val setListener = whisperClass.getMethod("setListener", listenerInterface)
            setListener.invoke(whisperObj, listenerObj)

            val startMethod = whisperClass.getMethod("start")
            startMethod.invoke(whisperObj)
            onStatus("Whisper 已启动：模型=${modelPath.name}")
        } catch (e: Exception) {
            onStatus("Whisper 启动失败：${e.message}")
            Log.e("WhisperAdapter", "start failed", e)
        }
    }

    override fun stop() {
        try {
            val obj = whisperObj ?: return
            val stopMethod = obj.javaClass.methods.firstOrNull { it.name == "stop" }
            stopMethod?.invoke(obj)
            onStatus("Whisper 已停止")
        } catch (_: Exception) {}
    }

    override fun writePcm16(bytes: ByteArray) {
        try {
            val obj = whisperObj ?: return
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val floats = FloatArray(bytes.size / 2)
            var i = 0
            while (bb.remaining() >= 2) {
                val s = bb.short
                floats[i++] = s.toFloat() / 32768f
            }
            val writeMethod = obj.javaClass.methods.firstOrNull { it.name == "writeBuffer" && it.parameterTypes.size == 1 }
            writeMethod?.invoke(obj, floats)
        } catch (_: Exception) {}
    }
}
