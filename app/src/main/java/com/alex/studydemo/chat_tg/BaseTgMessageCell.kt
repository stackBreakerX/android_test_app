package com.alex.studydemo.chat_tg

import android.content.Context
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
 import android.graphics.Path
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
        // 设置裁剪，确保子 view 不会超出边界
        setClipChildren(true)
        setClipToPadding(true)
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
        
        // 在动画期间，确保使用新的内容尺寸
        // TextContentView 的 getContentWidth() 已经在动画期间返回新 blocks 的宽度
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
        
        // 当执行“向上不影响邻居”的背景尺寸变化动画时，如果是变大过程，保持测量高度为旧值，避免推动上方气泡
        // 参考 Telegram：动画期间保持测量高度不变，只通过绘制插值来显示变化
        val animatingBackground = transitionParams.isRunning &&
                transitionParams.startRect.height() > 0f &&
                transitionParams.endRect.height() > 0f &&
                transitionParams.animateChangeProgress < 1f
        
        // 判断是否为“变大”过程，使用 startRect 或 lastBubbleRect 作为旧高度来源
        val startH = if (transitionParams.startRect.height() > 0f) transitionParams.startRect.height()
            else if (lastBubbleRect.height() > 0f) lastBubbleRect.height() else 0f
        val predictedIncreasing = startH > 0f && bubbleHeight.toFloat() > startH
        val increasing = (transitionParams.endRect.height() > transitionParams.startRect.height()) || predictedIncreasing
        val inTransition = transitionParams.awaitingLayout || animatingBackground
        val measuredHeight = if (increasing && inTransition && startH > 0f) {
            (startH + dpF(12f)).toInt()
        } else {
            totalHeight
        }
        setMeasuredDimension(width, measuredHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val oldRect = RectF(lastBubbleRect)
        val width = width.toFloat()
        
        // 如果正在动画，使用插值后的 rect，不重新计算 bubbleRect
        val isAnimating = transitionParams.isRunning && 
            transitionParams.startRect.width() > 0 && transitionParams.startRect.height() > 0 &&
            transitionParams.endRect.width() > 0 && transitionParams.endRect.height() > 0
        
        if (!isAnimating) {
            // 只有在非动画状态下才重新计算 bubbleRect
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
                // 设置 endRect 为新的 rect
                transitionParams.endRect.set(bubbleRect)
                transitionParams.awaitingLayout = false
                // Layout 完成后，延迟启动动画（确保 endRect 已设置）
                post {
                    startTransitionAnimation()
                }
            }
            lastBubbleRect.set(bubbleRect)
        }
        
        // 在动画期间，子 view 的布局应该基于插值后的 rect，而不是 bubbleRect
        val layoutRect = if (isAnimating) {
            // 顶部锚定；根据消息方向决定锚点：出站对齐右边距，入站对齐左边距
            val p = transitionParams.animateChangeProgress
            val start = transitionParams.startRect
            val end = transitionParams.endRect
            val w = lerp(start.width(), end.width(), p)
            val h = lerp(start.height(), end.height(), p)
            val top = start.top
            if (fromMe) {
                // 右侧固定
                val right = start.right
                RectF(right - w, top, right, top + h)
            } else {
                // 左侧固定
                val left = start.left
                RectF(left, top, left + w, top + h)
            }
        } else {
            bubbleRect
        }
        
        // 1) 顶部：用户名（根据方向决定左/右对齐）
        var cy = (layoutRect.top + paddingTop).toInt()
        fun anchorX(childWidth: Int): Int {
            return if (fromMe) {
                (layoutRect.right - getPaddingEndLocal() - childWidth).toInt()
            } else {
                (layoutRect.left + getPaddingStartLocal()).toInt()
            }
        }
        userNameView?.let { child ->
            val ax = anchorX(child.measuredWidth)
            child.layout(ax, cy, ax + child.measuredWidth, cy + child.measuredHeight)
            cy += child.measuredHeight + extraSpacing
        }
        // 2) 引用/转发（方向对齐）
        replyView?.let { child ->
            val ax = anchorX(child.measuredWidth)
            child.layout(ax, cy, ax + child.measuredWidth, cy + child.measuredHeight)
            cy += child.measuredHeight + extraSpacing
        }

        // 确保 contentView 的布局宽度不超过气泡内容宽度，防止文本溢出
        // 使用实际的气泡内容宽度（基于 bubbleRect，这是最终布局的宽度）
        val actualBubbleContentWidth = (bubbleRect.width() - getPaddingStartLocal() - getPaddingEndLocal()).toInt()
        val maxContentWidth = actualBubbleContentWidth.coerceAtLeast(1)
        // 强制限制布局宽度，确保不会超出气泡边界
        val contentLayoutWidth = kotlin.math.min(contentView.measuredWidth, maxContentWidth)
        val contentAx = anchorX(contentView.measuredWidth)
        contentView.layout(
            contentAx,
            cy,
            contentAx + contentView.measuredWidth,
            cy + contentView.measuredHeight
        )
        cy += contentView.measuredHeight + extraSpacing

        // 3) 底部：翻译
        translateView?.let { child ->
            val ax = anchorX(child.measuredWidth)
            child.layout(ax, cy, ax + child.measuredWidth, cy + child.measuredHeight)
        }
        // 4) LiveView 锚定气泡底部左侧
        liveView?.let { child ->
            val ly = (layoutRect.bottom - paddingBottom - child.measuredHeight).toInt()
            val ax = anchorX(child.measuredWidth)
            child.layout(ax, ly, ax + child.measuredWidth, ly + child.measuredHeight)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        // 先绘制气泡
        drawBubble(canvas)
        // 参考 Telegram：裁剪区域基于气泡的实际边界（bubbleRect），而不是动画插值后的 rect
        // 这样可以确保文本始终被裁剪在气泡的实际边界内，防止溢出
        // 参考 Telegram：r.left + dp(4), r.top + dp(4), r.right - dp(10/4), r.bottom - dp(4)
        canvas.save()
        if (fromMe) {
            // 右侧消息：右边距 dp(10)（为时间/状态预留更多空间）
            canvas.clipRect(
                bubbleRect.left + dpF(4f), bubbleRect.top + dpF(4f),
                bubbleRect.right - dpF(10f), bubbleRect.bottom - dpF(4f)
            )
        } else {
            // 左侧消息：右边距 dp(4)
            canvas.clipRect(
                bubbleRect.left + dpF(4f), bubbleRect.top + dpF(4f),
                bubbleRect.right - dpF(4f), bubbleRect.bottom - dpF(4f)
            )
        }
        // 获取当前过渡中的气泡绘制矩形（插值后），用于 translate
        val rect = getDrawBubbleRect(drawBubbleRect)
        // translate 使子 view 的内容对齐到动画后的位置
        val dx = rect.left - bubbleRect.left
        val dy = rect.top - bubbleRect.top
        canvas.translate(dx, dy)
        super.dispatchDraw(canvas)
        canvas.restore()
        // 绘制时间与状态（使用过渡矩形坐标）
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
        // 在过渡期间，为避免行内判定导致时间/状态消失，强制使用底部右对齐策略
        val isTransition = transitionParams.isRunning
        val lastBaseline = if (isTransition) null else contentAsTg.getLastLineBaseline()?.let { it + rect.top + paddingTop }
        val contentWidth = rect.width() - getPaddingStartLocal() - getPaddingEndLocal()
        val lastLineWidth = if (isTransition) 0f else contentAsTg.getLastLineWidth().toFloat()
        val inlineAllowed = !isTransition && inlineTimeWithText && timeInline &&
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

    /** 准备过渡动画（在更新内容前调用，保存 startRect） */
    fun prepareTransition(payloads: Set<String>) {
        transitionAnimator?.cancel()
        transitionParams.payloads = payloads
        transitionParams.animateChangeProgress = 0f
        transitionParams.isRunning = false
        transitionParams.awaitingLayout = true
        // 保存当前 rect 作为 startRect
        if (!lastBubbleRect.isEmpty) {
            transitionParams.startRect.set(lastBubbleRect)
        } else if (!bubbleRect.isEmpty) {
            transitionParams.startRect.set(bubbleRect)
        }
        // 清空 endRect，等待 layout 完成后设置
        transitionParams.endRect.setEmpty()
    }

    /** 启动过渡动画（在内容更新和 layout 完成后调用） */
    fun startTransitionAnimation() {
        // 确保 startRect 和 endRect 都已设置
        if (transitionParams.startRect.isEmpty) {
            // 如果 startRect 还没设置，使用当前 rect
            transitionParams.startRect.set(bubbleRect)
        }
        if (transitionParams.endRect.isEmpty) {
            // 如果 endRect 还没设置，使用当前 rect（layout 应该已经完成）
            transitionParams.endRect.set(bubbleRect)
        }
        
        // 检查 startRect 和 endRect 是否相同，如果相同则不需要动画
        if (transitionParams.startRect.left == transitionParams.endRect.left &&
            transitionParams.startRect.top == transitionParams.endRect.top &&
            transitionParams.startRect.right == transitionParams.endRect.right &&
            transitionParams.startRect.bottom == transitionParams.endRect.bottom) {
            // 尺寸没有变化，直接完成
            finishTransition()
            return
        }
        
        transitionParams.isRunning = true
        transitionParams.awaitingLayout = false
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220L
            interpolator = PathInterpolator(0.2f, 0f, 0.2f, 1f)
            addUpdateListener {
                val progress = it.animatedValue as Float
                transitionParams.animateChangeProgress = progress
                applyExtraViewProgress(transitionParams.payloads, progress)
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
        // 更新 lastBubbleRect 为最终状态
        lastBubbleRect.set(bubbleRect)
        // 清理 rect，准备下次动画
        transitionParams.startRect.setEmpty()
        transitionParams.endRect.setEmpty()
        transitionAnimator = null
        // 动画结束后，如果最终高度与起始高度不同，则申请一次布局以应用最终高度
        val startH = transitionParams.startRect.height()
        val endH = transitionParams.endRect.height()
        if (endH > 0f && startH != endH) {
            requestLayout()
        } else {
            invalidate()
        }
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
            val start = transitionParams.startRect
            val end = transitionParams.endRect
            val w = lerp(start.width(), end.width(), p)
            val h = lerp(start.height(), end.height(), p)
            val top = start.top
            if (fromMe) {
                val right = start.right
                out.set(right - w, top, right, top + h)
            } else {
                val left = start.left
                out.set(left, top, left + w, top + h)
            }
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
