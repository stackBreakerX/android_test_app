package com.alex.studydemo.chat_tg

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator
import kotlin.math.max

/**
 * 文本内容视图
 * - 负责将纯文本按宽度进行排版（StaticLayout），并逐块绘制
 * - 支持为“行内时间”预留右侧宽度，避免与末行时间文本重叠
 */
class TextContentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), TgContentView {

    /** 聊天文本画笔（来自主题） */
    private val textPaint = TgTheme.chatMsgTextPaint
    /** 当前消息文本内容 */
    private var messageText: String = ""
    /** 文本排版块集合（按 10 行切分） */
    private var blocks: List<TgTextLayoutBlock> = emptyList()
    /** 内容实际宽度（用于气泡宽度计算） */
    private var contentWidth: Int = 0
    // 为时间预留的右侧宽度（仅在文本行内时间时使用）
    private var reservedRight: Int = 0
    private var prebuiltPack: TgTextLayoutPack? = null

    /** 设置文本内容并请求重新布局与重绘 */
    fun setText(text: String, animate: Boolean = false) {
        if (animate && messageText.isNotEmpty() && messageText != text) {
            // 启动 crossfade 动画
            startCrossfade(text)
        } else {
            messageText = text
            crossfadeProgress = 1f
            requestLayout()
            invalidate()
        }
    }

    fun setLayoutPack(pack: TgTextLayoutPack?, animate: Boolean = false) {
        if (animate && prebuiltPack != null && prebuiltPack != pack) {
            // 启动 crossfade 动画
            startCrossfadeWithPack(pack)
        } else {
            prebuiltPack = pack
            crossfadeProgress = 1f
            requestLayout()
            invalidate()
        }
    }

    // Crossfade 动画相关
    /** 旧文本块（用于淡出） */
    private var animateOutBlocks: List<TgTextLayoutBlock> = emptyList()
    /** 新文本块（用于淡入） */
    private var animateInBlocks: List<TgTextLayoutBlock> = emptyList()
    /** Crossfade 动画进度（0f = 完全显示旧文本，1f = 完全显示新文本） */
    private var crossfadeProgress: Float = 1f
    /** Crossfade 动画器 */
    private var crossfadeAnimator: ValueAnimator? = null

