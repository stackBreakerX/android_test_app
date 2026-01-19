package com.alex.studydemo.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.viewbinding.ViewBinding
import com.alex.lib.R
import com.alex.studydemo.utils.EdgeToEdgeHelper

abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    private var _binding: VB? = null
    protected val binding: VB get() = _binding!!
    private var _toolbar: Toolbar? = null
    protected val toolbar: Toolbar? get() = _toolbar

    abstract fun inflateBinding(inflater: LayoutInflater): VB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = inflateBinding(layoutInflater)

        val baseRoot = layoutInflater.inflate(R.layout.layout_base_container, null) as ViewGroup
        setContentView(baseRoot)

        val contentContainer = baseRoot.findViewById<FrameLayout>(R.id.content_container)
        contentContainer.addView(binding.root)

        _toolbar = baseRoot.findViewById(R.id.base_toolbar)
        _toolbar?.let { tb ->
            setSupportActionBar(tb)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowHomeEnabled(true)
            supportActionBar?.setHomeButtonEnabled(true)
            tb.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
            supportActionBar?.title = title
        }

        _toolbar?.let { EdgeToEdgeHelper(this).insetsPadding(it, left = true, top = true, right = true) }
        EdgeToEdgeHelper(this).insetsPadding(contentContainer, left = true, top = false, right = true, bottom = true)

        onViewCreated(savedInstanceState)
    }

    open fun onViewCreated(savedInstanceState: Bundle?) {}

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }
}

