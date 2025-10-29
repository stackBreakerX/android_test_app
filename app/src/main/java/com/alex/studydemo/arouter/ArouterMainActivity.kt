package com.alex.studydemo.arouter

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.databinding.ActivityArouterMainBinding
import com.alibaba.android.arouter.launcher.ARouter

class ArouterMainActivity : BaseActivity<ActivityArouterMainBinding>() {

    companion object {
            fun newInstance(context: Context) {
                val intent = Intent(context,ArouterMainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(intent)
//                try {
//                    val intent = Intent(Utils.getApp(),ArouterMainActivity::class.java)
//                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//                    Utils.getApp().startActivity(intent)
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                }
            }
        }

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityArouterMainBinding =
        ActivityArouterMainBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
//        ARouter.getInstance().inject(this)

    }

    fun secondActivity(view: View) {
        ARouter.getInstance()
            .build(ArouterSecondActivity.PATH)
//            .withString("test","111111111")
            .navigation()
    }
}