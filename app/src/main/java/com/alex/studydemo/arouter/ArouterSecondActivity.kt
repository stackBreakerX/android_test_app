package com.alex.studydemo.arouter

import com.alex.studydemo.ui.StudyPage
import com.alex.studydemo.ui.StudyTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.alibaba.android.arouter.facade.annotation.Route

/** 保留路由路径；从 Intent 读取 ARouter 的参数，不依赖额外的字段注入。 */
@Route(path = ArouterSecondActivity.PATH)
class ArouterSecondActivity : ComponentActivity() {
    companion object { const val PATH = "/second/secondActivity" }
    /** 未提供 test 时明确展示空参数，返回结束当前页面。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val value = intent.getStringExtra("test").orEmpty()
        setContent {
            StudyTheme {
                StudyPage("ARouter 第二页", onBack = { finish() }) {
                    Text("路由：$PATH")
                    SelectionContainer { Text("test：${value.ifEmpty { "（未传入）" }}") }
                }
            }
        }
    }

}
