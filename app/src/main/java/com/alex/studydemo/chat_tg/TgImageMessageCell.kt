package com.alex.studydemo.chat_tg

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.text.TextPaint
import android.util.AttributeSet
import com.alex.studydemo.telegram.Theme

/**
 * 图片消息气泡 Cell
 * - 内容为 [AlbumImageContentView]：单图全宽或双图并排 + 1dp 分隔线，按 Path 裁剪
 * - 时间在媒体区底部：半透明圆角药丸（rgba(0,0,0,0.3)，radius=16dp）+ 白字，对齐 Figma 设计
 * - 使用 [Theme.MessageDrawable.TYPE_TEXT] 绘制带尾巴气泡
 */
class TgImageMessageCell @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BaseTgMessageCell(context, attrs) {

    override val bubbleDrawableType: Int = Theme.MessageDrawable.TYPE_TEXT

    /** 按气泡 Path 裁剪子 View，圆角与尾巴轮廓与背景一致 */
    override val clipChildrenToBubblePath: Boolean get() = true

    /** 为尾巴留出空白，避免位图盖住尖角（发出：右下；收到：左下） */
    override fun tailInsetEnd(fromMe: Boolean): Int = if (fromMe) dp(12f) else 0
    override fun tailInsetStart(fromMe: Boolean): Int = if (!fromMe) dp(12f) else 0
    override fun tailInsetBottom(): Int = dp(10f)

    /** 收紧内边距与右侧裁剪，使图片区域贴近气泡圆角（避免文本消息那套 8dp+16dp 留白） */
    override val bubblePaddingTop: Int get() = dp(2f)
    override val bubblePaddingBottom: Int get() = dp(2f)
    override val bubblePaddingStartOut: Int get() = dp(3f)
    override val bubblePaddingEndOut: Int get() = dp(3f)
    override val bubblePaddingStartIn: Int get() = dp(3f)
    override val bubblePaddingEndIn: Int get() = dp(3f)
    override val clipRightInsetOutDp: Float get() = 4f
    override val clipLeftInsetDp: Float get() = 2f
    override val clipTopInsetDp: Float get() = 2f
    override val clipBottomInsetDp: Float get() = 2f

    private val albumContentView = AlbumImageContentView(context)

    private val mediaTimePaintOut = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = TgAndroidUtilities.dp(13f, resources.displayMetrics.density).toFloat()
        color = 0xFFFFFFFF.toInt()
    }
    private val mediaTimePaintIn = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = TgAndroidUtilities.dp(13f, resources.displayMetrics.density).toFloat()
        color = 0xE6FFFFFF.toInt()
    }
    private val mediaStatusPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = TgAndroidUtilities.dp(13f, resources.displayMetrics.density).toFloat()
        color = 0xCCFFFFFF.toInt()
    }
    // Figma: rgba(0,0,0,0.3) 圆角药丸背景，radius=16dp
    private val timePillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x4D000000
    }
    private val timePillRect = RectF()

    /** 时间锚点：右下角 */
    override val timeAnchor: TgTimeAnchor = TgTimeAnchorBottomRight
    /** 内容视图（用于 ViewGroup 布局） */
    override val contentView = albumContentView
    /** 内容视图的 TG 能力 */
    override val contentAsTg: TgContentView = albumContentView

    /** 绑定图片消息：单图 / 双图相册与占位 */
    fun bindImage(
        time: String,
        fromMe: Boolean,
        imageUri: Uri?,
        secondImageUri: Uri? = null,
        albumDual: Boolean = false
    ) {
        bindBase(time, fromMe)
        val dual = albumDual || secondImageUri != null
        albumContentView.setImageUris(imageUri, secondImageUri, forceDual = dual)
    }

    override fun drawTimeBackground(
        canvas: Canvas, bubbleRect: RectF,
        timeX: Float, timeY: Float,
        timeWidth: Float, timeRight: Float
    ) {
        val timePaint = getTimePaintForMessage()
        val pillPaddingH = dpF(8f)
        val pillPaddingV = dpF(3f)
        val pillRadius = dpF(16f)
        timePillRect.set(
            timeX - pillPaddingH,
            timeY + timePaint.ascent() - pillPaddingV,
            timeRight + pillPaddingH,
            timeY + timePaint.descent() + pillPaddingV
        )
        canvas.drawRoundRect(timePillRect, pillRadius, pillRadius, timePillPaint)
    }

    override fun getTimePaintForMessage(): TextPaint = if (isOutgoing) mediaTimePaintOut else mediaTimePaintIn

    override fun getStatusPaintForMessage(): TextPaint = mediaStatusPaint
}