    /** 启动文本 crossfade 动画 */
    private fun startCrossfade(newText: String) {
        // 保存当前 blocks 作为旧文本
        animateOutBlocks = blocks.toList()
        // 更新文本
        messageText = newText
        // 请求布局以计算新 blocks
        requestLayout()
        // 新 blocks 会在 onMeasure 后可用，延迟启动动画
        post {
            // 确保 layout 完成后再获取新 blocks
            animateInBlocks = blocks.toList()
            crossfadeProgress = 0f
            crossfadeAnimator?.cancel()
            crossfadeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 220L
                interpolator = PathInterpolator(0.2f, 0f, 0.2f, 1f)
                addUpdateListener {
                    crossfadeProgress = it.animatedValue as Float
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        crossfadeProgress = 1f
                        // 动画结束后，blocks 已经是新的了（在 onMeasure 中已更新）
                        animateOutBlocks = emptyList()
                        animateInBlocks = emptyList()
                        invalidate()
                    }
                })
            }
            crossfadeAnimator?.start()
        }
    }

    /** 启动基于 pack 的 crossfade 动画 */
    private fun startCrossfadeWithPack(newPack: TgTextLayoutPack?) {
        animateOutBlocks = blocks.toList()
        prebuiltPack = newPack
        requestLayout()
        post {
            // 确保 layout 完成后再获取新 blocks
            if (prebuiltPack != null) {
                // 使用 pack 中的 blocks
                animateInBlocks = prebuiltPack!!.blocks.toList()
            } else {
                // 如果没有 pack，使用当前 blocks
                animateInBlocks = blocks.toList()
            }
            crossfadeProgress = 0f
            crossfadeAnimator?.cancel()
            crossfadeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 220L
                interpolator = PathInterpolator(0.2f, 0f, 0.2f, 1f)
                addUpdateListener {
                    crossfadeProgress = it.animatedValue as Float
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        crossfadeProgress = 1f
                        // 动画结束后，更新 blocks 为新的 blocks
                        blocks = animateInBlocks
                        animateOutBlocks = emptyList()
                        animateInBlocks = emptyList()
                        invalidate()
                    }
                })
            }
            crossfadeAnimator?.start()
        }
    }

    /** 设置为时间预留的右侧宽度（单位像素） */
    fun setReservedRight(value: Int) {
        if (reservedRight == value) return
        reservedRight = value
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        TgTheme.init(resources.displayMetrics.density)
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec)
        
        // 如果正在 crossfade 动画，使用新 blocks 来计算尺寸（用于气泡大小动画）
        val isAnimating = crossfadeProgress < 1f && animateInBlocks.isNotEmpty()
        val blocksToUse = if (isAnimating) animateInBlocks else blocks
        
        val pack = prebuiltPack
        // 仅当预计算布局与当前预留宽度、可用宽度一致时才复用
        if (pack != null && pack.computedForWidth == maxWidth - reservedRight && pack.reservedRight == reservedRight) {
            // 如果正在动画，不要更新 blocks，保持动画的稳定性
            if (!isAnimating) {
                blocks = pack.blocks
                contentWidth = pack.contentWidth
            } else {
                // 动画期间，使用新 blocks 的尺寸
                var cw = 0
                var totalH = 0
                for (b in animateInBlocks) {
                    cw = kotlin.math.max(cw, b.maxRight.toInt())
                    totalH += (b.height + b.padTop + b.padBottom)
                }
                contentWidth = cw
                // 确保宽度不超过可用宽度
                val finalWidth = kotlin.math.min(cw, maxWidth - reservedRight)
                setMeasuredDimension(finalWidth, totalH)
                return
            }
            // 确保宽度不超过可用宽度
            val finalWidth = kotlin.math.min(pack.contentWidth, maxWidth - reservedRight)
            setMeasuredDimension(finalWidth, pack.height)
            return
        }
        
        // 如果正在动画，不要更新 blocks
        if (isAnimating) {
            // 使用新 blocks 的尺寸
            var cw = 0
            var totalH = 0
            for (b in animateInBlocks) {
                cw = kotlin.math.max(cw, b.maxRight.toInt())
                totalH += (b.height + b.padTop + b.padBottom)
            }
            contentWidth = cw
            setMeasuredDimension(cw, totalH)
            return
        }
        
        val available = max(1, maxWidth - reservedRight)
        blocks = TgTextLayoutBuilder.buildBlocks(messageText, textPaint, available)
        var cw = 0
        var totalH = 0
        for (b in blocks) {
            cw = kotlin.math.max(cw, b.maxRight.toInt())
            totalH += (b.height + b.padTop + b.padBottom)
        }
        contentWidth = cw
        val height = totalH
        // 确保宽度不超过可用宽度
        val finalWidth = kotlin.math.min(contentWidth, available)
        setMeasuredDimension(finalWidth, height)
    }

    override fun onDraw(canvas: Canvas) {
        // 裁剪绘制区域，确保文本不会超出 View 边界
        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        
        // 如果正在 crossfade 动画，同时绘制旧文本（淡出）和新文本（淡入）
        if (crossfadeProgress < 1f && animateOutBlocks.isNotEmpty() && animateInBlocks.isNotEmpty()) {
            val oldAlpha = (255 * (1f - crossfadeProgress)).toInt().coerceIn(0, 255)
            val newAlpha = (255 * crossfadeProgress).toInt().coerceIn(0, 255)
            
            // 绘制旧文本（淡出）
            if (oldAlpha > 0) {
                canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), oldAlpha, Canvas.ALL_SAVE_FLAG)
                var y = 0f
                for (block in animateOutBlocks) {
                    canvas.save()
                    canvas.translate(0f, y + block.padTop)
                    // 确保文本不会超出当前 View 的宽度
                    canvas.clipRect(0f, 0f, width.toFloat(), block.height.toFloat())
                    block.textLayout.draw(canvas)
                    canvas.restore()
                    y += (block.padTop + block.height + block.padBottom).toFloat()
                }
                canvas.restore()
            }
            
            // 绘制新文本（淡入）
            if (newAlpha > 0) {
                canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), newAlpha, Canvas.ALL_SAVE_FLAG)
                var y = 0f
                for (block in animateInBlocks) {
                    canvas.save()
                    canvas.translate(0f, y + block.padTop)
                    // 确保文本不会超出当前 View 的宽度
                    canvas.clipRect(0f, 0f, width.toFloat(), block.height.toFloat())
                    block.textLayout.draw(canvas)
                    canvas.restore()
                    y += (block.padTop + block.height + block.padBottom).toFloat()
                }
                canvas.restore()
            }
        } else {
            // 正常绘制
            if (blocks.isEmpty()) {
                canvas.restore()
                return
            }
            var y = 0f
            for (block in blocks) {
                canvas.save()
                canvas.translate(0f, y + block.padTop)
                // 确保文本不会超出当前 View 的宽度
                canvas.clipRect(0f, 0f, width.toFloat(), block.height.toFloat())
                block.textLayout.draw(canvas)
                canvas.restore()
                y += (block.padTop + block.height + block.padBottom).toFloat()
            }
        }
        
        canvas.restore()
    }

    override fun getContentWidth(): Int {
        // 如果正在动画，使用新 blocks 的宽度
        if (crossfadeProgress < 1f && animateInBlocks.isNotEmpty()) {
            var cw = 0
            for (b in animateInBlocks) {
                cw = kotlin.math.max(cw, b.maxRight.toInt())
            }
            return cw
        }
        return contentWidth
    }

    override fun getLastLineBaseline(): Float? {
        // 如果正在动画，使用新 blocks
        val blocksToUse = if (crossfadeProgress < 1f && animateInBlocks.isNotEmpty()) animateInBlocks else blocks
        if (blocksToUse.isEmpty()) return null
        var y = 0f
        for (i in 0 until blocksToUse.size - 1) {
            val b = blocksToUse[i]
            y += (b.padTop + b.height + b.padBottom).toFloat()
        }
        val last = blocksToUse.last()
        val lastLine = last.textLayout.lineCount - 1
        val baseline = if (lastLine >= 0) last.textLayout.getLineBaseline(lastLine) else last.height
        return y + last.padTop + baseline
    }

    override fun getLastLineWidth(): Int {
        // 如果正在动画，使用新 blocks
        val blocksToUse = if (crossfadeProgress < 1f && animateInBlocks.isNotEmpty()) animateInBlocks else blocks
        if (blocksToUse.isEmpty()) return 0
        val last = blocksToUse.last()
        val lastLine = last.textLayout.lineCount - 1
        return if (lastLine >= 0) last.textLayout.getLineRight(lastLine).toInt() else 0
    }
}
