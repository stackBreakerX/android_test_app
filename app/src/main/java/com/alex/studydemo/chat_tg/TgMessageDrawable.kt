package com.alex.studydemo.chat_tg

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.Drawable
import com.alex.studydemo.telegram.Theme

/**
 * TG 风格消息气泡绘制器：路径与填充由移植的 [Theme.MessageDrawable] 实现，本类保留描边与颜色常量，
 * 并将 [TgSharedConfig.bubbleRadius] 同步到 [Theme.bubbleRadiusDp]。
 */
class TgMessageDrawable(context: Context, private var out: Boolean) : Drawable() {

    private val impl = Theme.MessageDrawable(context, Theme.MessageDrawable.TYPE_TEXT, out, false).apply {
        setBubbleColors(COLOR_IN, COLOR_OUT)
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.5f
        color = if (out) COLOR_OUT_STROKE else COLOR_IN_STROKE
    }

    fun setOut(out: Boolean) {
        if (this.out == out) return
        this.out = out
        impl.setOutgoing(out)
        strokePaint.color = if (out) COLOR_OUT_STROKE else COLOR_IN_STROKE
        invalidateSelf()
    }

    /**
     * 文本消息用 [Theme.MessageDrawable.TYPE_TEXT]（带尾巴），图片/视频/文件等媒体样式用 [Theme.MessageDrawable.TYPE_MEDIA]（圆角无尾巴）。
     */
    fun setBubbleType(type: Int) {
        impl.setType(type)
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        syncImplGeometry()
        impl.draw(canvas)
        canvas.drawPath(impl.makePath(), strokePaint)
    }

    /** 与 Telegram 一致：同步列表语义高度后路径才包含尾巴；否则 makePath 会裁掉底边 */
    private fun syncImplGeometry() {
        Theme.bubbleRadiusDp = TgSharedConfig.bubbleRadius
        val b = bounds
        impl.setBounds(b.left, b.top, b.right, b.bottom)
        impl.setTop(b.top, b.width(), b.height(), false, false)
    }

    /** 返回当前 bounds 对应的气泡路径，用于按气泡形状（圆角+尾巴）裁剪子 View */
    fun buildClipPath(): Path {
        syncImplGeometry()
        return Path(impl.makePath())
    }

    override fun setAlpha(alpha: Int) {
        impl.setAlpha(alpha)
        strokePaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        impl.setColorFilter(colorFilter)
        strokePaint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setBounds(left, top, right, bottom)
        impl.setBounds(left, top, right, bottom)
    }

    override fun setBounds(bounds: Rect) {
        super.setBounds(bounds)
        impl.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
    }

    private companion object {
        const val COLOR_OUT = 0xFFE1FFC7.toInt()
        const val COLOR_IN = 0xFFFFFFFF.toInt()
        const val COLOR_OUT_STROKE = 0x3300D0DB
        const val COLOR_IN_STROKE = 0x1A121212
    }
}
