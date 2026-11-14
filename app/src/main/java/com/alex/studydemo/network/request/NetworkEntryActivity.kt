package com.alex.studydemo.network.request

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
        setContent {
            StudyTheme {
                StudyPage("网络实验", onBack = { finish() }) {
                    Text("OkHttp 请求、拦截器与系统网络状态")
                    Button(onClick = { startActivity(Intent(this@NetworkEntryActivity, NetworkRequestActivity::class.java)) }) { Text("网络请求") }
                    Button(onClick = { startActivity(Intent(this@NetworkEntryActivity, NetworkMonitorActivity::class.java)) }) { Text("网络监控") }
                }
            }
        }
    }

}
