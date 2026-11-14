package com.alex.studydemo.module_view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.alex.studydemo.databinding.ItemMessageBinding

/** 保留 LayoutAnimationController 的消息上挤动画，输入及发送在 Compose 中呈现。 */
class MessageListActivity : ComponentActivity() {

    companion object {
        /** 保留外部页面的启动 API。 */
        fun newInstance(context: Context) {
            val intent = Intent(context, MessageListActivity::class.java)
            context.startActivity(intent)
        }
    }

    private val messages = mutableListOf<String>()
    private lateinit var adapter: MsgAdapter
    private var index = 1

    /** 列表实例只在 AndroidView 的 factory 中配置，重组不会重启动画。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = MsgAdapter(messages)
        setContent {
            var draft by rememberSaveable { mutableStateOf("") }
            var recycler by remember { mutableStateOf<RecyclerView?>(null) }
            ExperimentPage("消息上挤动画", { finish() }) {
                AndroidView(factory = { context ->
                    RecyclerView(context).apply {
                        layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
                        itemAnimator = null
                        layoutAnimation = android.view.animation.LayoutAnimationController(
                            android.view.animation.AnimationUtils.loadAnimation(context, com.alex.studydemo.R.anim.item_up_squeeze), 0f)
                        adapter = this@MessageListActivity.adapter
                        recycler = this
                    }
                }, modifier = Modifier.weight(1f).fillMaxWidth())
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = draft, onValueChange = { draft = it }, label = { Text("输入消息") }, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        // 空输入沿用自动编号消息，非空输入用于观察不同高度的条目动画。
                        messages.add(draft.ifBlank { "消息 $index" })
                        index++
                        draft = ""
                        adapter.notifyItemInserted(messages.lastIndex)
                        recycler?.scheduleLayoutAnimation()
                        recycler?.scrollToPosition(messages.lastIndex)
                    }) { Text("发送") }
                }
            }
        }
    }

    /** 仅负责原生消息条目的绑定，动画由列表布局控制器驱动。 */
    private class MsgAdapter(private val items: List<String>) : RecyclerView.Adapter<VH>() {
        /** 创建可复用的原生消息条目，不在此处启动动画。 */
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val binding = ItemMessageBinding.inflate(android.view.LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        /** 每次绑定覆盖消息文字，避免复用时残留旧内容。 */
        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.binding.tvMsg.text = items[position]
        }

        /** 数量与当前消息集合一致，插入由页面发送通知。 */
        override fun getItemCount(): Int = items.size
    }

    /** 保存消息条目的绑定对象，供列表复用。 */
    private class VH(val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root)
}
