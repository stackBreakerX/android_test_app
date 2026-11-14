package com.alex.studydemo.network.request

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
import android.content.Context
import android.content.Intent
import com.alex.studydemo.network.monitor.NetworkMonitorActivity

/** 网络专题入口，分别启动真实请求和系统网络监控。 */
class NetworkEntryActivity : ComponentActivity() {
    companion object {
        /** 保留已有显式 Intent 入口。 */
        fun newInstance(context: Context) {
            context.startActivity(Intent(context, NetworkEntryActivity::class.java))
        }
    }
    /** 导航不持有子页面状态，返回后仍停留在专题入口。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Page("网络实验") {
            Text("OkHttp 请求、拦截器与系统网络状态")
            Button(onClick = { startActivity(Intent(this@NetworkEntryActivity, NetworkRequestActivity::class.java)) }) { Text("网络请求") }
            Button(onClick = { startActivity(Intent(this@NetworkEntryActivity, NetworkMonitorActivity::class.java)) }) { Text("网络监控") }
        } }
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
