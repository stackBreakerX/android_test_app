package com.alex.studydemo.module_media

import android.os.Bundle
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.databinding.ActivityOfflineSttBinding

class OfflineSpeechToTextActivity : BaseActivity<ActivityOfflineSttBinding>() {

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityOfflineSttBinding =
        ActivityOfflineSttBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        supportActionBar?.title = "离线语音转文字"
        binding.txtStatus.text = "已迁移为系统识别方案"
    }
}
