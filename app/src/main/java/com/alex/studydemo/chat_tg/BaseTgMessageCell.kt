package com.alex.studydemo.chat_tg

import android.content.Context
import android.animation.Animator                      // 动画监听基类，用于监听动画结束/取消事件
import android.animation.AnimatorListenerAdapter       // AnimatorListener 的空实现适配器，只需重写关心的方法
import android.animation.ValueAnimator                 // 属性动画器，驱动气泡尺寸过渡（0f → 1f 进度）
import android.graphics.Canvas                         // 画布，dispatchDraw / drawBubble 时由系统传入
import android.graphics.Path                           // 矢量路径，用于按气泡轮廓（含尾巴）裁剪子 View
import android.graphics.RectF                          // 浮点矩形，存储气泡区域坐标（比 Rect 精度更高）
import android.util.AttributeSet                       // XML 属性集，@JvmOverloads 构造所需
import android.view.View                               // View 基类，子内容视图的类型声明
import android.view.ViewGroup                          // ViewGroup 基类，BaseTgMessageCell 继承自此
import android.view.animation.PathInterpolator         // 路径插值器，用于模拟 TG 标准弹性缓动曲线
import com.alex.studydemo.telegram.Theme               // 移植自 Telegram 的 Theme 类，含 MessageDrawable 和 dp 换算
import kotlin.math.max                                 // 取两数中较大值，用于防止负宽度

/**
 * TG 气泡消息基础 ViewGroup
 *
 * 职责：
 * 1. 统一绘制气泡背景（[TgMessageDrawable]，含圆角 + 尾巴 + 描边）
 * 2. 测量和布局多个子 View：内容（文本/图片）、时间状态、以及可选的用户名/引用/翻译/点赞块
 * 3. 支持气泡尺寸变化过渡动画（prepareTransition → startTransitionAnimation → finishTransition）
 * 4. 支持两种时间布局策略：行内时间（文本末行右侧）和右下角时间行
 *
 * 子类通过提供 [contentView]、[contentAsTg]、[timeAnchor] 三个抽象属性，
 * 得到文本/图片/视频/文件等不同展示能力，同时复用所有气泡绘制和动画逻辑。
 *
 * @param context  Android 上下文，用于 dp 换算和资源访问
 * @param attrs    XML 属性集，供布局文件中的属性解析（通常为 null，通过代码创建）
 */
