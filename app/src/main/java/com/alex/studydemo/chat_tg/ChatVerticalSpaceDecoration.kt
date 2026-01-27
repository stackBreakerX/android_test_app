package com.alex.studydemo.chat_tg

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class ChatVerticalSpaceDecoration(private val spacePx: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)
        if (position != RecyclerView.NO_POSITION) {
            outRect.top = spacePx
        }
    }
}

