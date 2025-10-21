package com.alex.studydemo.module_coroutine

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.alex.studydemo.databinding.ActivityFlowBinding

class FlowActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityFlowBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityFlowBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)


    }
}