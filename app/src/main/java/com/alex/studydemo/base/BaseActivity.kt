package com.alex.studydemo.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.viewbinding.ViewBinding
import com.alex.studydemo.R
import com.alex.studydemo.utils.EdgeToEdgeHelper

abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    private var _binding: VB? = null
    protected val binding: VB get() = _binding!!
    private var _toolbar: Toolbar? = null
    protected val toolbar: Toolbar? get() = _toolbar

    // Subclasses provide how to inflate their specific ViewBinding
    abstract fun inflateBinding(inflater: LayoutInflater): VB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = inflateBinding(layoutInflater)

        // Inflate base container with toolbar and content container
        val baseRoot = layoutInflater.inflate(R.layout.layout_base_container, null) as ViewGroup
        setContentView(baseRoot)

        // Attach child content into container
        val contentContainer = baseRoot.findViewById<FrameLayout>(R.id.content_container)
        contentContainer.addView(binding.root)

        // Setup toolbar with back button
        _toolbar = baseRoot.findViewById(R.id.base_toolbar)
        _toolbar?.let { tb ->
            setSupportActionBar(tb)
            // 强制开启 Home/Up 按钮以确保返回箭头显示
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowHomeEnabled(true)
            supportActionBar?.setHomeButtonEnabled(true)
            tb.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
            // Use Activity label as default title
            supportActionBar?.title = title
        }

        // Edge-to-edge: apply insets to toolbar (top) and content (left/right/bottom)
        _toolbar?.let { EdgeToEdgeHelper(this).insetsPadding(it, left = true, top = true, right = true) }
        EdgeToEdgeHelper(this).insetsPadding(contentContainer, left = true, top = false, right = true, bottom = true)

        onViewCreated(savedInstanceState)
    }

    // Optional hook for subclasses after binding and contentView are ready
    open fun onViewCreated(savedInstanceState: Bundle?) {}

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }
}