package com.alex.studydemo.module_recyclerview

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.R
import com.alex.studydemo.databinding.ActivityRecyclerViewBinding
import com.alex.studydemo.databinding.ItemTextViewBinding
import com.alex.studydemo.module_recyclerview.RecyclerViewActivity1.Companion.PATH
import com.alibaba.android.arouter.facade.annotation.Route
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.binder.QuickViewBindingItemBinder
import com.chad.library.adapter.base.viewholder.BaseViewHolder

@Route(path = PATH)
class RecyclerViewActivity1 : AppCompatActivity() {

//    private val adapter = BaseBinderAdapter()

    private lateinit var mAdapter: DemoAdapter

    private lateinit var viewBinding: ActivityRecyclerViewBinding

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
        const val PATH = "/view/recyclerView1"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewBinding = ActivityRecyclerViewBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        mAdapter = DemoAdapter(R.layout.item_text_view)
//        adapter.addItemBinder(ImageItemBinder())

        viewBinding.rvList.adapter = mAdapter
        viewBinding.rvList.addItemDecoration(VerticalSectionDecoration.create(this)
            .sectionSize(40)
            .sectionTextSize(20f)
            .sectionTextColor(Color.WHITE)
            .sectionColorProvider(object : VerticalSectionDecoration.SectionColorProvider {
                override fun sectionColor(position: Int, parent: RecyclerView?): Int {
                    return Color.BLUE
                }
            })
            .sectionProvider(object : VerticalSectionDecoration.SectionProvider {
                override fun sectionName(position: Int, parent: RecyclerView?): String? {
                    return if (position >= 0 && position < data.size) {
                        (data[position] as TestData).name
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

    private fun getData() {

        mAdapter.setNewInstance(data)
    }

    private class DemoAdapter(layoutResId: Int) : BaseQuickAdapter<TestData, BaseViewHolder>(
        layoutResId
    ) {
        override fun convert(holder: BaseViewHolder, item: TestData) {
            Log.d(TAG, "convert() called with: position = ${holder.adapterPosition}, item = $item")
            holder.setText(R.id.tvContent,item.name)
        }

        override fun getItemViewType(position: Int): Int {
            Log.d(TAG, "getItemViewType() called with: position = $position")
            return super.getItemViewType(position)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
            Log.d(TAG, "onCreateViewHolder() called with: viewType = $viewType")
            return super.onCreateViewHolder(parent, viewType)
        }

    }

    private class ImageItemBinder : QuickViewBindingItemBinder<TestData, ItemTextViewBinding>() {


        override fun convert(holder: BinderVBHolder<ItemTextViewBinding>, data: TestData) {
            holder.viewBinding.tvContent.text = data.name
        }

        override fun onCreateViewBinding(
            layoutInflater: LayoutInflater,
            parent: ViewGroup,
            viewType: Int
        ): ItemTextViewBinding {
//            Log.e(
//                TAG,
//                "onCreateViewBinding() called with: layoutInflater = $layoutInflater, parent = $parent, viewType = $viewType"
//            )
            val viewBinding = ItemTextViewBinding.inflate(layoutInflater, parent, false)
            return viewBinding
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): BinderVBHolder<ItemTextViewBinding> {
            Log.e(TAG, "onCreateViewHolder() called with: viewType = $viewType")
            return super.onCreateViewHolder(parent, viewType)
        }


    }

    fun remove(view: View) {
//        adapter.removeAt(2)
    }

    fun add(view: View) {
        val data = mutableListOf<Any>(
            TestData("1", "alex11"),
            TestData("2", "alex12"),
        )
//        adapter.addData(0, data)
    }
}


