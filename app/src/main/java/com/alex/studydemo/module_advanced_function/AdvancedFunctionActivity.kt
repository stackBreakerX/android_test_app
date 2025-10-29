package com.alex.studydemo.module_advanced_function

import android.os.Bundle
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.databinding.ActivityAdvancedFunctionBinding

class AdvancedFunctionActivity : BaseActivity<ActivityAdvancedFunctionBinding>() {

    companion object {
        const val TAG = "AdvancedFunctionActivity"
    }

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityAdvancedFunctionBinding =
        ActivityAdvancedFunctionBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        // 页面初始化逻辑可写在此方法中（原 onCreate 内容已由 BaseActivity 处理）
    }

    inline fun testInlineFunc(name: String, block: () -> Unit, noinline block1: () -> Unit): () -> Unit {
        block.invoke()
        return block1
    }

    inline fun testInlineFunc1(noinline block: () -> Unit, block1: () -> Unit) {
        testNoInlineFunc(block)
    }

    fun testNoInlineFunc(block: () -> Unit) {}
}