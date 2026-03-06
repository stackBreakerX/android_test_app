package com.alex.studydemo.listdemo

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.chat_tg.TgChatListItemAnimator
import com.alex.studydemo.databinding.ActivityAnimListBinding

class AnimListActivity : BaseActivity<ActivityAnimListBinding>() {
    private lateinit var adapter: AnimListAdapter
    private val data = mutableListOf<String>()
    private var next = 1

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityAnimListBinding =
        ActivityAnimListBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        title = "ItemAnimation 列表"
        adapter = AnimListAdapter()
        binding.recycler.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        binding.recycler.adapter = adapter
        binding.recycler.itemAnimator = TgChatListItemAnimator()
        seed()
        binding.addBtn.setOnClickListener {
            data.add(0, "新项 ${next++}")
            adapter.submitList(data.toList())
            binding.recycler.scrollToPosition(0)
        }
        binding.removeBtn.setOnClickListener {
            if (data.isNotEmpty()) {
                data.removeAt(0)
                adapter.submitList(data.toList())
            }
        }
        binding.updateBtn.setOnClickListener {
            if (data.isNotEmpty()) {
                data[0] = data[0] + " ✓"
                adapter.submitList(data.toList())
            }
        }
    }

    private fun seed() {
        repeat(20) {
            data.add("列表项 ${next++}")
        }
        adapter.submitList(data.toList())
    }
}
