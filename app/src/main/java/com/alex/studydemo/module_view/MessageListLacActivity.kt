package com.alex.studydemo.module_view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.LayoutAnimationController
import android.view.animation.DecelerateInterpolator
import android.widget.BaseAdapter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ListView
import com.alex.studydemo.R
import com.alex.studydemo.databinding.ItemMessageBinding

/** 保留 ListView 可见子项的位移动画实验，外部布局和添加按钮使用 Compose。 */
class MessageListLacActivity : ComponentActivity() {
    private lateinit var listView: ListView

    companion object {
        /** 保留外部页面的启动 API。 */
        fun newInstance(context: Context) {
            val intent = Intent(context, MessageListLacActivity::class.java)
            context.startActivity(intent)
        }
    }

    private val messages = mutableListOf<String>()
    private lateinit var adapter: MsgAdapter
    private var index = 1

    /** 原生区域仅包含 ListView，添加后在布局完成时计算实际条目高度。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = MsgAdapter(messages)
        setContent {
            ExperimentPage("ListView 消息位移", { finish() }) {
                AndroidView(factory = { context -> ListView(context).also {
                    listView = it
                    it.adapter = adapter
                    it.isStackFromBottom = true
                    it.divider = null
                    it.dividerHeight = dp(8f)
                    it.layoutAnimation = null
                } }, modifier = Modifier.weight(1f).fillMaxWidth())
                Button(onClick = { addMessage() }, modifier = Modifier.fillMaxWidth()) { Text("添加消息") }
            }
        }
    }

    /** 新消息不参与位移；已有可见项按新消息高度上移，再在 160 毫秒内归位。 */
    private fun addMessage() {
        messages.add("消息 ${index++}")
        adapter.notifyDataSetChanged()
        listView.setSelection(messages.lastIndex)
        listView.post {
            // 等待本轮布局后读取子项，尚无可见条目时使用 40dp 作为位移回退值。
            if (isDestroyed || !listView.isAttachedToWindow) return@post
            val last = listView.getChildAt(listView.childCount - 1)
            val height = (last?.height ?: dp(40f)).toFloat()
            for (i in 0 until listView.childCount - 1) {
                listView.getChildAt(i).apply {
                    animate().cancel()
                    translationY = -height
                    animate().translationY(0f).setDuration(160).setInterpolator(DecelerateInterpolator()).start()
                }
            }
        }
    }

    /** 将逻辑像素转换为原生动画需要的物理像素。 */
    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()

    /** 复用原生消息条目；只绑定文字，不接管列表位移。 */
    private class MsgAdapter(private val items: List<String>) : BaseAdapter() {
        /** 返回消息数量，供 ListView 计算可见范围。 */
        override fun getCount(): Int = items.size
        /** 按列表位置读取消息，位置由 ListView 保证有效。 */
        override fun getItem(position: Int): Any = items[position]
        /** 此实验按位置提供 ID，不启用稳定身份。 */
        override fun getItemId(position: Int): Long = position.toLong()
        /** 复用现有条目并覆盖文字；子项位移动画由页面统一控制。 */
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val binding = if (convertView == null) {
                ItemMessageBinding.inflate(android.view.LayoutInflater.from(parent.context), parent, false)
            } else {
                ItemMessageBinding.bind(convertView)
            }
            binding.tvMsg.text = items[position]
            return binding.root
        }
    }
}
