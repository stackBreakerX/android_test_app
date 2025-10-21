package com.alex.studydemo.network.monitor

import android.app.Application

/**
 * @description
 * @author <a href="mailto:zhangqiushi@snqu.com">张秋实</a>
 * @time 2021/5/28 6:27 下午
 * @version
 */
class NetworkManager private constructor() {

    lateinit var mApplication: Application

    companion object {

        @Volatile
        private var instance: NetworkManager? = null

        fun getDefault():NetworkManager {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = NetworkManager()
                    }
                }

            }
            return instance!!
        }
    }

    fun init(application: Application) {
        mApplication = application
    }


}