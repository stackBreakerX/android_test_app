package com.alex.studydemo.chat_tg

import android.content.Context          // Android 上下文，用于 dp 换算与资源访问
import android.graphics.Canvas          // 画布，draw() 时由系统传入，负责实际像素绘制
import android.graphics.Paint           // 画笔，控制颜色、描边宽度、抗锯齿等绘制属性
import android.graphics.Path            // 矢量路径，描述气泡轮廓（圆角矩形 + 尾巴贝塞尔曲线）
import android.graphics.Rect            // 整型矩形，表示 Drawable 的 bounds（像素坐标）
import android.graphics.drawable.Drawable // Drawable 基类，接入 Android View 背景/前景绘制体系
import com.alex.studydemo.telegram.Theme  // 移植自 Telegram 的 Theme 工具类，含 MessageDrawable 和 dp 换算

/**
 * TG 风格消息气泡绘制器（Drawable）
 *
 * 职责：
 * 1. 内部持有 [Theme.MessageDrawable] 作为核心实现（路径生成 + 填充色绘制）
 * 2. 在其上叠加半透明描边，使气泡边框更清晰
 * 3. 对外暴露 [buildClipPath] 用于按气泡轮廓（含尾巴）裁剪子 View
 * 4. 在每次绘制前通过 [syncImplGeometry] 将 [TgSharedConfig.bubbleRadius] 同步到 [Theme.bubbleRadiusDp]，
 *    保证气泡圆角半径与全局配置保持一致
 *
 * @param context Android 上下文，透传给 [Theme.MessageDrawable] 用于 dp 换算
 * @param out     true = 发出消息（尾巴在右下角，绿色气泡）；false = 收到消息（尾巴在左下角，白色气泡）
 */
class TgMessageDrawable(context: Context, private var out: Boolean) : Drawable() {

    /**
     * 气泡核心实现：由 Telegram 移植的 [Theme.MessageDrawable]
     * - STYLE_TAIL：带尾巴的气泡形状（文本/图片消息默认使用）
     * - setBubbleColors：分别设置收到(in)和发出(out)的填充色
     */
    private val impl = Theme.MessageDrawable(context, Theme.MessageDrawable.STYLE_TAIL, out, false).apply {
        setBubbleColors(COLOR_IN, COLOR_OUT) // 收到=白色，发出=Telegram经典浅绿色
    }

    /**
     * 描边画笔：在气泡填充色上方绘制一层细边框，增强视觉层次感
     * - STROKE 模式：只画轮廓，不填充
     * - strokeWidth=0.5f：半像素描边，保持精细感
     * - 颜色根据消息方向选择：发出用青绿色半透明，收到用黑色低透明
     */
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { // 开启抗锯齿，让曲线边缘平滑
        style = Paint.Style.STROKE          // 描边模式（不填充内部）
        strokeWidth = 0.5f                  // 描边宽度 0.5px，视觉上极细
        color = if (out) COLOR_OUT_STROKE else COLOR_IN_STROKE // 按发出/收到选色
    }

    /**
     * 切换消息方向（发出 ↔ 收到）
     *
     * 同时更新：
     * - [impl] 的方向（影响尾巴位置和填充色选取）
     * - [strokePaint] 的颜色
     * - 触发重绘（[invalidateSelf]）
     *
     * @param out true=发出（右下角尾巴），false=收到（左下角尾巴）
     */
    fun setOut(out: Boolean) {
        if (this.out == out) return         // 方向未变，跳过无效更新，避免不必要的重绘
        this.out = out                      // 更新本地缓存，供 strokePaint 颜色判断使用
        impl.setOutgoing(out)               // 通知 impl 切换方向（影响路径生成和填充色）
        strokePaint.color = if (out) COLOR_OUT_STROKE else COLOR_IN_STROKE // 同步描边颜色
        invalidateSelf()                    // 通知 View 系统重绘该 Drawable
    }

    /**
     * 切换气泡形状类型：
     * - [Theme.MessageDrawable.STYLE_TAIL]：带尾巴，适用于任何需要方向感的气泡
     * - [Theme.MessageDrawable.STYLE_ROUNDED]：纯圆角矩形，无尾巴，适用于无方向感的气泡
     *
     * @param type 气泡类型常量，来自 [Theme.MessageDrawable]
     */
    fun setBubbleType(type: Int) {
        impl.setType(type)   // 切换 impl 内部类型，下次 draw/makePath 生效
        invalidateSelf()     // 触发重绘，使形状变化立即反映到屏幕
    }

