package com.alex.studydemo.chat_tg

import android.content.Context
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import kotlin.math.max

/**
 * TG 气泡消息基础 ViewGroup
 * - 统一实现气泡绘制、时间文本绘制与布局测量逻辑
 * - 子类通过提供内容视图与时间锚点，获得文本/图片/视频/文件等不同展示能力
 * - 兼容“时间内联到末行”与“右下角时间行”两种策略，贴近 TG 行为
 */
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
    private val lastBubbleRect = RectF()
    private val drawBubbleRect = RectF()
    private var bubbleDrawable: TgMessageDrawable = TgMessageDrawable(true)
    private val transitionParams = TransitionParams()
    private var transitionAnimator: ValueAnimator? = null

    // TG 文本气泡内边距与时间布局参数
    private val paddingStartOut = dp(12f)
    private val paddingEndOut = dp(16f)
    private val paddingStartIn = dp(16f)
    private val paddingEndIn = dp(12f)
    private val paddingTop = dp(8f)
    private val paddingBottom = dp(8f)
    private val timeRowHeight = dp(16f)
    private val timeExtraWidth = dp(10f)
    // 消息状态（双勾等）与时间之间的间距
    private val statusGap = dp(6f)
    private val extraSpacing = dp(4f)
    private val maxBubbleWidthRatio = 0.72f

    // 时间定位策略（文本内联 / 右下角）
    protected abstract val timeAnchor: TgTimeAnchor
    // 文本是否需要“行内时间”规则
    protected open val inlineTimeWithText: Boolean = false
    // 内容 View（文本/图片/视频/文件）
    protected abstract val contentView: View
    protected abstract val contentAsTg: TgContentView

    // 额外内容视图（用户名、引用、翻译、点赞等，来自外部 XML）
    // 用户名：置于气泡内容顶部
    private var userNameView: View? = null
    // 引用：位于用户名下方、内容视图上方
    private var replyView: View? = null
    // 翻译：位于内容视图下方
    private var translateView: View? = null
    // 点赞/反应：锚定到气泡底部左侧
    private var liveView: View? = null

    // 时间是否内联 / 是否需要换行
    private var timeInline = false
    private var timeWrapped = false

    private var contentAttached = false

    // 消息状态绘制与宽度计算（简化为文本“✓✓”，可替换为图标）
    private val statusPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = TgAndroidUtilities.dp(13f, resources.displayMetrics.density).toFloat()
        color = 0xFF00B1BA.toInt()
    }
    private var showStatus: Boolean = true
    private var statusText: String = "✓✓"

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

    /** 设置“用户名” View（为空则不显示） */
    fun setUserNameView(view: View?) {
        userNameView?.let { removeView(it) }
        userNameView = view
        view?.let { addView(it) }
        requestLayout(); invalidate()
    }
    /** 设置“引用/转发” View（为空则不显示） */
    fun setReplyView(view: View?) {
        replyView?.let { removeView(it) }
        replyView = view
        view?.let { addView(it) }
        requestLayout(); invalidate()
    }
    /** 设置“翻译” View（为空则不显示） */
    fun setTranslateView(view: View?) {
        translateView?.let { removeView(it) }
        translateView = view
        view?.let { addView(it) }
        requestLayout(); invalidate()
    }
    /** 设置“点赞/反应” View（为空则不显示） */
    fun setLiveView(view: View?) {
        liveView?.let { removeView(it) }
        liveView = view
        view?.let { addView(it) }
        requestLayout(); invalidate()
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
        val statusWidth = if (showStatus) statusPaint.measureText(statusText).toInt() else 0
        var textWidth = contentAsTg.getContentWidth()
        var lastLineWidth = contentAsTg.getLastLineWidth()

        // 文本行内时间：先用完整宽度判断是否能内联
        timeInline = inlineTimeWithText &&
            lastLineWidth + timeExtraWidth + timeWidth + statusWidth + (if (showStatus) statusGap else 0) <= contentWidth
        timeWrapped = inlineTimeWithText && !timeInline

        // 如果需要行内时间，则为文本预留时间宽度，避免重叠
        if (inlineTimeWithText && timeInline && textContentView != null) {
            val reserve = timeWidth + timeExtraWidth + statusWidth + (if (showStatus) statusGap else 0)
            textContentView.setReservedRight(reserve)
            textContentView.measure(
                MeasureSpec.makeMeasureSpec(max(1, contentWidth), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            textWidth = contentAsTg.getContentWidth()
            lastLineWidth = contentAsTg.getLastLineWidth()
            // 重新判断内联条件，避免预留空间后仍然重叠
            timeInline = lastLineWidth + timeExtraWidth + timeWidth + statusWidth + (if (showStatus) statusGap else 0) <= contentWidth
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

        val inlineWidth = lastLineWidth + timeExtraWidth + timeWidth + statusWidth + (if (showStatus) statusGap else 0)
        val bubbleContentWidth = when {
            inlineTimeWithText && timeInline -> max(textWidth, inlineWidth)
            inlineTimeWithText && timeWrapped -> max(textWidth, contentWidth)
            else -> max(textWidth, timeWidth)
        }

        val bubbleWidth = bubbleContentWidth + getPaddingStartLocal() + getPaddingEndLocal()
        val contentHeight = contentView.measuredHeight

        // 计算顶部模块（用户名）与引用块的高度累加
        val userNameH = measureOptionalChild(userNameView, bubbleContentWidth)
        val replyH = measureOptionalChild(replyView, bubbleContentWidth)
        val topBlocksH = listOf(userNameH, replyH).filter { it > 0 }.sum() + if ((userNameH + replyH) > 0) extraSpacing else 0

        // 计算底部模块（翻译块）高度
        val translateH = measureOptionalChild(translateView, bubbleContentWidth)
        val bottomBlocksH = if (translateH > 0) translateH + extraSpacing else 0

        // 当存在额外块并使用“行内时间”策略时，强制时间换行（避免重叠）
        if ((topBlocksH > 0 || bottomBlocksH > 0) && inlineTimeWithText) {
            timeInline = false
            timeWrapped = true
        }

        // 为底部 LiveView 预留空间（锚定底部左侧）
        val liveH = measureOptionalChild(liveView, bubbleContentWidth)
        val liveReserve = if (liveH > 0) liveH + extraSpacing else 0

        // 气泡总高度 = 顶部块 + 内容 + 底部块 + 时间行（可选） + Live 预留 + 内边距
        val bubbleHeight = topBlocksH + contentHeight + bottomBlocksH + paddingTop + paddingBottom + liveReserve + if (timeWrapped) timeRowHeight else 0
        val totalHeight = bubbleHeight + dp(12f)
        setMeasuredDimension(width, totalHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val oldRect = RectF(lastBubbleRect)
        val width = width.toFloat()
        val timePaint = if (fromMe) timePaintOut else timePaintIn
        val maxBubbleWidth = (width * maxBubbleWidthRatio).toInt()
        val contentWidth = maxBubbleWidth - (getPaddingStartLocal() + getPaddingEndLocal())
        val timeWidth = timePaint.measureText(timeText).toInt()
        val statusWidth = if (showStatus) statusPaint.measureText(statusText).toInt() else 0
        val textWidth = contentAsTg.getContentWidth()
        val lastLineWidth = contentAsTg.getLastLineWidth()
        val inlineWidth = lastLineWidth + timeExtraWidth + timeWidth + statusWidth + (if (showStatus) statusGap else 0)
        val bubbleContentWidth = when {
            inlineTimeWithText && timeInline -> max(textWidth, inlineWidth)
            inlineTimeWithText && timeWrapped -> max(textWidth, contentWidth)
            else -> max(textWidth, timeWidth)
        }
        val bubbleWidth = bubbleContentWidth + getPaddingStartLocal() + getPaddingEndLocal()
        val contentHeight = contentView.measuredHeight
        val userNameH = userNameView?.measuredHeight ?: 0
        val replyH = replyView?.measuredHeight ?: 0
        val translateH = translateView?.measuredHeight ?: 0
        val topBlocksH = listOf(userNameH, replyH).filter { it > 0 }.sum() + if ((userNameH + replyH) > 0) extraSpacing else 0
        val bottomBlocksH = if (translateH > 0) translateH + extraSpacing else 0
        val liveH = liveView?.measuredHeight ?: 0
        val liveReserve = if (liveH > 0) liveH + extraSpacing else 0
        val bubbleHeight = topBlocksH + contentHeight + bottomBlocksH + paddingTop + paddingBottom + liveReserve + if (timeWrapped) timeRowHeight else 0
        val left = if (fromMe) width - bubbleWidth - dpF(8f) else dpF(8f)
        val top = dpF(6f)
        bubbleRect.set(left, top, left + bubbleWidth, top + bubbleHeight)
        if (transitionParams.awaitingLayout) {
            // 如果 startRect 还没设置，使用旧的 rect（如果存在）或当前 rect
            if (transitionParams.startRect.isEmpty) {
                if (!oldRect.isEmpty) {
                    transitionParams.startRect.set(oldRect)
                } else {
                    transitionParams.startRect.set(bubbleRect)
                }
            }
            // 设置 endRect 为新的 rect
            transitionParams.endRect.set(bubbleRect)
            transitionParams.awaitingLayout = false
        } else if (transitionParams.isRunning) {
            // 如果动画正在运行，更新 endRect（处理动画过程中的 layout）
            transitionParams.endRect.set(bubbleRect)
        }
        lastBubbleRect.set(bubbleRect)

        // 1) 顶部：用户名
        var cy = (bubbleRect.top + paddingTop).toInt()
        val cx = (bubbleRect.left + getPaddingStartLocal()).toInt()
        userNameView?.let { child ->
            child.layout(cx, cy, cx + child.measuredWidth, cy + child.measuredHeight)
            cy += child.measuredHeight + extraSpacing
        }
        // 2) 引用/转发
        replyView?.let { child ->
            child.layout(cx, cy, cx + child.measuredWidth, cy + child.measuredHeight)
            cy += child.measuredHeight + extraSpacing
        }

        contentView.layout(
            cx,
            cy,
            cx + contentView.measuredWidth,
            cy + contentView.measuredHeight
        )
        cy += contentView.measuredHeight + extraSpacing

        // 3) 底部：翻译
        translateView?.let { child ->
            child.layout(cx, cy, cx + child.measuredWidth, cy + child.measuredHeight)
        }
        // 4) LiveView 锚定气泡底部左侧
        liveView?.let { child ->
            val ly = (bubbleRect.bottom - paddingBottom - child.measuredHeight).toInt()
            child.layout(cx, ly, cx + child.measuredWidth, ly + child.measuredHeight)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        drawBubble(canvas)
        super.dispatchDraw(canvas)
        drawTime(canvas)
    }

    private fun drawBubble(canvas: Canvas) {
        val rect = getDrawBubbleRect(drawBubbleRect)
        bubbleDrawable.bounds = android.graphics.Rect(
            rect.left.toInt(),
            rect.top.toInt(),
            rect.right.toInt(),
            rect.bottom.toInt()
        )
        bubbleDrawable.draw(canvas)
    }

    private fun drawTime(canvas: Canvas) {
        val timePaint = if (fromMe) timePaintOut else timePaintIn
        val timeWidthF = timePaint.measureText(timeText)
        val statusWidthF = if (showStatus) statusPaint.measureText(statusText) else 0f
        val rect = getDrawBubbleRect(drawBubbleRect)
        val lastBaseline = contentAsTg.getLastLineBaseline()?.let { it + rect.top + paddingTop }
        val contentWidth = rect.width() - getPaddingStartLocal() - getPaddingEndLocal()
        val lastLineWidth = contentAsTg.getLastLineWidth().toFloat()
        val inlineAllowed = inlineTimeWithText && timeInline &&
            lastLineWidth + timeExtraWidth + timeWidthF + (if (showStatus) statusWidthF + statusGap else 0f) <= contentWidth + 0.5f
        // 时间与消息状态均右下角对齐：最右为状态，左侧为时间
        val timeX = rect.right - getPaddingEndLocal().toFloat() - (if (showStatus) statusWidthF + statusGap else 0f) - timeWidthF
        var timeY = if (inlineAllowed) {
            // 行内时间：基线略低于末行
            timeAnchor.getTimeY(rect, lastBaseline, timePaint, paddingBottom.toFloat())
        } else {
            // 行内条件不满足时，按底部时间行绘制避免重叠
            TgTimeAnchorBottomRight.getTimeY(rect, lastBaseline, timePaint, paddingBottom.toFloat())
        }
        val alphaFactor = if (transitionParams.isRunning &&
            (transitionParams.payloads.contains(TgMessagePayloads.TIME) ||
                transitionParams.payloads.contains(TgMessagePayloads.LAYOUT))) {
            transitionParams.animateChangeProgress
        } else {
            1f
        }
        val baseTimeAlpha = timePaint.alpha
        val baseStatusAlpha = statusPaint.alpha
        timePaint.alpha = (baseTimeAlpha * alphaFactor).toInt().coerceIn(0, 255)
        statusPaint.alpha = (baseStatusAlpha * alphaFactor).toInt().coerceIn(0, 255)
        canvas.drawText(timeText, timeX, timeY, timePaint)
        // 绘制消息状态（紧随时间右侧）
        if (showStatus && statusWidthF > 0f) {
            val statusX = rect.right - getPaddingEndLocal().toFloat() - statusWidthF
            val statusY = timeY
            canvas.drawText(statusText, statusX, statusY, statusPaint)
        }
        timePaint.alpha = baseTimeAlpha
        statusPaint.alpha = baseStatusAlpha
    }

    /** 可选子 View 测量（为空返回 0，高度用于累加） */
    private fun measureOptionalChild(child: View?, maxWidth: Int): Int {
        child ?: return 0
        val widthSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST)
        val heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        child.measure(widthSpec, heightSpec)
        return child.measuredHeight
    }

    private fun getPaddingStartLocal(): Int = if (fromMe) paddingStartOut else paddingStartIn
    private fun getPaddingEndLocal(): Int = if (fromMe) paddingEndOut else paddingEndIn

    protected fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()
    protected fun dpF(value: Float): Float = TgAndroidUtilities.dpF(value, resources.displayMetrics.density)

    /** 启动基于 Diff payload 的过渡动画 */
    fun runTransition(payloads: Set<String>) {
        transitionAnimator?.cancel()
        transitionParams.payloads = payloads
        transitionParams.animateChangeProgress = 0f
        transitionParams.isRunning = false // 先不启动，等 layout 完成
        transitionParams.awaitingLayout = true
        // 保存当前 rect 作为 startRect（如果还没有保存过）
        if (transitionParams.startRect.isEmpty && !lastBubbleRect.isEmpty) {
            transitionParams.startRect.set(lastBubbleRect)
        }
        requestLayout()
        // 延迟启动动画，确保 layout 完成
        post {
            if (transitionParams.awaitingLayout) {
                // 如果 layout 还没完成，再等一帧
                post {
                    startTransitionAnimation(payloads)
                }
            } else {
                startTransitionAnimation(payloads)
            }
        }
    }

    private fun startTransitionAnimation(payloads: Set<String>) {
        // 确保 startRect 和 endRect 都已设置
        if (transitionParams.startRect.isEmpty || transitionParams.endRect.isEmpty) {
            // 如果还没准备好，使用当前 rect
            if (transitionParams.startRect.isEmpty) {
                transitionParams.startRect.set(bubbleRect)
            }
            if (transitionParams.endRect.isEmpty) {
                transitionParams.endRect.set(bubbleRect)
            }
        }
        transitionParams.isRunning = true
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220L
            interpolator = PathInterpolator(0.2f, 0f, 0.2f, 1f)
            addUpdateListener {
                val progress = it.animatedValue as Float
                transitionParams.animateChangeProgress = progress
                applyExtraViewProgress(payloads, progress)
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    finishTransition()
                }

                override fun onAnimationEnd(animation: Animator) {
                    finishTransition()
                }
            })
        }
        transitionAnimator = animator
        animator.start()
    }

    private fun finishTransition() {
        transitionParams.animateChangeProgress = 1f
        transitionParams.isRunning = false
        transitionParams.awaitingLayout = false
        applyExtraViewProgress(transitionParams.payloads, 1f)
        // 清理 rect，准备下次动画
        transitionParams.startRect.setEmpty()
        transitionParams.endRect.setEmpty()
        transitionAnimator = null
        invalidate()
    }

    private fun applyExtraViewProgress(payloads: Set<String>, progress: Float) {
        val offset = dpF(4f) * (1f - progress)
        if (payloads.contains(TgMessagePayloads.USER_NAME)) {
            userNameView?.apply { alpha = progress; translationY = offset }
        }
        if (payloads.contains(TgMessagePayloads.QUOTE)) {
            replyView?.apply { alpha = progress; translationY = offset }
        }
        if (payloads.contains(TgMessagePayloads.TRANSLATION)) {
            translateView?.apply { alpha = progress; translationY = offset }
        }
        if (payloads.contains(TgMessagePayloads.REACTIONS)) {
            liveView?.apply { alpha = progress; translationY = offset }
        }
    }

    private fun getDrawBubbleRect(out: RectF): RectF {
        // 只有在动画运行且 startRect 和 endRect 都有效时才插值
        if (transitionParams.isRunning && 
            transitionParams.startRect.width() > 0 && transitionParams.startRect.height() > 0 &&
            transitionParams.endRect.width() > 0 && transitionParams.endRect.height() > 0) {
            val p = transitionParams.animateChangeProgress
            out.set(
                lerp(transitionParams.startRect.left, transitionParams.endRect.left, p),
                lerp(transitionParams.startRect.top, transitionParams.endRect.top, p),
                lerp(transitionParams.startRect.right, transitionParams.endRect.right, p),
                lerp(transitionParams.startRect.bottom, transitionParams.endRect.bottom, p)
            )
        } else {
            // 动画未运行或数据无效时，使用当前 rect
            out.set(bubbleRect)
        }
        return out
    }

    private fun lerp(start: Float, end: Float, progress: Float): Float = start + (end - start) * progress
}

/** 气泡过渡参数（简化版，用于驱动插值动画） */
class TransitionParams {
    var animateChangeProgress: Float = 1f
    var isRunning: Boolean = false
    var awaitingLayout: Boolean = false
    var payloads: Set<String> = emptySet()
    val startRect: RectF = RectF()
    val endRect: RectF = RectF()
}
