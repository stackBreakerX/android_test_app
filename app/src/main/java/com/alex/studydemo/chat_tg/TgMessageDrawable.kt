package com.alex.studydemo.chat_tg

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.max

class TgMessageDrawable(private var out: Boolean) : Drawable() {

    // 气泡填充/描边画笔（对应 TG 消息气泡）
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (out) COLOR_OUT else COLOR_IN
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.5f
        color = if (out) COLOR_OUT_STROKE else COLOR_IN_STROKE
    }
    fun setOut(out: Boolean) {
        if (this.out == out) return
        this.out = out
        paint.color = if (out) COLOR_OUT else COLOR_IN
        strokePaint.color = if (out) COLOR_OUT_STROKE else COLOR_IN_STROKE
        invalidateSelf()
    }
    // 绘制缓存对象
    private val rect = RectF()
    private val path = Path()
    private val tmpRect = RectF()

    // 对齐 TG 的气泡邻接与裁剪参数（在简化版中预留）
    var drawFullBubble: Boolean = false
    var topY: Int = 0
    var currentBackgroundHeight: Int = 0
    var isTopNear: Boolean = false
    var isBottomNear: Boolean = false
    var botButtonsBottom: Boolean = false

    override fun draw(canvas: Canvas) {
        val b = bounds
        rect.set(b)
        val padding = dp(2f)
        val rad = dp(TgSharedConfig.bubbleRadius.toFloat())
        val nearRad = dp(minOf(6, TgSharedConfig.bubbleRadius).toFloat())
        val smallRad = dp(6f)
        val top = max(b.top, 0)
        val drawFullBottom = true
        val drawFullTop = true
        // 复用 TG 的气泡路径生成逻辑，包含尾巴与邻接圆角
        generatePath(path, b, padding, rad, smallRad, nearRad, top, drawFullBottom, drawFullTop)
        canvas.drawPath(path, paint)
        canvas.drawPath(path, strokePaint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        strokePaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT

    private fun generatePath(
        path: Path,
        bounds: Rect,
        padding: Int,
        rad: Int,
        smallRad: Int,
        nearRad: Int,
        top: Int,
        drawFullBottom: Boolean,
        drawFullTop: Boolean
    ) {
        path.rewind()
        var radius = rad
        val heightHalf = (bounds.height() - padding) / 2
        if (radius > heightHalf) {
            radius = heightHalf
        }
        if (out) {
            if (drawFullBubble || drawFullBottom) {
                val radToUse = if (botButtonsBottom) nearRad else radius
                path.moveTo(bounds.right - dpF(2.6f), (bounds.bottom - padding).toFloat())
                path.lineTo(bounds.left + padding + radToUse.toFloat(), (bounds.bottom - padding).toFloat())
                tmpRect.set(
                    (bounds.left + padding).toFloat(),
                    (bounds.bottom - padding - radToUse * 2).toFloat(),
                    (bounds.left + padding + radToUse * 2).toFloat(),
                    (bounds.bottom - padding).toFloat()
                )
                path.arcTo(tmpRect, 90f, 90f, false)
            } else {
                path.moveTo(bounds.right - dpF(8f), (top - topY + currentBackgroundHeight).toFloat())
                path.lineTo((bounds.left + padding).toFloat(), (top - topY + currentBackgroundHeight).toFloat())
            }
            if (drawFullBubble || drawFullTop) {
                path.lineTo((bounds.left + padding).toFloat(), (bounds.top + padding + radius).toFloat())
                tmpRect.set(
                    (bounds.left + padding).toFloat(),
                    (bounds.top + padding).toFloat(),
                    (bounds.left + padding + radius * 2).toFloat(),
                    (bounds.top + padding + radius * 2).toFloat()
                )
                path.arcTo(tmpRect, 180f, 90f, false)

                val radToUse = if (isTopNear) nearRad else radius
                path.lineTo(bounds.right - dpF(8f) - radToUse, (bounds.top + padding).toFloat())
                tmpRect.set(
                    (bounds.right - dpF(8f) - radToUse * 2),
                    (bounds.top + padding).toFloat(),
                    (bounds.right - dpF(8f)),
                    (bounds.top + padding + radToUse * 2).toFloat()
                )
                path.arcTo(tmpRect, 270f, 90f, false)
            } else {
                path.lineTo((bounds.left + padding).toFloat(), (top - topY - dpF(2f)).toFloat())
                path.lineTo(bounds.right - dpF(8f), (top - topY - dpF(2f)).toFloat())
            }
            if (drawFullBubble || drawFullBottom) {
                path.lineTo(bounds.right - dpF(8f), (bounds.bottom - padding - smallRad - dpF(3f)).toFloat())
                tmpRect.set(
                    (bounds.right - dpF(8f)),
                    (bounds.bottom - padding - smallRad * 2 - dpF(9f)).toFloat(),
                    (bounds.right - dpF(7f) + smallRad * 2),
                    (bounds.bottom - padding - dpF(1f)).toFloat()
                )
                path.arcTo(tmpRect, 180f, -83f, false)
            } else {
                path.lineTo(bounds.right - dpF(8f), (top - topY + currentBackgroundHeight).toFloat())
            }
        } else {
            if (drawFullBubble || drawFullBottom) {
                val radToUse = if (botButtonsBottom) nearRad else radius
                path.moveTo(bounds.left + dpF(2.6f), (bounds.bottom - padding).toFloat())
                path.lineTo((bounds.right - padding - radToUse).toFloat(), (bounds.bottom - padding).toFloat())
                tmpRect.set(
                    (bounds.right - padding - radToUse * 2).toFloat(),
                    (bounds.bottom - padding - radToUse * 2).toFloat(),
                    (bounds.right - padding).toFloat(),
                    (bounds.bottom - padding).toFloat()
                )
                path.arcTo(tmpRect, 90f, -90f, false)
            } else {
                path.moveTo(bounds.left + dpF(8f), (top - topY + currentBackgroundHeight).toFloat())
                path.lineTo((bounds.right - padding).toFloat(), (top - topY + currentBackgroundHeight).toFloat())
            }
            if (drawFullBubble || drawFullTop) {
                path.lineTo((bounds.right - padding).toFloat(), (bounds.top + padding + radius).toFloat())
                tmpRect.set(
                    (bounds.right - padding - radius * 2).toFloat(),
                    (bounds.top + padding).toFloat(),
                    (bounds.right - padding).toFloat(),
                    (bounds.top + padding + radius * 2).toFloat()
                )
                path.arcTo(tmpRect, 0f, -90f, false)

                val radToUse = if (isTopNear) nearRad else radius
                path.lineTo(bounds.left + dpF(8f) + radToUse, (bounds.top + padding).toFloat())
                tmpRect.set(
                    (bounds.left + dpF(8f)),
                    (bounds.top + padding).toFloat(),
                    (bounds.left + dpF(8f) + radToUse * 2),
                    (bounds.top + padding + radToUse * 2).toFloat()
                )
                path.arcTo(tmpRect, 270f, -90f, false)
            } else {
                path.lineTo((bounds.right - padding).toFloat(), (top - topY - dpF(2f)).toFloat())
                path.lineTo(bounds.left + dpF(8f), (top - topY - dpF(2f)).toFloat())
            }
            if (drawFullBubble || drawFullBottom) {
                path.lineTo(bounds.left + dpF(8f), (bounds.bottom - padding - smallRad - dpF(3f)).toFloat())
                tmpRect.set(
                    (bounds.left + dpF(7f) - smallRad * 2),
                    (bounds.bottom - padding - smallRad * 2 - dpF(9f)).toFloat(),
                    (bounds.left + dpF(8f)),
                    (bounds.bottom - padding - dpF(1f)).toFloat()
                )
                path.arcTo(tmpRect, 0f, 83f, false)
            } else {
                path.lineTo(bounds.left + dpF(8f), (top - topY + currentBackgroundHeight).toFloat())
            }
        }
        path.close()
    }

    private fun dp(value: Float): Int = TgAndroidUtilities.dp(value, TgTheme.density)
    private fun dpF(value: Float): Float = TgAndroidUtilities.dpF(value, TgTheme.density)

    companion object {
        private const val COLOR_OUT = 0xFFE1FFC7.toInt()
        private const val COLOR_IN = 0xFFFFFFFF.toInt()
        private const val COLOR_OUT_STROKE = 0x3300D0DB
        private const val COLOR_IN_STROKE = 0x1A121212
    }
}