abstract class BaseTgMessageCell @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    // ── 消息基本属性 ──────────────────────────────────────────────────────────

    /** 当前消息的时间文本，由 [bindBase] 设置，传给 [TgTimeStatusView] 绘制 */
    private var timeText: String = ""

    /** 消息方向：true = 自己发出（右侧气泡），false = 收到（左侧气泡） */
    private var fromMe: Boolean = true

    /**
     * 子类（如图片气泡）在绘制渐变/时间遮罩时读取消息方向。
     * 只读，外部不可直接修改 [fromMe]，只能通过 [bindBase] 统一更新。
     */
    protected val isOutgoing: Boolean get() = fromMe

    // ── 气泡区域矩形 ──────────────────────────────────────────────────────────

    /**
     * 当前帧的气泡目标矩形（非动画时 = 实际位置；动画时 = 动画最终目标）。
     * 每次 [onLayout] 在非动画分支中重新计算。
     */
    private val bubbleRect = RectF()

    /**
     * 上一次 [onLayout] 计算出的气泡矩形，在 [prepareTransition] 中作为动画起始值。
     * 用于"动画结束后回写 bubbleRect"的对称逻辑，保持 lastBubbleRect 始终代表布局后的最终位置。
     */
    private val lastBubbleRect = RectF()

    /**
     * 绘制时使用的插值矩形（复用对象，避免每帧 GC）。
     * [getDrawBubbleRect] 将 startRect ↔ endRect 的插值结果写入此对象并返回。
     */
    private val drawBubbleRect = RectF()

    // ── 气泡绘制器与动画参数 ──────────────────────────────────────────────────

    /**
     * 气泡 Drawable：封装了 [Theme.MessageDrawable]（路径生成 + 填充色）和半透明描边。
     * 初始方向为 true（发出），[bindBase] 中会根据实际消息更新。
     */
    private val bubbleDrawable: TgMessageDrawable = TgMessageDrawable(context, true)

    /**
     * 过渡动画参数容器：保存 startRect / endRect、动画进度、运行状态等。
     * 驱动气泡在内容更新时平滑伸缩，避免突变跳帧。
     */
    private val transitionParams = TransitionParams()

    /**
     * 当前正在执行的过渡动画器，null 表示无动画。
     * [prepareTransition] 调用前先 cancel，防止旧动画与新动画冲突。
     */
    private var transitionAnimator: ValueAnimator? = null

    // ── 气泡内边距（子类可覆盖以改变内容留白） ────────────────────────────────

    /**
     * 发出消息气泡的 start 侧（左侧）内边距，默认 12dp。
     * 图片消息子类覆盖为 0，让图片贴满气泡左边。
     */
    protected open val bubblePaddingStartOut: Int get() = dp(12f)

    /**
     * 发出消息气泡的 end 侧（右侧）内边距，默认 16dp。
     * 右侧需要给尾巴留出更多空间，因此比 start 侧大。
     */
    protected open val bubblePaddingEndOut: Int get() = dp(16f)

    /**
     * 收到消息气泡的 start 侧（左侧）内边距，默认 16dp。
     * 收到消息尾巴在左侧，因此 start 侧需要更多空间。
     */
    protected open val bubblePaddingStartIn: Int get() = dp(16f)

    /**
     * 收到消息气泡的 end 侧（右侧）内边距，默认 12dp。
     */
    protected open val bubblePaddingEndIn: Int get() = dp(12f)

    /** 气泡顶部内边距，默认 8dp（文本与气泡顶边的距离） */
    protected open val bubblePaddingTop: Int get() = dp(8f)

    /** 气泡底部内边距，默认 8dp（时间文本与气泡底边的距离） */
    protected open val bubblePaddingBottom: Int get() = dp(8f)

    /**
     * 为 true 时，子 View 按 [TgMessageDrawable.buildClipPath] 生成的气泡轮廓（含圆角+尾巴）裁剪。
     * 为 false 时，用矩形加内缩量裁剪（适用于文本消息，兼容行内时间区域）。
     * 图片消息应开启，使图片四角与气泡圆角吻合，不超出尾巴。
     */
    protected open val clipChildrenToBubblePath: Boolean get() = false

    // ── 尾巴内缩量（防止内容覆盖尾巴） ──────────────────────────────────────

    /**
     * contentView 在 start 侧（尾巴所在侧）的额外内缩量（px）。
     * 图片消息若需要留出尾巴区域不被图片覆盖，子类可在此返回非零值。
     * 默认 0 = 图片延伸到尾巴区域（由 clipPath 裁剪控制最终显示）。
     */
    protected open fun tailInsetStart(fromMe: Boolean): Int = 0

    /**
     * contentView 在 end 侧的额外内缩量（px），默认 0。
     * 通常 end 侧（发出=左，收到=右）无尾巴，保持 0。
     */
    protected open fun tailInsetEnd(fromMe: Boolean): Int = 0

    /**
     * contentView 底部额外内缩量（px），默认 0。
     * 若尾巴从底部延伸，可在此给出内缩量，防止内容压到底部尾巴。
     */
    protected open fun tailInsetBottom(): Int = 0

    // ── 子 View 矩形裁剪内缩量 ───────────────────────────────────────────────

    /**
     * 子视图裁剪矩形的左侧内缩量（dp），默认 4dp（对应气泡圆角半径，避免子 View 遮挡圆角）。
     * 仅在 [clipChildrenToBubblePath] = false 时生效。
     */
    protected open val clipLeftInsetDp: Float get() = 4f

    /** 子视图裁剪矩形的顶部内缩量（dp），默认 4dp */
    protected open val clipTopInsetDp: Float get() = 4f

    /** 子视图裁剪矩形的底部内缩量（dp），默认 4dp */
    protected open val clipBottomInsetDp: Float get() = 4f

    /**
     * 发出消息时，子视图裁剪矩形的右侧内缩量（dp），默认 10dp。
     * 右侧多留 10dp 是为行内时间文本腾出空间，防止文字末尾与时间重叠。
     */
    protected open val clipRightInsetOutDp: Float get() = 10f

    /**
     * 收到消息时，子视图裁剪矩形的右侧内缩量（dp），默认 4dp（与左侧对称）。
     * 收到消息右侧无时间，无需额外留空。
     */
    protected open val clipRightInsetInDp: Float get() = 4f

    // ── 布局常量 ──────────────────────────────────────────────────────────────

    /** 时间换行时单独占用的行高（px），固定 16dp */
    private val timeRowHeight = dp(16f)

    /**
     * 行内时间模式下，时间文本左侧到末行文字右侧的最小间距（px），固定 10dp。
     * 用于判断末行是否还有足够空间放下时间，不够则换行。
     */
    private val timeExtraWidth = dp(10f)

    /**
     * 顶部 / 底部可选块与内容之间的间距（px），固定 4dp。
     * 用于 userName、reply、translate 块之间的间隔。
     */
    private val extraSpacing = dp(4f)

    /**
     * 气泡最大宽度 = 父容器宽度 × 0.72f，对齐 TG 原版比例。
     * 防止长文本撑满整个屏幕，保持消息列表的视觉层次。
     */
    private val maxBubbleWidthRatio = 0.72f

    // ── 气泡渲染样式 ──────────────────────────────────────────────────────────

    /**
     * 气泡渲染样式，来自 [Theme.MessageDrawable] 的常量：
     * - [Theme.MessageDrawable.STYLE_TAIL]（默认）：带尾巴的气泡，适用于文本/图片消息
     * - [Theme.MessageDrawable.STYLE_ROUNDED]：纯圆角矩形，无尾巴，适用于视频/文件消息
     * - [Theme.MessageDrawable.STYLE_COMPACT]：小圆角（6dp）紧凑气泡，适用于预览卡片
     */
    protected open val bubbleDrawableType: Int = Theme.MessageDrawable.STYLE_TAIL

    // ── 时间布局策略与内容抽象 ───────────────────────────────────────────────

    /**
     * 时间定位策略（抽象，子类必须实现）：
     * - [TgTimeAnchorInlineText]：时间内联到文本末行右侧，节省气泡高度
     * - [TgTimeAnchorBottomRight]：时间固定在气泡右下角，适用于图片/文件/视频
     */
    protected abstract val timeAnchor: TgTimeAnchor

    /**
     * 是否使用行内时间规则（文本消息专用）。
     * true = 优先尝试将时间放在末行右侧，只有放不下才换行。
     * false（默认）= 时间始终独立布局（如图片的叠加模式）。
     */
    protected open val inlineTimeWithText: Boolean = false

    /**
     * 内容视图（抽象，子类必须提供）：如 TextContentView、AlbumImageContentView 等。
     * 作为 ViewGroup 的核心子 View，承载消息的主要可视内容。
     */
    protected abstract val contentView: View

    /**
     * 内容视图的 TG 接口（抽象），提供 [TgContentView.getContentWidth] 和
     * [TgContentView.getLastLineWidth] 等方法，供布局宽度计算使用。
     * 通常与 [contentView] 指向同一对象（contentView 同时实现 TgContentView）。
     */
    protected abstract val contentAsTg: TgContentView

    // ── 可选子 View（由外部通过 setXxxView 注入） ────────────────────────────

    /**
     * 用户名 View（群聊中显示发送者昵称），置于气泡内容顶部。
     * 为 null 时不显示，不占用高度。
     */
    private var userNameView: View? = null

    /**
     * 引用/转发 View，置于用户名下方、内容视图上方。
     * 为 null 时不显示。
     */
    private var replyView: View? = null

    /**
     * 翻译 View，显示在内容视图下方。
     * 为 null 时不显示。
     */
    private var translateView: View? = null

    /**
     * 点赞/表情反应 View，锚定到气泡底部左侧。
     * 为 null 时不占用高度，`liveReserve = 0`。
     */
    private var liveView: View? = null

    // ── 时间布局状态 ──────────────────────────────────────────────────────────

    /**
     * 当前消息时间是否内联（与末行文字同行）。
     * 由 [onMeasure] 根据末行剩余宽度动态计算，每次测量后更新。
     */
    private var timeInline = false

    /**
     * 当前消息时间是否需要换行（独立占一行）。
     * timeWrapped = inlineTimeWithText && !timeInline。
     * timeWrapped 为 true 时，[onMeasure] 会在气泡高度中加入 [timeRowHeight]。
     */
    private var timeWrapped = false

    // ── 子 View 挂载状态 ─────────────────────────────────────────────────────

    /**
     * contentView 是否已通过 [addView] 挂载到 ViewGroup 中。
     * 防止 [onAttachedToWindow] 多次调用时重复 addView 导致崩溃。
     */
    private var contentAttached = false

    /**
     * 为 true 时，时间/状态 View 叠加在内容上方（图片/视频消息专用）。
     * 为 false（默认）时，时间/状态 View 位于气泡内，随气泡高度布局。
     */
    protected open val timeOverlay: Boolean = false

    /**
     * 公共时间+状态 View，所有消息类型共用。
     * 由 [TgTimeStatusView] 统一绘制时间文本、已读双勾等状态图标。
     */
    private val timeStatusView = TgTimeStatusView(context)

    /**
     * timeStatusView 是否已通过 [addView] 挂载，与 [contentAttached] 作用相同。
     */
    private var timeStatusAttached = false

    // ── 初始化 ────────────────────────────────────────────────────────────────

    init {
        setWillNotDraw(false)    // ViewGroup 默认不调用 onDraw/dispatchDraw，强制关闭此优化以绘制气泡
        setClipChildren(true)    // 子 View 超出父 ViewGroup 边界时裁剪，防止渲染溢出
        setClipToPadding(true)   // padding 区域内不绘制子 View（与 clipChildren 配合）
    }

    // ── 生命周期：挂载到窗口 ──────────────────────────────────────────────────

    override fun onAttachedToWindow() {
        bubbleDrawable.setBubbleType(bubbleDrawableType)  // 将子类声明的渲染样式同步到气泡绘制器
        super.onAttachedToWindow()                         // 调用父类，完成 ViewGroup 的挂载流程
        // contentView 在子类构造函数中创建，延迟到此处 addView，避免构造期抽象属性未初始化导致 NPE
        if (!contentAttached) {
            addView(contentView)          // 将内容视图（文本/图片等）作为子 View 加入 ViewGroup
            contentAttached = true        // 标记已挂载，防止后续 onAttachedToWindow 重复 addView
        }
        if (!timeStatusAttached) {
            addView(timeStatusView)       // 将时间+状态 View 加入 ViewGroup
            timeStatusAttached = true     // 标记已挂载
        }
    }

    // ── 数据绑定 ──────────────────────────────────────────────────────────────

    /**
     * 绑定消息基础信息（时间文本 + 方向），所有子类的 bindXxx 方法必须调用此方法。
     *
     * @param time 消息发送时间字符串（如 "12:30"）
     * @param out  true = 自己发出（右侧气泡），false = 收到（左侧气泡）
     */
    fun bindBase(time: String, out: Boolean) {
        timeText = time                                   // 保存时间文本供 timeStatusView 使用
        fromMe = out                                      // 更新消息方向，影响气泡左右位置和内边距
        bubbleDrawable.setOut(out)                        // 同步方向到气泡绘制器（更新尾巴位置和填充色）
        // 根据是否叠加时间选择时间样式：媒体叠加用半透明药丸，气泡内用纯色文字
        val tsStyle = if (timeOverlay) TgTimeStatusView.Style.BUBBLE else TgTimeStatusView.Style.PLAIN
        timeStatusView.bind(time, out, tsStyle)           // 将时间、方向、样式绑定到时间状态 View
        requestLayout()                                   // 触发重新测量和布局（气泡宽高可能变化）
        invalidate()                                      // 触发重绘（气泡方向/颜色已更新）
    }

    // ── 可选子 View 注入接口 ──────────────────────────────────────────────────

    /**
     * 设置用户名 View（群聊昵称，置于气泡顶部）。
     * 传 null 则移除并不显示，传新 View 则替换旧 View。
     */
    fun setUserNameView(view: View?) {
        userNameView?.let { removeView(it) }  // 先移除旧的用户名 View（如有），避免子 View 重复
        userNameView = view                    // 更新引用
        view?.let { addView(it) }              // 将新 View 加入 ViewGroup（null 时跳过）
        requestLayout(); invalidate()          // 布局和绘制都需要刷新（高度可能变化）
    }

    /**
     * 设置引用/转发 View（位于用户名下方、内容上方）。
     * 传 null 则移除。
     */
    fun setReplyView(view: View?) {
        replyView?.let { removeView(it) }     // 移除旧引用块
        replyView = view                       // 更新引用
        view?.let { addView(it) }              // 添加新引用块
        requestLayout(); invalidate()          // 刷新布局和绘制
    }

    /**
     * 设置翻译 View（位于内容下方）。
     * 传 null 则移除。
     */
    fun setTranslateView(view: View?) {
        translateView?.let { removeView(it) } // 移除旧翻译块
        translateView = view                   // 更新引用
        view?.let { addView(it) }              // 添加新翻译块
        requestLayout(); invalidate()          // 刷新布局和绘制
    }

    /**
     * 设置点赞/表情反应 View（锚定气泡底部左侧）。
     * 传 null 则移除。
     */
    fun setLiveView(view: View?) {
        liveView?.let { removeView(it) }      // 移除旧点赞块
        liveView = view                        // 更新引用
        view?.let { addView(it) }              // 添加新点赞块
        requestLayout(); invalidate()          // 刷新布局和绘制
    }

    // ── 测量 ──────────────────────────────────────────────────────────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        TgTheme.init(resources.displayMetrics.density)    // 初始化 TG 文本/时间画笔（只做一次，有幂等保护）
        val width = MeasureSpec.getSize(widthMeasureSpec) // 获取父容器分配的可用宽度（px）
        val maxBubbleWidth = (width * maxBubbleWidthRatio).toInt()  // 气泡最大宽度 = 容器 × 0.72
        // 气泡内容可用宽度 = 气泡最大宽度 - 两侧内边距（根据发出/收到选取对应内边距）
        val contentWidth = maxBubbleWidth - (getPaddingStartLocal() + getPaddingEndLocal())

        val textContentView = contentView as? TextContentView  // 尝试将 contentView 转为文本视图（图片等为 null）
        // 先清掉旧的预留宽度，避免上一条消息的预留影响本次内联时间判断
        textContentView?.setReservedRight(0)

        // 第一次测量 contentView（使用完整内容宽度，不预留时间空间）
        contentView.measure(
            MeasureSpec.makeMeasureSpec(max(1, contentWidth), MeasureSpec.AT_MOST), // 最大不超过 contentWidth
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)                 // 高度不限制
        )

        // 测量时间状态 View（数据在 bindBase 中已通过 bind() 设置好）
        timeStatusView.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), // 宽度不限制，让其自适应
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)  // 高度不限制
        )
        val tsContentW = timeStatusView.getContentWidth()  // 时间状态 View 的实际内容宽度（px）
        var textWidth = contentAsTg.getContentWidth()       // 内容视图的实际内容宽度（用于计算气泡宽度）
        var lastLineWidth = contentAsTg.getLastLineWidth()  // 文本末行宽度（用于判断行内时间是否放得下）

        if (timeOverlay) {
            // 媒体叠加模式（图片/视频）：时间 View 浮在内容上，不参与气泡宽度/高度计算
            timeInline = false   // 媒体消息无行内时间概念
            timeWrapped = false  // 媒体消息无时间换行概念
        } else {
            // 文本气泡行内时间：判断末行右侧是否能放下时间+间距
            timeInline = inlineTimeWithText &&
                lastLineWidth + timeExtraWidth + tsContentW <= contentWidth
            // 内联放不下时，时间需要独立换行
            timeWrapped = inlineTimeWithText && !timeInline

            // 如果判定可以内联，则为 TextContentView 预留时间宽度，避免文字与时间重叠
            if (inlineTimeWithText && timeInline && textContentView != null) {
                val reserve = (timeExtraWidth + tsContentW).toInt()  // 需要为时间预留的像素宽度
                textContentView.setReservedRight(reserve)             // 告知 TextContentView 末行右侧被时间占用
                // 第二次测量：带预留宽度重新测量，末行会避让时间位置
                textContentView.measure(
                    MeasureSpec.makeMeasureSpec(max(1, contentWidth), MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                )
                textWidth = contentAsTg.getContentWidth()      // 更新内容宽度（可能因换行而变宽）
                lastLineWidth = contentAsTg.getLastLineWidth() // 更新末行宽度（预留后末行可能已变短）
                // 二次验证：预留后末行是否仍然放得下时间（可能因行回绕导致末行变长）
                timeInline = lastLineWidth + timeExtraWidth + tsContentW <= contentWidth
                timeWrapped = inlineTimeWithText && !timeInline
                if (!timeInline) {
                    // 预留后仍然放不下，退回"时间换行"模式，释放预留宽度
                    textContentView.setReservedRight(0)
                    // 第三次测量：不带预留宽度，让文本自由排布
                    textContentView.measure(
                        MeasureSpec.makeMeasureSpec(max(1, contentWidth), MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                    )
                    textWidth = contentAsTg.getContentWidth()      // 再次更新内容宽度
                    lastLineWidth = contentAsTg.getLastLineWidth() // 再次更新末行宽度
                }
            }
        }

        // 计算行内时间宽度（末行 + 间距 + 时间）
        val inlineWidthF = lastLineWidth.toFloat() + timeExtraWidth.toFloat() + tsContentW

        // 根据时间布局模式决定气泡内容宽度：取内容宽度与时间相关宽度的较大值
        val bubbleContentWidth = when {
            timeOverlay                                 -> textWidth              // 媒体叠加：气泡宽度只看内容
            inlineTimeWithText && timeInline            -> max(textWidth, inlineWidthF.toInt()) // 内联：比较内容和"末行+时间"
            inlineTimeWithText && timeWrapped           -> max(textWidth, contentWidth)          // 换行：气泡撑满可用宽度
            else                                        -> max(textWidth, tsContentW.toInt())   // 其他：内容和时间的较大值
        }

        // 气泡总宽度 = 内容宽度 + 两侧内边距
        val bubbleWidth = bubbleContentWidth + getPaddingStartLocal() + getPaddingEndLocal()
        val contentHeight = contentView.measuredHeight  // 内容视图的测量高度（已测量过）

        // 测量顶部可选块：用户名 + 引用，返回总高度（含块间间距）
        val userNameH = measureOptionalChild(userNameView, bubbleContentWidth)
        val replyH = measureOptionalChild(replyView, bubbleContentWidth)
        // 若顶部块不为空，则在其后加间距；全部为空则不加
        val topBlocksH = listOf(userNameH, replyH).filter { it > 0 }.sum() +
            if ((userNameH + replyH) > 0) extraSpacing else 0

        // 测量底部可选块：翻译
        val translateH = measureOptionalChild(translateView, bubbleContentWidth)
        val bottomBlocksH = if (translateH > 0) translateH + extraSpacing else 0  // 翻译块加上与内容的间距

        // 当存在顶部或底部额外块时，行内时间会与它们在宽度上竞争，强制换行避免重叠
        if ((topBlocksH > 0 || bottomBlocksH > 0) && inlineTimeWithText) {
            timeInline = false  // 有额外块时不使用行内时间
            timeWrapped = true  // 时间改为独立换行
        }

        // 测量点赞/反应 View 并计算底部预留高度
        val liveH = measureOptionalChild(liveView, bubbleContentWidth)
        val liveReserve = if (liveH > 0) liveH + extraSpacing else 0  // 点赞块加上与底部的间距

        // 气泡总高度 = 顶部块 + 内容 + 底部块 + 时间换行高度（可选）+ 点赞预留 + 上下内边距
        val bubbleHeight = topBlocksH + contentHeight + bottomBlocksH + bubblePaddingTop + bubblePaddingBottom +
            liveReserve + if (timeWrapped) timeRowHeight else 0
        val totalHeight = bubbleHeight + dp(12f)  // 气泡上下各留 6dp 与 RecyclerView item 边缘的间距

        // 动画进行中或 awaitingLayout 期间，保持测量高度为旧值（startRect 高度），
        // 避免气泡变大时向上推动列表中的其他条目。仅通过绘制插值展示变化。
        val animatingBackground = (transitionParams.isRunning || transitionParams.awaitingLayout) &&
            transitionParams.startRect.height() > 0f  // startRect 有效才生效

        val measuredHeight = if (animatingBackground) {
            // 动画期间：使用 startRect 高度（旧气泡高度），等效于"测量高度冻结"
            val oldTotalHeight = if (transitionParams.startRect.height() > 0f) {
                // startRect 存储的是气泡矩形高度，加上上下间距（6dp × 2）还原为 item 总高度
                (transitionParams.startRect.height() + dpF(12f)).toInt()
            } else {
                totalHeight  // 兜底：startRect 无效时用真实高度
            }
            oldTotalHeight
        } else {
            totalHeight  // 正常情况：使用真实计算高度
        }
        setMeasuredDimension(width, measuredHeight)  // 向系统报告测量结果（宽度始终取父容器宽度）
    }

    // ── 布局 ──────────────────────────────────────────────────────────────────

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val oldRect = RectF(lastBubbleRect)  // 保存上次布局的 rect（仅作调试/参考，当前未使用）
        val width = width.toFloat()           // 获取当前 View 的宽度（px，已在 onMeasure 中设置）

        // 判断是否处于动画运行中（startRect 和 endRect 都有效才算）
        val isAnimating = transitionParams.isRunning &&
            transitionParams.startRect.width() > 0 && transitionParams.startRect.height() > 0 &&
            transitionParams.endRect.width() > 0 && transitionParams.endRect.height() > 0

        if (!isAnimating) {
            // 非动画状态：重新计算 bubbleRect（目标气泡矩形）
            val maxBubbleWidth = (width * maxBubbleWidthRatio).toInt()
            val contentWidth = maxBubbleWidth - (getPaddingStartLocal() + getPaddingEndLocal())
            val tsContentW = timeStatusView.getContentWidth()        // 时间状态 View 宽度
            val textWidth = contentAsTg.getContentWidth()            // 内容宽度（测量后已计算）
            val lastLineWidth = contentAsTg.getLastLineWidth()       // 末行宽度（仅文本有意义）
            val inlineWidthF = lastLineWidth.toFloat() + timeExtraWidth.toFloat() + tsContentW
            // 与 onMeasure 中相同的逻辑：根据时间布局模式确定气泡内容宽度
            val bubbleContentWidth = when {
                timeOverlay                                 -> textWidth
                inlineTimeWithText && timeInline            -> max(textWidth, inlineWidthF.toInt())
                inlineTimeWithText && timeWrapped           -> max(textWidth, contentWidth)
                else                                        -> max(textWidth, tsContentW.toInt())
            }
            val bubbleWidth = bubbleContentWidth + getPaddingStartLocal() + getPaddingEndLocal()
            val contentHeight = contentView.measuredHeight
            val userNameH = userNameView?.measuredHeight ?: 0       // 用户名高度（可选）
            val replyH = replyView?.measuredHeight ?: 0             // 引用高度（可选）
            val translateH = translateView?.measuredHeight ?: 0     // 翻译高度（可选）
            val topBlocksH = listOf(userNameH, replyH).filter { it > 0 }.sum() +
                if ((userNameH + replyH) > 0) extraSpacing else 0   // 顶部块总高度
            val bottomBlocksH = if (translateH > 0) translateH + extraSpacing else 0  // 底部块总高度
            val liveH = liveView?.measuredHeight ?: 0               // 点赞块高度（可选）
            val liveReserve = if (liveH > 0) liveH + extraSpacing else 0  // 底部预留高度
            val bubbleHeight = topBlocksH + contentHeight + bottomBlocksH +
                bubblePaddingTop + bubblePaddingBottom + liveReserve +
                if (timeWrapped) timeRowHeight else 0                // 气泡总高度
            // 气泡左上角坐标：发出消息靠右（距右边 8dp），收到消息靠左（距左边 8dp）
            val left = if (fromMe) width - bubbleWidth - dpF(8f) else dpF(8f)
            val top = dpF(6f)  // 气泡顶部距 item 顶部 6dp，为视觉间距
            bubbleRect.set(left, top, left + bubbleWidth, top + bubbleHeight)  // 更新气泡目标矩形

            if (transitionParams.awaitingLayout) {
                // awaitingLayout = true 表示 prepareTransition 已保存 startRect，现在 layout 完成，
                // 设置 endRect（新气泡矩形）并启动动画
                transitionParams.endRect.set(bubbleRect)      // 记录动画终点
                transitionParams.awaitingLayout = false        // 清除等待标志
                // 通过 post 延迟一帧启动动画，确保 endRect 在主线程已完整写入
                post {
                    startTransitionAnimation()
                }
            }
            lastBubbleRect.set(bubbleRect)  // 同步 lastBubbleRect，供下次 prepareTransition 使用
        }

        // layoutRect：确定本次 onLayout 中子 View 应该对齐的气泡矩形
        // 三种情况：动画进行中（插值矩形）/ awaitingLayout（旧矩形，防止跳变）/ 正常（当前 bubbleRect）
        val layoutRect = when {
            isAnimating -> {
                // 动画进行中：根据进度在 startRect 和 endRect 之间插值，子 View 随气泡平滑移动
                val p = transitionParams.animateChangeProgress
                RectF(
                    lerp(transitionParams.startRect.left, transitionParams.endRect.left, p),
                    lerp(transitionParams.startRect.top, transitionParams.endRect.top, p),
                    lerp(transitionParams.startRect.right, transitionParams.endRect.right, p),
                    lerp(transitionParams.startRect.bottom, transitionParams.endRect.bottom, p)
                )
            }
            transitionParams.awaitingLayout && !transitionParams.startRect.isEmpty -> {
                // 等待 layout 阶段：布局已更新但动画未开始，子 View 仍保持在旧位置防止跳变
                transitionParams.startRect
            }
            else -> {
                // 正常情况：子 View 对齐当前气泡矩形
                bubbleRect
            }
        }

        // ── 子 View 布局（从上到下依次排列） ───────────────────────────────────
        // cy = 当前纵向游标（从气泡顶部内边距开始）
        var cy = (layoutRect.top + bubblePaddingTop).toInt()

        // start 侧横坐标（发出消息=气泡左边，收到消息=气泡右边，需减去子 View 宽度）
        val cx = if (fromMe) {
            (layoutRect.left + getPaddingStartLocal()).toInt()   // 发出：从气泡左边 + start 内边距
        } else {
            val userNameWidth = userNameView?.measuredWidth ?: 0
            (layoutRect.right - getPaddingEndLocal() - userNameWidth).toInt()  // 收到：右对齐
        }

        // 1) 用户名 View（可选）
        userNameView?.let { child ->
            child.layout(cx, cy, cx + child.measuredWidth, cy + child.measuredHeight)  // 布局用户名
            cy += child.measuredHeight + extraSpacing  // 游标下移（用户名高度 + 间距）
        }

        // 2) 引用/转发 View（可选）
        replyView?.let { child ->
            val replyX = if (fromMe) {
                (layoutRect.left + getPaddingStartLocal()).toInt()               // 发出：左对齐
            } else {
                (layoutRect.right - getPaddingEndLocal() - child.measuredWidth).toInt()  // 收到：右对齐
            }
            child.layout(replyX, cy, replyX + child.measuredWidth, cy + child.measuredHeight)
            cy += child.measuredHeight + extraSpacing  // 游标下移
        }

        // 3) 内容 View（核心，文本/图片等）
        // 限制 contentLayoutWidth 不超过气泡内容宽度（动画插值时 layoutRect 可能比内容窄）
        val actualBubbleContentWidth = (layoutRect.width() - getPaddingStartLocal() - getPaddingEndLocal()).toInt()
        val maxContentWidth = actualBubbleContentWidth.coerceAtLeast(1)   // 至少为 1，防止宽度为负
        val contentLayoutWidth = kotlin.math.min(contentView.measuredWidth, maxContentWidth)  // 取测量宽度和最大宽度的较小值

        val contentX = if (fromMe) {
            (layoutRect.left + getPaddingStartLocal()).toInt()               // 发出：左对齐
        } else {
            (layoutRect.right - getPaddingEndLocal() - contentLayoutWidth).toInt()  // 收到：右对齐
        }
        val tiStart = tailInsetStart(fromMe)   // 尾巴侧额外内缩（如有）
        val tiEnd = tailInsetEnd(fromMe)       // 对侧额外内缩
        val tiBottom = tailInsetBottom()       // 底部额外内缩
        contentView.layout(
            contentX + tiStart,                          // left：加上尾巴侧内缩
            cy,                                          // top：当前游标
            contentX + contentLayoutWidth - tiEnd,       // right：减去对侧内缩
            cy + contentView.measuredHeight - tiBottom   // bottom：减去底部内缩
        )
        cy += contentView.measuredHeight + extraSpacing  // 游标下移（内容高度 + 间距）

        // 4) 翻译 View（可选，置于内容下方）
        translateView?.let { child ->
            val translateX = if (fromMe) {
                (layoutRect.left + getPaddingStartLocal()).toInt()               // 发出：左对齐
            } else {
                (layoutRect.right - getPaddingEndLocal() - child.measuredWidth).toInt()  // 收到：右对齐
            }
            child.layout(translateX, cy, translateX + child.measuredWidth, cy + child.measuredHeight)
            // 注意：cy 此处不递增，translate 之后无其他顺序块（liveView 使用绝对底部定位）
        }

        // 5) 点赞/反应 View（可选，锚定到气泡底部左侧，使用绝对 y 坐标）
        liveView?.let { child ->
            val ly = (layoutRect.bottom - bubblePaddingBottom - child.measuredHeight).toInt()  // 距气泡底部内边距
            val liveX = if (fromMe) {
                (layoutRect.left + getPaddingStartLocal()).toInt()               // 发出：左对齐
            } else {
                (layoutRect.right - getPaddingEndLocal() - child.measuredWidth).toInt()  // 收到：右对齐
            }
            child.layout(liveX, ly, liveX + child.measuredWidth, ly + child.measuredHeight)
        }

        // 6) 时间+状态 View（所有消息类型公用，由 TgTimeStatusView 统一绘制）
        val tsW = timeStatusView.measuredWidth
        val tsH = timeStatusView.measuredHeight
        if (timeOverlay) {
            // 媒体叠加模式：时间 View 置于图片内容区域的右下角（减去尾巴内缩和内边距）
            val tiEnd    = tailInsetEnd(fromMe)      // 端侧尾巴内缩（避免时间跑到尾巴区域外）
            val tiBottom = tailInsetBottom()          // 底部尾巴内缩
            val imgRight  = layoutRect.right  - getPaddingEndLocal()  - tiEnd    // 图片有效区域右边界
            val imgBottom = layoutRect.bottom - bubblePaddingBottom   - tiBottom // 图片有效区域下边界
            val marginPx  = dpF(8f)                  // 时间 View 距图片右/下边的内边距 8dp
            val tsX = (imgRight  - marginPx - tsW).toInt()  // 时间 View 左边坐标
            val tsY = (imgBottom - marginPx - tsH).toInt()  // 时间 View 顶边坐标
            timeStatusView.layout(tsX, tsY, tsX + tsW, tsY + tsH)
        } else {
            // 气泡内模式（文本/文件）：时间 View 置于气泡右下角的内边距内
            val tsX = (layoutRect.right - getPaddingEndLocal() - tsW).toInt()    // 右对齐
            val tsY = (layoutRect.bottom - bubblePaddingBottom - tsH).toInt()    // 底部对齐
            timeStatusView.layout(tsX, tsY, tsX + tsW, tsY + tsH)
        }
    }

    // ── 绘制 ──────────────────────────────────────────────────────────────────

    override fun dispatchDraw(canvas: Canvas) {
        drawBubble(canvas)  // 先绘制气泡背景（含圆角+尾巴），使用动画插值后的矩形
        val rect = getDrawBubbleRect(drawBubbleRect)  // 获取当前帧的气泡绘制矩形（插值结果复用到 drawBubbleRect）
        canvas.save()  // 保存当前 Canvas 状态，以便 restore 后恢复裁剪区域
        if (clipChildrenToBubblePath) {
            // 按气泡轮廓 Path（含圆角+尾巴形状）裁剪子 View，适用于图片消息
            try {
                // bounds 已在 drawBubble 中设置到 bubbleDrawable，此处直接生成裁剪路径
                canvas.clipPath(bubbleDrawable.buildClipPath())
            } catch (_: Exception) {
                // Path 裁剪失败时（如硬件加速不支持某些路径操作），降级为矩形裁剪
                if (fromMe) {
                    canvas.clipRect(
                        rect.left + dpF(clipLeftInsetDp), rect.top + dpF(clipTopInsetDp),
                        rect.right - dpF(clipRightInsetOutDp), rect.bottom - dpF(clipBottomInsetDp)
                    )
                } else {
                    canvas.clipRect(
                        rect.left + dpF(clipLeftInsetDp), rect.top + dpF(clipTopInsetDp),
                        rect.right - dpF(clipRightInsetInDp), rect.bottom - dpF(clipBottomInsetDp)
                    )
                }
            }
        } else if (fromMe) {
            // 发出消息矩形裁剪：右侧多留 clipRightInsetOutDp（默认 10dp）给行内时间
            canvas.clipRect(
                rect.left + dpF(clipLeftInsetDp), rect.top + dpF(clipTopInsetDp),
                rect.right - dpF(clipRightInsetOutDp), rect.bottom - dpF(clipBottomInsetDp)
            )
        } else {
            // 收到消息矩形裁剪：左右对称（收到消息右侧无时间，用 clipRightInsetInDp = 4dp）
            canvas.clipRect(
                rect.left + dpF(clipLeftInsetDp), rect.top + dpF(clipTopInsetDp),
                rect.right - dpF(clipRightInsetInDp), rect.bottom - dpF(clipBottomInsetDp)
            )
        }
        // 子 View 布局坐标基于 bubbleRect（最终位置），直接绘制，无需 canvas.translate 偏移
        super.dispatchDraw(canvas)  // 调用父类绘制所有子 View（在裁剪区域内）
        canvas.restore()            // 恢复 Canvas 状态，移除裁剪区域限制
    }

    /**
     * 绘制气泡背景到画布（先于子 View 绘制）。
     *
     * 将插值后的浮点矩形 [drawBubbleRect] 转换为整型 [android.graphics.Rect]，
     * 设置为 [bubbleDrawable] 的 bounds，再调用其 [draw] 方法渲染气泡。
     */
    private fun drawBubble(canvas: Canvas) {
        val rect = getDrawBubbleRect(drawBubbleRect)  // 获取当前帧的气泡矩形（含动画插值）
        bubbleDrawable.bounds = android.graphics.Rect(  // 将浮点矩形转换为整型并设置为 Drawable 边界
            rect.left.toInt(),
            rect.top.toInt(),
            rect.right.toInt(),
            rect.bottom.toInt()
        )
        bubbleDrawable.draw(canvas)  // 绘制气泡填充色 + 圆角路径 + 描边
    }

    // ── 辅助方法 ──────────────────────────────────────────────────────────────

    /**
     * 测量可选子 View 的高度，子 View 为 null 时返回 0。
     *
     * @param child    可选子 View（userNameView / replyView / translateView / liveView）
     * @param maxWidth 子 View 的最大可用宽度（气泡内容宽度，px）
     * @return 子 View 的测量高度（px），null 时返回 0
     */
    private fun measureOptionalChild(child: View?, maxWidth: Int): Int {
        child ?: return 0  // null 时跳过测量，返回 0（不占高度）
        val widthSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST)    // 宽度最大不超过气泡内容宽度
        val heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)      // 高度不限，让子 View 自适应
        child.measure(widthSpec, heightSpec)  // 执行测量
        return child.measuredHeight           // 返回测量后的高度
    }

    /**
     * 根据消息方向获取 start 侧内边距。
     * 发出消息（右侧）的 start 侧 = 气泡左边（[bubblePaddingStartOut]）；
     * 收到消息（左侧）的 start 侧 = 气泡左边（[bubblePaddingStartIn]）。
     */
    private fun getPaddingStartLocal(): Int = if (fromMe) bubblePaddingStartOut else bubblePaddingStartIn

    /**
     * 根据消息方向获取 end 侧内边距。
     * 发出消息（右侧）的 end 侧 = 气泡右边（[bubblePaddingEndOut]）；
     * 收到消息（左侧）的 end 侧 = 气泡右边（[bubblePaddingEndIn]）。
     */
    private fun getPaddingEndLocal(): Int = if (fromMe) bubblePaddingEndOut else bubblePaddingEndIn

    /** 将 dp 值转换为整型像素（四舍五入），使用当前屏幕密度 */
    protected fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    /** 将 dp 值转换为浮点像素，使用 TgAndroidUtilities 工具方法 */
    protected fun dpF(value: Float): Float = TgAndroidUtilities.dpF(value, resources.displayMetrics.density)

    // ── 过渡动画 ──────────────────────────────────────────────────────────────

    /**
     * 准备过渡动画（在更新消息内容之前调用）。
     *
     * 职责：
     * 1. 取消正在执行的旧动画，防止动画叠加
     * 2. 将当前气泡矩形保存为 startRect（动画起点）
     * 3. 清空 endRect，等待下次 [onLayout] 计算后填入
     * 4. 设置 [TransitionParams.awaitingLayout] = true，标记"等待布局完成"
     *
     * @param payloads 本次变化涉及的字段集合（如 TEXT / USER_NAME 等），
     *                 用于 [applyExtraViewProgress] 中决定哪些子 View 需要进场动画
     */
    fun prepareTransition(payloads: Set<String>) {
        transitionAnimator?.cancel()                  // 取消旧动画（会触发 onAnimationCancel → finishTransition）
        transitionParams.payloads = payloads           // 记录本次变化的 payload，供动画进度回调使用
        transitionParams.animateChangeProgress = 0f   // 重置动画进度到起点
        transitionParams.isRunning = false             // 标记动画未启动（启动后变为 true）
        transitionParams.awaitingLayout = true         // 标记"等待 layout 完成后再启动动画"
        // 优先使用 lastBubbleRect 作为 startRect（更稳定，等同于上次布局的最终矩形）
        if (!lastBubbleRect.isEmpty) {
            transitionParams.startRect.set(lastBubbleRect)  // 保存旧气泡矩形为动画起点
        } else if (!bubbleRect.isEmpty) {
            transitionParams.startRect.set(bubbleRect)      // 兜底：使用 bubbleRect
        }
        transitionParams.endRect.setEmpty()  // 清空终点，等待 onLayout 设置新 bubbleRect 后填入
    }

    /**
     * 启动过渡动画（由 [onLayout] 中 awaitingLayout 分支 post 调用，确保 endRect 已设置）。
     *
     * 动画参数：
     * - 时长：220ms（对齐 TG 气泡变化动画时长）
     * - 插值器：PathInterpolator(0.2, 0, 0.2, 1)（TG 标准弹性缓动，快入慢出）
     * - 每帧更新 [TransitionParams.animateChangeProgress]，触发 requestLayout + invalidate
     */
    fun startTransitionAnimation() {
        // 兜底：若 startRect 还未设置，用当前 bubbleRect 作为起点（通常不应发生）
        if (transitionParams.startRect.isEmpty) {
            transitionParams.startRect.set(bubbleRect)
        }
        // 兜底：若 endRect 还未设置（layout 未执行），用当前 bubbleRect 作为终点
        if (transitionParams.endRect.isEmpty) {
            transitionParams.endRect.set(bubbleRect)
        }

        // 若起点和终点完全相同（气泡尺寸未变），无需执行动画，直接完成
        if (transitionParams.startRect.left == transitionParams.endRect.left &&
            transitionParams.startRect.top == transitionParams.endRect.top &&
            transitionParams.startRect.right == transitionParams.endRect.right &&
            transitionParams.startRect.bottom == transitionParams.endRect.bottom) {
            finishTransition()  // 尺寸无变化，跳过动画
            return
        }

        transitionParams.isRunning = true     // 标记动画正在执行
        transitionParams.awaitingLayout = false  // 清除等待标志（layout 已完成）
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {  // 创建 0→1 的进度动画
            duration = 220L                              // 动画时长 220ms
            interpolator = PathInterpolator(0.2f, 0f, 0.2f, 1f)  // TG 标准缓动：快出慢入
            addUpdateListener {
                val progress = it.animatedValue as Float           // 当前动画进度 [0, 1]
                transitionParams.animateChangeProgress = progress   // 更新过渡参数中的进度值
                applyExtraViewProgress(transitionParams.payloads, progress)  // 驱动额外子 View 的进场动画
                // 重新布局：子 View 的坐标基于插值 layoutRect，需要每帧重新 layout
                requestLayout()
                invalidate()  // 重绘：气泡矩形基于插值 drawBubbleRect，也需要每帧重绘
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    finishTransition()  // 动画被取消时（如新消息到来），立即完成到终点状态
                }
                override fun onAnimationEnd(animation: Animator) {
                    finishTransition()  // 动画正常结束，清理状态
                }
            })
        }
        transitionAnimator = animator  // 保存引用，供 prepareTransition 中 cancel 使用
        animator.start()               // 启动动画
    }

    /**
     * 完成过渡动画（无论是正常结束还是取消），将所有状态重置为"静止"。
     *
     * 重置内容：
     * - animateChangeProgress = 1f（停在终点）
     * - isRunning = false
     * - 清空 startRect / endRect
     * - 更新 lastBubbleRect 为最终状态
     * - requestLayout + invalidate 确保子 View 定位到最终坐标
     */
    private fun finishTransition() {
        transitionParams.animateChangeProgress = 1f   // 进度置为 1，确保所有插值都停在终点
        transitionParams.isRunning = false              // 标记动画已停止
        transitionParams.awaitingLayout = false         // 清除等待标志
        applyExtraViewProgress(transitionParams.payloads, 1f)  // 将额外子 View 动画推进到最终状态
        lastBubbleRect.set(bubbleRect)                  // 更新 lastBubbleRect 为动画结束后的气泡矩形
        transitionParams.startRect.setEmpty()           // 清空 startRect，为下次动画准备
        transitionParams.endRect.setEmpty()             // 清空 endRect，为下次动画准备
        transitionAnimator = null                       // 释放动画器引用，允许 GC
        requestLayout()  // 重新布局，使子 View 定位到最终位置（不再走动画插值分支）
        invalidate()      // 重绘，确保最终气泡矩形正确显示
    }

    /**
     * 根据动画进度 [progress] 驱动额外子 View（用户名/引用/翻译/点赞）的进场动画。
     *
     * 每个子 View 的动画效果：
     * - alpha：0 → 1（淡入）
     * - translationY：4dp → 0（向上平移，从稍低位置飞入）
     *
     * 只有 [payloads] 中包含对应字段时才执行该 View 的动画，避免无关 View 也跟着动。
     *
     * @param payloads  本次变化涉及的字段集合
     * @param progress  动画进度 [0f, 1f]，0 = 动画起点，1 = 终点
     */
    private fun applyExtraViewProgress(payloads: Set<String>, progress: Float) {
        val offset = dpF(4f) * (1f - progress)  // 从 4dp 向上平移到 0（progress=1 时 offset=0）
        if (payloads.contains(TgMessagePayloads.USER_NAME)) {
            userNameView?.apply { alpha = progress; translationY = offset }  // 用户名淡入 + 上移
        }
        if (payloads.contains(TgMessagePayloads.QUOTE)) {
            replyView?.apply { alpha = progress; translationY = offset }     // 引用块淡入 + 上移
        }
        if (payloads.contains(TgMessagePayloads.TRANSLATION)) {
            translateView?.apply { alpha = progress; translationY = offset } // 翻译块淡入 + 上移
        }
        if (payloads.contains(TgMessagePayloads.REACTIONS)) {
            liveView?.apply { alpha = progress; translationY = offset }      // 点赞块淡入 + 上移
        }
    }

    /**
     * 获取当前帧的气泡绘制矩形（含动画插值），结果写入 [out] 并返回（复用对象，减少 GC）。
     *
     * 逻辑：
     * - 动画运行中且 startRect/endRect 都有效 → 按进度插值四条边
     * - 否则（动画未运行或数据无效）→ 直接使用 [bubbleRect]（当前目标矩形）
     *
     * @param out 结果矩形（复用传入的 [drawBubbleRect]，不每帧 new）
     * @return    [out] 本身，方便链式调用
     */
    private fun getDrawBubbleRect(out: RectF): RectF {
        if (transitionParams.isRunning &&
            transitionParams.startRect.width() > 0 && transitionParams.startRect.height() > 0 &&
            transitionParams.endRect.width() > 0 && transitionParams.endRect.height() > 0) {
            // 动画运行且两端矩形都有效：对四条边分别线性插值
            val p = transitionParams.animateChangeProgress
            out.set(
                lerp(transitionParams.startRect.left,   transitionParams.endRect.left,   p),  // 左边插值
                lerp(transitionParams.startRect.top,    transitionParams.endRect.top,    p),  // 上边插值
                lerp(transitionParams.startRect.right,  transitionParams.endRect.right,  p),  // 右边插值
                lerp(transitionParams.startRect.bottom, transitionParams.endRect.bottom, p)   // 下边插值
            )
        } else {
            // 动画未运行或矩形数据无效：直接使用当前气泡矩形
            out.set(bubbleRect)
        }
        return out
    }

    /**
     * 供发送 overlay 使用，拿到当前绘制中的气泡矩形（cell 坐标系，返回新对象）。
     */
    fun getBubbleRect(): RectF = getDrawBubbleRect(RectF())

    /**
     * 线性插值：在 [start] 和 [end] 之间按 [progress] 比例取值。
     * progress=0 → start，progress=1 → end，中间值为两端的加权平均。
     *
     * @param start    起始值
     * @param end      终止值
     * @param progress 进度 [0f, 1f]
     * @return         插值结果
     */
    private fun lerp(start: Float, end: Float, progress: Float): Float = start + (end - start) * progress
}

