package com.alex.studydemo.module_view

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.alex.studydemo.R

class CustomerViewActivity : AppCompatActivity() {

    companion object {
        fun newInstance(context: Context) {
            val intent = Intent(context, CustomerViewActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_view)
    }
}