## 总体架构
- 目标：以“消息气泡 Cell”为核心，抽象统一的可组合容器，覆盖文本、图片、视频、文件等内容形态，支持时间/状态/翻译/回复/点赞等模块化叠加，保持 Telegram 级别的性能与动画。
- 模块分层：
  - 数据层：MessageModel（文本/媒体/状态/时间/用户信息/回复/翻译/点赞列表）
  - 渲染层：BaseTgMessageCell（容器）+ 子视图（BubbleView、ContentView、ReplyView、TranslateView、LiveView、Time/Status）
  - 动画层：Enter/Highlight/选择涟漪、消息飞入叠加（MessageEnterTransitionContainer）
  - 主题层：颜色/圆角/阴影/文字样式的主题提供器

## 布局结构（与草图一致）
- BaseTgMessageCell（自绘 + 复合子 View）
  - 左侧：选择圆、AvatarView（可选）
  - 中间：BubbleView（自绘圆角气泡 + 主题阴影）
    - 顶部：UserName（按需显示）
    - ReplyView（引用/转发区域，可复用 xml inflate）
    - ContentView（多态：TextContentView / MediaContentView / FileContentView）
    - TranslateView（翻译区域，可复用 xml inflate）
    - 底部：LiveView（点赞/反应列表）
    - 底侧：编辑中/时间/消息状态（自绘）

## 关键测量与时间位置规则
- 统一测量：先测 Bubble 内部内容矩形 contentRect，再根据消息类型决定 TimeRect 放置：
  - 文本消息：TimeRect 贴合文本的“右下角”，以最后一行基线为参考，向右偏移 timeLeftPadding，向下偏移 baselinePadding；若最后一行宽度不足，为避免遮挡，向左回退至 min(bubbleRight - timeWidth - timePadding, textRight)。
  - 媒体消息（图片/视频）：TimeRect 置于媒体内容的右下角 overlay 层（半透明底），以 contentView 的 measured bounds 为参照，向内缩 timeInset；考虑播放控件与角标占位，优先避让。
- 伪代码：
```kotlin
val bubble = bubbleRect()
val content = contentRect()
val timeSz = measureTime()
if (isText) {
  val last = textLayout.getLineCount()-1
  val textRight = textLayout.getLineRight(last)
  val baseY = textTop + textLayout.getLineBottom(last)
  val x = bubble.right - timeSz.w - timePadding
  val y = baseY - timeBaselineShift
  timeRect.set(x, y - timeSz.h, x + timeSz.w, y)
} else { // media
  val x = content.right - timeSz.w - timeInset
  val y = content.bottom - timeSz.h - timeInset
  timeRect.set(x, y, x + timeSz.w, y + timeSz.h)
}
```

## 绘制顺序
- 背景：BubbleDrawable（阴影/圆角）
- 内容：ReplyView → ContentView → TranslateView → LiveView
- 叠加：编辑中/时间/状态图标 → 选择高亮涟漪 → 进入过渡（overlay）

## 组件职责
- BubbleView：自绘圆角与阴影、外边距/内边距策略、方向（入/出）
- TextContentView：
  - StaticLayout pack 缓存（prebuiltPack）
  - 多段文本/emoji/RTL 行混合；提供行宽与行底对齐数据用于时间定位
- MediaContentView：
  - 图片/视频尺寸策略（maxW、maxH、等比缩放）
  - 右下角 overlay 容器：时间、播放、时长角标
- ReplyView/TranslateView/LiveView：数据驱动可显隐；支持 xml inflate + 自定义 View
- Time/Status：
  - 自绘数字与图标；主题色随入/出方向
  - 文本与媒体不同锚点策略（见测量规则）

## 数据模型
- MessageModel：
  - type: TEXT | IMAGE | VIDEO | FILE
  - text/attributedSpans、mediaMeta(width, height, duration)、fromUser、reply/forward、translate、reactions、status、edited、time
- LayoutPack：
  - TextPack：StaticLayout、每行 metrics、总宽高
  - MediaPack：content bounds、缩放后的可视尺寸

## 动画与交互
- Enter：列表 ItemAnimator → 进入位移/淡入；若可用再叠加 MessageEnterTransition（文本飞入）
- Highlight：点击气泡涟漪（alpha 反转动画）
- 选择：选中态遮罩/OutlinePath
- 滚动复用：可重用 prebuiltPack，避免重复 build StaticLayout

## 主题与适配
- 入/出消息不同色与弧角；暗黑模式色板与阴影强度
- 字体与行距：遵循设计图（示例：正文 16sp / 用户名 14sp 粗体 / 时间 12sp）

## BaseTgMessageCell API（草拟）
```kotlin
class BaseTgMessageCell(context: Context) : ViewGroup {
  fun bind(model: MessageModel)
  override fun onMeasure(wSpec: Int, hSpec: Int)
  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int)
  override fun onDraw(canvas: Canvas)
  // 子视图：avatarView, bubbleView, replyView, contentView, translateView, liveView
  // 布局结果：bubbleRect, contentRect, timeRect, statusRect
}
```
- TextContentView 提供：getTextBounds(), getLastLineMetrics()
- MediaContentView 提供：getContentBounds(), getCornerOverlaysArea()

## 文件结构（tg 模块）
- chat/cell/BaseTgMessageCell.kt（核心容器）
- chat/cell/content/TextContentView.kt / MediaContentView.kt / FileContentView.kt
- chat/cell/extra/ReplyView.kt / TranslateView.kt / LiveView.kt
- chat/bubble/BubbleDrawable.kt（或 BubbleView）
- chat/transition/MessageEnterTransitionContainer.kt、TextMessageEnterTransition.kt
- chat/theme/TgTheme.kt

## 适配器集成
- MessageListAdapter：根据 model.type 创建/绑定对应 contentView
- ItemAnimator：加入进入位移/淡入与文本飞入（条件触发）

## 性能要点
- StaticLayout 预构建与复用；长文分块
- 避免频繁 requestLayout；在尺寸稳定时仅 invalidate
- 媒体图层硬件加速与裁剪（clipToOutline/clipPath）

## 验证用例
- 文本短/多行，时间贴合右下角；
- 图片/视频不同尺寸，时间置于右下 overlay；
- 有/无回复、翻译、点赞；
- 出/入消息方向与主题切换；
- 动画：进入、涟漪、文本飞入叠加。

## 实施步骤
1) 新增 BaseTgMessageCell 与 BubbleDrawable；搭起容器与基本测量/布局
2) 接入 TextContentView（StaticLayout pack 与行 metrics）
3) 接入 MediaContentView（尺寸策略与 overlay）
4) 实现时间/状态自绘与位置规则（文本 vs 媒体）
5) 组装 Reply/Translate/Live 可选模块与显隐
6) 接入 Enter/Highlight 动画；优化缓存与复用
7) 适配主题与 RTL；完成自测与示例页面联调

请确认以上方案，我将开始编码落地（按步骤交付，优先时间定位与文本/媒体两类内容）。