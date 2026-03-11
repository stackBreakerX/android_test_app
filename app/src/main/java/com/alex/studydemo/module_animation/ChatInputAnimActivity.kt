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
    private var hidden = false
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
            com.alex.studydemo.R.id.action_toggle_input -> {
                toggleInputBar()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleInputBar() {
        val bar = binding.inputBar
        bar.post {
            val distance = bar.height + binding.root.paddingBottom
            if (!hidden) {
                bar.animate()
                    .translationY(distance.toFloat())
                    .alpha(0f)
                    .setDuration(220L)
                    .setInterpolator(interpolator)
                    .start()
            } else {
                bar.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(220L)
                    .setInterpolator(interpolator)
                    .start()
            }
            hidden = !hidden
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
