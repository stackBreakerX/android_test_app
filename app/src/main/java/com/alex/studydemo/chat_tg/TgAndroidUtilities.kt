package com.alex.studydemo.chat_tg

import android.graphics.Typeface
import kotlin.math.roundToInt

/**
 * Android 工具方法（与 dp/字体等相关）
 * - 提供 dp/dpf2/dpF 转换、整型线性插值与粗体 Typeface
 */
object TgAndroidUtilities {
    fun dp(value: Float, density: Float): Int = (value * density).roundToInt()
    fun dpf2(value: Float, density: Float): Float = value * density
    fun dpF(value: Float, density: Float): Float = value * density
    fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).toInt()
    fun bold(): Typeface = Typeface.DEFAULT_BOLD
}
