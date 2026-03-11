package com.alex.studydemo.module_animation

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.databinding.ActivityAnimationsEntryBinding
import com.alex.studydemo.listdemo.AnimListActivity

class AnimationsEntryActivity : BaseActivity<ActivityAnimationsEntryBinding>() {

    data class Entry(val title: String, val action: () -> Unit)

    private val items = mutableListOf<Entry>()

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityAnimationsEntryBinding =
        ActivityAnimationsEntryBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        title = "Animation Entry"
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = EntryAdapter(items)
        buildEntries()
    }

    private fun buildEntries() {
        items.clear()
        items.add(
            Entry("ItemAnimation 列表") {
                startActivity(Intent(this, AnimListActivity::class.java))
            }
        )
        items.add(
            Entry("Chat 输入框动画") {
                startActivity(Intent(this, ChatInputAnimActivity::class.java))
            }
        )
        (binding.recycler.adapter as EntryAdapter).submit(items.toList())
    }

    private class EntryAdapter(private var data: List<Entry>) :
        RecyclerView.Adapter<EntryAdapter.VH>() {
        class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false) as TextView
            tv.textSize = 16f
            tv.setPadding(tv.paddingLeft, (tv.paddingTop + 8), tv.paddingRight, (tv.paddingBottom + 8))
            return VH(tv)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = data[position]
            holder.tv.text = item.title
            holder.tv.setOnClickListener { item.action.invoke() }
        }
        override fun getItemCount(): Int = data.size
        fun submit(newData: List<Entry>) {
            data = newData
            notifyDataSetChanged()
        }
    }
}
