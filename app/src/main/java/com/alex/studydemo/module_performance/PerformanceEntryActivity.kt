package com.alex.studydemo.module_performance

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.databinding.ActivityPerformanceEntryBinding
import com.alex.studydemo.databinding.ItemMainEntryBinding

class PerformanceEntryActivity : BaseActivity<ActivityPerformanceEntryBinding>() {

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityPerformanceEntryBinding =
        ActivityPerformanceEntryBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        supportActionBar?.title = "性能入口"

        val recycler = binding.recyclerPerformanceEntry
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = EntryAdapter(buildEntries())
    }

    private data class EntryItem(
        val title: String,
        val onClick: (View) -> Unit
    )

    private fun buildEntries(): List<EntryItem> = listOf(
        EntryItem("JankStats 卡顿检测") { startActivity(Intent(this, JankStatsActivity::class.java)) },
    )

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

