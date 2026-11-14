package com.alex.studydemo.listdemo

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.alex.studydemo.module_view.ExperimentPage
import com.alex.studydemo.chat_tg.TgChatListItemAnimator

/** 使用真实 RecyclerView 和 TgChatListItemAnimator 观察插入、移除与内容更新动画。 */
class AnimListActivity : ComponentActivity() {
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: AnimListAdapter
    private val data = mutableListOf<String>()
    private var next = 1

    /** 提交不可变快照触发异步 Diff；提交完成后再滚动到新增项。 */
    private fun addItem() {
        data.add(0, "新项 ${next++}")
        adapter.submitList(data.toList()) { recycler.scrollToPosition(0) }
    }

    /** 空列表不执行删除，避免反复点击导致越界。 */
    private fun removeItem() {
        if (data.isNotEmpty()) {
            data.removeAt(0)
            adapter.submitList(data.toList())
        }
    }

    /** 保留字符串更新方式，以观察现有 Diff 的替换动画语义。 */
    private fun updateItem() {
        if (data.isNotEmpty()) {
            data[0] = data[0] + " ✓"
            adapter.submitList(data.toList())
        }
    }

    /** 数据与适配器在宿主创建时初始化，避免 Compose 重组重复填充数据。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = AnimListAdapter()
        seed()
        setContent {
            ExperimentPage("ItemAnimation 列表", { finish() }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(onClick = { addItem() }) { Text("添加") }
                    Button(onClick = { removeItem() }) { Text("移除") }
                    Button(onClick = { updateItem() }) { Text("更新") }
                }
                AndroidView(factory = { context ->
                    RecyclerView(context).also {
                        recycler = it
                        it.layoutManager = LinearLayoutManager(context)
                        it.adapter = adapter
                        it.itemAnimator = TgChatListItemAnimator()
                    }
                }, modifier = Modifier.weight(1f).fillMaxWidth())
            }
        }
    }

    /** 首次创建填充二十项，与原有实验数据一致。 */
    private fun seed() {
        repeat(20) {
            data.add("列表项 ${next++}")
        }
        adapter.submitList(data.toList())
    }

}
