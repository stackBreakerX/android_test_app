package com.alex.studydemo.arouter

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
import com.alibaba.android.arouter.facade.annotation.Route

/** 保留路由路径；从 Intent 读取 ARouter 的参数，不依赖额外的字段注入。 */
@Route(path = ArouterSecondActivity.PATH)
class ArouterSecondActivity : ComponentActivity() {
    companion object { const val PATH = "/second/secondActivity" }
    /** 未提供 test 时明确展示空参数，返回结束当前页面。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val value = intent.getStringExtra("test").orEmpty()
        setContent { Page("ARouter 第二页") {
            Text("路由：$PATH")
            SelectionContainer { Text("test：${value.ifEmpty { "（未传入）" }}") }
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
