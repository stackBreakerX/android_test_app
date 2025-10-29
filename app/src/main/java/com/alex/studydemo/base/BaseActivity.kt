package com.alex.studydemo.base

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import com.alex.studydemo.utils.EdgeToEdgeHelper

abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    private var _binding: VB? = null
    protected val binding: VB get() = _binding!!

    // Subclasses provide how to inflate their specific ViewBinding
    abstract fun inflateBinding(inflater: LayoutInflater): VB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = inflateBinding(layoutInflater)
        setContentView(binding.root)
        // Apply edge-to-edge padding to the root view (refer to MainActivity)
        EdgeToEdgeHelper(this).insetsPadding(binding.root, left = true, top = true, right = true)
        onViewCreated(savedInstanceState)
    }

    // Optional hook for subclasses after binding and contentView are ready
    open fun onViewCreated(savedInstanceState: Bundle?) {}

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }
}