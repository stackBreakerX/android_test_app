package com.alex.studydemo.module_animation

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.animation.PathInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.databinding.ActivityChatInputAnimBinding

class ChatInputAnimActivity : BaseActivity<ActivityChatInputAnimBinding>() {
    private var showingInput = true
    private val interpolator = PathInterpolator(0.2f, 0f, 0.2f, 1f)

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityChatInputAnimBinding =
        ActivityChatInputAnimBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        title = "Chat 输入框动画"
        binding.recycler.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        binding.recycler.adapter = SimpleChatAdapter()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(com.alex.studydemo.R.menu.menu_chat_input_anim, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            com.alex.studydemo.R.id.action_show_actions -> {
                showActionsHideInput()
                true
            }
            com.alex.studydemo.R.id.action_show_input -> {
                showInputHideActions()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showActionsHideInput() {
        val input = binding.inputBar
        val actions = binding.actionBar
        input.post {
            /*
             translationY 说明：
             - 仅影响绘制阶段的垂直位移（y = top + translationY），不触发重新测量/布局，列表不抖动。
             - 优于修改 margin/padding 或 y：保持原始布局坐标稳定，动画友好。
             
             为什么使用 distance = input.height + root.paddingBottom：
             - 下移至少 input.height，才能让输入栏完全越过父容器底边界（顶部刚好到达底边以下）。
             - 再加上父容器的底部内边距 paddingBottom（视图留白/安全区），避免在留白区域残留可见/可点击部分。
             - 合计位移精确覆盖可见垂直空间，确保“彻底隐藏”，同时不改变布局尺寸。
             
             出现/消失的基准值：
             - 出现：translationY = 0。因为 y = top + 0，视图回到布局计算出的正常位置（底部对齐），同时 alpha 0→1 淡入。
             - 消失：translationY = distance（height + paddingBottom）。确保跨过父容器底边和其底部留白，完全滑出可见区域，alpha 1→0 淡出。
             
             操作栏与输入框的协作：
             - 显示操作栏：actions 先设置为可见，并放在 distance 位置且透明（translationY=distance, alpha=0），再动画到 0 和 alpha=1；
               同时输入框动画到 distance/alpha=0，形成对向联动。
             - 隐藏操作栏：反向处理，actions 动画到 distance/alpha=0 并在结束回调设为 GONE，避免拦截触摸；输入框动画到 0/alpha=1。
             
             工程效果：
             - 布局稳定、无回流；位移量与父容器真实可见空间耦合，适配不同设备安全区。
             
             如何让两者“同时做动画”：
             - 放在同一个 choreographer 帧启动：在 post{} 内同时调用两个 ViewPropertyAnimator.start()，它们共用同一插值器与时长；
             - 为“出现”的一方先设置初始状态（translationY=distance, alpha=0），再与另一方一起 start()；
             - 不必使用 AnimatorSet：两个独立的 ViewPropertyAnimator 在同一帧开始即可同步；如需更复杂编排可改用 AnimatorSet.playTogether()。
             */
            val distance = input.height + binding.root.paddingBottom
            // 输入框向下淡出
            input.animate()
                .translationY(distance.toFloat())
                .alpha(0f)
                .setDuration(220L)
                .setInterpolator(interpolator)
                .start()
            // 操作栏准备出现：先放到下方并透明
            actions.visibility = android.view.View.VISIBLE
            actions.translationY = distance.toFloat()
            actions.alpha = 0f
            // 操作栏向上淡入
            actions.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(220L)
                .setInterpolator(interpolator)
                .start()
            showingInput = false
        }
    }

    private fun showInputHideActions() {
        val input = binding.inputBar
        val actions = binding.actionBar
        input.post {
            // 当操作栏尚未测量（height=0）时，回退到“输入栏高度 + 父容器底部内边距”的总位移，保证淡出彻底
            val distance = actions.height.takeIf { it > 0 } ?: (input.height + binding.root.paddingBottom)
            // 输入框向上淡入
            input.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(220L)
                .setInterpolator(interpolator)
                .start()
            // 操作栏向下淡出
            actions.animate()
                .translationY(distance.toFloat())
                .alpha(0f)
                .setDuration(220L)
                .setInterpolator(interpolator)
                .withEndAction {
                    actions.visibility = android.view.View.GONE
                }
                .start()
            showingInput = true
        }
    }
}

class SimpleChatAdapter : RecyclerView.Adapter<SimpleChatAdapter.VH>() {
    private val data = List(20) { i -> if (i % 2 == 0) "这是一条消息 $i" else "另一条消息 $i" }
    class VH(val tv: android.widget.TextView) : RecyclerView.ViewHolder(tv)
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val tv = android.view.LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false) as android.widget.TextView
        tv.textSize = 16f
        return VH(tv)
    }
    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.tv.text = data[position]
    }
    override fun getItemCount(): Int = data.size
}
