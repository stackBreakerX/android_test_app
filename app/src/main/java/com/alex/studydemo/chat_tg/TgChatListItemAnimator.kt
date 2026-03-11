package com.alex.studydemo.chat_tg

import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import androidx.recyclerview.widget.RecyclerView

class TgChatListItemAnimator : RecyclerView.ItemAnimator() {
    companion object {
        val DEFAULT_INTERPOLATOR: Interpolator = PathInterpolator(0.199f, 0.0106f, 0.2792f, 0.9103f)
        const val DEFAULT_DURATION = 220L
        const val MOVE_DURATION = 180L
        const val ADD_DURATION = 250L
        const val REMOVE_DURATION = 140L
    }

    override fun animateAppearance(
        holder: RecyclerView.ViewHolder,
        preLayoutInfo: ItemHolderInfo?,
        postLayoutInfo: ItemHolderInfo
    ): Boolean {
        val v = holder.itemView
        v.animate().cancel()
        v.alpha = 0f
        v.translationY = v.height * 0.12f
        v.scaleX = 0.98f
        v.scaleY = 0.98f
        v.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(ADD_DURATION)
            .setInterpolator(DEFAULT_INTERPOLATOR)
            .withEndAction { dispatchAnimationFinished(holder) }
            .start()
        return true
    }

    override fun animateDisappearance(
        holder: RecyclerView.ViewHolder,
        preLayoutInfo: ItemHolderInfo,
        postLayoutInfo: ItemHolderInfo?
    ): Boolean {
        val v = holder.itemView
        v.animate().cancel()
        v.animate()
            .alpha(0f)
            .translationY(v.translationY + v.height * 0.08f)
            .setDuration(REMOVE_DURATION)
            .setInterpolator(DEFAULT_INTERPOLATOR)
            .withEndAction {
                v.alpha = 1f
                v.translationY = 0f
                dispatchAnimationFinished(holder)
            }
            .start()
        return true
    }

    override fun animatePersistence(
        holder: RecyclerView.ViewHolder,
        preLayoutInfo: ItemHolderInfo,
        postLayoutInfo: ItemHolderInfo
    ): Boolean {
        val v = holder.itemView
        val dx = (postLayoutInfo.left - preLayoutInfo.left).toFloat()
        val dy = (postLayoutInfo.top - preLayoutInfo.top).toFloat()
        v.translationX = -dx
        v.translationY = -dy
        v.animate().cancel()
        v.animate()
            .translationX(0f)
            .translationY(0f)
            .setDuration(MOVE_DURATION)
            .setInterpolator(DEFAULT_INTERPOLATOR)
            .withEndAction { dispatchAnimationFinished(holder) }
            .start()
        return true
    }

    override fun animateChange(
        oldHolder: RecyclerView.ViewHolder,
        newHolder: RecyclerView.ViewHolder,
        preLayoutInfo: ItemHolderInfo,
        postLayoutInfo: ItemHolderInfo
    ): Boolean {
        // 使用持久化动画和布局插值驱动尺寸变化，change 动画交由内部布局处理
        return false
    }


    override fun runPendingAnimations() {}
    override fun endAnimation(item: RecyclerView.ViewHolder) {
        item.itemView.animate().cancel()
    }
    override fun endAnimations() {}
    override fun isRunning(): Boolean = false
}
