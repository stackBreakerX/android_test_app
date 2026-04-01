package com.alex.studydemo.chat_tg

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * 图片内容视图
 * - 自适应宽高比：默认 1:1（对齐 Figma 单图气泡设计），图片加载后按实际比例更新
 * - 比例范围钳制：[MIN_RATIO, MAX_RATIO]，避免极端尺寸
 * - 无 URI 时灰色占位，有 URI 时解码并 CENTER_CROP 绘制
 */
class ImageContentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), TgContentView {

    companion object {
        /** 默认 1:1，与 Figma 单图气泡（277×273）对齐 */
        private const val DEFAULT_RATIO = 1.0f
        /** 宽高比下限：约 16:9 横屏（height/width = 0.56） */
        private const val MIN_RATIO = 0.56f
        /** 宽高比上限：约 3:4 竖屏（height/width = 1.33） */
        private const val MAX_RATIO = 1.33f
    }

    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDDDDDD.toInt() }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val drawMatrix = Matrix()

    private var contentWidth = 0
    private var contentHeight = 0

    /** 当前目标宽高比（height/width），默认 1:1 */
    private var targetRatio: Float = DEFAULT_RATIO

    private var imageUri: Uri? = null
    private var bitmap: Bitmap? = null
    private var loadGeneration = 0

    fun setImageUri(uri: Uri?) {
        if (imageUri == uri && (uri == null || bitmap != null)) return
        imageUri = uri
        bitmap = null
        loadGeneration++
        if (uri == null) {
            if (targetRatio != DEFAULT_RATIO) {
                targetRatio = DEFAULT_RATIO
                requestLayout()
            } else {
                invalidate()
            }
            return
        }
        val gen = loadGeneration
        val cr = context.applicationContext.contentResolver
        val maxSidePx = (1600 * resources.displayMetrics.density).toInt().coerceAtLeast(720)
        Thread {
            val result = decodeWithRatio(cr, uri, maxSidePx)
            post {
                if (gen == loadGeneration && imageUri == uri) {
                    val ratioChanged = result.ratio != targetRatio
                    targetRatio = result.ratio
                    bitmap = result.bitmap
                    // 比例变化时需要重新测量布局；否则只刷新绘制
                    if (ratioChanged) requestLayout() else invalidate()
                }
            }
        }.start()
    }

    private data class DecodeResult(val bitmap: Bitmap?, val ratio: Float)

    /**
     * 先读取图片尺寸元数据（inJustDecodeBounds）计算目标比例，再完整解码。
     * 复用同一 Options 流程，避免重复打开流。
     */
    private fun decodeWithRatio(
        cr: android.content.ContentResolver,
        uri: Uri,
        maxSidePx: Int
    ): DecodeResult {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val bw = bounds.outWidth
            val bh = bounds.outHeight
            val ratio = if (bw > 0 && bh > 0)
                (bh.toFloat() / bw.toFloat()).coerceIn(MIN_RATIO, MAX_RATIO)
            else DEFAULT_RATIO
            var sample = 1
            val maxSide = max(bw.coerceAtLeast(1), bh.coerceAtLeast(1))
            while (maxSide / sample > maxSidePx) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            DecodeResult(bmp, ratio)
        } catch (_: Exception) {
            DecodeResult(null, DEFAULT_RATIO)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec)
        contentWidth = maxWidth
        contentHeight = (maxWidth * targetRatio).toInt().coerceAtLeast(1)
        setMeasuredDimension(contentWidth, contentHeight)
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap
        if (bmp != null && !bmp.isRecycled) {
            val vw = width.toFloat()
            val vh = height.toFloat()
            if (vw > 0f && vh > 0f) {
                // CENTER_CROP：等比放大至铺满视图，与 Telegram 相册缩略图一致
                val scale = max(vw / bmp.width.toFloat(), vh / bmp.height.toFloat())
                drawMatrix.reset()
                drawMatrix.postScale(scale, scale)
                drawMatrix.postTranslate(
                    (vw - bmp.width * scale) / 2f,
                    (vh - bmp.height * scale) / 2f
                )
                canvas.drawBitmap(bmp, drawMatrix, bitmapPaint)
            }
        } else {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), placeholderPaint)
        }
    }

    override fun getContentWidth(): Int = contentWidth
    override fun getLastLineBaseline(): Float? = null
    override fun getLastLineWidth(): Int = 0
}
