package com.alex.studydemo.module_coroutine

import android.os.Bundle
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.databinding.ActivityFlowBinding

class FlowActivity : BaseActivity<ActivityFlowBinding>() {

    private val viewBinding: ActivityFlowBinding get() = binding

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityFlowBinding =
        ActivityFlowBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        // 该页面目前仅展示布局，无额外逻辑
    }
}