package com.alex.studydemo.module_view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.databinding.ActivityMessageListBinding
import com.alex.studydemo.databinding.ItemMessageBinding

class MessageListActivity : BaseActivity<ActivityMessageListBinding>() {

    companion object {
        fun newInstance(context: Context) {
            val intent = Intent(context, MessageListActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityMessageListBinding =
        ActivityMessageListBinding.inflate(inflater)

    private val messages = mutableListOf<String>()
    private lateinit var adapter: MsgAdapter
    private var index = 1

    override fun onViewCreated(savedInstanceState: Bundle?) {
        val lm = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.recyclerView.layoutManager = lm
        binding.recyclerView.itemAnimator = null
        val baseAnim = android.view.animation.AnimationUtils.loadAnimation(this, com.alex.studydemo.R.anim.item_up_squeeze)
        val controller = android.view.animation.LayoutAnimationController(baseAnim, 0f).apply {
            order = android.view.animation.LayoutAnimationController.ORDER_NORMAL
        }
        binding.recyclerView.layoutAnimation = controller
        adapter = MsgAdapter(messages)
        binding.recyclerView.adapter = adapter

        binding.sendButton.setOnClickListener {
            messages.add("消息 $index")
            adapter.notifyItemInserted(messages.size - 1)
            binding.recyclerView.scheduleLayoutAnimation()
            binding.recyclerView.scrollToPosition(messages.size - 1)
            index++
        }
    }

    private class MsgAdapter(private val items: List<String>) : RecyclerView.Adapter<VH>() {
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val binding = ItemMessageBinding.inflate(android.view.LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.binding.tvMsg.text = items[position]
        }

        override fun getItemCount(): Int = items.size
    }

    private class VH(val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root)
}
