package com.alex.studydemo.module_recyclerview

import android.graphics.Color
import android.util.Log
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
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.R
import com.alex.studydemo.module_recyclerview.RecyclerViewActivity1.Companion.PATH
import com.alibaba.android.arouter.facade.annotation.Route
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder

/** 保留 ARouter 路径和原生列表适配器，便于比较分组装饰与条目复用。 */
@Route(path = PATH)
class RecyclerViewActivity1 : ComponentActivity() {

    private lateinit var mAdapter: DemoAdapter

    private lateinit var recycler: RecyclerView

    /** 对外保留初始数据列表；装饰器始终查询适配器当前数据，保证插删后分组正确。 */
    val data = mutableListOf<TestData>(
        TestData("1", "alex1"),
        TestData("2", "alex2"),
        TestData("3", "alex3"),
        TestData("4", "alex4"),
        TestData("5", "alex5"),
        TestData("6", "alex6"),
        TestData("7", "alex7"),
        TestData("8", "alex8"),
        TestData("9", "alex9"),
        TestData("10", "alex10"),
    )

    companion object {
        private const val TAG = "RecyclerViewActivity"
        /** 对外导航路径保持不变。 */
        const val PATH = "/view/recyclerView1"
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
                    TextButton(onClick = { finish() }) { Text("上一页") }
                }
            }
        }
    }

    /** 每个原生列表实例只初始化一次，避免重组重复注册装饰器及手势回调。 */
    private fun configureRecycler() {
        mAdapter = DemoAdapter(R.layout.item_text_view)

        recycler.adapter = mAdapter
        recycler.addItemDecoration(VerticalSectionDecoration.create(this)
            .sectionSize(40)
            .sectionTextSize(20f)
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
                    return if (position >= 0 && position < mAdapter.data.size) {
                        (mAdapter.data[position] as TestData).name
                    } else {
                        null
                    }
                }

            })
            .size(15)
            .color(Color.BLACK)
            .build()
        )
        getData()

    }

    /** 静态初始数据交给适配器，后续操作由适配器发送差量通知。 */
    private fun getData() {

        mAdapter.setNewInstance(data)
    }

    /** 保留 BaseQuickAdapter 复用过程的日志，供观察列表绑定时机。 */
    private class DemoAdapter(layoutResId: Int) : BaseQuickAdapter<TestData, BaseViewHolder>(
        layoutResId
    ) {
        /** 复用条目时覆盖文字内容，保持滚动后的数据显示正确。 */
        override fun convert(holder: BaseViewHolder, item: TestData) {
            Log.d(TAG, "convert() called with: position = ${holder.adapterPosition}, item = $item")
            holder.setText(R.id.tvContent,item.name)
        }

        /** 记录视图类型查询时机，不改变 BaseQuickAdapter 的类型判断。 */
        override fun getItemViewType(position: Int): Int {
            Log.d(TAG, "getItemViewType() called with: position = $position")
            return super.getItemViewType(position)
        }

        /** 保留创建日志，以观察复用池命中后的创建次数。 */
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
            Log.d(TAG, "onCreateViewHolder() called with: viewType = $viewType")
            return super.onCreateViewHolder(parent, viewType)
        }

    }

    /** 删除第三项；连续点击至不足三项后不再删除。 */
    fun remove(view: View) {
        if (mAdapter.itemCount > 2) mAdapter.removeAt(2)
    }

    /** 在头部插入两个不同分组，供观察 BaseQuickAdapter 的装饰更新。 */
    fun add(view: View) {
        mAdapter.addData(0, listOf(TestData("1", "alex11"), TestData("2", "alex12")))
    }
}
