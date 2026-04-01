package com.alex.studydemo.chat_tg

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/**
 * 单图或双图并排（Telegram 相册样式）：双图时中间 1dp 分隔线，两列等高、各自 center-crop。
 */
class AlbumImageContentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs), TgContentView {

    private val left = ImageContentView(context)
    private val right = ImageContentView(context)
    private val divider = View(context).apply {
        setBackgroundColor(0x4D000000)
    }

    /** 双列：第二列可为空（灰块）；单图全宽 */
    private var dualColumn: Boolean = false

    init {
        addView(left)
        addView(divider)
        addView(right)
    }

    /**
     * @param forceDual 为 true 时强制双列（参考 Telegram 并排相册），右图可为 null
     */
    fun setImageUris(first: Uri?, second: Uri?, forceDual: Boolean = false) {
        dualColumn = forceDual || second != null
        left.setImageUri(first)
        right.setImageUri(if (dualColumn) second else null)
        divider.visibility = if (dualColumn) VISIBLE else GONE
        right.visibility = if (dualColumn) VISIBLE else GONE
        requestLayout()
    }

    private fun dp1(): Int = (resources.displayMetrics.density * 1f).toInt().coerceAtLeast(1)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        if (!dualColumn) {
            divider.measure(0, 0)
            right.measure(0, 0)
            left.measure(
                MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            setMeasuredDimension(w, left.measuredHeight)
        } else {
            val dw = dp1()
            val colW = (w - dw) / 2
            val h = (colW * 3f / 4f).toInt().coerceAtLeast(1)
            val ws = MeasureSpec.makeMeasureSpec(colW, MeasureSpec.EXACTLY)
            val hs = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
            left.measure(ws, hs)
            divider.measure(
                MeasureSpec.makeMeasureSpec(dw, MeasureSpec.EXACTLY),
                hs
            )
            right.measure(ws, hs)
            setMeasuredDimension(w, h)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (!dualColumn) {
            left.layout(0, 0, left.measuredWidth, left.measuredHeight)
        } else {
            val dw = dp1()
            val colW = (width - dw) / 2
            left.layout(0, 0, colW, height)
            divider.layout(colW, 0, colW + dw, height)
            right.layout(colW + dw, 0, width, height)
        }
    }

    override fun getContentWidth(): Int = measuredWidth.takeIf { it > 0 } ?: width
    override fun getLastLineBaseline(): Float? = null
    override fun getLastLineWidth(): Int = 0
}
