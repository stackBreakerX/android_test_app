package com.alex.studydemo.app

import android.app.Application
import com.alibaba.android.arouter.launcher.ARouter
import com.blankj.utilcode.util.Utils
import com.alex.studydemo.module_performance.GlobalJankMonitor

/**
 * @description
 * @author <a href="mailto:zhangqiushi@snqu.com">张秋实</a>
 * @time 2021/1/18 10:24 上午
 * @version
 */
class MyApplication : Application() {

    /** 在创建首个页面前安装转场策略，所有实验共享同一套导航动效。 */
    override fun onCreate() {
        super.onCreate()
        com.alex.studydemo.ui.StudyPageTransitions.install(this)
        ARouter.openLog()
        ARouter.openDebug()
        Utils.init(this)

        ARouter.init(this)

        GlobalJankMonitor.init(this)
    }
}
