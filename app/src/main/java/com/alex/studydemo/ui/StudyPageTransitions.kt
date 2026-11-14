package com.alex.studydemo.ui

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import com.alex.studydemo.R
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.toArgb

/** 集中配置应用内 Activity 转场，覆盖 Intent 和 ARouter 入口，不改变页面栈或系统返回分发。 */
object StudyPageTransitions {
    /** Application 创建时注册一次；旧系统使用主题资源，新系统显式配置兼容预测返回的动画。 */
    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            /** 在首次绘制前设置窗口底色；Compose 首帧尚未提交时避免露出主题默认白色。 */
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                val preference = application.getSharedPreferences("learning_workspace", 0).getString("theme", "system")
                val dark = when (preference) {
                    "dark" -> true
                    "light" -> false
                    else -> activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                }
                val background = backgroundColor(dark)
                activity.window.setBackgroundDrawable(ColorDrawable(background))
                if (Build.VERSION.SDK_INT >= 34) {
                    // 不使用已弃用的 overridePendingTransition，以保留系统预测返回和取消手势的控制权。
                    // 这些资源控制普通跳转和返回提交后的动画，不将系统手势伪装成 iOS 跟手转场。
                    activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN,
                        R.anim.study_page_open_enter, R.anim.study_page_open_exit, background)
                    activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE,
                        R.anim.study_page_close_enter, R.anim.study_page_close_exit, background)
                }
            }

            /** 其余生命周期不触发动画；从后台恢复、权限回调和重组不会重播页面转场。 */
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /** 与 StudyTheme 背景匹配；窗口在页面出现前便可使用颜色，不依赖 Compose 完成重组。 */
    fun backgroundColor(dark: Boolean): Int = if (dark) darkColorScheme().background.toArgb() else Color.rgb(246, 248, 250)
}
