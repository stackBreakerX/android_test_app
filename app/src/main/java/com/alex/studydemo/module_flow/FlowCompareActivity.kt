package com.alex.studydemo.module_flow

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 对比无缓冲 SharedFlow 与 StateFlow；订阅在 STARTED 时运行，退出时随生命周期取消。 */
class FlowCompareActivity : ComponentActivity() {
    // 这些状态只在主线程读写；慢订阅开关在每次收到事件后决定是否延迟。
    private var slowCollector by mutableStateOf(false)
    private var stateValue by mutableStateOf(0)
    private var logText by mutableStateOf("")


    private val sharedFlowReplay0 = MutableSharedFlow<Int>(replay = 0, extraBufferCapacity = 0)
    private val sharedFlowReplay1 = MutableSharedFlow<Int>(replay = 1, extraBufferCapacity = 0)
    private val stateFlow = MutableStateFlow(0)

    private val collectorJobs = mutableListOf<Job>()

    private var seq = 0
    private var sharedReplay0CollectorId = 0
    private var sharedReplay1CollectorId = 0
    private var stateCollectorId = 0

    private val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    companion object {
        /** 保留原入口，调用方需提供可启动页面的 Context。 */
        fun newInstance(context: Context) {
            context.startActivity(Intent(context, FlowCompareActivity::class.java))
        }
    }

    /** 创建控制面板；每个按钮仍调用对应的真实 Flow 操作。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Page("Flow 对比") {
                Text("StateFlow 当前值：$stateValue")
                Text("SharedFlow replay=0 / replay=1，额外缓冲均为 0")
                Row { Checkbox(slowCollector, { slowCollector = it }); Text("慢订阅者（120 ms）") }
                Button(onClick = { emitOnce() }) { Text("单次 tryEmit") }
                Button(onClick = { tryEmitBurst() }) { Text("连续 tryEmit 20 次") }
                Button(onClick = { emitBurstSuspend() }) { Text("挂起 emit 20 次") }
                Button(onClick = { addSharedCollector(0) }) { Text("添加 SharedFlow replay=0 订阅") }
                Button(onClick = { addSharedCollector(1) }) { Text("添加 SharedFlow replay=1 订阅") }
                Button(onClick = { addStateCollector() }) { Text("添加 StateFlow 订阅") }
                Button(onClick = { cancelCollectors() }) { Text("取消全部订阅") }
                TextButton(onClick = { logText = "" }) { Text("清空日志") }
                SelectionContainer { Text(logText) }
            }
        }
        log("ready: SharedFlow(replay=0/1) & StateFlow(initial=0)")
    }

    /** 取消订阅句柄；发射任务由 lifecycleScope 在销毁时取消。 */
    override fun onDestroy() {
        cancelCollectors()
        super.onDestroy()
    }

    /** 立即尝试发射，记录布尔返回值以观察无缓冲 SharedFlow 的丢弃条件。 */
    private fun emitOnce() {
        val v = ++seq
        val ok0 = sharedFlowReplay0.tryEmit(v)
        val ok1 = sharedFlowReplay1.tryEmit(v)
        stateFlow.value = v
        renderState()
        log("emit v=$v | shared(r0).tryEmit=$ok0 | shared(r1).tryEmit=$ok1 | state.value=$v")
    }

    /** 以 10 ms 间隔尝试发射 20 次，不等待慢订阅者。 */
    private fun tryEmitBurst() {
        lifecycleScope.launch {
            log("tryEmit burst start (1..20)")
            repeat(20) {
                val v = ++seq
                val ok0 = sharedFlowReplay0.tryEmit(v)
                val ok1 = sharedFlowReplay1.tryEmit(v)
                stateFlow.value = v
                renderState()
                log("tryEmit v=$v | shared(r0)=$ok0 | shared(r1)=$ok1 | state=$v")
                delay(10)
            }
            log("tryEmit burst end")
        }
    }

    /** 挂起发射等待订阅者腾出空间，页面销毁时取消。 */
    private fun emitBurstSuspend() {
        lifecycleScope.launch {
            log("emit burst start (1..20)")
            repeat(20) {
                val v = ++seq
                sharedFlowReplay0.emit(v)
                sharedFlowReplay1.emit(v)
                stateFlow.value = v
                renderState()
                log("emit v=$v")
                delay(10)
            }
            log("emit burst end")
        }
    }

    /** 添加独立订阅；后台暂停收集，重新可见时按 replay 规则恢复。 */
    private fun addSharedCollector(replay: Int) {
        val (name, id, flow) = when (replay) {
            0 -> Triple("SharedFlow(replay=0)", ++sharedReplay0CollectorId, sharedFlowReplay0)
            else -> Triple("SharedFlow(replay=1)", ++sharedReplay1CollectorId, sharedFlowReplay1)
        }

        val job = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                log("$name collector#$id start")
                flow.collect { value ->
                    log("$name collector#$id <- $value")
                    if (slowCollector) delay(120)
                }
            }
        }
        collectorJobs += job
        log("added $name collector#$id")
    }

    /** 订阅最新状态；相同值不重复发送，慢订阅可跳过中间状态。 */
    private fun addStateCollector() {
        val id = ++stateCollectorId
        val job = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                log("StateFlow collector#$id start")
                stateFlow.collect { value ->
                    stateValue = value
                    log("StateFlow collector#$id <- $value")
                    if (slowCollector) delay(120)
                }
            }
        }
        collectorJobs += job
        log("added StateFlow collector#$id")
    }

    /** 只取消订阅，不重置序号或 replay 缓存，便于重新订阅对比。 */
    private fun cancelCollectors() {
        if (collectorJobs.isEmpty()) return
        collectorJobs.forEach { it.cancel() }
        collectorJobs.clear()
        log("all collectors cancelled")
    }

    /** 同步展示当前 StateFlow 值，即使尚无订阅者也可观察。 */
    private fun renderState() {
        stateValue = stateFlow.value
    }

    /** 主线程追加时间戳日志，仅保留末尾 8000 字符限制内存。 */
    private fun log(message: String) {
        val ts = timeFormatter.format(Date())
        val line = "$ts $message"

        val prev = logText
        val merged = if (prev.isEmpty()) line else "$prev\n$line"
        logText = if (merged.length > 8000) merged.takeLast(8000) else merged
    }

    /** 页面使用应用主题和可滚动内容；返回按钮结束当前 Activity，系统返回沿用默认行为。 */
    @Composable
    private fun Page(title: String, content: @Composable ColumnScope.() -> Unit) {
        com.alex.studydemo.ui.StudyTheme {
            Scaffold(topBar = {
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp)) {
                        TextButton(onClick = { finish() }) { Text("返回") }
                        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(12.dp))
                    }
                }
            }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            }
        }
    }
}
