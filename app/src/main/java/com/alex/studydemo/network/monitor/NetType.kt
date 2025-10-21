package com.alex.studydemo.network.monitor

/**
 * @description
 * @author <a href="mailto:zhangqiushi@snqu.com">张秋实</a>
 * @time 2021/5/28 6:33 下午
 * @version
 */
enum class NetType {
    AUTO,
    WIFI,

    // pc，笔记本，PDA设备 上网
    CMNET,

    // 手机上网
    CMWAP,

    //没有网络
    NONE;
}