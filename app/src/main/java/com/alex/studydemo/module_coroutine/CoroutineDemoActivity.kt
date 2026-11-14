package com.alex.studydemo.module_coroutine

import com.alex.studydemo.ui.StudyPage
import com.alex.studydemo.ui.StudyTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.content.Context
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import com.alibaba.android.arouter.facade.annotation.Route
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** 展示 async 并行合并与生命周期 Flow，保留历史路径和结果码入口。 */
@Route(path = CoroutineDemoActivity.PATH)
class CoroutineDemoActivity : ComponentActivity() {
    private var output by mutableStateOf("")
    private var job: Job? = null
    companion object {
        const val PATH = "/second/coroutine"
        /** 保留显式 Intent 入口，Manifest 是否开放由集成层配置。 */
        fun newInstance(context: Context) { context.startActivity(Intent(context, CoroutineDemoActivity::class.java)) }
    }
    /** 重复点击先取消上次实验，避免不同运行的结果混在一起。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StudyTheme {
                StudyPage("协程实验", onBack = { finish() }) {
                    Button(onClick = {
                        job?.cancel()
                        job = lifecycleScope.launch {
                            val first = async { delay(1000); 1 }
                            val second = async { delay(100); 2 }
                            output = "async 合并：${first.await() + second.await()}\n"
                            flowTest()
                        }
                    }) { Text("运行 async 与 Flow") }
                    Button(onClick = { job?.cancel() }) { Text("取消") }
                    Button(onClick = { setResult(100); finish() }) { Text("返回结果 100") }
                    Text(output)
                }
            }
        }
    }
    /** 保留公开挂起 API；仅在 STARTED 以上收集，销毁或调用方取消时退出。 */
    suspend fun flowTest() {
        flow { for (i in 1..5) { delay(100); emit(i) } }
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .collect { output += "生命周期 Flow：$it\n" }
    }

}
