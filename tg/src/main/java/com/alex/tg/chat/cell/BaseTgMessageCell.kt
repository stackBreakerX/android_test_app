package com.alex.tg.chat.cell

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.TextView
import com.alex.tg.chat.bubble.BubbleDrawable
import com.alex.tg.chat.cell.content.MediaContentView
import com.alex.tg.chat.cell.content.TextContentView
import com.alex.tg.chat.cell.extra.LiveView
import com.alex.tg.chat.cell.extra.ReplyView
import com.alex.tg.chat.cell.extra.TranslateView
import com.alex.tg.chat.model.MessageModel

class BaseTgMessageCell @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    private val bubble = BubbleDrawable(0xFFEFF6FF.toInt(), 18f * resources.displayMetrics.density)
    private val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF7A7A7A.toInt()
        textSize = 12f * resources.displayMetrics.scaledDensity
    }
    private val bubblePaddingH = (12f * resources.displayMetrics.density).toInt()
    private val bubblePaddingV = (8f * resources.displayMetrics.density).toInt()
    private val verticalSpacing = (6f * resources.displayMetrics.density).toInt()
    private val bubbleRect = RectF()
    private val timeRect = Rect()

    private val textContent = TextContentView(context)
    private val mediaContent = MediaContentView(context)
    private val replyView = ReplyView(context)
    private val translateView = TranslateView(context)
    private val liveView = LiveView(context)
    private val userNameView = TextView(context).apply {
        textSize = 14f
        setTextColor(0xFF2E7D32.toInt())
    }

    private var model: MessageModel? = null
    private var timeText: String = ""

    init {
        addView(userNameView)
        addView(replyView)
        addView(textContent)
        addView(mediaContent)
        addView(translateView)
        addView(liveView)
        replyView.visibility = GONE
        translateView.visibility = GONE
        liveView.visibility = GONE
        userNameView.visibility = GONE
    }

    fun bind(m: MessageModel) {
        model = m
        timeText = m.time
        userNameView.text = m.fromUser.orEmpty()
        userNameView.visibility = if (m.fromUser.isNullOrEmpty()) GONE else VISIBLE
        replyView.text = m.replyText.orEmpty()
        replyView.visibility = if (m.replyText.isNullOrEmpty()) GONE else VISIBLE
        translateView.text = m.translateText.orEmpty()
        translateView.visibility = if (m.translateText.isNullOrEmpty()) GONE else VISIBLE
        liveView.reactions = m.reactions
        liveView.visibility = if (m.reactions.isEmpty()) GONE else VISIBLE

        when (m.type) {
            MessageModel.Type.TEXT -> {
                textContent.visibility = VISIBLE
                mediaContent.visibility = GONE
                textContent.text = m.text.orEmpty()
            }
            MessageModel.Type.IMAGE, MessageModel.Type.VIDEO -> {
                textContent.visibility = GONE
                mediaContent.visibility = VISIBLE
                mediaContent.mediaWidth = m.mediaWidth
                mediaContent.mediaHeight = m.mediaHeight
                mediaContent.requestLayout()
            }
            else -> {
                textContent.visibility = VISIBLE
                mediaContent.visibility = GONE
                textContent.text = m.text.orEmpty()
            }
        }
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxBubbleW = (resources.displayMetrics.widthPixels * 0.75f).toInt()
        val childMaxW = maxBubbleW - bubblePaddingH * 2
        val childWidthSpec = MeasureSpec.makeMeasureSpec(childMaxW, MeasureSpec.AT_MOST)
        val childHeightSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.AT_MOST)

        var usedW = 0
        var usedH = 0

        if (userNameView.visibility == VISIBLE) {
            userNameView.measure(childWidthSpec, childHeightSpec)
            usedW = usedW.coerceAtLeast(userNameView.measuredWidth)
            usedH += userNameView.measuredHeight + verticalSpacing
        }

        if (replyView.visibility == VISIBLE) {
            replyView.measure(childWidthSpec, childHeightSpec)
            usedW = usedW.coerceAtLeast(replyView.measuredWidth)
            usedH += replyView.measuredHeight + verticalSpacing
        }

        if (textContent.visibility == VISIBLE) {
            textContent.measure(childWidthSpec, childHeightSpec)
            usedW = usedW.coerceAtLeast(textContent.measuredWidth)
            usedH += textContent.measuredHeight + verticalSpacing
        }
        if (mediaContent.visibility == VISIBLE) {
            mediaContent.measure(childWidthSpec, childHeightSpec)
            usedW = usedW.coerceAtLeast(mediaContent.measuredWidth)
            usedH += mediaContent.measuredHeight + verticalSpacing
        }

        if (translateView.visibility == VISIBLE) {
            translateView.measure(childWidthSpec, childHeightSpec)
            usedW = usedW.coerceAtLeast(translateView.measuredWidth)
            usedH += translateView.measuredHeight + verticalSpacing
        }

        val bubbleW = usedW + bubblePaddingH * 2
        val bubbleHWithoutBottom = usedH + bubblePaddingV

        // LiveView 固定在气泡底左；预留空间
        var liveReserveH = 0
        var liveReserveW = 0
        if (liveView.visibility == VISIBLE) {
            liveView.measure(childWidthSpec, childHeightSpec)
            liveReserveH = liveView.measuredHeight + verticalSpacing
            liveReserveW = liveView.measuredWidth
        }

        val bubbleH = bubbleHWithoutBottom + liveReserveH + bubblePaddingV
        setMeasuredDimension(bubbleW, bubbleH)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        bubbleRect.set(0f, 0f, width.toFloat(), height.toFloat())
        val cx = bubblePaddingH
        var cy = bubblePaddingV

        if (userNameView.visibility == VISIBLE) {
            userNameView.layout(cx, cy, cx + userNameView.measuredWidth, cy + userNameView.measuredHeight)
            cy += userNameView.measuredHeight + verticalSpacing
        }
        if (replyView.visibility == VISIBLE) {
            replyView.layout(cx, cy, cx + replyView.measuredWidth, cy + replyView.measuredHeight)
            cy += replyView.measuredHeight + verticalSpacing
        }
        if (textContent.visibility == VISIBLE) {
            textContent.layout(cx, cy, cx + textContent.measuredWidth, cy + textContent.measuredHeight)
            cy += textContent.measuredHeight + verticalSpacing
        }
        if (mediaContent.visibility == VISIBLE) {
            mediaContent.layout(cx, cy, cx + mediaContent.measuredWidth, cy + mediaContent.measuredHeight)
            cy += mediaContent.measuredHeight + verticalSpacing
        }
        if (translateView.visibility == VISIBLE) {
            translateView.layout(cx, cy, cx + translateView.measuredWidth, cy + translateView.measuredHeight)
            cy += translateView.measuredHeight + verticalSpacing
        }

        // LiveView 锚定到气泡底部左侧
        if (liveView.visibility == VISIBLE) {
            val ly = (bubbleRect.bottom - bubblePaddingV - liveView.measuredHeight).toInt()
            liveView.layout(cx, ly, cx + liveView.measuredWidth, ly + liveView.measuredHeight)
        }
        // 计算时间文字尺寸
        val tw = timePaint.measureText(timeText).toInt()
        val th = (timePaint.fontMetrics.descent - timePaint.fontMetrics.ascent).toInt()
        timeRect.set(0, 0, tw, th)
        val isText = textContent.visibility == VISIBLE
        if (isText) {
            val metrics = textContent.getLastLineMetrics()
            val baseY = cy + metrics.bottom
            val x = (bubbleRect.right - bubblePaddingH - tw).toInt()
            val y = (baseY - (timePaint.descent() - timePaint.ascent()) * 0.3f).toInt()
            timeRect.offsetTo(x, y - th)
        } else {
            val bounds = mediaContent.getContentBounds()
            val inset = 8f * resources.displayMetrics.density
            var x = (bubblePaddingH + bounds.right - tw - inset).toInt()
            var y = (bubblePaddingV + bounds.bottom - th - inset).toInt()
            // 若底部存在 LiveView，避免遮挡，将时间上移
            if (liveView.visibility == VISIBLE) {
                val liveTop = liveView.top
                if (y + th > liveTop) {
                    y = liveTop - th - inset.toInt()
                }
            }
            timeRect.offsetTo(x, y)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        bubble.draw(canvas, bubbleRect.left, bubbleRect.top, bubbleRect.right, bubbleRect.bottom)
        super.dispatchDraw(canvas)
    }

    override fun onDraw(canvas: Canvas) {
        if (timeText.isNotEmpty()) {
            canvas.drawText(timeText, timeRect.left.toFloat(), (timeRect.bottom).toFloat(), timePaint)
        }
    }
}
