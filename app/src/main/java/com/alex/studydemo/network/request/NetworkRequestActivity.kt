package com.alex.studydemo.network.request

import com.alex.studydemo.ui.StudyPage
import com.alex.studydemo.ui.StudyTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
        setContent {
            StudyTheme {
                StudyPage("网络请求", onBack = { finish() }) {
                    OutlinedTextField(url, { url = it }, label = { Text("GET URL") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { doRequest() }, enabled = !loading) { Text("发送请求") }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    SelectionContainer { Text(result) }
                }
            }
        }
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

}
