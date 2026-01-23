package com.alex.studydemo.chat_tg

import android.graphics.Typeface
import kotlin.math.roundToInt

object TgAndroidUtilities {
    fun dp(value: Float, density: Float): Int = (value * density).roundToInt()
    fun dpf2(value: Float, density: Float): Float = value * density
    fun dpF(value: Float, density: Float): Float = value * density
    fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).toInt()
    fun bold(): Typeface = Typeface.DEFAULT_BOLD
}

