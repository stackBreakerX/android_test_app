package com.alex.studydemo.module_recyclerview

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import com.alex.studydemo.R
import com.alex.studydemo.databinding.ActivityRecyclerViewAnimationBinding
import com.alex.studydemo.databinding.ItemRvAnimationBinding
import com.chad.library.adapter.base.BaseBinderAdapter
import com.chad.library.adapter.base.binder.QuickViewBindingItemBinder

class RecyclerViewAnimationActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityRecyclerViewAnimationBinding

    private val adapter = BaseBinderAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityRecyclerViewAnimationBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        adapter.addItemBinder(AnimationItemBinder())
        viewBinding.rvAnimation.adapter = adapter
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_animation,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            R.id.action_add -> {

            }
            else -> {

            }
        }
        return true
    }

    private class AnimationItemBinder : QuickViewBindingItemBinder<TestData, ItemRvAnimationBinding>() {


        override fun convert(holder: BinderVBHolder<ItemRvAnimationBinding>, data: TestData) {
            holder.viewBinding.tvAnimationContent.text = data.name
        }

        override fun onCreateViewBinding(
            layoutInflater: LayoutInflater,
            parent: ViewGroup,
            viewType: Int
        ): ItemRvAnimationBinding {
//            Log.e(
//                TAG,
//                "onCreateViewBinding() called with: layoutInflater = $layoutInflater, parent = $parent, viewType = $viewType"
//            )
            return ItemRvAnimationBinding.inflate(layoutInflater, parent, false)
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): BinderVBHolder<ItemRvAnimationBinding> {
            return super.onCreateViewHolder(parent, viewType)
        }


    }
}