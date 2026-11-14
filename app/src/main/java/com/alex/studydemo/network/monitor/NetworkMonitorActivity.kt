package com.alex.studydemo.network.monitor

import com.alex.studydemo.ui.StudyPage
import com.alex.studydemo.ui.StudyTheme
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.util.Log

/** 在页面可见期间监听默认网络；带宽为系统估计值，不执行测速。 */
class NetworkMonitorActivity : ComponentActivity(), NetChangeObserver {

    // 网络回调来自 Binder 线程，界面状态统一经主线程更新。
    private var status by mutableStateOf("状态：未知")
    private var details by mutableStateOf("详情：等待网络回调")

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private enum class Strength { NONE, WEAK, STRONG }

    private var lastStrength: Strength = Strength.NONE
    private var lastTransport: String = "-"

    // 阈值可根据实际体验调整：单位 kbps
    private val WIFI_WEAK_DOWN_KBPS = 3000
    private val CELL_WEAK_DOWN_KBPS = 1500

    /** 初始化系统服务，状态等待回调填充。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StudyTheme {
                StudyPage("网络监控", onBack = { finish() }) {
                    Text(status, style = MaterialTheme.typography.headlineMedium)
                    Text(details)
                    Text("Wi-Fi 弱网阈值 3000 kbps；蜂窝弱网阈值 1500 kbps。未验证互联网连接时按弱网显示。")
                }
            }
        }

        connectivityManager = getSystemService(ConnectivityManager::class.java)

    }

    /** 页面可见时注册，避免后台持续监听。 */
    override fun onStart() {
        super.onStart()
        registerNetworkCallback()
    }

    /** 页面不可见时注销，允许再次进入后重新注册。 */
    override fun onStop() {
        super.onStop()
        unregisterNetworkCallback()
    }

    /** 仅注册一次默认网络回调，兼容分支仍保留。 */
    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val cm = connectivityManager ?: return

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            /** 可用通知尚无带宽信息，等待能力回调后再更新强弱。 */
            override fun onAvailable(network: Network) {
                Log.d(TAG, "onAvailable: $network")
                // 等待 capabilities 变化进行强/弱判断
            }

            /** 默认网络丢失时清空当前可达状态。 */
            override fun onLost(network: Network) {
                Log.d(TAG, "onLost: $network")
                updateUi(Strength.NONE, transport = "-", downKbps = 0, validated = false)
            }

            /** 使用回调提供的能力快照，避免同步重新查询读到过时数据。 */
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

        // 权限或系统服务异常显示在页面，下一次进入前台可以重新尝试。
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(networkCallback!!)
            } else {
                cm.registerNetworkCallback(
                    android.net.NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                    networkCallback!!
                )
            }
        } catch (e: Exception) {
            networkCallback = null
            status = "监听失败"
            details = e.message.orEmpty()
            Log.w(TAG, "注册网络回调失败", e)
        }
    }

    /** 注销失败写日志，清理句柄以便后续重新注册。 */
    private fun unregisterNetworkCallback() {
        val cm = connectivityManager ?: return
        networkCallback?.let {
            try {
                cm.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.w(TAG, "注销网络回调失败", e)
            }
        }
        networkCallback = null
    }

    /** 根据互联网能力、验证状态和估计带宽分类。 */
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

    /** 回调可来自任意线程；切换主线程后更新状态并记录转换。 */
    private fun updateUi(strength: Strength, transport: String, downKbps: Int, validated: Boolean) {
        runOnUiThread {
            val statusText = when (strength) {
                Strength.NONE -> "无网"
                Strength.WEAK -> "弱网"
                Strength.STRONG -> "有网"
            }
            status = "状态：$statusText"
            details = "传输：$transport\n已验证：$validated\n下行带宽(kbps)：$downKbps"
            // 转换日志与界面状态在同一主线程事务内更新，避免不同回调线程相互覆盖。
            when {
                lastStrength == Strength.STRONG && strength == Strength.WEAK -> Log.i(TAG, "状态转换：有网 -> 弱网")
                lastStrength == Strength.WEAK && strength == Strength.STRONG -> Log.i(TAG, "状态转换：弱网 -> 有网")
            }
            lastStrength = strength
            lastTransport = transport
        }
    }

    // 兼容已有接口（当前页面不依赖旧的 BroadcastReceiver 判定）
    /** 保留旧观察接口，仅记录通知，不覆盖系统能力判断。 */
    override fun onConnect(type: NetType) {
        Log.d(TAG, "onConnect() type=$type")
    }

    /** 保留旧断开接口并显示无网。 */
    override fun onDisConnect() {
        Log.d(TAG, "onDisConnect()")
        updateUi(Strength.NONE, transport = lastTransport, downKbps = 0, validated = false)
    }

    companion object {
        private const val TAG = "NetworkMonitorActivity"
    }

}
