package com.alex.studydemo.hilt

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

/** 展示现有 Driver 与 ElectricEngine 的手动依赖组装；项目未启用 Hilt 插件，不声称框架注入。 */
class HiltMainActivity : ComponentActivity() {
    private val driver = Driver()
    private val engine = ElectricEngine()
    private var started by mutableStateOf(false)
    private var output by mutableStateOf("")
    companion object {
        /** 保留历史页面入口，Manifest 注册由集成层决定。 */
        fun newInstance(context: Context) { context.startActivity(Intent(context, HiltMainActivity::class.java)) }
    }
    /** 仅在点击时执行真实引擎接口，重组不会重复调用 start 或 shutdown。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Page("依赖组装实验") {
            Text("当前构建未启用 Hilt；这里使用手动构造的依赖。")
            Text("Driver：${driver.driverName()}")
            Button(enabled = !started, onClick = { engine.start(); started = true; output = "ElectricEngine 已启动，司机 ${driver.driverName()}" }) { Text("启动引擎") }
            Button(enabled = started, onClick = { engine.shutdown(); started = false; output = "ElectricEngine 已关闭" }) { Text("关闭引擎") }
            Text(output)
        } }
    }
    /** 页面销毁时关闭仍在运行的演示引擎。 */
    override fun onDestroy() {
        if (started) engine.shutdown()
        super.onDestroy()
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
