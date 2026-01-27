package com.alex.studydemo.chat_tg

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class ChatGapOverlayDecoration(private val gapPx: Int) : RecyclerView.ItemDecoration() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF2F4F7.toInt()
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)
        if (position != RecyclerView.NO_POSITION) {
            outRect.top = outRect.top + gapPx
        }
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val left = parent.paddingLeft.toFloat()
        val right = (parent.width - parent.paddingRight).toFloat()
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val top = (child.top - gapPx).toFloat()
            val bottom = child.top.toFloat()
            if (bottom > top) {
                c.drawRect(left, top, right, bottom, paint)
            }
        }
    }
}