    /**
     * 绘制气泡到画布（由 View 系统在需要刷新时调用）
     *
     * 绘制顺序：
     * 1. 同步几何参数（bounds + 圆角半径）
     * 2. impl 绘制气泡填充色（绿色/白色背景）
     * 3. 在相同路径上叠加描边
     */
    override fun draw(canvas: Canvas) {
        syncImplGeometry()                          // 确保 impl 的 bounds/radius/top 与当前状态一致
        impl.draw(canvas)                           // 绘制填充色气泡（含尾巴形状）
        canvas.drawPath(impl.makePath(), strokePaint) // 在填充层上叠加半透明描边轮廓
    }

    /**
     * 同步几何参数到 [impl]，确保路径生成时使用最新的 bounds 和圆角配置
     *
     * 必须在每次 [draw] / [buildClipPath] 前调用，原因：
     * - [TgSharedConfig.bubbleRadius] 可能在运行时变化（如字体/主题设置页）
     * - [Theme.bubbleRadiusDp] 是全局变量，需在此处同步，避免 impl 使用旧值
     * - [setTop] 告知 impl "当前帧是完整气泡"（非分片），路径才包含底边和尾巴
     */
    private fun syncImplGeometry() {
        Theme.bubbleRadiusDp = TgSharedConfig.bubbleRadius // 将全局配置圆角半径写入 Theme 全局变量
        val b = bounds                                      // 取当前 Drawable 的边界矩形（像素坐标）
        impl.setBounds(b.left, b.top, b.right, b.bottom)   // 更新 impl 的绘制区域
        impl.setTop(b.top, b.width(), b.height(), false, false) // 设置列表位置：top=b.top，完整气泡（非分片）
    }

    /**
     * 生成当前 bounds 对应的气泡裁剪路径（含圆角 + 尾巴）
     *
     * 供 [BaseTgMessageCell] 使用：当 [BaseTgMessageCell.clipChildrenToBubblePath] = true 时，
     * 用此路径对子 View（如图片）做 Canvas clip，保证图片不超出气泡轮廓（含尾巴区域）。
     *
     * @return 新建的 [Path] 对象（深拷贝，防止外部修改影响 impl 内部状态）
     */
    fun buildClipPath(): Path {
        syncImplGeometry()          // 确保路径基于最新 bounds 生成
        return Path(impl.makePath()) // 深拷贝路径后返回，外部可自由变换而不污染 impl
    }

    /**
     * 设置整体透明度（0=全透明，255=不透明），同步到填充和描边两层
     *
     * @param alpha 透明度值 [0, 255]
     */
    override fun setAlpha(alpha: Int) {
        impl.setAlpha(alpha)         // 更新填充色气泡的透明度
        strokePaint.alpha = alpha    // 同步描边画笔的透明度，保持两层视觉一致
    }

    /**
     * 设置颜色滤镜（如灰度、色调映射），同步到填充和描边两层
     *
     * @param colorFilter 颜色滤镜，null 表示移除
     */
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        impl.setColorFilter(colorFilter)      // 应用到填充层
        strokePaint.colorFilter = colorFilter // 应用到描边层，保持视觉风格统一
    }

    /**
     * 返回透明度级别：[android.graphics.PixelFormat.TRANSLUCENT]
     * 表示该 Drawable 含有半透明像素（描边+尾巴区域），需要与背景合成。
     */
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT

    /**
     * 设置 Drawable 的绘制边界（四个独立坐标值版本）
     * 需同时更新父类（记录 bounds）和 impl（用于路径计算）
     *
     * @param left   左边界（px）
     * @param top    上边界（px）
     * @param right  右边界（px）
     * @param bottom 下边界（px）
     */
    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setBounds(left, top, right, bottom)       // 父类保存 bounds，供 getBounds() 返回
        impl.setBounds(left, top, right, bottom)        // impl 同步 bounds，路径生成时以此为坐标系
    }

    /**
     * 设置 Drawable 的绘制边界（Rect 版本，功能与上一个重载相同）
     *
     * @param bounds 边界矩形（px）
     */
    override fun setBounds(bounds: Rect) {
        super.setBounds(bounds)                                          // 父类保存 bounds
        impl.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom) // impl 同步 bounds
    }

    /** 颜色常量（伴生对象，类级别共享，避免每个实例重复分配） */
    private companion object {
        /** 发出消息气泡填充色：Telegram 经典浅绿色 #E1FFC7，不透明 */
        const val COLOR_OUT = 0xFFE1FFC7.toInt()

        /** 收到消息气泡填充色：纯白色 #FFFFFF，不透明 */
        const val COLOR_IN = 0xFFFFFFFF.toInt()

        /** 发出消息气泡描边色：青绿色 #00D0DB，透明度 0x33（约 20%），轻微可见边框 */
        const val COLOR_OUT_STROKE = 0x3300D0DB

        /** 收到消息气泡描边色：黑色 #121212，透明度 0x1A（约 10%），极淡边框防止白底融合 */
        const val COLOR_IN_STROKE = 0x1A121212
    }
}
