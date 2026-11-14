package com.alex.studydemo.module_view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** 保留 ConstructorClass 的 Canvas 文字绘制实验，页面布局由 Compose 管理。 */
class CustomerViewActivity : ComponentActivity() {
    companion object {
        /** 保留既有入口；由调用方提供可启动 Activity 的 Context。 */
        fun newInstance(context: Context) { context.startActivity(Intent(context, CustomerViewActivity::class.java)) }
    }
    /** 原生桥接仅创建待研究的自定义文字 View，不加载页面 XML。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExperimentPage("自定义文字绘制", { finish() }) {
                Text("观察 ConstructorClass 的文字居中与基线绘制")
                AndroidView(factory = { ConstructorClass(it).apply { text = "发记录卡啥的姐夫" } },
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp).height(80.dp))
            }
        }
    }
}
