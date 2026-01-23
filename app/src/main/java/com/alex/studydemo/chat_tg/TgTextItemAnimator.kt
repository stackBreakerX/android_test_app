package com.alex.studydemo.chat_tg

import android.animation.ValueAnimator
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import androidx.recyclerview.widget.RecyclerView

/**
 * TG 风格的 RecyclerView Item 动画
 * - 入场：淡入 + 轻微上移 + 缩放回弹
 * - 移动：位移平滑回到目标位置
 * - 移除：淡出 + 轻微下移
 */
class TgTextItemAnimator : RecyclerView.ItemAnimator() {

    // 对齐 TG 的入场曲线（ChatListItemAnimator.DEFAULT_INTERPOLATOR）
    private val interpolator: Interpolator = PathInterpolator(0.199f, 0.0106f, 0.2792f, 0.9103f)
    private val addDurationMs = 250L
    private val moveDurationMs = 180L
    private val removeDurationMs = 140L

    override fun animateAppearance(
        holder: RecyclerView.ViewHolder,
        preLayoutInfo: ItemHolderInfo?,
        postLayoutInfo: ItemHolderInfo
    ): Boolean {
        val v = holder.itemView
        v.animate().cancel()
        // TG 风格：轻微上移 + 透明度过渡
        v.alpha = 0f
        v.translationY = v.height * 0.12f
        v.scaleX = 0.98f
        v.scaleY = 0.98f
        v.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(addDurationMs)
            .setInterpolator(interpolator)
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
        return false
    }

    override fun runPendingAnimations() {}
    override fun endAnimation(item: RecyclerView.ViewHolder) {
        item.itemView.animate().cancel()
    }
    override fun endAnimations() {}
    override fun isRunning(): Boolean = false
}
