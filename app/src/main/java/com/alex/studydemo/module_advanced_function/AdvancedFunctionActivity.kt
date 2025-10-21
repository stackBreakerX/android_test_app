package com.alex.studydemo.module_advanced_function

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.alex.studydemo.R
import com.alex.studydemo.databinding.ActivityAdvancedFunctionBinding
import com.alex.studydemo.databinding.ActivityCoroutineDemoBinding

class AdvancedFunctionActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityAdvancedFunctionBinding

    companion object {
        const val TAG = "AdvancedFunctionActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityAdvancedFunctionBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
    }

    inline fun testInlineFunc(name: String, block: () -> Unit, noinline block1: () -> Unit):() -> Unit {
        block.invoke()
        return block1
    }

    inline fun testInlineFunc1(noinline block: () -> Unit, block1: () -> Unit) {

        testNoInlineFunc(block)
    }

    fun testNoInlineFunc( block: () -> Unit) {

    }
}