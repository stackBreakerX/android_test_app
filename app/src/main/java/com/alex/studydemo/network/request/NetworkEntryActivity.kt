package com.alex.studydemo.network.request

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.databinding.ActivityNetworkEntryBinding

class NetworkEntryActivity : BaseActivity<ActivityNetworkEntryBinding>() {

    companion object {
        fun newInstance(context: Context) {
            val intent = Intent(context, NetworkEntryActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityNetworkEntryBinding =
        ActivityNetworkEntryBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        binding.btnGoRequest.setOnClickListener {
            val intent = Intent(this, NetworkRequestActivity::class.java)
            startActivity(intent)
        }
    }
}

