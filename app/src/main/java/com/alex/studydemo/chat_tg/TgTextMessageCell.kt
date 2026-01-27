package com.alex.studydemo.chat_tg

import android.content.Context
import android.util.AttributeSet
import com.alex.studydemo.chat_tg.TgAndroidUtilities.dp

/**
 * 文本消息气泡 Cell
 * - 内容为 TextContentView，支持“时间内联到末行”的策略
 * - 通过 BaseTgMessageCell 提供气泡绘制与时间布局能力
 */
class TgTextMessageCell @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BaseTgMessageCell(context, attrs) {

    /** 文本内容视图（负责文本排版与绘制） */
    private val textContentView = TextContentView(context)

    /** 时间锚点：文本行内（靠近末行基线） */
    override val timeAnchor: TgTimeAnchor = TgTimeAnchorInlineText
    /** 文本类 Cell 采用“时间内联到末行”的布局规则 */
    override val inlineTimeWithText: Boolean = true
    /** 内容视图（用于 ViewGroup 布局） */
    override val contentView = textContentView
    /** 内容视图的 TG 能力（提供末行宽度/基线等信息） */
    override val contentAsTg: TgContentView = textContentView

    /** 绑定文本消息内容与时间/方向 */
    fun bindMessage(text: String, time: String, out: Boolean, pack: TgTextLayoutPack?) {
        // 如果是纯文本更新，触发 TextContentView 的内部 CrossFade 动画
        // 注意：如果是首次绑定或非更新操作，animate 应为 false。
        // 这里通过 pack 是否变化或 text 是否变化来简单判断，但 adapter 已经通过 payloads 控制了动画时机
        // 我们需要暴露一个 animate 参数给 adapter 调用
        bindMessage(text, time, out, pack, false)
    }

    fun bindMessage(text: String, time: String, out: Boolean, pack: TgTextLayoutPack?, animate: Boolean) {
        if (pack != null) {
            textContentView.setLayoutPack(pack, animate)
        } else {
            textContentView.setText(text, animate)
        }
        // 绑定时间与气泡（这里暂不处理 BaseCell 的 runTransition，因为它处理的是气泡大小插值，
        // 而 TransitionManager 已经接管了大小变化。我们只需确保内容淡入淡出）
        bindBase(time, out)
    }
}
