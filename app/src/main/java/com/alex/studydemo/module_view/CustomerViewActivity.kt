package com.alex.studydemo.module_view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.databinding.ActivityCustomerViewBinding

class CustomerViewActivity : BaseActivity<ActivityCustomerViewBinding>() {

    companion object {
        fun newInstance(context: Context) {
            val intent = Intent(context, CustomerViewActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityCustomerViewBinding =
        ActivityCustomerViewBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        // 此页面目前仅展示自定义 View 布局，无额外逻辑
    }
}