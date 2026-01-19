package com.alex.studydemo.module_view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.databinding.ActivityFullTextBinding

class FullTextActivity : BaseActivity<ActivityFullTextBinding>() {
    private var totalLines: Int = 0
    private var collapsedLines: Int = 4
    private var expanded = false
    private var endIndex: Int = 0
    private var content: String = ""
    private var extraSpan: MutableForegroundColorSpan? = null

    companion object {
        fun newInstance(context: Context) {
            val intent = Intent(context, FullTextActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityFullTextBinding =
        ActivityFullTextBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        content = "在产品详情页中，默认只展示部分文案，点击\"全文\"后应以柔和的过渡呈现剩余内容。展开时，仅底部新增的文本以淡入方式出现，避免整段文本闪烁。再次收起时，底部文本淡出并隐藏，保留顶部固定的内容，保证页面整洁。该段文案用于示例，长度适中以便观察到展开与收起的过渡效果。\n\n具体实现包含：1）计算前四行的精确截断位置作为固定文本；2）使用一个 TextView，通过行数与透明度的动画仅对底部新增文本进行从上到下的淡入；3）点击切换按钮控制展开与收起状态。"

        binding.tvContent.text = content
        binding.tvContent.maxLines = collapsedLines

        binding.root.post {
            val width = binding.tvContent.width
            val paint = binding.tvContent.paint
            val spacingAdd = binding.tvContent.lineSpacingExtra
            val spacingMult = 1.0f
            val layout = StaticLayout.Builder.obtain(content, 0, content.length, paint, width)
                .setLineSpacing(spacingAdd, spacingMult)
                .setIncludePad(false)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()

            totalLines = layout.lineCount
            if (totalLines <= collapsedLines) {
                binding.tvToggle.visibility = View.GONE
                return@post
            }
            endIndex = layout.getLineEnd(collapsedLines - 1)
        }

        binding.tvToggle.setOnClickListener {
            if (!expanded) {
                val ss = SpannableString(content)
                val baseColor = binding.tvContent.currentTextColor
                val span = MutableForegroundColorSpan(baseColor)
                span.alphaFraction = 0f
                ss.setSpan(span, endIndex, content.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                extraSpan = span
                binding.tvContent.text = ss

                val linesAnim = ValueAnimator.ofInt(collapsedLines, totalLines)
                linesAnim.duration = 300
                linesAnim.interpolator = AccelerateDecelerateInterpolator()
                linesAnim.addUpdateListener { binding.tvContent.maxLines = it.animatedValue as Int }
                linesAnim.start()
                val alphaAnim = ValueAnimator.ofFloat(0f, 1f)
                alphaAnim.duration = 300
                alphaAnim.interpolator = AccelerateDecelerateInterpolator()
                alphaAnim.addUpdateListener {
                    extraSpan?.alphaFraction = it.animatedValue as Float
                    binding.tvContent.invalidate()
                }
                alphaAnim.start()
                binding.tvToggle.text = "收起"
            } else {
                val current = binding.tvContent.maxLines
                val linesAnim = ValueAnimator.ofInt(current, collapsedLines)
                linesAnim.duration = 200
                linesAnim.interpolator = AccelerateDecelerateInterpolator()
                linesAnim.addUpdateListener { binding.tvContent.maxLines = it.animatedValue as Int }
                linesAnim.start()
                val alphaAnim = ValueAnimator.ofFloat(1f, 0f)
                alphaAnim.duration = 200
                alphaAnim.interpolator = AccelerateDecelerateInterpolator()
                alphaAnim.addUpdateListener {
                    extraSpan?.alphaFraction = it.animatedValue as Float
                    binding.tvContent.invalidate()
                }
                alphaAnim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        binding.tvContent.text = content
                        extraSpan = null
                    }
                })
                alphaAnim.start()
                binding.tvToggle.text = "全文"
            }
            expanded = !expanded
        }
    }

    private class MutableForegroundColorSpan(private var baseColor: Int) : CharacterStyle(), UpdateAppearance {
        var alphaFraction: Float = 1f
        override fun updateDrawState(tp: TextPaint) {
            val a = (alphaFraction.coerceIn(0f, 1f) * 255).toInt()
            val rgb = baseColor and 0x00FFFFFF
            tp.color = (a shl 24) or rgb
        }
    }
}
