package com.alex.studydemo.chat_tg

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

/**
 * 类似 Telegram 的 MessageEnterTransitionContainer。
 * 自身不关心具体动画细节，只提供一个顶层绘制容器，让过渡动画脱离 RecyclerView/item 生命周期。
 */
@SuppressLint("ViewConstructor")
class TgSendTextOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Transition {
        fun onDraw(canvas: Canvas)
    }

    private val transitions = ArrayList<Transition>()

    init {
        visibility = GONE
    }

    fun addTransition(transition: Transition) {
        if (transitions.contains(transition)) return
        transitions.add(transition)
        if (visibility != VISIBLE) {
            visibility = VISIBLE
        }
        invalidate()
    }

    fun removeTransition(transition: Transition) {
        transitions.remove(transition)
        if (transitions.isEmpty()) {
            visibility = GONE
        }
        invalidate()
    }

    fun clearTransitions() {
        transitions.clear()
        visibility = GONE
        invalidate()
    }

    fun isRunning(): Boolean = transitions.isNotEmpty()

    override fun onDraw(canvas: Canvas) {
        if (transitions.isEmpty()) return
        for (i in transitions.indices) {
            transitions[i].onDraw(canvas)
        }
    }
}
