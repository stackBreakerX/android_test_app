package com.alex.studydemo.module_recyclerview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * @description
 * @version
 */
class VerticalSectionDecoration private constructor(private val mContext: Context) :
    RecyclerView.ItemDecoration() {

    private var mColorPaint: Paint = Paint()

    private var mSectionTextPaint: Paint = Paint()

    private var mSectionTextRec = Rect()

    private var mSectionTextLeftOffset = 0f

    protected var mDividerType = DividerType.COLOR

    protected var mSectionType = DividerType.COLOR

    private val TAG = "TestItemDecoration"

    fun build(): VerticalSectionDecoration {
        mColorPaint.isAntiAlias = true

        mSectionTextPaint.isAntiAlias = true
        mSectionTextPaint.textSize = 20f
        mSectionTextPaint.isDither = true

        if (mColorProvider != null) {
            mDividerType = DividerType.COLOR
        } else if (mDrawableProvider != null) {
            mDividerType = DividerType.DRAWABLE
        }

        if (mSectionColorProvider != null) {
            mSectionType = DividerType.COLOR
        } else if (mSectionDrawableProvider != null) {
            mSectionType = DividerType.DRAWABLE
        }

        if (sectionTextLeftOffset != null) {
            mSectionTextLeftOffset = sectionTextLeftOffset ?: 0f
        }

        if (sectionTextSize != null) {
            mSectionTextPaint.textSize = sectionTextSize ?: 0f
        }

        if (sectionTextColor != null) {
            mSectionTextPaint.color = sectionTextColor ?: 0
        }
        return this
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        super.getItemOffsets(outRect, view, parent, state)
        val childPosition = parent.getChildAdapterPosition(view)
        val itemCount = parent.adapter?.itemCount ?: 0
        val lastDividerOffset = getLastDividerOffset(parent)
//        if (!mShowLastDivider && childPosition >= itemCount - lastDividerOffset) {
//            // Don't set item offset for last line if mShowLastDivider = false
//            return
//        }

        val groupIndex = getGroupIndex(childPosition, parent)
        if (mVisibilityProvider.shouldHideDivider(groupIndex, parent)) {
            return
        }

        if (mSectionProvider == null) {
            outRect.top = mSizeProvider?.dividerSize(childPosition, parent) ?: 0
        } else {
            val currentName = mSectionProvider?.sectionName(childPosition, parent) ?: ""
            val previousSectionName = mSectionProvider?.sectionName(childPosition - 1, parent) ?: ""

            if (currentName != previousSectionName) {
                outRect.top = mSectionSizeProvider?.sectionSize(childPosition, parent) ?: 0
            } else {
                outRect.top = mSizeProvider?.dividerSize(childPosition, parent) ?: 0
            }
        }
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDraw(c, parent, state)
        val adapter = parent.adapter ?: return
        val itemCount = adapter.itemCount
        val lastDividerOffset = getLastDividerOffset(parent)
        val validChildCount = parent.childCount
        var lastChildPosition = -1

        for (i in 0 until validChildCount) {
            val child = parent.getChildAt(i)

            val childPosition = parent.getChildAdapterPosition(child)
            if (childPosition < lastChildPosition) {
                // Avoid remaining divider when animation starts
                continue
            }
            lastChildPosition = childPosition
//            if (!mShowLastDivider && childPosition >= itemCount - lastDividerOffset) {
//                // Don't draw divider for last line if mShowLastDivider = false
//                continue
//            }

            if (wasDividerAlreadyDrawn(childPosition, parent)) {
                // No need to draw divider again as it was drawn already by previous column
                continue
            }

            val groupIndex = getGroupIndex(childPosition, parent)
            if (mVisibilityProvider.shouldHideDivider(groupIndex, parent)) {
                continue
            }

            val currentName = mSectionProvider?.sectionName(childPosition, parent) ?: ""
            val previousSectionName = mSectionProvider?.sectionName(childPosition - 1, parent) ?: ""


            val left = parent.paddingLeft.toFloat() + mMarginProvider.dividerLeftMargin(
                childPosition,
                parent
            )
            val bottom = child.top.toFloat()
            val right =
                (parent.width - parent.paddingRight).toFloat() - mMarginProvider.dividerRightMargin(
                    childPosition,
                    parent
                )
            if (mSectionProvider != null && mSectionSizeProvider != null && previousSectionName != currentName) {
                mSectionTextPaint.getTextBounds(currentName, 0, currentName.length, mSectionTextRec)
                val sectionHeight = mSectionSizeProvider?.sectionSize(childPosition, parent) ?: 0
                val top =
                    (child.top - sectionHeight).toFloat()
                sectionDraw(
                    canvas = c,
                    position = childPosition,
                    parent = parent,
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom
                )
                val dx = left + mSectionTextLeftOffset
                val fontMetrics = mSectionTextPaint.fontMetrics
                val dy = (fontMetrics.bottom - fontMetrics.top) / 2 - fontMetrics.bottom
                val baseLine = top + (sectionHeight / 2) + dy
                c.drawText(currentName, dx, baseLine, mSectionTextPaint)
            } else {
                val dividerHeight = mSizeProvider?.dividerSize(childPosition, parent) ?: 0
                val top = (child.top - dividerHeight).toFloat()
                dividerDraw(
                    canvas = c,
                    position = childPosition,
                    parent = parent,
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom
                )
            }
        }
    }

    private fun sectionDraw(
        canvas: Canvas,
        position: Int,
        parent: RecyclerView,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ) {
        when (mSectionType) {
            DividerType.COLOR -> {
                mColorPaint.color = mSectionColorProvider?.sectionColor(position, parent) ?: 0
                canvas.drawRect(
                    left,
                    top,
                    right,
                    bottom,
                    mColorPaint
                )
            }
            else -> {
                val drawable = mSectionDrawableProvider?.sectionDrawableProvider(position, parent)
                drawable?.setBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
                drawable?.draw(canvas)
            }
        }
    }

    private fun dividerDraw(
        canvas: Canvas,
        position: Int,
        parent: RecyclerView,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ) {
        when (mDividerType) {
            DividerType.COLOR -> {
                mColorPaint.color = mColorProvider?.dividerColor(position, parent) ?: 0
                canvas.drawRect(
                    left,
                    top,
                    right,
                    bottom,
                    mColorPaint
                )
            }
            else -> {
                val drawable = mDrawableProvider?.drawableProvider(position, parent)
                drawable?.setBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
                drawable?.draw(canvas)
            }
        }
    }


    /**
     * Determines whether divider was already drawn for the row the item is in,
     * effectively only makes sense for a grid
     *
     * @param position current view position to draw divider
     * @param parent   RecyclerView
     * @return true if the divider can be skipped as it is in the same row as the previous one.
     */
    private fun wasDividerAlreadyDrawn(position: Int, parent: RecyclerView): Boolean {
        if (parent.layoutManager is GridLayoutManager) {
            val layoutManager = parent.layoutManager as GridLayoutManager?
            val spanSizeLookup = layoutManager!!.spanSizeLookup
            val spanCount = layoutManager.spanCount
            return spanSizeLookup.getSpanIndex(position, spanCount) > 0
        }
        return false
    }

    /**
     * Returns a group index for GridLayoutManager.
     * for LinearLayoutManager, always returns position.
     *
     * @param position current view position to draw divider
     * @param parent   RecyclerView
     * @return group index of items
     */
    private fun getGroupIndex(position: Int, parent: RecyclerView): Int {
        if (parent.layoutManager is GridLayoutManager) {
            val layoutManager = parent.layoutManager as GridLayoutManager?
            val spanSizeLookup = layoutManager!!.spanSizeLookup
            val spanCount = layoutManager.spanCount
            return spanSizeLookup.getSpanGroupIndex(position, spanCount)
        }
        return position
    }

    /**
     * In the case mShowLastDivider = false,
     * Returns offset for how many views we don't have to draw a divider for,
     * for LinearLayoutManager it is as simple as not drawing the last child divider,
     * but for a GridLayoutManager it needs to take the span count for the last items into account
     * until we use the span count configured for the grid.
     *
     * @param parent RecyclerView
     * @return offset for how many views we don't have to draw a divider or 1 if its a
     * LinearLayoutManager
     */
    private fun getLastDividerOffset(parent: RecyclerView): Int {
        if (parent.layoutManager is GridLayoutManager) {
            val layoutManager = parent.layoutManager as GridLayoutManager?
            val spanSizeLookup = layoutManager!!.spanSizeLookup
            val spanCount = layoutManager.spanCount
            val itemCount = parent.adapter!!.itemCount
            for (i in itemCount - 1 downTo 0) {
                if (spanSizeLookup.getSpanIndex(i, spanCount) == 0) {
                    return itemCount - i
                }
            }
        }
        return 1
    }

    /**
     * Interface for controlling divider visibility
     */
    interface VisibilityProvider {
        /**
         * Returns true if divider should be hidden.
         *
         * @param position Divider position (or group index for GridLayoutManager)
         * @param parent   RecyclerView
         * @return True if the divider at position should be hidden
         */
        fun shouldHideDivider(position: Int, parent: RecyclerView?): Boolean
    }

    /**
     * Interface for controlling paint instance for divider drawing
     */
    interface PaintProvider {
        /**
         * Returns [Paint] for divider
         *
         * @param position Divider position (or group index for GridLayoutManager)
         * @param parent   RecyclerView
         * @return Paint instance
         */
        fun dividerPaint(position: Int, parent: RecyclerView?): Paint?
    }

    /**
     * Interface for controlling divider color
     */
    interface ColorProvider {
        /**
         * Returns [android.graphics.Color] value of divider
         *
         * @param position Divider position (or group index for GridLayoutManager)
         * @param parent   RecyclerView
         * @return Color value
         */
        fun dividerColor(position: Int, parent: RecyclerView?): Int
    }

    /**
     * Interface for controlling drawable object for divider drawing
     */
    interface DrawableProvider {
        /**
         * Returns drawable instance for divider
         *
         * @param position Divider position (or group index for GridLayoutManager)
         * @param parent   RecyclerView
         * @return Drawable instance
         */
        fun drawableProvider(position: Int, parent: RecyclerView?): Drawable?
    }

    /**
     * Interface for controlling divider size
     */
    interface SizeProvider {
        /**
         * Returns size value of divider.
         * Height for horizontal divider, width for vertical divider
         *
         * @param position Divider position (or group index for GridLayoutManager)
         * @param parent   RecyclerView
         * @return Size of divider
         */
        fun dividerSize(position: Int, parent: RecyclerView?): Int
    }

    /**
     * Interface for controlling divider size
     */
    interface SectionSizeProvider {
        /**
         * Returns size value of divider.
         * Height for horizontal divider, width for vertical divider
         *
         * @param position Divider position (or group index for GridLayoutManager)
         * @param parent   RecyclerView
         * @return Size of divider
         */
        fun sectionSize(position: Int, parent: RecyclerView?): Int
    }

    /**
     * Interface for controlling divider color
     */
    interface SectionColorProvider {
        /**
         * Returns [android.graphics.Color] value of divider
         *
         * @param position Divider position (or group index for GridLayoutManager)
         * @param parent   RecyclerView
         * @return Color value
         */
        fun sectionColor(position: Int, parent: RecyclerView?): Int
    }

    interface SectionProvider {
        fun sectionName(position: Int, parent: RecyclerView?): String?
    }

    /**
     * Interface for controlling drawable object for divider drawing
     */
    interface SectionDrawableProvider {
        /**
         * Returns drawable instance for divider
         *
         * @param position Divider position (or group index for GridLayoutManager)
         * @param parent   RecyclerView
         * @return Drawable instance
         */
        fun sectionDrawableProvider(position: Int, parent: RecyclerView?): Drawable?
    }

    /**
     * Interface for controlling divider margin
     */
    interface MarginProvider {
        /**
         * Returns left margin of divider.
         *
         * @param position Divider position (or group index for GridLayoutManager)
         * @param parent   RecyclerView
         * @return left margin
         */
        fun dividerLeftMargin(position: Int, parent: RecyclerView?): Int

        /**
         * Returns right margin of divider.
         *
         * @param position Divider position (or group index for GridLayoutManager)
         * @param parent   RecyclerView
         * @return right margin
         */
        fun dividerRightMargin(position: Int, parent: RecyclerView?): Int
    }

    private var mPaintProvider: PaintProvider? = null
    private var mColorProvider: ColorProvider? = null
    private var mSizeProvider: SizeProvider? = null
    private var mDrawableProvider: DrawableProvider? = null
    private var mShowLastDivider = false
    private var mPositionInsideItem = false

    private var mSectionSizeProvider: SectionSizeProvider? = null
    private var mSectionProvider: SectionProvider? = null
    private var mSectionColorProvider: SectionColorProvider? = null
    private var mSectionDrawableProvider: SectionDrawableProvider? = null
    private var sectionTextSize: Float? = null
    private var sectionTextColor: Int? = null
    private var sectionTextLeftOffset: Float? = null


    private var mMarginProvider: MarginProvider = object : MarginProvider {
        override fun dividerLeftMargin(position: Int, parent: RecyclerView?): Int {
            return 0
        }

        override fun dividerRightMargin(position: Int, parent: RecyclerView?): Int {
            return 0
        }
    }

    private var mVisibilityProvider: VisibilityProvider = object :
        VisibilityProvider {
        override fun shouldHideDivider(position: Int, parent: RecyclerView?): Boolean {
            return false
        }
    }

    fun paint(paint: Paint?): VerticalSectionDecoration {
        return paintProvider(object : PaintProvider {
            override fun dividerPaint(position: Int, parent: RecyclerView?): Paint? {
                return paint
            }
        })
    }

    fun paintProvider(provider: PaintProvider?): VerticalSectionDecoration {
        mPaintProvider = provider
        return this
    }

    fun color(color: Int?): VerticalSectionDecoration {
        return colorProvider(object : ColorProvider {
            override fun dividerColor(position: Int, parent: RecyclerView?): Int {
                return color ?: 0
            }
        })
    }


    fun colorResId(@ColorRes colorId: Int): VerticalSectionDecoration {
        return color(mContext.let { ContextCompat.getColor(it, colorId) })
    }

    fun colorProvider(provider: ColorProvider): VerticalSectionDecoration {
        mColorProvider = provider
        return this
    }

    fun marginResId(
        @DimenRes leftMarginId: Int,
        @DimenRes rightMarginId: Int
    ): VerticalSectionDecoration {
        return margin(
            mContext.resources.getDimensionPixelSize(leftMarginId),
            mContext.resources.getDimensionPixelSize(rightMarginId)
        )
    }

    fun margin(size: Int): VerticalSectionDecoration {
        return margin(size, size)
    }

    fun margin(
        leftMargin: Int,
        rightMargin: Int
    ): VerticalSectionDecoration {
        mMarginProvider = object : MarginProvider {
            override fun dividerLeftMargin(position: Int, parent: RecyclerView?): Int {
                return leftMargin
            }

            override fun dividerRightMargin(position: Int, parent: RecyclerView?): Int {
                return rightMargin
            }
        }
        return this
    }

    fun drawable(@DrawableRes id: Int): VerticalSectionDecoration {
        return drawable(ContextCompat.getDrawable(mContext, id))
    }

    fun drawable(drawable: Drawable?): VerticalSectionDecoration {
        return drawableProvider(object : DrawableProvider {
            override fun drawableProvider(position: Int, parent: RecyclerView?): Drawable? {
                return drawable
            }
        })
    }

    fun drawableProvider(provider: DrawableProvider): VerticalSectionDecoration {
        mDrawableProvider = provider
        return this
    }

    fun size(size: Int): VerticalSectionDecoration {
        return sizeProvider(object : SizeProvider {
            override fun dividerSize(position: Int, parent: RecyclerView?): Int {
                return size
            }
        })
    }

    fun sectionSize(size: Int): VerticalSectionDecoration {
        return sectionSizeProvider(object : SectionSizeProvider {
            override fun sectionSize(position: Int, parent: RecyclerView?): Int {
                return size
            }
        })
    }

    fun sectionColorProvider(provider: SectionColorProvider): VerticalSectionDecoration {
        mSectionColorProvider = provider
        return this
    }

    fun sectionDrawableProvider(provider: SectionDrawableProvider): VerticalSectionDecoration {
        mSectionDrawableProvider = provider
        return this
    }

    fun sectionProvider(provider: SectionProvider): VerticalSectionDecoration {
        mSectionProvider = provider
        return this
    }

    fun sectionSizeProvider(provider: SectionSizeProvider): VerticalSectionDecoration {
        mSectionSizeProvider = provider
        return this
    }

    fun sizeProvider(provider: SizeProvider): VerticalSectionDecoration {
        mSizeProvider = provider
        return this
    }

    fun visibilityProvider(provider: VisibilityProvider): VerticalSectionDecoration {
        mVisibilityProvider = provider
        return this
    }

    fun showLastDivider(): VerticalSectionDecoration {
        mShowLastDivider = true
        return this
    }

    fun sectionTextColor(textColor: Int): VerticalSectionDecoration {
        this.sectionTextColor = textColor
        return this
    }

    fun sectionTextSize(textSize: Float): VerticalSectionDecoration {
        this.sectionTextSize = textSize
        return this
    }

    fun sectionTextLeftOffset(textLeftOffset: Float): VerticalSectionDecoration {
        this.sectionTextLeftOffset = textLeftOffset
        return this
    }

    companion object {
        fun create(context: Context): VerticalSectionDecoration {
            val sectionDecoration = VerticalSectionDecoration(context)
            return sectionDecoration
        }
    }

    object DividerType {
        const val DRAWABLE = 1
        const val PAINT = 2
        const val COLOR = 3
    }
}