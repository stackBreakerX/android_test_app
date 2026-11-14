package com.alex.studydemo.hilt

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
        setContent {
            StudyTheme {
                StudyPage("依赖组装实验", onBack = { finish() }) {
                    Text("当前构建未启用 Hilt；这里使用手动构造的依赖。")
                    Text("Driver：${driver.driverName()}")
                    Button(enabled = !started, onClick = { engine.start(); started = true; output = "ElectricEngine 已启动，司机 ${driver.driverName()}" }) { Text("启动引擎") }
                    Button(enabled = started, onClick = { engine.shutdown(); started = false; output = "ElectricEngine 已关闭" }) { Text("关闭引擎") }
                    Text(output)
                }
            }
        }
    }
    /** 页面销毁时关闭仍在运行的演示引擎。 */
    override fun onDestroy() {
        if (started) engine.shutdown()
        super.onDestroy()
    }

}
