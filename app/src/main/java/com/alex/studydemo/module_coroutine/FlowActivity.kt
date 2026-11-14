package com.alex.studydemo.module_coroutine

import com.alex.studydemo.ui.StudyPage
import com.alex.studydemo.ui.StudyTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import com.alex.studydemo.module_flow.FlowCompareActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** 冷 Flow 的创建、变换、收集和取消实验，附带热流对比入口。 */
class FlowActivity : ComponentActivity() {
    private var output by mutableStateOf("")
    private var running by mutableStateOf(false)
    private var job: Job? = null
    /** 只有点击后才开始收集；Compose 重组不会重新启动流。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StudyTheme {
                StudyPage("Flow 基础", onBack = { finish() }) {
                    Text("冷流在收集时开始，map 将 1..5 转换为平方。每项间隔 500 ms。")
                    Button(onClick = { collectFlow() }, enabled = !running) { Text("开始收集") }
                    Button(onClick = { job?.cancel() }, enabled = running) { Text("取消收集") }
                    Button(onClick = { startActivity(Intent(this@FlowActivity, FlowCompareActivity::class.java)) }) { Text("SharedFlow / StateFlow 对比") }
                    SelectionContainer { Text(output) }
                }
            }
        }
    }
    /** 上游在 Default 运行，结果恢复主线程更新；异常展示，取消保留取消语义。 */
    private fun collectFlow() {
        running = true
        output = "开始收集\n"
        job = lifecycleScope.launch {
            try {
                flow { for (i in 1..5) { delay(500); emit(i) } }
                    .map { it * it }.flowOn(Dispatchers.Default)
                    .collect { output += "收到 $it\n" }
                output += "收集完成\n"
            } catch (e: CancellationException) { output += "收集取消\n"; throw e }
            catch (e: Exception) { output += "收集失败：${e.message}\n" }
            finally { running = false }
        }
    }

}
