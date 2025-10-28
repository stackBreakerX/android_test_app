package com.alex.studydemo.network.monitor

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.alex.studydemo.R
import com.alex.studydemo.databinding.ActivityNetworkMonitorBinding

class NetworkMonitorActivity : AppCompatActivity(), NetChangeObserver {

    private lateinit var binding: ActivityNetworkMonitorBinding

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private enum class Strength { NONE, WEAK, STRONG }

    private var lastStrength: Strength = Strength.NONE
    private var lastTransport: String = "-"

    // 阈值可根据实际体验调整：单位 kbps
    private val WIFI_WEAK_DOWN_KBPS = 3000
    private val CELL_WEAK_DOWN_KBPS = 1500

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNetworkMonitorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        connectivityManager = getSystemService(ConnectivityManager::class.java)

        binding.tvStatus.text = "状态：未知"
        binding.tvDetails.text = "详情：等待网络回调"
    }

    override fun onStart() {
        super.onStart()
        registerNetworkCallback()
    }

    override fun onStop() {
        super.onStop()
        unregisterNetworkCallback()
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val cm = connectivityManager ?: return

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "onAvailable: $network")
                // 等待 capabilities 变化进行强/弱判断
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "onLost: $network")
                updateUi(Strength.NONE, transport = "-", downKbps = 0, validated = false)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val transport = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "BLUETOOTH"
                    else -> "OTHER"
                }

                val downKbps = caps.linkDownstreamBandwidthKbps
                val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

                val strength = evaluateStrength(caps)
                updateUi(strength, transport, downKbps, validated)
            }
        }

        // 默认网络回调即可满足从有网->弱网、弱网->有网的检测
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cm.registerDefaultNetworkCallback(networkCallback!!)
        } else {
            // 兜底：API 24 已是 minSdk，理论不会走到这里
            cm.registerNetworkCallback(
                android.net.NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                networkCallback!!
            )
        }
    }

    private fun unregisterNetworkCallback() {
        val cm = connectivityManager ?: return
        networkCallback?.let {
            try {
                cm.unregisterNetworkCallback(it)
            } catch (_: Exception) {
            }
        }
        networkCallback = null
    }

    private fun evaluateStrength(caps: NetworkCapabilities): Strength {
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (!hasInternet) return Strength.NONE

        // 如果系统未验证互联网可达，视为弱网
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        if (!validated) return Strength.WEAK

        val down = caps.linkDownstreamBandwidthKbps
        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isCell = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

        // 带宽阈值判断（粗略；系统值为估算值）
        return when {
            isWifi && down in 0..WIFI_WEAK_DOWN_KBPS -> Strength.WEAK
            isCell && down in 0..CELL_WEAK_DOWN_KBPS -> Strength.WEAK
            down <= 0 -> Strength.WEAK
            else -> Strength.STRONG
        }
    }

    private fun updateUi(strength: Strength, transport: String, downKbps: Int, validated: Boolean) {
        val statusText = when (strength) {
            Strength.NONE -> "无网"
            Strength.WEAK -> "弱网"
            Strength.STRONG -> "有网"
        }

        binding.tvStatus.text = "状态：$statusText"
        binding.tvDetails.text = "传输：$transport\n已验证：$validated\n下行带宽(kbps)：$downKbps"

        // 记录并提示状态转换
        when {
            lastStrength == Strength.STRONG && strength == Strength.WEAK -> {
                Log.i(TAG, "状态转换：有网 -> 弱网")
            }
            lastStrength == Strength.WEAK && strength == Strength.STRONG -> {
                Log.i(TAG, "状态转换：弱网 -> 有网")
            }
        }
        lastStrength = strength
        lastTransport = transport
    }

    // 兼容已有接口（当前页面不依赖旧的 BroadcastReceiver 判定）
    override fun onConnect(type: NetType) {
        Log.d(TAG, "onConnect() type=$type")
    }

    override fun onDisConnect() {
        Log.d(TAG, "onDisConnect()")
        updateUi(Strength.NONE, transport = lastTransport, downKbps = 0, validated = false)
    }

    companion object {
        private const val TAG = "NetworkMonitorActivity"
    }
}