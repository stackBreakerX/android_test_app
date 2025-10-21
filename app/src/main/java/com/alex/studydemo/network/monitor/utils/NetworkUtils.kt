package com.alex.studydemo.network.monitor.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import com.alex.studydemo.network.monitor.NetType
import com.alex.studydemo.network.monitor.NetworkManager


/**
 * @description
 * @author <a href="mailto:zhangqiushi@snqu.com">张秋实</a>
 * @time 2021/5/28 6:47 下午
 * @version
 */
object NetworkUtils {

    @SuppressLint("MissingPermission")
    fun isNetworkAvailable(): Boolean {
        val connMgr = NetworkManager.getDefault().mApplication
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            ?: return false
        val infos = connMgr.allNetworkInfo
        for (info in infos) {
            if (info.state == NetworkInfo.State.CONNECTED) {
                return true
            }
        }
        return false
    }

    fun getNetType(): NetType {
        val connMgr = NetworkManager.getDefault().mApplication
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

//        val networkInfo = connMgr.getNetworkInfo(connMgr.activeNetwork?)

        // 获取当前激活的网络连接信息
        val networkInfo = connMgr.activeNetworkInfo ?: return NetType.NONE

        val nType = networkInfo.type

        if (nType == ConnectivityManager.TYPE_MOBILE) {
            if (networkInfo.extraInfo.toLowerCase() == "cmnet") {

            } else {
                return NetType.CMWAP
            }

        } else if (nType == ConnectivityManager.TYPE_WIFI) {
            return NetType.WIFI
        }

        return NetType.NONE
    }
}