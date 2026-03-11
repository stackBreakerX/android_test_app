package com.alex.studydemo.listdemo

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.chat_tg.TgChatListItemAnimator
import com.alex.studydemo.databinding.ActivityAnimListBinding

class AnimListActivity : BaseActivity<ActivityAnimListBinding>() {
    private lateinit var adapter: AnimListAdapter
    private val data = mutableListOf<String>()
    private var next = 1

    private fun addItem() {
        data.add(0, "新项 ${next++}")
        adapter.submitList(data.toList())
        binding.recycler.scrollToPosition(0)
    }

    private fun removeItem() {
        if (data.isNotEmpty()) {
            data.removeAt(0)
            adapter.submitList(data.toList())
        }
    }

    private fun updateItem() {
        if (data.isNotEmpty()) {
            data[0] = data[0] + " ✓"
            adapter.submitList(data.toList())
        }
    }

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityAnimListBinding =
        ActivityAnimListBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        title = "ItemAnimation 列表"
        adapter = AnimListAdapter()
        binding.recycler.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        binding.recycler.adapter = adapter
        binding.recycler.itemAnimator = TgChatListItemAnimator()
        seed()
    }

    private fun seed() {
        repeat(20) {
            data.add("列表项 ${next++}")
        }
        adapter.submitList(data.toList())
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(com.alex.studydemo.R.menu.menu_anim_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            com.alex.studydemo.R.id.action_add -> addItem()
            com.alex.studydemo.R.id.action_remove -> removeItem()
            com.alex.studydemo.R.id.action_update -> updateItem()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
}
