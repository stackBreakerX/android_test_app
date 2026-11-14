package com.alex.studydemo.module_recyclerview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.databinding.ItemRvAnimationBinding
import com.alex.studydemo.module_view.ExperimentPage
import com.chad.library.adapter.base.BaseBinderAdapter
import com.chad.library.adapter.base.binder.QuickViewBindingItemBinder

/** 保留原生 RecyclerView 默认动画及 Binder 实验，通过 Compose 按钮触发插入。 */
class RecyclerViewAnimationActivity : ComponentActivity() {
    private val adapter = BaseBinderAdapter()
    /** 单调编号区分新项；列表初始为空，与原实验保持一致。 */
    private var next = 1

    /** 每次宿主创建只注册一次 Binder，重组仅保留原生列表实例。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter.addItemBinder(AnimationItemBinder())
        setContent {
            ExperimentPage("RecyclerView 条目动画", { finish() }) {
                Button(onClick = {
                    // 原菜单的添加入口在 Compose 中直接提交数据，让默认插入动画可观察。
                    val id = next++
                    adapter.addData(0, TestData(id.toString(), "列表项 $id"))
                }) { Text("添加") }
                AndroidView(factory = { context -> RecyclerView(context).apply {
                    layoutManager = LinearLayoutManager(context)
                    itemAnimator = DefaultItemAnimator()
                    adapter = this@RecyclerViewAnimationActivity.adapter
                } }, modifier = Modifier.weight(1f).fillMaxWidth())
            }
        }
    }

    /** XML 只用于技术实验的原生条目，不承载页面工具栏或控制按钮。 */
    private class AnimationItemBinder : QuickViewBindingItemBinder<TestData, ItemRvAnimationBinding>() {
        /** 复用时覆盖文字，避免显示上一个条目的内容。 */
        override fun convert(holder: BinderVBHolder<ItemRvAnimationBinding>, data: TestData) {
            holder.viewBinding.tvAnimationContent.text = data.name
        }
        /** 使用父容器参数创建条目，但由 RecyclerView 决定挂载时机。 */
        override fun onCreateViewBinding(layoutInflater: LayoutInflater, parent: ViewGroup, viewType: Int): ItemRvAnimationBinding =
            ItemRvAnimationBinding.inflate(layoutInflater, parent, false)
    }
}
