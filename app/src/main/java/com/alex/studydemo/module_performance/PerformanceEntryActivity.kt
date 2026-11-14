package com.alex.studydemo.module_performance

import com.alex.studydemo.ui.StudyPage
import com.alex.studydemo.ui.StudyTheme
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 保留旧性能入口的 Activity 身份，页面内容和导航均由 Compose 提供。 */
class PerformanceEntryActivity : ComponentActivity() {
    /** 独立进入时仍可返回调用方，不另建一份应用工作台。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StudyTheme {
                StudyPage("性能实验室", onBack = ::finish,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Card(onClick = { startActivity(Intent(this@PerformanceEntryActivity, JankStatsActivity::class.java)) }) {
                            Column(Modifier.padding(24.dp)) {
                                Text("帧率观测", style = MaterialTheme.typography.titleLarge)
                                Text("观察 JankStats 帧耗时并主动模拟卡顿。")
                            }
                        }
                    }
            }
        }
    }
}
