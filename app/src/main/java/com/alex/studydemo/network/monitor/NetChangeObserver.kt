package com.alex.studydemo.network.monitor

/**
 * @description
 * @author <a href="mailto:zhangqiushi@snqu.com">张秋实</a>
 * @time 2021/5/28 6:38 下午
 * @version
 */
interface NetChangeObserver {

    fun onConnect(type: NetType)

    fun onDisConnect()
}