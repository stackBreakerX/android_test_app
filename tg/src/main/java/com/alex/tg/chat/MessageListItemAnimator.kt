package com.alex.tg.chat

import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemAnimator
import androidx.recyclerview.widget.RecyclerView.ItemAnimator.ItemHolderInfo

class MessageListItemAnimator(
    private val enterContainer: MessageEnterTransitionContainer
) : ItemAnimator() {

    private val interpolator = DecelerateInterpolator()
    private val addDurationMs = 220L
    private val moveDurationMs = 180L
    private val removeDurationMs = 140L

    override fun animateAppearance(
        holder: RecyclerView.ViewHolder,
        preLayoutInfo: ItemHolderInfo?,
        postLayoutInfo: ItemHolderInfo
    ): Boolean {
        val v = holder.itemView
        v.animate().cancel()
        v.alpha = 0f
        v.translationY = v.height * 0.15f
        v.scaleX = 0.98f
        v.scaleY = 0.98f

        val transition = TextMessageEnterTransition(v)
        enterContainer.addTransition(transition)

        v.animate().cancel()
        v.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(addDurationMs)
            .setInterpolator(interpolator)
            .withEndAction {
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
            .setDuration(moveDurationMs)
            .setInterpolator(interpolator)
            .withEndAction {
                dispatchAnimationFinished(holder)
            }
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
            .translationY(v.translationY + v.height * 0.1f)
            .setDuration(removeDurationMs)
            .setInterpolator(interpolator)
            .withEndAction {
                v.alpha = 1f
                v.translationY = 0f
                dispatchAnimationFinished(holder)
            }
            .start()
        return true
    }

    override fun animateChange(
        oldHolder: RecyclerView.ViewHolder,
        newHolder: RecyclerView.ViewHolder,
        preLayoutInfo: ItemHolderInfo,
        postLayoutInfo: ItemHolderInfo
    ): Boolean {
        return false
    }

    override fun runPendingAnimations() {}
    override fun endAnimation(item: RecyclerView.ViewHolder) {
        item.itemView.animate().cancel()
    }
    override fun endAnimations() {}
    override fun isRunning(): Boolean = false
}

