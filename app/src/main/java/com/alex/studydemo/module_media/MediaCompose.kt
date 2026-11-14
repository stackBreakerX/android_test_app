package com.alex.studydemo.module_media

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/** 媒体页的可观察输出；任务属于 Activity，销毁时取消，不跨页面保存大批转换日志。 */
internal class MediaOutput {
    var text by mutableStateOf("结果会显示在这里")
    var running by mutableStateOf(false)
        private set

    /** 防止重复点击并发改写同一份日志；阻塞编解码在调用方指定的 IO 上执行。 */
    fun launch(owner: ComponentActivity, context: CoroutineContext = Dispatchers.Main, block: suspend CoroutineScope.() -> Unit) {
        if (running) return
        running = true
        owner.lifecycleScope.launch {
            try {
                withContext(context, block)
            } catch (cancelled: CancellationException) {
                // 页面关闭时继续传播取消，不把正常退出显示成转换错误。
                throw cancelled
            } catch (error: Exception) {
                text += "\n处理失败：${error.message}"
            } finally {
                running = false
            }
        }
    }
}

/** 独立页面使用自包含主题和安全区；返回按钮与系统返回均结束当前 Activity。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    com.alex.studydemo.ui.StudyTheme {
        Scaffold(topBar = {
            TopAppBar(title = { Text(title) }, navigationIcon = {
                TextButton(onClick = onBack) { Text("返回") }
            })
        }) { insets ->
            Column(
                Modifier.fillMaxSize().padding(insets).imePadding()
                    .verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content
            )
        }
    }
}

/** 日志允许复制 URI、耗时和识别文字；长内容随页面滚动，不截断输出。 */
@Composable
internal fun MediaResult(text: String) {
    SelectionContainer {
        Text(text, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp))
    }
}

/** 使用按下/释放事件实现持续录音；手势被系统取消或移出页面也必须停止采集。 */
@Composable
internal fun MediaHoldToRecord(label: String, onStart: () -> Unit, onStop: () -> Unit) {
    // 回调随重组更新，但手势协程不能因识别状态刷新而重启。
    val currentStart by rememberUpdatedState(onStart)
    val currentStop by rememberUpdatedState(onStop)
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    currentStart()
                    try { tryAwaitRelease() } finally { currentStop() }
                })
            }
    ) {
        Text(label, Modifier.padding(24.dp))
    }
}
