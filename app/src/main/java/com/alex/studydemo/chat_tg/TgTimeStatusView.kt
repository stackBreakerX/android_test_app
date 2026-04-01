package com.alex.studydemo.chat_tg

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint
import android.view.View

/**
 * 消息时间 + 状态公共 View，兼容所有消息类型。
 *
 * 样式：
 *  - [Style.PLAIN]：透明背景，彩色文字（文本/文件气泡内时间行）
 *  - [Style.BUBBLE]：半透明药丸背景 + 白色文字 + Canvas Path 双勾（图片/视频媒体叠加层）
 *
 * 父 ViewGroup ([BaseTgMessageCell]) 负责调用 [bind] 并定位本 View。
 */
class TgTimeStatusView(context: Context) : View(context) {

    enum class Style { PLAIN, BUBBLE }

    private val density = resources.displayMetrics.density

    // ── PLAIN 样式画笔（文本/文件气泡） ──────────────────────────────────
    private val plainTimePaintOut = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = TgAndroidUtilities.dp(13f, density).toFloat()
        color    = 0xFF00B1BA.toInt()
    }
    private val plainTimePaintIn = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = TgAndroidUtilities.dp(13f, density).toFloat()
        color    = 0x7A121212.toInt()
    }
    private val plainStatusPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = TgAndroidUtilities.dp(13f, density).toFloat()
        color    = 0xFF00B1BA.toInt()
    }

    // ── BUBBLE 样式画笔（媒体叠加，白字） ────────────────────────────────
    private val bubbleTimePaintOut = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = TgAndroidUtilities.dp(13f, density).toFloat()
        color    = 0xFFFFFFFF.toInt()
    }
    private val bubbleTimePaintIn = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = TgAndroidUtilities.dp(13f, density).toFloat()
        color    = 0xE6FFFFFF.toInt()
    }

    // ── 药丸背景（BUBBLE 专用）────────────────────────────────────────────
    // Figma: rgba(0,0,0,0.3)，border-radius=16dp，H pad=8dp，V pad=3dp
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x4D000000 }
    private val pillRect  = RectF()

    // ── 状态图标（Canvas Path 双勾，BUBBLE 专用）─────────────────────────
    private val iconPath        = Path()
    private val iconStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style      = Paint.Style.STROKE
        strokeCap  = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // ── 间距常量 ──────────────────────────────────────────────────────────
    private val statusGapPx  = TgAndroidUtilities.dpF(6f,  density)
    private val pillPaddingH = TgAndroidUtilities.dpF(8f,  density)
    private val pillPaddingV = TgAndroidUtilities.dpF(3f,  density)
    private val pillRadius   = TgAndroidUtilities.dpF(16f, density)

    // ── 当前绑定数据 ──────────────────────────────────────────────────────
    private var _style      = Style.PLAIN
    private var _time       = ""
    private var _fromMe     = true
    private var _showStatus = true

    /**
     * 更新显示数据。
     * 不触发 [requestLayout]——由父 [BaseTgMessageCell] 的 [BaseTgMessageCell.bindBase] 负责。
     */
    fun bind(time: String, fromMe: Boolean, style: Style) {
        _time       = time
        _fromMe     = fromMe
        _style      = style
        _showStatus = fromMe   // 仅发出消息显示状态
        invalidate()
    }

    /** 返回当前时间文字适用的画笔（供父 ViewGroup 测量/定位使用） */
    fun getTimePaint(): TextPaint = when (_style) {
        Style.PLAIN  -> if (_fromMe) plainTimePaintOut else plainTimePaintIn
        Style.BUBBLE -> if (_fromMe) bubbleTimePaintOut else bubbleTimePaintIn
    }

    /** 时间文字宽度（px） */
    fun getTimeTextWidth(): Float = getTimePaint().measureText(_time)

    /** 状态图标区域总宽度（含与时间之间的间距；不显示时为 0） */
    fun getStatusTotalWidth(): Float {
        if (!_showStatus) return 0f
        return when (_style) {
            Style.PLAIN -> plainStatusPaint.measureText("✓✓") + statusGapPx
            Style.BUBBLE -> {
                val p     = getTimePaint()
                val textH = -p.ascent() + p.descent()
                textH * 13.5f / 8f + statusGapPx
            }
        }
    }

    /** 内容总宽度（时间 + 状态，不含药丸水平 padding） */
    fun getContentWidth(): Float = getTimeTextWidth() + getStatusTotalWidth()

    // ── 测量 ──────────────────────────────────────────────────────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val paint    = getTimePaint()
        val textH    = -paint.ascent() + paint.descent()
        val contentW = getContentWidth()
        val w: Float
        val h: Float
        if (_style == Style.BUBBLE) {
            w = contentW + pillPaddingH * 2
            h = textH    + pillPaddingV * 2
        } else {
            w = contentW
            h = textH
        }
        setMeasuredDimension(
            w.toInt().coerceAtLeast(1),
            h.toInt().coerceAtLeast(1)
        )
    }

    // ── 绘制 ──────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val paint = getTimePaint()

        val timeX: Float
        val baselineY: Float

        if (_style == Style.BUBBLE) {
            // 绘制半透明药丸背景
            pillRect.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(pillRect, pillRadius, pillRadius, pillPaint)
            timeX     = pillPaddingH
            baselineY = pillPaddingV + (-paint.ascent())
        } else {
            timeX     = 0f
            baselineY = -paint.ascent()
        }

        canvas.drawText(_time, timeX, baselineY, paint)

        if (_showStatus) {
            val statusX = timeX + getTimeTextWidth() + statusGapPx
            if (_style == Style.BUBBLE) {
                val textH = -paint.ascent() + paint.descent()
                drawStatusPath(canvas, statusX, baselineY + paint.ascent(), textH)
            } else {
                canvas.drawText("✓✓", statusX, baselineY, plainStatusPaint)
            }
        }
    }

    /**
     * Canvas Path 双勾状态图标（对齐 Figma icon_16_receive）。
     *
     * 逻辑坐标 (0,0)→(13.5,8)，整体缩放至 statusWidth × textHeight 区域。
     * - 第一勾（稍透明）：(1.5,4)→(3.5,6.5)→(7.5,1.5)
     * - 第二勾（主体）  ：(5.0,4)→(7.0,6.5)→(13.5,1.5)
     */
    private fun drawStatusPath(canvas: Canvas, x: Float, top: Float, drawH: Float) {
        val iconLogicW = 13.5f
        val iconLogicH = 8f
        val drawW      = (getStatusTotalWidth() - statusGapPx).coerceAtLeast(1f)
        val scaleX     = drawW / iconLogicW
        val scaleY     = drawH / iconLogicH
        iconStrokePaint.strokeWidth = (drawH * 0.20f).coerceIn(
            TgAndroidUtilities.dpF(1.2f, density),
            TgAndroidUtilities.dpF(2f,   density)
        )
        canvas.save()
        canvas.translate(x, top)
        canvas.scale(scaleX, scaleY)

        iconPath.reset()
        iconPath.moveTo(1.5f, 4.0f)
        iconPath.lineTo(3.5f, 6.5f)
        iconPath.lineTo(7.5f, 1.5f)
        iconStrokePaint.color = 0xB3FFFFFF.toInt()  // 70% white
        canvas.drawPath(iconPath, iconStrokePaint)

        iconPath.reset()
        iconPath.moveTo(5.0f, 4.0f)
        iconPath.lineTo(7.0f, 6.5f)
        iconPath.lineTo(13.5f, 1.5f)
        iconStrokePaint.color = 0xCCFFFFFF.toInt()  // 80% white
        canvas.drawPath(iconPath, iconStrokePaint)

        canvas.restore()
    }
}
