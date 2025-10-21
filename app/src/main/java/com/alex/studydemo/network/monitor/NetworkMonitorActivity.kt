package com.alex.studydemo.network.monitor

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.alex.studydemo.R

class NetworkMonitorActivity : AppCompatActivity(), NetChangeObserver {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_network_monitor)
    }

    override fun onConnect(type: NetType) {

    }

    override fun onDisConnect() {

    }
}