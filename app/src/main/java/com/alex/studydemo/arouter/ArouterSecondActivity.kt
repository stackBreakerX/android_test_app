package com.alex.studydemo.arouter

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import com.alex.studydemo.R
import com.alex.studydemo.arouter.ArouterSecondActivity.Companion.PATH
import com.alibaba.android.arouter.facade.annotation.Autowired
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter

@Route(path = PATH)
class ArouterSecondActivity : AppCompatActivity() {

//    @Autowired(name = "test")
//    @JvmField
//    var title = ""


    companion object {
        const val PATH = "/second/secondActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_arouter_second)
//        ARouter.getInstance().inject(this)
//        findViewById<TextView>(R.id.textView).text = title
    }
}