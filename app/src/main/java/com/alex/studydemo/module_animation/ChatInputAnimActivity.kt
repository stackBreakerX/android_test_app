package com.alex.studydemo.module_animation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.module_view.ExperimentPage

/** 输入栏与操作栏共享固定底部区域，通过绘制位移切换，避免动画导致列表重新布局。 */
class ChatInputAnimActivity : ComponentActivity() {
    /** 220 毫秒曲线与原生 PathInterpolator 保持一致，快速切换由 Compose 从当前帧续接。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var showingInput by rememberSaveable { mutableStateOf(true) }
            var draft by rememberSaveable { mutableStateOf("") }
            var feedback by remember { mutableStateOf("") }
            val messages = remember { mutableStateListOf<String>().apply {
                addAll(List(20) { if (it % 2 == 0) "这是一条消息 $it" else "另一条消息 $it" })
            } }
            val animation = tween<Float>(220, easing = CubicBezierEasing(.2f, 0f, .2f, 1f))
            val slide = tween<androidx.compose.ui.unit.IntOffset>(220, easing = CubicBezierEasing(.2f, 0f, .2f, 1f))
            ExperimentPage("Chat 输入框动画", { finish() }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { showingInput = false }) { Text("显示操作栏") }
                    TextButton(onClick = { showingInput = true }) { Text("显示输入框") }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(messages.toList()) { Text(it, Modifier.fillMaxWidth().padding(16.dp)) }
                }
                if (feedback.isNotEmpty()) Text(feedback)
                // 两栏叠放于固定高度的容器，退场后移除语义及触摸目标，隐藏栏不会拦截输入。
                Box(Modifier.fillMaxWidth().height(88.dp)) {
                    androidx.compose.animation.AnimatedVisibility(showingInput, modifier = Modifier.align(Alignment.BottomCenter),
                        enter = slideInVertically(slide) { it } + fadeIn(animation),
                        exit = slideOutVertically(slide) { it } + fadeOut(animation)) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = draft, onValueChange = { draft = it }, singleLine = true,
                                placeholder = { Text("输入内容") }, modifier = Modifier.weight(1f))
                            Button(onClick = {
                                // 空草稿不生成消息，非空内容发送后清空，便于反复切换验证草稿状态。
                                if (draft.isNotBlank()) { messages.add(draft); draft = "" }
                            }) { Text("发送") }
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(!showingInput, modifier = Modifier.align(Alignment.BottomCenter),
                        enter = slideInVertically(slide) { it } + fadeIn(animation),
                        exit = slideOutVertically(slide) { it } + fadeOut(animation)) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Button(onClick = {
                                if (messages.isNotEmpty()) messages.removeAt(messages.lastIndex)
                                feedback = "已删除末条消息"
                            }) { Text("删除") }
                            Button(onClick = { feedback = messages.lastOrNull()?.let { "转发预览：$it" } ?: "暂无可转发消息" }) { Text("转发") }
                        }
                    }
                }
            }
        }
    }
}

/** 保留原生适配器公开类型供既有调用方使用；当前页面列表由 Compose 绘制。 */
class SimpleChatAdapter : RecyclerView.Adapter<SimpleChatAdapter.VH>() {
    private val data = List(20) { i -> if (i % 2 == 0) "这是一条消息 $i" else "另一条消息 $i" }
    /** 保留原生适配器的公开 ViewHolder 类型。 */
    class VH(val tv: android.widget.TextView) : RecyclerView.ViewHolder(tv)
    /** 创建可复用的原生消息条目，不在此处启动动画。 */
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val tv = android.view.LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false) as android.widget.TextView
        tv.textSize = 16f
        return VH(tv)
    }
    /** 每次绑定覆盖消息文字，避免复用时残留旧内容。 */
    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.tv.text = data[position]
    }
    /** 数量与当前消息集合一致，插入由页面发送通知。 */
    override fun getItemCount(): Int = data.size
}
