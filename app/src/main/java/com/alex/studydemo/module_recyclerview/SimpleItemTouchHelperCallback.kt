package com.alex.studydemo.module_recyclerview

import android.graphics.Canvas
import android.util.Log
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * @description
 * @version
 */
class SimpleItemTouchHelperCallback : ItemTouchHelper.Callback() {

    private var currentDx: Float = 0F

    private var isReturning: Boolean = false

    private val SWIPE_THRESHOLD_PROPORTION = 0.325F



    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        val swipeFlags = ItemTouchHelper.START
        return when {
            !isReturning ->
                ItemTouchHelper.Callback.makeMovementFlags(0, ItemTouchHelper.LEFT)
            else ->
                0
        }
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return false
    }

    override fun isItemViewSwipeEnabled(): Boolean {
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (abs(currentDx) < 300) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
        currentDx = dX

        if (currentDx == 0F) {
            isReturning = false
        } else {
            isReturning = !isCurrentlyActive
            val viewHolderMarginY = (viewHolder.itemView.layoutParams as? RecyclerView.LayoutParams)?.let { it.leftMargin + it.rightMargin }
                ?: 0

            val parentMetrics = ParentMetrics(
                width = viewHolder.itemView.measuredWidth.toFloat() - viewHolderMarginY.toFloat(),
                height = viewHolder.itemView.measuredHeight.toFloat()
            )

            val swipeProportion = abs(dX) / parentMetrics.width
        }
        Log.d(
            "11111111",
            "onChildDraw() called with: dX = $dX, dY = $dY, actionState = $actionState, isCurrentlyActive = $isCurrentlyActive"
        )
    }

    override fun convertToAbsoluteDirection(flags: Int, layoutDirection: Int): Int {
        if (abs(currentDx) > 299) return 0
        Log.d(
            "1111111",
            "convertToAbsoluteDirection() called with: flags = $flags, layoutDirection = $layoutDirection"
        )
        return super.convertToAbsoluteDirection(flags, layoutDirection)
    }

}