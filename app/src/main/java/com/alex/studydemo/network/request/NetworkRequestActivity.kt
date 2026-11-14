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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 真实执行 OkHttp 请求；加载、错误和响应均由 Compose 状态展示。 */
class NetworkRequestActivity : ComponentActivity() {
    private var loading by mutableStateOf(false)
    private var result by mutableStateOf("")
    private var url by mutableStateOf("https://postman-echo.com/get?foo=bar")

    /** 保留进入页面立即请求的行为；手动请求期间禁用重复提交。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Page("网络请求") {
            OutlinedTextField(url, { url = it }, label = { Text("GET URL") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { doRequest() }, enabled = !loading) { Text("发送请求") }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            SelectionContainer { Text(result) }
        } }
        doRequest()
    }

    /** URL 在点击时快照；取消继续向上传播，普通失败展示后允许重试。 */
    private fun doRequest() {
        if (loading) return
        val requestUrl = url
        loading = true
        result = ""
        lifecycleScope.launch {
            try { result = NetworkClient.get(requestUrl) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { result = "请求失败：${e.message}" }
            finally { loading = false }
        }
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
