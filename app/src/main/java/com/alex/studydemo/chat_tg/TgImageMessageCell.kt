package com.alex.studydemo.chat_tg

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import com.alex.studydemo.telegram.Theme

/**
 * 图片消息气泡 Cell — 对齐 Figma 设计（node 11086:66621）
 *
 * 视觉特性：
 * - 内容为 [AlbumImageContentView]：单图自适应宽高比（默认 1:1）或双图并排，按 Path 裁剪圆角
 * - 时间药丸：由 [TgTimeStatusView.Style.BUBBLE] 统一实现
 *   （半透明圆角矩形 rgba(0,0,0,0.3)，radius=16dp，H pad=8dp，V pad=3dp）
 * - 状态图标：[TgTimeStatusView] 内部 Canvas Path 双勾，白色
 * - 气泡使用 [Theme.MessageDrawable.TYPE_TEXT] 绘制带尾巴的气泡形状
 */
class TgImageMessageCell @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BaseTgMessageCell(context, attrs) {

    override val bubbleDrawableType: Int = Theme.MessageDrawable.TYPE_TEXT

    /** 时间+状态 View 叠加在图片上方，使用 BUBBLE 药丸样式 */
    override val timeOverlay: Boolean = true

    /** 按气泡 Path（含圆角+尾巴）裁剪子 View，保证图片不超出气泡轮廓 */
    override val clipChildrenToBubblePath: Boolean get() = true

    /**
     * 图片铺满整个气泡 rect，不留尾巴 inset。
     * 气泡轮廓（含尾巴形状）由 [clipChildrenToBubblePath] 的 clip path 裁剪，
     * 尾巴区域（超出 bubble rect 外的部分）显示气泡填充色。
     */
    // tailInset 全部使用基类默认值 0

    /** 图片贴边填充，内边距全部为 0 */
    override val bubblePaddingTop:      Int get() = 0
    override val bubblePaddingBottom:   Int get() = 0
    override val bubblePaddingStartOut: Int get() = 0
    override val bubblePaddingEndOut:   Int get() = 0
    override val bubblePaddingStartIn:  Int get() = 0
    override val bubblePaddingEndIn:    Int get() = 0

    /** 子 View 裁剪矩形内缩量（clipChildrenToBubblePath = true 时此项不生效） */
    override val clipRightInsetOutDp: Float get() = 4f
    override val clipLeftInsetDp:     Float get() = 2f
    override val clipTopInsetDp:      Float get() = 2f
    override val clipBottomInsetDp:   Float get() = 2f

    private val albumContentView = AlbumImageContentView(context)

    override val timeAnchor: TgTimeAnchor = TgTimeAnchorBottomRight
    override val contentView               = albumContentView
    override val contentAsTg: TgContentView = albumContentView

    // ── 公开 API ─────────────────────────────────────────────────────────────

    /** 绑定图片消息：单图 / 双图相册与占位 */
    fun bindImage(
        time: String,
        fromMe: Boolean,
        imageUri: Uri?,
        secondImageUri: Uri? = null,
        albumDual: Boolean = false
    ) {
        bindBase(time, fromMe)
        val dual = albumDual || secondImageUri != null
        albumContentView.setImageUris(imageUri, secondImageUri, forceDual = dual)
    }
}
