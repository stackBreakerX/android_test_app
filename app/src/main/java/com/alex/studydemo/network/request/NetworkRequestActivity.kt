package com.alex.studydemo.network.request

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.databinding.ActivityNetworkRequestBinding
import kotlinx.coroutines.launch

class NetworkRequestActivity : BaseActivity<ActivityNetworkRequestBinding>() {

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityNetworkRequestBinding =
        ActivityNetworkRequestBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        binding.btnRequest.setOnClickListener { doRequest() }
        doRequest()
    }

    private fun doRequest() {
        binding.progress.visibility = android.view.View.VISIBLE
        binding.tvResult.text = ""
        lifecycleScope.launch {
            try {
                val result = NetworkClient.get("https://postman-echo.com/get?foo=bar")
                binding.tvResult.text = result
            } catch (t: Throwable) {
                binding.tvResult.text = "请求失败: ${t.message}"
            } finally {
                binding.progress.visibility = android.view.View.GONE
            }
        }
    }
}

