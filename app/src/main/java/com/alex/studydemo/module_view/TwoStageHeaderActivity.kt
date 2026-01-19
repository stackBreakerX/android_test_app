package com.alex.studydemo.module_view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.R
import com.alex.studydemo.databinding.ActivityTwoStageHeaderBinding
import com.alex.studydemo.databinding.ItemTextViewBinding
import kotlin.math.max
import kotlin.math.min

class TwoStageHeaderActivity : BaseActivity<ActivityTwoStageHeaderBinding>() {

    companion object {
        fun newInstance(context: Context) {
            val intent = Intent(context, TwoStageHeaderActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityTwoStageHeaderBinding =
        ActivityTwoStageHeaderBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        val recycler = binding.recycler
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = SimpleAdapter(List(40) { "Item $it" })

        var progress = 0f
        var stretch = 0f
        val maxStretch = dp(120f)
        var phase: Int = 0 // 0: collapsed<->expanded, 1: expanded stretch

        binding.root.post {
            binding.headerImage.pivotY = 0f
            binding.headerImage.pivotX = binding.headerImage.width / 2f
            binding.motionLayout.setTransition(R.id.collapsed, R.id.expanded)
            binding.motionLayout.progress = 0f
        }

        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val ml = binding.motionLayout

                if (!rv.canScrollVertically(-1) && dy < 0) {
                    stretch = min(maxStretch, stretch + (-dy))
                    if (phase != 0) {
                        ml.setTransition(R.id.collapsed, R.id.expanded)
                        phase = 0
                    }
                    progress = stretch / maxStretch
                    ml.progress = progress
                    val scale = 1f + (stretch / maxStretch) * 0.25f
                    binding.headerImage.scaleX = scale
                    binding.headerImage.scaleY = scale
                    binding.headerImage.translationY = 0f
                    return
                }

                if (progress > 0f || stretch > 0f) {
                    val dec = max(0f, dy.toFloat())
                    if (stretch > 0f) {
                        stretch = max(0f, stretch - dec)
                        val scale = 1f + (stretch / maxStretch) * 0.25f
                        binding.headerImage.scaleX = scale
                        binding.headerImage.scaleY = scale
                        if (stretch > 0f) return
                        binding.headerImage.scaleX = 1f
                        binding.headerImage.scaleY = 1f
                    }
                    progress = max(0f, progress - dec / maxStretch)
                    ml.setTransition(R.id.collapsed, R.id.expanded)
                    ml.progress = progress
                    if (progress > 0f) return
                }

                binding.headerImage.translationY = 0f
            }
        })
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    private class SimpleAdapter(private val items: List<String>) : RecyclerView.Adapter<VH>() {
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val binding = ItemTextViewBinding.inflate(android.view.LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.binding.tvContent.text = items[position]
        }

        override fun getItemCount(): Int = items.size
    }

    private class VH(val binding: ItemTextViewBinding) : RecyclerView.ViewHolder(binding.root)
}
