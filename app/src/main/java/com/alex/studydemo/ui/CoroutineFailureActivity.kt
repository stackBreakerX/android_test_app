package com.alex.studydemo.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

/** 将首页原有的 try/catch 实验变为可观察页面，对比子协程异常是否取消兄弟任务。 */
class CoroutineFailureActivity : ComponentActivity() {
    private val events = mutableStateListOf<String>()
    private var task: Job? = null
    private var running by mutableStateOf(false)

    /** 页面销毁时 lifecycleScope 取消任务；手动取消和失败分别记录，不能吞掉取消信号。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StudyTheme {
                StudyPage("异常与取消", onBack = ::finish,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("任务 A 在 300ms 后失败，任务 B 在 600ms 后完成。比较两种作用域下 B 的结果。")
                        Button(onClick = { runExperiment(false) }, enabled = !running) { Text("运行 coroutineScope") }
                        Button(onClick = { runExperiment(true) }, enabled = !running) { Text("运行 supervisorScope") }
                        OutlinedButton(onClick = { task?.cancel() }, enabled = running) { Text("取消任务") }
                        events.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
            }
        }
    }

    /** await 将 async 的失败传给调用方；监督作用域在局部处理 A 的失败，让 B 继续完成。 */
    private fun runExperiment(supervised: Boolean) {
        events.clear()
        running = true
        task = lifecycleScope.launch {
            try {
                events.add(if (supervised) "supervisorScope 开始" else "coroutineScope 开始")
                val children: suspend CoroutineScope.() -> Unit = {
                    val failing = async { delay(300); error("任务 A 模拟失败") }
                    val sibling = async {
                        try { delay(600); events.add("任务 B 完成") }
                        finally { if (!isActive) events.add("任务 B 被取消") }
                    }
                    if (supervised) {
                        try { failing.await() }
                        catch (cancel: CancellationException) { throw cancel }
                        catch (error: IllegalStateException) { events.add("局部捕获：${error.message}") }
                        sibling.await()
                    } else {
                        awaitAll(failing, sibling)
                    }
                }
                if (supervised) supervisorScope(children) else coroutineScope(children)
                events.add("作用域正常结束")
            } catch (cancel: CancellationException) {
                events.add("实验已取消")
                throw cancel
            } catch (error: IllegalStateException) {
                events.add("外层捕获：${error.message}")
            } finally {
                running = false
            }
        }
    }
}
