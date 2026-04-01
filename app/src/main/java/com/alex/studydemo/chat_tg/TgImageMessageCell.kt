package com.alex.studydemo.chat_tg

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.text.TextPaint
import android.util.AttributeSet
import com.alex.studydemo.telegram.Theme

/**
 * 图片消息气泡 Cell — 对齐 Figma 设计（node 11086:66925 / 11302:57458）
 *
 * 视觉特性：
 * - 内容为 [AlbumImageContentView]：单图自适应宽高比（默认 1:1）或双图并排，按 Path 裁剪圆角
 * - 时间药丸：底部右侧叠加半透明圆角矩形（rgba(0,0,0,0.3)，radius=16dp，H pad=8dp V pad=3dp）
 * - 状态图标：Canvas Path 绘制双勾（received），替代文字 "✓✓"
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

    /** 收紧内边距，使图片贴近气泡圆角 */
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

    // ── 时间 & 状态画笔 ──────────────────────────────────────────────────────
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

    // ── 时间药丸背景 ─────────────────────────────────────────────────────────
    // Figma: rgba(0,0,0,0.3)，border-radius=16dp
    private val timePillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x4D000000
    }
    private val timePillRect = RectF()

    // ── 状态图标（双勾 Path）────────────────────────────────────────────────
    // Figma icon_16_receive：16×16dp 容器，内部 13.5×8dp 双勾
    private val iconPath = Path()
    private val iconStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // ── 内容视图绑定 ─────────────────────────────────────────────────────────
    override val timeAnchor: TgTimeAnchor = TgTimeAnchorBottomRight
    override val contentView = albumContentView
    override val contentAsTg: TgContentView = albumContentView

    // ── 公开 API ─────────────────────────────────────────────────────────────

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

    // ── 绘制重写 ─────────────────────────────────────────────────────────────

    /**
     * 时间药丸背景（Figma：rgba(0,0,0,0.3) 圆角矩形，完整包裹时间文字 + 状态图标）。
     * 在时间文字和状态图标绘制之前调用，所以使用传入的精确坐标。
     */
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

    /**
     * Canvas Path 双勾状态图标（对齐 Figma icon_16_receive）。
     *
     * 逻辑坐标系：左上角 (0,0)，右下角 (13.5, 8)
     * - 第一勾（已发送）：从 (1.5, 4) → (3.5, 6.5) → (7.5, 1.5)，稍透明
     * - 第二勾（已接收）：从 (5.0, 4) → (7.0, 6.5) → (13.5, 1.5)，主色
     * 整体缩放至 statusWidth × textHeight 的图标区域绘制。
     */
    override fun drawStatusIcon(
        canvas: Canvas,
        statusX: Float, statusY: Float,
        statusWidth: Float,
        paint: TextPaint
    ) {
        // 以文字行高为图标高度，statusWidth 为宽度
        val iconLogicW = 13.5f
        val iconLogicH = 8f
        val drawH = (-paint.ascent() + paint.descent()).coerceAtLeast(1f)
        val drawW = statusWidth.coerceAtLeast(1f)
        val scaleX = drawW / iconLogicW
        val scaleY = drawH / iconLogicH
        val iconTop = statusY + paint.ascent()

        iconStrokePaint.strokeWidth = (drawH * 0.20f).coerceIn(dpF(1.2f), dpF(2f))

        canvas.save()
        canvas.translate(statusX, iconTop)
        canvas.scale(scaleX, scaleY)

        // 第一勾：已发送（稍透明，左侧起始，偏后）
        iconPath.reset()
        iconPath.moveTo(1.5f, 4.0f)
        iconPath.lineTo(3.5f, 6.5f)
        iconPath.lineTo(7.5f, 1.5f)
        iconStrokePaint.color = 0xB3FFFFFF.toInt()   // 70% white
        canvas.drawPath(iconPath, iconStrokePaint)

        // 第二勾：已接收（主体，右侧延伸）
        iconPath.reset()
        iconPath.moveTo(5.0f, 4.0f)
        iconPath.lineTo(7.0f, 6.5f)
        iconPath.lineTo(13.5f, 1.5f)
        iconStrokePaint.color = 0xCCFFFFFF.toInt()   // 80% white
        canvas.drawPath(iconPath, iconStrokePaint)

        canvas.restore()
    }

    override fun getTimePaintForMessage(): TextPaint = if (isOutgoing) mediaTimePaintOut else mediaTimePaintIn

    override fun getStatusPaintForMessage(): TextPaint = mediaStatusPaint
}
