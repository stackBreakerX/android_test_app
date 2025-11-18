package com.alex.studydemo.module_thread

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.databinding.ActivityThreadEntryBinding
import com.alex.studydemo.databinding.ItemMainEntryBinding
import com.alibaba.android.arouter.facade.annotation.Route

@Route(path = ThreadEntryActivity.PATH)
class ThreadEntryActivity : BaseActivity<ActivityThreadEntryBinding>() {

    companion object {
        const val PATH = "/thread/summary"
    }

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityThreadEntryBinding =
        ActivityThreadEntryBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        supportActionBar?.title = "多线程模块汇总"

        val recycler = binding.recyclerThreadEntry
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = EntryAdapter(buildEntries())
    }

    private data class EntryItem(
        val title: String,
        val onClick: (View) -> Unit
    )

    private fun buildEntries(): List<EntryItem> = listOf(
        EntryItem("Thread 创建与启动") { showTodo("Thread 创建与启动") },
        EntryItem("HandlerThread 与 Looper") { showTodo("HandlerThread 与 Looper") },
        EntryItem("Executors 线程池") { showTodo("Executors 线程池") },
        EntryItem("锁与同步示例") { showTodo("锁与同步示例") },
        EntryItem("异步任务与回调") { showTodo("异步任务与回调") }
    )

    private fun showTodo(feature: String) {
        Toast.makeText(this, "即将实现：$feature", Toast.LENGTH_SHORT).show()
    }

    private inner class EntryAdapter(
        private val items: List<EntryItem>
    ) : RecyclerView.Adapter<EntryAdapter.VH>() {

        inner class VH(val binding: ItemMainEntryBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemMainEntryBinding.inflate(layoutInflater, parent, false)
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.binding.btnEntry.text = item.title
            holder.binding.btnEntry.setOnClickListener { v -> item.onClick(v) }
        }

        override fun getItemCount(): Int = items.size
    }
}