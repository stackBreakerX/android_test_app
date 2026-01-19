package com.alex.studydemo.module_view

import android.view.View
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView

class UpwardSqueezeItemAnimator : DefaultItemAnimator() {
    init {
        addDuration = 160
        moveDuration = 160
    }

    override fun animateAdd(holder: RecyclerView.ViewHolder?): Boolean {
        if (holder == null) return false
        val v: View = holder.itemView
        v.alpha = 0f
        // 初始向下偏移，随后回到0形成“向上挤入”的感觉
        val startTrans = v.height.takeIf { it > 0 }?.toFloat() ?: 40f
        v.translationY = startTrans
        val anim = v.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(addDuration)
        anim.withEndAction {
            dispatchAddFinished(holder)
        }
        anim.start()
        return true
    }
}