/**
 * 气泡尺寸过渡动画参数容器。
 *
 * 职责：集中管理动画状态，避免在 [BaseTgMessageCell] 中分散多个零散字段。
 * 由 [BaseTgMessageCell.prepareTransition] 写入，[BaseTgMessageCell.startTransitionAnimation]
 * 和 [BaseTgMessageCell.finishTransition] 读写，驱动气泡宽高变化的平滑插值动画。
 */
class TransitionParams {
    /**
     * 当前动画进度 [0f, 1f]：0 = 完全在 startRect，1 = 完全在 endRect。
     * 初始值 1f 表示"静止在终点状态"，避免首次布局前错误插值。
     */
    var animateChangeProgress: Float = 1f

    /**
     * 动画是否正在执行。
     * true = [startTransitionAnimation] 已调用，ValueAnimator 正在运行。
     */
    var isRunning: Boolean = false

    /**
     * 是否处于"等待 layout 完成"状态。
     * true = [prepareTransition] 已调用（startRect 已保存），但 layout 尚未执行（endRect 未设置）。
     * 此状态下 [onMeasure] 使用 startRect 高度冻结测量，[onLayout] 完成后置为 false 并触发动画。
     */
    var awaitingLayout: Boolean = false

    /**
     * 本次动画关联的变化字段集合（来自 [TgMessagePayloads]）。
     * 用于 [BaseTgMessageCell.applyExtraViewProgress] 判断哪些可选子 View 需要进场动画。
     */
    var payloads: Set<String> = emptySet()

    /**
     * 动画起始矩形（动画开始前的气泡区域）。
     * 由 [BaseTgMessageCell.prepareTransition] 在内容更新前保存，动画结束后通过 [setEmpty] 清空。
     */
    val startRect: RectF = RectF()

    /**
     * 动画终止矩形（内容更新后的气泡区域）。
     * 由 [BaseTgMessageCell.onLayout] 在计算出新 bubbleRect 后填入，动画结束后通过 [setEmpty] 清空。
     */
    val endRect: RectF = RectF()
}
