package com.alex.studydemo.module_recyclerview

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import com.alex.studydemo.module_view.ExperimentPage
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.databinding.ItemTextViewBinding
import com.alex.studydemo.module_recyclerview.RecyclerViewActivity.Companion.PATH
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.chad.library.adapter.base.BaseBinderAdapter
import com.chad.library.adapter.base.binder.QuickViewBindingItemBinder

/** 保留 ARouter 路径和原生列表适配器，便于比较分组装饰与条目复用。 */
@Route(path = PATH)
class RecyclerViewActivity : ComponentActivity() {

    private lateinit var recycler: RecyclerView

    private val adapter = BaseBinderAdapter()

    /** 对外保留初始数据列表；装饰器始终查询适配器当前数据，保证插删后分组正确。 */
    val data = mutableListOf<Any>(
        TestData("1", "alex1"),
        TestData("2", "alex1"),
        TestData("3", "alex1"),
        TestData("4", "alex4"),
        TestData("100", "alex4"),
        TestData("101", "alex4"),
        TestData("5", "alex5"),
        TestData("6", "alex5"),
        TestData("7", "alex5"),
        TestData("8", "alex8"),
        TestData("200", "alex8"),
        TestData("201", "alex8"),
        TestData("202", "alex8"),
        TestData("10", "alex10"),
        TestData("11", "alex10"),
        TestData("12", "alex10"),
        TestData("14", "alex11"),
        TestData("15", "alex11"),
        TestData("17", "alex11"),
        TestData("19", "alex12"),
        TestData("20", "alex12"),
        TestData("27", "alex13"),
        TestData("37", "alex14"),
        TestData("30", "alex15"),
        TestData("31", "alex16"),
        TestData("32", "alex17"),
        TestData("33", "alex17"),
    )

    companion object {
        private const val TAG = "RecyclerViewActivity"
        /** 对外导航路径保持不变。 */
        const val PATH = "/view/recyclerView"
    }

    /** 原生 RecyclerView 仅承载装饰与手势实验；控制区在 Compose 中提供。 */
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExperimentPage("RecyclerView 分组实验", { finish() }) {
                AndroidView(factory = { context ->
                    RecyclerView(context).also {
                        recycler = it
                        it.layoutManager = LinearLayoutManager(context)
                        configureRecycler()
                    }
                }, modifier = Modifier.weight(1f).fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(onClick = { remove(recycler) }) { Text("remove") }
                    Button(onClick = { add(recycler) }) { Text("add") }
                    Button(onClick = { next(recycler) }) { Text("next") }
                }
            }
        }
    }

    /** 每个原生列表实例只初始化一次，避免重组重复注册装饰器及手势回调。 */
    private fun configureRecycler() {
        adapter.addItemBinder(ImageItemBinder())

        recycler.adapter = adapter
        recycler.addItemDecoration(VerticalSectionDecoration.create(this)
            .sectionSize(50)
            .sectionTextSize(20f)
            .sectionTextLeftOffset(20f)
            .sectionTextColor(Color.WHITE)
            .sectionColorProvider(object : VerticalSectionDecoration.SectionColorProvider {
                /** 使用固定蓝色突出分组栏，与原始实验保持一致。 */
                override fun sectionColor(position: Int, parent: RecyclerView?): Int {
                    return Color.BLUE
                }
            })
            .sectionProvider(object : VerticalSectionDecoration.SectionProvider {
                /** 预布局可能出现无效位置，返回空分组以避开越界。 */
                override fun sectionName(position: Int, parent: RecyclerView?): String? {
                    return if (position >= 0 && position < adapter.data.size) {
                        (adapter.data[position] as TestData).name
                    } else {
                        null
                    }
                }

            })
            .size(15)
            .color(Color.BLACK)
            .build()
        )

        val callback = SimpleItemTouchHelperCallback()
        val touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(recycler)
        getData()

    }

    /** 将可变数据交给适配器，保留原来的插入事件日志。 */
    private fun getData() {

        adapter.setNewInstance(data)
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {

            /** 记录适配器的插入范围，便于观察差量通知。 */
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                Log.d(
                    TAG,
                    "onItemRangeInserted() called with: positionStart = $positionStart, itemCount = $itemCount"
                )
            }
        })
    }

    /** 绑定实验条目布局；XML 仅用于 RecyclerView 条目本身。 */
    private class ImageItemBinder : QuickViewBindingItemBinder<TestData, ItemTextViewBinding>() {

        /** 复用条目时覆盖文字内容，保持滚动后的数据显示正确。 */
        override fun convert(holder: BinderVBHolder<ItemTextViewBinding>, data: TestData) {
            holder.viewBinding.tvContent.text = data.name
        }

        /** XML 仅创建原生列表条目，由 RecyclerView 接管挂载。 */
        override fun onCreateViewBinding(
            layoutInflater: LayoutInflater,
            parent: ViewGroup,
            viewType: Int
        ): ItemTextViewBinding {

            val viewBinding = ItemTextViewBinding.inflate(layoutInflater, parent, false)
            return viewBinding
        }

        /** 保留创建日志，以观察复用池命中后的创建次数。 */
        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): BinderVBHolder<ItemTextViewBinding> {
            Log.e(TAG, "onCreateViewHolder() called with: viewType = $viewType")
            return super.onCreateViewHolder(parent, viewType)
        }

    }

    /** 删除第三项；不足三项时保持列表不变，避免连续点击越界。 */
    fun remove(view: View) {
        if (adapter.itemCount > 2) adapter.removeAt(2)
    }

    /** 在第二个位置插入两项；空列表回退到头部。 */
    fun add(view: View) {
        val inserted = mutableListOf<Any>(
            TestData("1", "alex11"),
            TestData("2", "alex12"),
        )
        adapter.addData(minOf(1, adapter.itemCount), inserted)
    }

    /** 保留第二页的 ARouter 导航入口。 */
    fun next(view: View) {
        ARouter.getInstance()
            .build(RecyclerViewActivity1.PATH)
            .navigation(this)
    }
}

