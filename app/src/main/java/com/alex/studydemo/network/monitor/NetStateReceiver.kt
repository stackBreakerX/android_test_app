package com.alex.studydemo.network.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alex.studydemo.network.monitor.utils.ANDROID_NET_CHANGE_ACTION
import com.alex.studydemo.network.monitor.utils.NetworkUtils

/**
 * @description
 * @author <a href="mailto:zhangqiushi@snqu.com">张秋实</a>
 * @time 2021/5/28 6:28 下午
 * @version
 */
class NetStateReceiver : BroadcastReceiver() {

    private var netType: NetType = NetType.NONE

    private var listener: NetChangeObserver? = null


    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || intent.action == null) {
            return
        }

        //处理广播时间
        if (intent.action.equals(ANDROID_NET_CHANGE_ACTION,true)) {
            netType = NetworkUtils.getNetType()
            if (NetworkUtils.isNetworkAvailable()) {
                listener?.onConnect(netType)
            } else {
                listener?.onDisConnect()
            }
        }
    }
}