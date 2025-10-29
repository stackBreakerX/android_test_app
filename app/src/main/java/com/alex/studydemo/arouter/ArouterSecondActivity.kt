package com.alex.studydemo.arouter

import android.os.Bundle
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.databinding.ActivityArouterSecondBinding
import com.alex.studydemo.arouter.ArouterSecondActivity.Companion.PATH
import com.alibaba.android.arouter.facade.annotation.Autowired
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter

@Route(path = PATH)
class ArouterSecondActivity : BaseActivity<ActivityArouterSecondBinding>() {

//    @Autowired(name = "test")
//    @JvmField
//    var title = ""


    companion object {
        const val PATH = "/second/secondActivity"
    }

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityArouterSecondBinding =
        ActivityArouterSecondBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
//        ARouter.getInstance().inject(this)
//        binding.textView.text = title
    }
}