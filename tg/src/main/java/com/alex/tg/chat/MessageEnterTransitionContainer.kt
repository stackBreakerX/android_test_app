package com.alex.tg.chat

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

class MessageEnterTransitionContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val transitions = mutableListOf<SimpleEnterTransition>()
    private var drawing = false

    fun addTransition(t: SimpleEnterTransition) {
        transitions.add(t)
        t.onAttached(this)
        visibility = VISIBLE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (transitions.isEmpty()) {
            visibility = GONE
            return
        }
        drawing = true
        var anyAlive = false
        transitions.forEach { tr ->
            tr.onDraw(canvas)
            if (!tr.isFinished()) {
                anyAlive = true
            }
        }
        transitions.removeAll { it.isFinished() }
        drawing = false
        if (anyAlive) {
            postInvalidateOnAnimation()
        } else {
            visibility = GONE
        }
    }
}

abstract class SimpleEnterTransition {
    protected var container: MessageEnterTransitionContainer? = null
    fun onAttached(container: MessageEnterTransitionContainer) {
        this.container = container
        onStart()
    }
    protected open fun onStart() {}
    abstract fun onDraw(canvas: Canvas)
    abstract fun isFinished(): Boolean
}

