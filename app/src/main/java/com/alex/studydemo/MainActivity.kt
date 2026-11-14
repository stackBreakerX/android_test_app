package com.alex.studydemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.alex.studydemo.ui.LearningApp

/** 应用入口只负责挂载 Compose；实验状态由独立页面持有，工作台保留自己的导航位置。 */
class MainActivity : ComponentActivity() {
    /** 边到边窗口由 Scaffold 消费安全区，避免状态栏和底部导航遮挡内容。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LearningApp() }
    }
}
