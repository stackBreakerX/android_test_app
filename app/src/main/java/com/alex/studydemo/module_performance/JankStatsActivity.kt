package com.alex.studydemo.module_performance

import com.alex.studydemo.ui.StudyPage
import com.alex.studydemo.ui.StudyTheme
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.metrics.performance.JankStats
import java.io.File
import java.util.concurrent.Executors

/** 使用真实窗口帧数据驱动 Compose 仪表；日志在单线程队列写入，避免诊断动作干扰被测帧。 */
class JankStatsActivity : ComponentActivity() {
    private var stats: JankStats? = null
    private val writer = Executors.newSingleThreadExecutor()
    private var tracking by mutableStateOf(true)
    private var lastEvent by mutableStateOf("等待帧数据")
    private var slowFrames by mutableIntStateOf(0)
    private var failure by mutableStateOf("")
    private lateinit var logFile: File

    /** 文件保存在应用私有目录，不需要存储权限；错误会显示在页面而非中断实验。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        logFile = File(filesDir, "jank_${System.currentTimeMillis()}.txt")
        setContent {
            StudyTheme {
                StudyPage("帧率观测", onBack = ::finish,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("JankStats · 真实窗口帧耗时", color = MaterialTheme.colorScheme.primary)
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (tracking) "监控中" else "已暂停")
                                Text("$slowFrames 个慢帧", style = MaterialTheme.typography.headlineMedium)
                                Text(lastEvent)
                            }
                        }
                        Button(onClick = {
                            tracking = !tracking
                            stats?.isTrackingEnabled = tracking
                        }) { Text(if (tracking) "暂停监控" else "开始监控") }
                        Button(onClick = {
                            // 故意阻塞主线程以生成慢帧，只用于用户主动触发的性能实验。
                            window.decorView.post { SystemClock.sleep(1200) }
                        }) { Text("模拟主线程卡顿（1.2 秒）") }
                        Text("日志路径", style = MaterialTheme.typography.titleMedium)
                        Text(logFile.absolutePath, style = MaterialTheme.typography.bodySmall)
                        if (failure.isNotEmpty()) Text(failure, color = MaterialTheme.colorScheme.error)
                        Text("慢帧阈值：当前刷新周期的 1.2 倍。返回或切到后台时暂停采集。")
                    }
            }
        }
    }

    /** 回调只保存慢帧快照；界面更新切回主线程，写入失败不会导致采样线程崩溃。 */
    override fun onResume() {
        super.onResume()
        if (stats == null) stats = JankStats.createAndTrack(window) { frame ->
            val duration = frame.frameDurationUiNanos / 1_000_000.0
            val budget = 1000.0 / (window.decorView.display?.refreshRate ?: 60f)
            if (duration > budget * 1.2) {
                val line = "ui=%.2fms  budget=%.2fms".format(duration, budget)
                runOnUiThread { slowFrames++; lastEvent = line }
                writer.execute {
                    try { logFile.appendText("${System.currentTimeMillis()} $line\n") }
                    catch (error: java.io.IOException) { runOnUiThread { failure = "日志写入失败：${error.message}" } }
                }
            }
        }
        stats?.isTrackingEnabled = tracking
    }

    /** 暂停 Activity 后停止回调，避免后台窗口继续计入当前实验。 */
    override fun onPause() {
        stats?.isTrackingEnabled = false
        super.onPause()
    }

    /** shutdown 会排空已提交写入，不在主线程等待磁盘完成。 */
    override fun onDestroy() {
        stats?.isTrackingEnabled = false
        stats = null
        writer.shutdown()
        super.onDestroy()
    }
}
