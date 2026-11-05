package com.alex.studydemo.module_compose.components.iosmodal

import androidx.compose.runtime.Immutable

/** 决定背景是否跟随卡片退后，并选择贴底卡片或带留白的浮层外观。 */
enum class IosModalPresentationStyle {
    /** 背景随进度缩小并下移，卡片贴住窗口底部。 */
    Stacked,

    /** 背景保持原尺寸和位置，卡片以四角圆角浮在页面上方。 */
    Overlay,
}

/**
 * 模态的布局与交互配置；修改配置会在当前弹窗中生效，无须重新创建组件。
 *
 * @param heightFraction 卡片占窗口高度的比例，范围为 0.3 到 1，实际高度仍受顶部安全区限制。
 * @param dismissOnBackPress 是否允许系统返回键请求关闭，不影响业务按钮的关闭回调。
 * @param dismissOnClickOutside 是否允许点击遮罩请求关闭，关闭后遮罩仍拦截背景触摸。
 * @param dragToDismiss 是否允许标题拖动和内容到顶下拉；禁用后内容仍可正常滚动。
 * @param presentationStyle 默认保留背景缩放效果；Overlay 在整个呈现过程中保持背景不动。
 */
@Immutable
data class IosModalProperties(
    val heightFraction: Float = 0.92f,
    val dismissOnBackPress: Boolean = true,
    val dismissOnClickOutside: Boolean = true,
    val dragToDismiss: Boolean = true,
    val presentationStyle: IosModalPresentationStyle = IosModalPresentationStyle.Stacked,
) {
    init {
        require(heightFraction in 0.3f..1f) { "heightFraction 必须在 0.3 到 1 之间" }
    }
}
