package com.alex.studydemo.module_performance

import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import android.os.Bundle
import android.os.SystemClock
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import androidx.metrics.performance.JankStats
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.databinding.ActivityJankStatsBinding
import com.blankj.utilcode.util.ThreadUtils
import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JankStatsActivity : BaseActivity<ActivityJankStatsBinding>() {

    private var jankStats: JankStats? = null
    private var logFile: File? = null
    private var logWriter: BufferedWriter? = null
    private val mainThread by lazy { Looper.getMainLooper().thread }

    private val TAG = "JankStatsActivity"

    override fun inflateBinding(inflater: LayoutInflater): ActivityJankStatsBinding =
        ActivityJankStatsBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        supportActionBar?.title = "JankStats 卡顿检测"

        prepareLogFile()
        binding.tvLogPath.text = logFile?.absolutePath ?: ""

        binding.btnSimulateJank.setOnClickListener {
            simulateMainThreadJank()
        }

        binding.btnToggleTracking.setOnClickListener {
            jankStats?.let { js ->
                val enable = !js.isTrackingEnabled
                js.isTrackingEnabled = enable
                binding.tvStatus.text = if (enable) "监控中" else "已暂停"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (jankStats == null) {
            jankStats = JankStats.createAndTrack(window) { frameData ->
                val uiNanos = frameData.frameDurationUiNanos
                val uiMs = uiNanos / 1_000_000.0
                val budget = frameBudgetMs()
                val isSlow = uiMs > budget * 1.2
                val isFrozen = uiMs > 700.0
                val isCustomJank = isSlow || isFrozen
                if (isCustomJank) {
                    val line = formatFrameData(uiNanos, isCustomJank)
                    val stack = formatStackTrace(mainThread.stackTrace)
                    logWriter?.apply {
                        write(line)
                        newLine()
                        write(stack)
                        newLine()
                        flush()
                    }
                    ThreadUtils.runOnUiThread {
                        binding.tvLastEvent.text = line
                        binding.tvJankInfo.text = "$line\n$stack"
                    }
                }
            }
            binding.tvStatus.text = "监控中"
        } else {
            jankStats?.isTrackingEnabled = true
            binding.tvStatus.text = "监控中"
        }
    }

    override fun onPause() {
        super.onPause()
        jankStats?.isTrackingEnabled = false
        binding.tvStatus.text = "已暂停"
    }

    override fun onDestroy() {
        try {
            logWriter?.flush()
            logWriter?.close()
        } catch (_: Throwable) {}
        logWriter = null
        jankStats = null
        super.onDestroy()
    }

    private fun prepareLogFile() {
        val ext = getExternalFilesDir("Performance")
        val dir = ext ?: File(filesDir, "Performance").apply { mkdirs() }
        val name = "jank_log_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".txt"
        logFile = File(dir, name)
        logWriter = BufferedWriter(FileWriter(logFile!!, true))
    }

    private fun simulateMainThreadJank() {
        binding.tvStatus.text = "模拟卡顿中..."
        SystemClock.sleep(600)
        repeat(5) { SystemClock.sleep(120) }
        binding.tvStatus.text = "模拟完成"
    }

    private fun formatFrameData(uiNanos: Long, isJank: Boolean): String {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val uiMs = uiNanos / 1_000_000.0
        val budget = frameBudgetMs()
        return "[$ts] jank=$isJank ui=${"%.2f".format(uiMs)}ms budget=${"%.2f".format(budget)}ms"
    }

    private fun formatStackTrace(stack: Array<StackTraceElement>, limit: Int = 30): String {
        val sb = StringBuilder()
        sb.append("stacktrace:\n")
        val size = minOf(stack.size, limit)
        for (i in 0 until size) {
            val e = stack[i]
            sb.append("    at ")
            sb.append(e.className)
            sb.append(".")
            sb.append(e.methodName)
            sb.append("(")
            sb.append(e.fileName ?: "Unknown")
            sb.append(":")
            sb.append(e.lineNumber)
            sb.append(")\n")
        }
        if (stack.size > limit) sb.append("    ... \n")
        return sb.toString()
    }

    private fun frameBudgetMs(): Double {
        val rate = binding.root.display?.refreshRate ?: window.decorView.display?.refreshRate ?: 60f
        return 1000.0 / rate
    }
}
