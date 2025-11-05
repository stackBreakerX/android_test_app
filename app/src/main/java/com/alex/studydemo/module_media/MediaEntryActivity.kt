package com.alex.studydemo.module_media

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.databinding.ActivityMediaEntryBinding
import com.alex.studydemo.databinding.ItemMainEntryBinding

class MediaEntryActivity : BaseActivity<ActivityMediaEntryBinding>() {

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityMediaEntryBinding =
        ActivityMediaEntryBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        supportActionBar?.title = "多媒体入库"

        val recycler = binding.recyclerMediaEntry
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = EntryAdapter(buildEntries())
    }

    private data class EntryItem(
        val title: String,
        val onClick: (View) -> Unit
    )

    private fun buildEntries(): List<EntryItem> = listOf(
        EntryItem("WebP 转换") { startActivity(Intent(this, WebpConvertActivity::class.java)) },
        EntryItem("libwebp 转换") { startActivity(Intent(this, WebpLibwebpActivity::class.java)) },
        EntryItem("PNG 转换（非JPG/PNG）") { startActivity(Intent(this, PngConvertActivity::class.java)) },
        EntryItem("JPG 转换（非JPG/PNG）") { startActivity(Intent(this, JpgConvertActivity::class.java)) },
        EntryItem("PNG 透明通道检测") { startActivity(Intent(this, PngAlphaCheckActivity::class.java)) },
        EntryItem("图片选择器") { startActivity(Intent(this, com.alex.studydemo.module_image.ImagePickerActivity::class.java)) },
        EntryItem("快速缩略图") { startActivity(Intent(this, com.alex.studydemo.module_image.FastThumbnailActivity::class.java)) },
        EntryItem("DNG处理转WebP") { startActivity(Intent(this, com.alex.studydemo.module_image.DngProcessActivity::class.java)) },
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