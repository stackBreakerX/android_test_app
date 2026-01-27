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
        // 包括 awaitingLayout 期间，也应该使用旧高度，避免在动画开始前就改变高度
        val animatingBackground = (transitionParams.isRunning || transitionParams.awaitingLayout) &&
                transitionParams.startRect.height() > 0f &&
                transitionParams.endRect.height() > 0f
        
        val measuredHeight = if (animatingBackground) {
            // 动画期间，始终使用 startRect 的高度（旧高度），避免推动上方气泡
            // 这样气泡变大时，测量高度不变，只有绘制时才会显示变大效果
            val oldTotalHeight = if (transitionParams.startRect.height() > 0f) {
                // startRect 是气泡 rect，需要加上上下间距（6dp * 2）
                (transitionParams.startRect.height() + dpF(12f)).toInt()
            } else {
                totalHeight
            }
            oldTotalHeight
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
        
        // 参考 Telegram：文本位置固定在气泡的固定边缘
        // 右侧消息（fromMe = true）：文本固定在气泡左边（左对齐）
        // 左侧消息（fromMe = false）：文本固定在气泡右边（右对齐）
        // 动画过程中，如果气泡位置变化（如从长变短），文本应该跟随气泡的固定边缘移动
        
        // 获取动画插值后的气泡位置，用于布局
        // 如果正在等待布局（awaitingLayout），应该使用 startRect，避免文本位置跳到最终位置
        val layoutRect = when {
            isAnimating -> {
                // 动画进行中，使用插值后的位置
                val p = transitionParams.animateChangeProgress
                RectF(
                    lerp(transitionParams.startRect.left, transitionParams.endRect.left, p),
                    lerp(transitionParams.startRect.top, transitionParams.endRect.top, p),
                    lerp(transitionParams.startRect.right, transitionParams.endRect.right, p),
                    lerp(transitionParams.startRect.bottom, transitionParams.endRect.bottom, p)
                )
            }
            transitionParams.awaitingLayout && !transitionParams.startRect.isEmpty -> {
                // 等待布局时，使用 startRect（旧位置），避免文本位置跳到最终位置
                transitionParams.startRect
            }
            else -> {
                // 正常情况，使用当前 bubbleRect
                bubbleRect
            }
        }
        
        // 1) 顶部：用户名
        var cy = (layoutRect.top + paddingTop).toInt()
        val cx = if (fromMe) {
            // 右侧消息：固定在气泡左边（左对齐），跟随动画插值后的位置
            (layoutRect.left + getPaddingStartLocal()).toInt()
        } else {
            // 左侧消息：固定在气泡右边（右对齐），跟随动画插值后的位置
            val userNameWidth = userNameView?.measuredWidth ?: 0
            (layoutRect.right - getPaddingEndLocal() - userNameWidth).toInt()
        }
        userNameView?.let { child ->
            child.layout(cx, cy, cx + child.measuredWidth, cy + child.measuredHeight)
            cy += child.measuredHeight + extraSpacing
        }
        // 2) 引用/转发
        replyView?.let { child ->
            val replyX = if (fromMe) {
                // 右侧消息：固定在气泡左边（左对齐），跟随动画插值后的位置
                (layoutRect.left + getPaddingStartLocal()).toInt()
            } else {
                // 左侧消息：固定在气泡右边（右对齐），跟随动画插值后的位置
                (layoutRect.right - getPaddingEndLocal() - child.measuredWidth).toInt()
            }
            child.layout(replyX, cy, replyX + child.measuredWidth, cy + child.measuredHeight)
            cy += child.measuredHeight + extraSpacing
        }

        // 确保 contentView 的布局宽度不超过气泡内容宽度，防止文本溢出
        val actualBubbleContentWidth = (layoutRect.width() - getPaddingStartLocal() - getPaddingEndLocal()).toInt()
        val maxContentWidth = actualBubbleContentWidth.coerceAtLeast(1)
        // 强制限制布局宽度，确保不会超出气泡边界
        val contentLayoutWidth = kotlin.math.min(contentView.measuredWidth, maxContentWidth)
        
        // 计算 contentView 的布局位置
        val contentX = if (fromMe) {
            // 右侧消息：固定在气泡左边（左对齐），跟随动画插值后的位置
            (layoutRect.left + getPaddingStartLocal()).toInt()
        } else {
            // 左侧消息：固定在气泡右边（右对齐），跟随动画插值后的位置
            (layoutRect.right - getPaddingEndLocal() - contentLayoutWidth).toInt()
        }
        contentView.layout(
            contentX,
            cy,
            contentX + contentLayoutWidth,
            cy + contentView.measuredHeight
        )
        cy += contentView.measuredHeight + extraSpacing

        // 3) 底部：翻译
        translateView?.let { child ->
            val translateX = if (fromMe) {
                // 右侧消息：固定在气泡左边（左对齐），跟随动画插值后的位置
                (layoutRect.left + getPaddingStartLocal()).toInt()
            } else {
                // 左侧消息：固定在气泡右边（右对齐），跟随动画插值后的位置
                (layoutRect.right - getPaddingEndLocal() - child.measuredWidth).toInt()
            }
            child.layout(translateX, cy, translateX + child.measuredWidth, cy + child.measuredHeight)
        }
        // 4) LiveView 锚定气泡底部左侧
        liveView?.let { child ->
            val ly = (layoutRect.bottom - paddingBottom - child.measuredHeight).toInt()
            val liveX = if (fromMe) {
                // 右侧消息：固定在气泡左边（左对齐），跟随动画插值后的位置
                (layoutRect.left + getPaddingStartLocal()).toInt()
            } else {
                // 左侧消息：固定在气泡右边（右对齐），跟随动画插值后的位置
                (layoutRect.right - getPaddingEndLocal() - child.measuredWidth).toInt()
            }
            child.layout(liveX, ly, liveX + child.measuredWidth, ly + child.measuredHeight)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        // 先绘制气泡（使用动画插值后的 rect）
        drawBubble(canvas)
        // 获取当前过渡中的气泡绘制矩形（插值后），用于裁剪
        val rect = getDrawBubbleRect(drawBubbleRect)
        // 参考 Telegram：裁剪区域基于动画后的气泡边界（rect），确保文本在动画过程中被正确裁剪
        // 参考 Telegram：r.left + dp(4), r.top + dp(4), r.right - dp(10/4), r.bottom - dp(4)
        canvas.save()
        if (fromMe) {
            // 右侧消息：右边距 dp(10)（为时间/状态预留更多空间）
            canvas.clipRect(
                rect.left + dpF(4f), rect.top + dpF(4f),
                rect.right - dpF(10f), rect.bottom - dpF(4f)
            )
        } else {
            // 左侧消息：右边距 dp(4)
            canvas.clipRect(
                rect.left + dpF(4f), rect.top + dpF(4f),
                rect.right - dpF(4f), rect.bottom - dpF(4f)
            )
        }
        // 文本位置固定，不需要 translate
        // 子 view 的布局位置基于 bubbleRect（最终位置），绘制时直接绘制即可
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
        // 时间和消息状态始终显示，没有渐变效果，只有位置会变化
        // 位置基于动画插值后的 rect，会跟随气泡大小变化
        canvas.drawText(timeText, timeX, timeY, timePaint)
        // 绘制消息状态（紧随时间右侧）
        if (showStatus && statusWidthF > 0f) {
            val statusX = rect.right - getPaddingEndLocal().toFloat() - statusWidthF
            val statusY = timeY
            canvas.drawText(statusText, statusX, statusY, statusPaint)
        }
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
                // 动画过程中需要重新布局子 view，使它们跟随气泡位置变化
                requestLayout()
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
        // 动画结束后，重新布局以确保子 view 位置正确
        requestLayout()
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
