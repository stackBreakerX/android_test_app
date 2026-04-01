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
 * - 以固定比例（3:4）测量内容尺寸；无 URI 时灰色占位，有 URI 时解码并等比铺满绘制
 */
class ImageContentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), TgContentView {

    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDDDDDD.toInt() }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val drawMatrix = Matrix()
    private var contentWidth = 0
    private var contentHeight = 0

    private var imageUri: Uri? = null
    private var bitmap: Bitmap? = null
    private var loadGeneration = 0

    fun setImageUri(uri: Uri?) {
        if (imageUri == uri && (uri == null || bitmap != null)) return
        imageUri = uri
        bitmap = null
        loadGeneration++
        if (uri == null) {
            invalidate()
            return
        }
        val gen = loadGeneration
        val cr = context.applicationContext.contentResolver
        Thread {
            val bmp = decodeBitmap(cr, uri, maxSidePx = (1600 * resources.displayMetrics.density).toInt().coerceAtLeast(720))
            post {
                if (gen == loadGeneration && imageUri == uri) {
                    bitmap = bmp
                    invalidate()
                }
            }
        }.start()
    }

    private fun decodeBitmap(cr: android.content.ContentResolver, uri: Uri, maxSidePx: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var w = bounds.outWidth
            var h = bounds.outHeight
            if (w <= 0 || h <= 0) return null
            var sample = 1
            while (max(w, h) / sample > maxSidePx) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (_: Exception) {
            null
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec)
        contentWidth = maxWidth
        contentHeight = (maxWidth * 3f / 4f).toInt()
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
