package com.alex.studydemo.chat_tg

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import kotlin.math.max

abstract class BaseTgMessageCell @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    // 文本/时间画笔（对齐 TG 的字号/颜色）
    private val textPaint = TgTheme.chatMsgTextPaint
    private val timePaintOut = TgTheme.chatTimePaintOut
    private val timePaintIn = TgTheme.chatTimePaintIn

    // 当前消息时间、方向（out=自己发）
    private var timeText: String = ""
    private var fromMe: Boolean = true

    // 气泡区域与绘制器
    private val bubbleRect = RectF()
    private var bubbleDrawable: TgMessageDrawable = TgMessageDrawable(true)

    // TG 文本气泡内边距与时间布局参数
    private val paddingStartOut = dp(12f)
    private val paddingEndOut = dp(16f)
    private val paddingStartIn = dp(16f)
    private val paddingEndIn = dp(12f)
    private val paddingTop = dp(8f)
    private val paddingBottom = dp(8f)
    private val timeRowHeight = dp(16f)
    private val timeExtraWidth = dp(10f)
    private val extraSpacing = dp(4f)
    private val maxBubbleWidthRatio = 0.72f

    // 时间定位策略（文本内联 / 右下角）
    protected abstract val timeAnchor: TgTimeAnchor
    // 文本是否需要“行内时间”规则
    protected open val inlineTimeWithText: Boolean = false
    // 内容 View（文本/图片/视频/文件）
    protected abstract val contentView: View
    protected abstract val contentAsTg: TgContentView

    // 额外内容视图（翻译/引用/点赞等，来自外部 XML）
    private var extraView: View? = null

    // 时间是否内联 / 是否需要换行
    private var timeInline = false
    private var timeWrapped = false

    private var contentAttached = false

    init {
        setWillNotDraw(false)
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 子类的 contentView 在构造完成后再挂载，避免空指针
        if (!contentAttached) {
            addView(contentView)
            contentAttached = true
        }
    }

    fun bindBase(time: String, out: Boolean) {
        timeText = time
        fromMe = out
        bubbleDrawable.setOut(out)
        requestLayout()
        invalidate()
    }

    fun setExtraView(view: View?) {
        extraView?.let { removeView(it) }
        extraView = view
        if (view != null) {
            addView(view)
        }
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 初始化 TG 文本/时间画笔尺寸（只做一次）
        TgTheme.init(resources.displayMetrics.density)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val maxBubbleWidth = (width * maxBubbleWidthRatio).toInt()
        val contentWidth = maxBubbleWidth - (getPaddingStartLocal() + getPaddingEndLocal())

        val textContentView = contentView as? TextContentView
        // 先清掉旧的预留宽度，避免复用导致判断失真
        textContentView?.setReservedRight(0)
        contentView.measure(
            MeasureSpec.makeMeasureSpec(max(1, contentWidth), MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )

        val timePaint = if (fromMe) timePaintOut else timePaintIn
        val timeWidth = timePaint.measureText(timeText).toInt()
        var textWidth = contentAsTg.getContentWidth()
        var lastLineWidth = contentAsTg.getLastLineWidth()

        // 文本行内时间：先用完整宽度判断是否能内联
        timeInline = inlineTimeWithText &&
            lastLineWidth + timeExtraWidth + timeWidth <= contentWidth
        timeWrapped = inlineTimeWithText && !timeInline

        // 如果需要行内时间，则为文本预留时间宽度，避免重叠
        if (inlineTimeWithText && timeInline && textContentView != null) {
            textContentView.setReservedRight(timeWidth + timeExtraWidth)
            textContentView.measure(
                MeasureSpec.makeMeasureSpec(max(1, contentWidth), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            textWidth = contentAsTg.getContentWidth()
            lastLineWidth = contentAsTg.getLastLineWidth()
            // 重新判断内联条件，避免预留空间后仍然重叠
            timeInline = lastLineWidth + timeExtraWidth + timeWidth <= contentWidth
            timeWrapped = inlineTimeWithText && !timeInline
            if (!timeInline) {
                // 退回到“时间换行”的布局，释放预留宽度
                textContentView.setReservedRight(0)
                textContentView.measure(
                    MeasureSpec.makeMeasureSpec(max(1, contentWidth), MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                )
                textWidth = contentAsTg.getContentWidth()
                lastLineWidth = contentAsTg.getLastLineWidth()
            }
        }

        val inlineWidth = lastLineWidth + timeExtraWidth + timeWidth
        val bubbleContentWidth = when {
            inlineTimeWithText && timeInline -> max(textWidth, inlineWidth)
            inlineTimeWithText && timeWrapped -> max(textWidth, contentWidth)
            else -> max(textWidth, timeWidth)
        }

        val bubbleWidth = bubbleContentWidth + getPaddingStartLocal() + getPaddingEndLocal()
        val contentHeight = contentView.measuredHeight

        val extraHeight = measureExtraView(bubbleContentWidth)
        val extraBlockHeight = if (extraHeight > 0) extraHeight + extraSpacing else 0
        if (extraHeight > 0 && inlineTimeWithText) {
            // 有额外块时，文本时间固定在底部行
            timeInline = false
            timeWrapped = true
        }

        val bubbleHeight = contentHeight + paddingTop + paddingBottom + extraBlockHeight + if (timeWrapped) timeRowHeight else 0
        val totalHeight = bubbleHeight + dp(12f)
        setMeasuredDimension(width, totalHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = width.toFloat()
        val timePaint = if (fromMe) timePaintOut else timePaintIn
        val maxBubbleWidth = (width * maxBubbleWidthRatio).toInt()
        val contentWidth = maxBubbleWidth - (getPaddingStartLocal() + getPaddingEndLocal())
        val timeWidth = timePaint.measureText(timeText).toInt()
        val textWidth = contentAsTg.getContentWidth()
        val lastLineWidth = contentAsTg.getLastLineWidth()
        val inlineWidth = lastLineWidth + timeExtraWidth + timeWidth
        val bubbleContentWidth = when {
            inlineTimeWithText && timeInline -> max(textWidth, inlineWidth)
            inlineTimeWithText && timeWrapped -> max(textWidth, contentWidth)
            else -> max(textWidth, timeWidth)
        }
        val bubbleWidth = bubbleContentWidth + getPaddingStartLocal() + getPaddingEndLocal()
        val contentHeight = contentView.measuredHeight
        val extraHeight = extraView?.measuredHeight ?: 0
        val extraBlockHeight = if (extraHeight > 0) extraHeight + extraSpacing else 0
        val bubbleHeight = contentHeight + paddingTop + paddingBottom + extraBlockHeight + if (timeWrapped) timeRowHeight else 0
        val left = if (fromMe) width - bubbleWidth - dpF(8f) else dpF(8f)
        val top = dpF(6f)
        bubbleRect.set(left, top, left + bubbleWidth, top + bubbleHeight)

        contentView.layout(
            (bubbleRect.left + getPaddingStartLocal()).toInt(),
            (bubbleRect.top + paddingTop).toInt(),
            (bubbleRect.left + getPaddingStartLocal() + contentView.measuredWidth).toInt(),
            (bubbleRect.top + paddingTop + contentView.measuredHeight).toInt()
        )

        extraView?.let { child ->
            val cx = (bubbleRect.left + getPaddingStartLocal()).toInt()
            val cy = (bubbleRect.top + paddingTop + contentHeight + extraSpacing).toInt()
            child.layout(
                cx,
                cy,
                cx + child.measuredWidth,
                cy + child.measuredHeight
            )
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        drawBubble(canvas)
        super.dispatchDraw(canvas)
        drawTime(canvas)
    }

    private fun drawBubble(canvas: Canvas) {
        bubbleDrawable.bounds = android.graphics.Rect(
            bubbleRect.left.toInt(),
            bubbleRect.top.toInt(),
            bubbleRect.right.toInt(),
            bubbleRect.bottom.toInt()
        )
        bubbleDrawable.draw(canvas)
    }

    private fun drawTime(canvas: Canvas) {
        val timePaint = if (fromMe) timePaintOut else timePaintIn
        val timeWidth = timePaint.measureText(timeText)
        val lastBaseline = contentAsTg.getLastLineBaseline()?.let { it + bubbleRect.top + paddingTop }
        val contentWidth = bubbleRect.width() - getPaddingStartLocal() - getPaddingEndLocal()
        val lastLineWidth = contentAsTg.getLastLineWidth().toFloat()
        val inlineAllowed = inlineTimeWithText && timeInline &&
            lastLineWidth + timeExtraWidth + timeWidth <= contentWidth + 0.5f
        // 时间始终右对齐在气泡内（TG 行为）
        val timeX = timeAnchor.getTimeX(bubbleRect, timeWidth, getPaddingEndLocal().toFloat())
        val timeY = if (inlineAllowed) {
            // 行内时间：基线略低于末行
            timeAnchor.getTimeY(bubbleRect, lastBaseline, timePaint, paddingBottom.toFloat())
        } else {
            // 行内条件不满足时，按底部时间行绘制避免重叠
            TgTimeAnchorBottomRight.getTimeY(bubbleRect, lastBaseline, timePaint, paddingBottom.toFloat())
        }
        canvas.drawText(timeText, timeX, timeY, timePaint)
    }

    private fun measureExtraView(maxWidth: Int): Int {
        val child = extraView ?: return 0
        val widthSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST)
        val heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        child.measure(widthSpec, heightSpec)
        return child.measuredHeight
    }

    private fun getPaddingStartLocal(): Int = if (fromMe) paddingStartOut else paddingStartIn
    private fun getPaddingEndLocal(): Int = if (fromMe) paddingEndOut else paddingEndIn

    protected fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()
    protected fun dpF(value: Float): Float = TgAndroidUtilities.dpF(value, resources.displayMetrics.density)
}

