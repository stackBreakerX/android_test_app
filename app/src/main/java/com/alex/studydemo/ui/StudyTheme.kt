package com.alex.studydemo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** 工作台和实验页面共享蓝绿色调；默认读取应用外观设置，未设置时跟随系统；调用方可临时覆盖明暗模式。 */
@Composable
fun StudyTheme(dark: Boolean? = null, content: @Composable () -> Unit) {
    val resolvedDark = dark ?: StudyDarkTheme()
    StudySystemBars(resolvedDark)
    MaterialTheme(
        colorScheme = if (resolvedDark) darkColorScheme(primary = Color(0xFF79D9C8), secondary = Color(0xFFA9BCFF))
        else lightColorScheme(primary = Color(0xFF006B5D), secondary = Color(0xFF4860A8), background = Color(0xFFF6F8FA), surface = Color.White),
        content = content,
    )
}

/** 自定义配色的实验也使用同一外观设置，同时保留各自的主题色。 */
@Composable
fun StudyDarkTheme(): Boolean {
    val preference = LocalContext.current.getSharedPreferences("learning_workspace", 0).getString("theme", "system")
    return when (preference) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
}

/** 外观切换同时更新系统栏图标，避免应用深色模式与系统浅色模式组合时失去对比度。 */
@Composable
fun StudySystemBars(dark: Boolean) {
    var context = LocalContext.current
    while (context is ContextWrapper && context !is ComponentActivity) context = context.baseContext
    val activity = context as? ComponentActivity ?: return
    // 只在宿主或外观改变时配置窗口；预览等非 Activity 环境不修改窗口。
    DisposableEffect(activity, dark) {
        // 外观在当前页切换后同步窗口底色，返回和系统手势预览不露出旧颜色。
        activity.window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(StudyPageTransitions.backgroundColor(dark)))
        val style = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { /* 窗口随宿主销毁；切换页面后由新页面配置自己的系统栏。 */ }
    }
}
