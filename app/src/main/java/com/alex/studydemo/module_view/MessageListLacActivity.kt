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
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.R
import com.alex.studydemo.databinding.ActivityMessageListLacBinding
import com.alex.studydemo.databinding.ItemMessageBinding

class MessageListLacActivity : BaseActivity<ActivityMessageListLacBinding>() {

    companion object {
        fun newInstance(context: Context) {
            val intent = Intent(context, MessageListLacActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityMessageListLacBinding =
        ActivityMessageListLacBinding.inflate(inflater)

    private val messages = mutableListOf<String>()
    private lateinit var adapter: MsgAdapter
    private var index = 1

    override fun onViewCreated(savedInstanceState: Bundle?) {
        adapter = MsgAdapter(messages)
        binding.listView.adapter = adapter

        binding.listView.layoutAnimation = null

        binding.btnAdd.setOnClickListener {
            messages.add("消息 $index")
            adapter.notifyDataSetChanged()
            binding.listView.setSelection(messages.size - 1)
            binding.listView.post {
                val last = binding.listView.getChildAt(binding.listView.childCount - 1)
                val h = (last?.height ?: dp(40f)).toFloat()
                for (i in 0 until binding.listView.childCount - 1) {
                    val c = binding.listView.getChildAt(i)
                    c.translationY = -h
                    c.animate().translationY(0f).setDuration(160).setInterpolator(DecelerateInterpolator()).start()
                }
            }
            index++
        }
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()

    private class MsgAdapter(private val items: List<String>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()
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
