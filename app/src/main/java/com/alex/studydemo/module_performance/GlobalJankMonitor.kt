package com.alex.studydemo.module_performance

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Looper
import androidx.metrics.performance.JankStats
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object GlobalJankMonitor {
    private val map = ConcurrentHashMap<Activity, JankStats>()
    private val mainThread by lazy { Looper.getMainLooper().thread }
    private var writer: BufferedWriter? = null

    fun init(app: Application) {
        prepareLogFile(app)
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                val stats = JankStats.createAndTrack(activity.window) { frameData ->
                    val uiNanos = frameData.frameDurationUiNanos
                    val uiMs = uiNanos / 1_000_000.0
                    val budget = frameBudgetMs(activity)
                    val isSlow = uiMs > budget * 1.2
                    val isFrozen = uiMs > 700.0
                    val isCustomJank = frameData.isJank || isSlow || isFrozen
                    if (isCustomJank) {
                        val line = formatFrameData(activity, uiNanos, budget)
                        val stack = formatStackTrace(mainThread.stackTrace)
                        writer?.apply {
                            write(line)
                            newLine()
                            write(stack)
                            newLine()
                            flush()
                        }
                    }
                }
                stats.isTrackingEnabled = true
                map[activity] = stats
            }

            override fun onActivityPaused(activity: Activity) {
                map[activity]?.isTrackingEnabled = false
            }

            override fun onActivityDestroyed(activity: Activity) {
                map.remove(activity)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
    }

    private fun prepareLogFile(app: Application) {
        val dir = app.getExternalFilesDir("Performance") ?: File(app.filesDir, "Performance").apply { mkdirs() }
        val name = "jank_global_" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".txt"
        writer = BufferedWriter(FileWriter(File(dir, name), true))
    }

    private fun formatFrameData(activity: Activity, uiNanos: Long, budget: Double): String {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val uiMs = uiNanos / 1_000_000.0
        return "[$ts] activity=${activity.javaClass.simpleName} ui=${"%.2f".format(uiMs)}ms budget=${"%.2f".format(budget)}ms"
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

    private fun frameBudgetMs(activity: Activity): Double {
        val rate = activity.window.decorView.display?.refreshRate ?: 60f
        return 1000.0 / rate
    }
}

