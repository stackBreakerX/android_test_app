package com.alex.studydemo.module_animation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alex.studydemo.listdemo.AnimListActivity
import com.alex.studydemo.module_view.ExperimentPage

/** 动画专题入口，保留列表动画与输入栏动画的独立 Activity。 */
class AnimationsEntryActivity : ComponentActivity() {
    /** 标题与点击动作组成的入口契约，保留原有公开类型。 */
    data class Entry(val title: String, val action: () -> Unit)

    /** 入口没有异步加载，点击直接启动对应实验页。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val entries = listOf(
            Entry("ItemAnimation 列表") { startActivity(Intent(this, AnimListActivity::class.java)) },
            Entry("Chat 输入框动画") { startActivity(Intent(this, ChatInputAnimActivity::class.java)) }
        )
        setContent {
            ExperimentPage("Animation Entry", { finish() }) {
                entries.forEach { entry ->
                    OutlinedCard(onClick = entry.action, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(entry.title, Modifier.padding(20.dp))
                    }
                }
            }
        }
    }
}
