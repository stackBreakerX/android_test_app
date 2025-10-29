package com.alex.studydemo.module_recyclerview

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.alex.studydemo.base.BaseActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.databinding.ActivityRecyclerViewBinding
import com.alex.studydemo.databinding.ItemTextViewBinding
import com.alex.studydemo.module_recyclerview.RecyclerViewActivity.Companion.PATH
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.chad.library.adapter.base.BaseBinderAdapter
import com.chad.library.adapter.base.binder.QuickViewBindingItemBinder

@Route(path = PATH)
class RecyclerViewActivity : BaseActivity<ActivityRecyclerViewBinding>() {

    private val adapter = BaseBinderAdapter()

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
        const val PATH = "/view/recyclerView"
    }

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityRecyclerViewBinding =
        ActivityRecyclerViewBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: android.os.Bundle?) {
        adapter.addItemBinder(ImageItemBinder())

        binding.rvList.adapter = adapter
        binding.rvList.addItemDecoration(VerticalSectionDecoration.create(this)
            .sectionSize(50)
            .sectionTextSize(20f)
            .sectionTextLeftOffset(20f)
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

        val callback = SimpleItemTouchHelperCallback()
        val touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(binding.rvList)
        getData()

    }

    private fun getData() {


        adapter.setNewInstance(data)
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                Log.d(
                    TAG,
                    "onItemRangeInserted() called with: positionStart = $positionStart, itemCount = $itemCount"
                )
            }
        })
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
        adapter.removeAt(2)
    }

    fun add(view: View) {
        val data = mutableListOf<Any>(
            TestData("1", "alex11"),
            TestData("2", "alex12"),
        )
        adapter.addData(1, data)
    }

    fun next(view: View) {
        ARouter.getInstance()
            .build(RecyclerViewActivity1.PATH)
            .navigation(this)
    }
}


