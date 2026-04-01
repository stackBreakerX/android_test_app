package com.alex.studydemo.telegram; // 本类隶属演示工程包，与 TG 包名隔离

import android.content.Context; // 用于读取屏幕 density 做 dp 换算
import android.graphics.Canvas; // 气泡绘制画布
import android.graphics.Color; // 取 alpha 等（选中遮罩）
import android.graphics.ColorFilter; // Drawable 颜色滤镜接口
import android.graphics.Paint; // 填充气泡与选中层
import android.graphics.Path; // 气泡矢量轮廓
import android.graphics.PixelFormat; // getOpacity 返回值
import android.graphics.Rect; // 整型边界与缓存比较
import android.graphics.RectF; // arcTo 用的浮点圆角矩形
import android.graphics.drawable.Drawable; // MessageDrawable 基类

import androidx.annotation.NonNull; // 标记非空返回值/参数
import androidx.core.graphics.ColorUtils; // blendARGB、setAlphaComponent

/**
 * 自 Telegram {@code org.telegram.ui.ActionBar.Theme} 精简移植：仅保留气泡绘制相关的 dp/圆角与
 * {@link MessageDrawable}。不含颜色表、壁纸、主题包等其余上万行逻辑。
 *
 * <p><b>职责划分：</b></p>
 * <ul>
 *   <li>{@link Theme} — 全局气泡圆角 dp（{@link #bubbleRadiusDp}）与 dp 换算、标量插值</li>
 *   <li>{@link MessageDrawable} — 与 ChatMessageCell 对齐的气泡 Path（文本尾巴 / 媒体圆角）、选中遮罩、裁剪用 {@link #makePath()}</li>
 * </ul>
 *
 * <p>若需完整主题系统，请在 Telegram 工程内直接使用原版 Theme。</p>
 *
 * <p><b>关于「每行注释」：</b>本文件对主要语句逐行或紧邻行加了 {@code //} 说明；import、字段、方法体均覆盖。
 * Telegram 原版 {@code Theme.java} 体量极大，不适合逐行注释，请以类/方法 JavaDoc 与分段注释为准。</p>
 */
public final class Theme { // final：无子类，仅作命名空间与静态工具

    /**
     * 气泡主圆角半径（dp），与 Telegram {@code SharedConfig.bubbleRadius} 默认相近；演示页可通过
     * {@code TgSharedConfig} 等与产品一致。
     */
    public static int bubbleRadiusDp = 12; // 默认 12dp，对齐 union.xml Figma 设计

    private Theme() {
        // 显式私有构造，禁止 new Theme()；本类只提供静态成员与内部类
    }

    /**
     * dp → 像素，向上取整，与列表测量里「至少 1px」策略一致。
     */
    public static int dp(Context context, float value) { // value 为 dp 数值
        return (int) Math.ceil(value * context.getResources().getDisplayMetrics().density); // density 为 px/dp
    }

    /**
     * 线性插值（不依赖 {@code androidx.core.math.MathUtils}，避免部分 core 版本无 {@code lerp} 或未进 classpath）。
     *
     * @param amount 0～1，0 返回 start，1 返回 end
     */
    public static float lerp(float start, float end, float amount) { // 标量线性插值
        return start + (end - start) * amount; // 展开为 start*(1-t)+end*t
    }

    /**
     * 消息气泡背景（自 Telegram {@code Theme.MessageDrawable} 剥离渐变/NinePatch/动效后的子集）。
     *
     * <p><b>类型：</b>{@link #TYPE_TEXT} 带尾巴（靠屏幕一侧底角）；{@link #TYPE_MEDIA} 四角圆角无尾巴；
     * {@link #TYPE_PREVIEW} 小圆角用于主题预览。</p>
     *
     * <p><b>与列表配合：</b>全量 Telegram 里由 {@code setTop} 传入整条消息块高度与相邻关系，用于长消息分片裁剪；
     * 本精简版在 {@link #draw(Canvas)} 开头同步 bounds，避免独立 Drawable 未调用 {@link #setTop} 时丢尾巴。</p>
     */
    public static class MessageDrawable extends Drawable { // 自 Drawable，可设 bounds 并 draw 到 Canvas

        /** 文本类气泡（含右下角/左下角尾巴） */
        public static final int TYPE_TEXT = 0; // 与 TG 常量值一致
        /** 媒体类气泡：仅圆角，无尾巴 */
        public static final int TYPE_MEDIA = 1;
        /** 主题预览用小圆角 */
        public static final int TYPE_PREVIEW = 2;

        private final Context appContext; // ApplicationContext，避免持有 Activity
        /** 主填充：纯色或外部传入的替代 Paint */
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); // 抗锯齿填充
        /** 选中时叠加在路径上的半透明层 */
        private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF(); // 复用，减少 arcTo 时分配
        private final Path path = new Path(); // 主缓存路径（非 drawCached 时用）

        private int currentType; // TYPE_TEXT / TYPE_MEDIA / TYPE_PREVIEW
        /** true = 发出（右侧），false = 收到（左侧）；决定尾巴在右下还是左下 */
        private boolean isOut;
        public boolean isSelected; // 是否显示选中加深/遮罩
        public boolean themePreview; // true 时内部 dp() 用放大系数（主题编辑器预览）

        /** 当前片段在「整段消息背景」里的纵向偏移（全量客户端分片绘制用） */
        private int topY;
        private boolean isTopNear; // 与上条消息相邻则顶角用小圆角
        private boolean isBottomNear; // 与下条相邻则底角用小圆角
        private boolean botButtonsBottom; // 底部机器人按钮时底边圆角策略
        /** 整段消息背景高度；与 bounds 高度比较用于判断是否画全角/全尾巴 */
        private int currentBackgroundHeight;
        private boolean drawFullBubble; // 强制整气泡（少用）
        private int overrideRoundRadius; // 非 0 时覆盖默认圆角像素
        /** 0～1，与最小边一半之间插值圆角 */
        private float overrideRounding;
        private int alpha = 255; // Drawable 整体不透明度 0～255

        private PathDrawParams pathDrawCacheParams; // drawCached 时指向外部缓存参数

        private int colorIn; // 收到气泡色
        private int colorOut; // 发出气泡色
        private int colorInSelected; // 收到选中
        private int colorOutSelected; // 发出选中
        private int selectedOverlayColor = 0x33000000; // 选中叠加层 ARGB

        public MessageDrawable(Context context, int type, boolean out, boolean selected) { // 构造：初始化颜色与 Path
            appContext = context.getApplicationContext(); // 避免泄漏 Activity
            currentType = type; // 记录气泡类型
            isOut = out; // 发出/收到
            isSelected = selected; // 初始选中态
            colorIn = 0xFFFFFFFF; // 默认收到白
            colorOut = 0xFFE1FFC7; // 默认发出浅绿（TG 经典）
            colorInSelected = ColorUtils.blendARGB(colorIn, 0xFF000000, 0.08f); // 选中略压暗
            colorOutSelected = ColorUtils.blendARGB(colorOut, 0xFF000000, 0.08f);
            selectedPaint.setStyle(Paint.Style.FILL); // 选中层实心
            applyMainColor(); // 同步 paint 颜色
        }

        /** 切换发出/收到，刷新填充色 */
        public void setOutgoing(boolean out) { // 与 TG setOutgoing 语义一致
            if (this.isOut == out) { // 无变化则短路
                return; // 避免无效重绘
            }
            this.isOut = out; // 更新方向
            applyMainColor(); // 重算主色
            invalidateSelf(); // 请求重绘
        }

        /**
         * 设置收发气泡颜色；选中态在基础上做轻度压暗，也可用 {@link #setSelectedOverlayColor(int)} 覆写选中遮罩。
         */
        public void setBubbleColors(int incoming, int outgoing) { // 自定义收发底色
            colorIn = incoming; // 收到
            colorOut = outgoing; // 发出
            colorInSelected = ColorUtils.blendARGB(incoming, 0xFF000000, 0.08f); // 选中派生
            colorOutSelected = ColorUtils.blendARGB(outgoing, 0xFF000000, 0.08f);
            applyMainColor(); // 应用当前态
            invalidateSelf(); // 刷新
        }

        public void setSelectedOverlayColor(int color) { // 自定义选中蒙层颜色
            selectedOverlayColor = color; // 存储
            invalidateSelf(); // 重绘
        }

        /** 根据 {@link #isOut}、{@link #isSelected} 写入 {@link #paint} */
        private void applyMainColor() { // 内部：更新主画笔颜色与 alpha
            int c; // 当前应使用的 ARGB
            if (isOut) { // 发出
                c = isSelected ? colorOutSelected : colorOut; // 选中用压暗色
            } else { // 收到
                c = isSelected ? colorInSelected : colorIn;
            }
            paint.setColor(c); // 设置填充色
            paint.setShader(null); // 精简版无渐变着色器
            paint.setAlpha(alpha); // 与 Drawable alpha 一致
        }

        public void setType(int type) { // 切换 TEXT/MEDIA/PREVIEW
            currentType = type; // 保存
            invalidateSelf(); // Path 形状会变
        }

        public Path getPath() { // 外部读取当前主 Path 引用
            return path; // 注意与 draw 时生成内容同步
        }

        public void setBotButtonsBottom(boolean v) { // TG：底部机器人键盘时影响底圆角
            botButtonsBottom = v; // 保存标志
        }

        public void setTopBottomNear(boolean topNear, boolean bottomNear) { // 仅更新相邻标志
            isTopNear = topNear; // 顶相邻
            isBottomNear = bottomNear; // 底相邻
        }

        public void setTop(int top, int backgroundWidth, int backgroundHeight, boolean topNear, boolean bottomNear) { // 重载：heightOffset=backgroundHeight
            setTop(top, backgroundWidth, backgroundHeight, backgroundHeight, topNear, bottomNear); // 委托完整方法
        }

        /**
         * 与 Telegram 一致：记录列表中的垂直位置与上下是否“挨条消息”，供 {@link #generatePath} 裁剪分支使用。
         */
        public void setTop(int top, int backgroundWidth, int backgroundHeight, int heightOffset,
                boolean topNear, boolean bottomNear) { // 全量 TG 还有更多参数，此处精简
            currentBackgroundHeight = backgroundHeight; // 整段背景高度
            topY = top; // 本片段顶部在背景中的偏移
            isTopNear = topNear; // 顶相邻
            isBottomNear = bottomNear; // 底相邻
        }

        public void setDrawFullBubble(boolean v) { // 是否整段绘制完整气泡
            drawFullBubble = v; // 保存
        }

        public void setRoundRadius(int radius) { // 像素级固定圆角覆盖
            overrideRoundRadius = radius; // 非 0 时优先生效
            invalidateSelf(); // 重算 Path
        }

        public void setRoundingRadius(float rounding) { // 0～1 动态圆角
            overrideRounding = rounding; // 保存比例
            invalidateSelf(); // 重绘
        }

        /**
         * 使用外部传入的 {@link PathDrawParams} 绘制，避免与实例内 {@link #path} 冲突（缓存路径场景）。
         */
        public void drawCached(@NonNull Canvas canvas, PathDrawParams params, Paint paintToUse) { // 带自定义 Paint
            pathDrawCacheParams = params; // 临时指向缓存
            draw(canvas, paintToUse); // 绘制
            pathDrawCacheParams = null; // 恢复，避免泄漏外部状态
        }

        public void drawCached(@NonNull Canvas canvas, PathDrawParams params) { // 使用内部 paint
            drawCached(canvas, params, null); // paintToUse=null
        }

        @Override
        public void draw(@NonNull Canvas canvas) { // Drawable 接口
            draw(canvas, null); // 默认内部 paint
        }

        /**
         * @param paintToUse 非 null 时用其替代内部 {@link #paint}（例如预览或着色）
         */
        public void draw(@NonNull Canvas canvas, Paint paintToUse) { // 核心绘制
            Rect bounds = getBounds(); // 当前 Drawable 边界
            if (bounds.isEmpty()) { // 无尺寸则不画
                return; // 避免无效计算
            }
            // 与 ChatMessageCell 中 setTop 一致：独立 Drawable 绘制时也要同步，否则 generatePath/makePath 缺尾巴
            setTop(bounds.top, bounds.width(), bounds.height(), false, false); // 用 bounds 模拟整段高度
            int padding = dp(2f); // 内边距（与 TG 一致 2dp）
            int rad; // 主圆角半径 px
            int nearRad; // 「相邻消息」用小圆角时的半径 px
            if (overrideRoundRadius != 0) { // 强制圆角
                rad = overrideRoundRadius; // 主半径
                nearRad = overrideRoundRadius; // 与主相同
            } else if (overrideRounding > 0) { // 按比例插值到pill形
                rad = (int) lerp(dp(Theme.bubbleRadiusDp), Math.min(bounds.width(), bounds.height()) / 2f, overrideRounding); // 大圆角
                nearRad = (int) lerp(dp(Math.min(6, Theme.bubbleRadiusDp)), Math.min(bounds.width(), bounds.height()) / 2f, overrideRounding); // 小圆角上限 6dp
            } else if (currentType == TYPE_PREVIEW) { // 预览用小圆角
                rad = dp(6f); // 6dp
                nearRad = dp(6f);
            } else { // 默认
                rad = dp(Theme.bubbleRadiusDp); // 全局 bubbleRadiusDp
                nearRad = dp(Math.min(6, Theme.bubbleRadiusDp)); // 相邻角不超过 6dp
            }
            int smallRad = dp(6f); // 尾巴附近小弧用半径

            Paint p = paintToUse == null ? paint : paintToUse; // 选用画笔
            int top = Math.max(bounds.top, 0); // 顶坐标不低于 0
            boolean drawFullBottom; // 是否绘制完整底边（分片裁剪用）
            boolean drawFullTop; // 是否绘制完整顶边
            if (pathDrawCacheParams != null && bounds.height() < currentBackgroundHeight) { // 缓存且高度小于背景：视为分片
                drawFullBottom = true; // 简化：全画
                drawFullTop = true;
            } else { // 非分片或不用缓存
                drawFullBottom = true; // 精简版恒 true（与原版 draw 分支一致）
                drawFullTop = true;
            }

            Path pathToDraw; // 实际要 draw 的 Path 引用
            boolean invalidatePath; // 是否需重新 generatePath
            if (pathDrawCacheParams != null) { // 使用外部缓存 Path
                pathToDraw = pathDrawCacheParams.path; // 指向缓存
                invalidatePath = pathDrawCacheParams.invalidatePath(bounds, drawFullBottom, drawFullTop); // 由缓存判断是否失效
            } else { // 使用实例 path
                pathToDraw = this.path; // 主 Path
                invalidatePath = true; // 总是重建（与原版一致）
            }
            if (invalidatePath || overrideRoundRadius != 0) { // 需重建或强制圆角
                generatePath(pathToDraw, bounds, padding, rad, smallRad, nearRad, top, drawFullBottom, drawFullTop, paintToUse != null); // customPaint=外部画笔非空
            }
            canvas.drawPath(pathToDraw, p); // 填充主气泡

            if (paintToUse == null && isSelected) { // 仅默认 paint 且选中时画蒙层
                selectedPaint.setColor(ColorUtils.setAlphaComponent(selectedOverlayColor,
                        (int) (Color.alpha(selectedOverlayColor) * (alpha / 255f)))); // 按整体 alpha 缩放蒙层
                canvas.drawPath(pathToDraw, selectedPaint); // 叠一层
            }
        }

        @NonNull
        public Path makePath() { // 无参：用当前 pathDrawCacheParams（可能为 null）
            return makePath(pathDrawCacheParams); // 委托
        }

        /**
         * 生成与当前绘制一致的闭合路径，供 {@code Canvas.clipPath} 裁剪子 View（与气泡轮廓一致）。
         */
        @NonNull
        public Path makePath(PathDrawParams cacheParams) { // 供裁剪用，逻辑与 draw 略异（currentBackgroundHeight<=0）
            Rect bounds = getBounds(); // 边界
            int padding = dp(2f); // 内边距
            int rad; // 主圆角
            int nearRad; // 近邻圆角
            if (overrideRoundRadius != 0) {
                rad = overrideRoundRadius;
                nearRad = overrideRoundRadius;
            } else if (overrideRounding > 0) {
                rad = (int) lerp(dp(Theme.bubbleRadiusDp), Math.min(bounds.width(), bounds.height()) / 2f, overrideRounding);
                nearRad = (int) lerp(dp(Math.min(6, Theme.bubbleRadiusDp)), Math.min(bounds.width(), bounds.height()) / 2f, overrideRounding);
            } else if (currentType == TYPE_PREVIEW) {
                rad = dp(6f);
                nearRad = dp(6f);
            } else {
                rad = dp(Theme.bubbleRadiusDp);
                nearRad = dp(Math.min(6, Theme.bubbleRadiusDp));
            }
            int smallRad = dp(6f); // 尾巴小弧
            int top = Math.max(bounds.top, 0);
            boolean drawFullBottom;
            boolean drawFullTop;
            if (cacheParams != null && bounds.height() < currentBackgroundHeight) { // 分片高度不足
                drawFullBottom = true;
                drawFullTop = true;
            } else if (currentBackgroundHeight <= 0) {
                // 独立 Cell、未调用 setTop 时：按完整气泡绘制（含文本尾巴），避免误判为裁剪块而丢失尾巴
                drawFullBottom = true; // 画全底（含尾巴）
                drawFullTop = true; // 画全顶
            } else { // 列表分片真实裁剪
                drawFullBottom = currentType == TYPE_MEDIA // 媒体判断式不同
                        ? topY + bounds.bottom - smallRad * 2 < currentBackgroundHeight // 媒体用 smallRad
                        : topY + bounds.bottom - rad < currentBackgroundHeight; // 文本用 rad
                drawFullTop = topY + rad * 2 >= 0; // 顶是否在可视上方
            }
            Path outPath; // 输出 Path 引用
            boolean invalidatePath;
            if (cacheParams != null) {
                outPath = cacheParams.path;
                invalidatePath = cacheParams.invalidatePath(bounds, drawFullBottom, drawFullTop);
            } else {
                outPath = this.path;
                invalidatePath = true;
            }
            if (invalidatePath || overrideRoundRadius != 0) {
                generatePath(outPath, bounds, padding, rad, smallRad, nearRad, top, drawFullBottom, drawFullTop, true); // makePath 固定 customPaint=true
            }
            return outPath; // 供 clipPath
        }

        /**
         * 按 Telegram 几何规则拼接 Path：先底边再侧边再顶边，最后闭合；{@code isOut} 分支决定尾巴在左还是右。
         *
         * @param customPaint true 表示 makePath/裁剪用途，部分 MEDIA 分支与 draw 一致
         */
        private void generatePath(Path path, Rect bounds, int padding, int rad, int smallRad, int nearRad, int top,
                boolean drawFullBottom, boolean drawFullTop, boolean customPaint) { // 核心几何
            path.rewind(); // 清空再画
            int heightHalf = (bounds.height() - padding) >> 1; // 半高（去 padding）
            if (rad > heightHalf) { // 圆角不能超过半高
                rad = heightHalf; // 钳制
            }
            if (isOut) { // 发出：尾巴在屏幕右侧底部
                // ——— 发出消息：尾巴在右下角 ———
                if (drawFullBubble || currentType == TYPE_PREVIEW || customPaint || drawFullBottom) { // 画完整底边或等价条件
                    int radToUse = botButtonsBottom ? nearRad : rad; // 机器人底边用 near 半径
                    if (currentType == TYPE_MEDIA) { // 媒体从内侧开始
                        path.moveTo(bounds.right - dp(8f) - radToUse, bounds.bottom - padding); // 左移 8dp+圆角
                    } else { // 文本：贴近右缘起笔
                        path.moveTo(bounds.right - dp(2.6f), bounds.bottom - padding); // 2.6dp 微调对齐尾巴
                    }
                    path.lineTo(bounds.left + padding + radToUse, bounds.bottom - padding); // 底边向左
                    rect.set(bounds.left + padding, bounds.bottom - padding - radToUse * 2,
                            bounds.left + padding + radToUse * 2, bounds.bottom - padding); // 左下圆角外接矩形
                    path.arcTo(rect, 90, 90, false); // 左下 90° 弧
                } else { // 分片：直线截断
                    path.moveTo(bounds.right - dp(4f), top - topY + currentBackgroundHeight); // 移到截断高度
                    path.lineTo(bounds.left + padding, top - topY + currentBackgroundHeight); // 水平线
                }
                if (drawFullBubble || currentType == TYPE_PREVIEW || customPaint || drawFullTop) { // 完整顶边区域
                    path.lineTo(bounds.left + padding, bounds.top + padding + rad); // 左边向上
                    rect.set(bounds.left + padding, bounds.top + padding, bounds.left + padding + rad * 2, bounds.top + padding + rad * 2); // 左上圆角
                    path.arcTo(rect, 180, 90, false); // 左上弧
                    int radToUse = isTopNear ? nearRad : rad; // 顶相邻用小圆角
                    if (currentType == TYPE_MEDIA) { // 媒体顶边靠右
                        path.lineTo(bounds.right - padding - radToUse, bounds.top + padding); // 顶边向右
                        rect.set(bounds.right - padding - radToUse * 2, bounds.top + padding, bounds.right - padding, bounds.top + padding + radToUse * 2); // 右上
                    } else { // 文本：右上有 4dp 偏移（对齐 union.xml）
                        path.lineTo(bounds.right - dp(4f) - radToUse, bounds.top + padding);
                        rect.set(bounds.right - dp(4f) - radToUse * 2, bounds.top + padding, bounds.right - dp(4f), bounds.top + padding + radToUse * 2);
                    }
                    path.arcTo(rect, 270, 90, false); // 右上弧
                } else { // 顶部分片
                    path.lineTo(bounds.left + padding, top - topY - dp(2f)); // 左竖
                    if (currentType == TYPE_MEDIA) {
                        path.lineTo(bounds.right - padding, top - topY - dp(2f)); // 媒体顶截断
                    } else {
                        path.lineTo(bounds.right - dp(4f), top - topY - dp(2f)); // 文本顶截断
                    }
                }
                if (currentType == TYPE_MEDIA) { // 媒体右侧竖边与右下圆角
                    if (customPaint || drawFullBottom) { // 需要完整右下
                        int radToUse = isBottomNear ? nearRad : rad; // 底相邻
                        path.lineTo(bounds.right - padding, bounds.bottom - padding - radToUse); // 向下
                        rect.set(bounds.right - padding - radToUse * 2, bounds.bottom - padding - radToUse * 2,
                                bounds.right - padding, bounds.bottom - padding); // 右下圆角
                        path.arcTo(rect, 0, 90, false); // 右下 90°
                    } else {
                        path.lineTo(bounds.right - padding, top - topY + currentBackgroundHeight); // 分片竖边
                    }
                } else { // 文本：画尾巴弧
                    if (drawFullBubble || currentType == TYPE_PREVIEW || customPaint || drawFullBottom) { // 完整尾巴
                        // Figma union.xml 贝塞尔尾巴（发出，右下角）
                        path.lineTo(bounds.right - dp(4f), bounds.bottom - padding - dp(8f)); // 右侧向下到气泡底右角
                        path.cubicTo(
                                bounds.right - dp(4f), bounds.bottom - padding - dp(8f),
                                bounds.right - dp(4f), bounds.bottom - padding - dp(6f),
                                bounds.right - dp(3f), bounds.bottom - padding - dp(4f));
                        path.cubicTo(
                                bounds.right - dp(2f), bounds.bottom - padding - dp(2f),
                                bounds.right,          bounds.bottom - padding,
                                bounds.right,          bounds.bottom - padding);
                    } else {
                        path.lineTo(bounds.right - dp(4f), top - topY + currentBackgroundHeight); // 分片
                    }
                }
            } else { // 收到：镜像，尾巴在左下
                // ——— 收到消息：尾巴在左下角（与上面对称） ———
                if (drawFullBubble || currentType == TYPE_PREVIEW || customPaint || drawFullBottom) {
                    int radToUse = botButtonsBottom ? nearRad : rad;
                    if (currentType == TYPE_MEDIA) {
                        path.moveTo(bounds.left + dp(8f) + radToUse, bounds.bottom - padding); // 从左下圆角内侧起
                    } else {
                        path.moveTo(bounds.left + dp(2.6f), bounds.bottom - padding); // 文本尾巴侧
                    }
                    path.lineTo(bounds.right - padding - radToUse, bounds.bottom - padding); // 底边向右
                    rect.set(bounds.right - padding - radToUse * 2, bounds.bottom - padding - radToUse * 2,
                            bounds.right - padding, bounds.bottom - padding); // 右下圆角
                    path.arcTo(rect, 90, -90, false); // 右下（顺时针负角）
                } else {
                    path.moveTo(bounds.left + dp(4f), top - topY + currentBackgroundHeight);
                    path.lineTo(bounds.right - padding, top - topY + currentBackgroundHeight);
                }
                if (drawFullBubble || currentType == TYPE_PREVIEW || customPaint || drawFullTop) {
                    path.lineTo(bounds.right - padding, bounds.top + padding + rad); // 右侧向上
                    rect.set(bounds.right - padding - rad * 2, bounds.top + padding, bounds.right - padding, bounds.top + padding + rad * 2); // 右上
                    path.arcTo(rect, 0, -90, false); // 右上
                    int radToUse = isTopNear ? nearRad : rad;
                    if (currentType == TYPE_MEDIA) {
                        path.lineTo(bounds.left + padding + radToUse, bounds.top + padding); // 顶边向左
                        rect.set(bounds.left + padding, bounds.top + padding, bounds.left + padding + radToUse * 2, bounds.top + padding + radToUse * 2); // 左上
                    } else {
                        path.lineTo(bounds.left + dp(4f) + radToUse, bounds.top + padding);
                        rect.set(bounds.left + dp(4f), bounds.top + padding, bounds.left + dp(4f) + radToUse * 2, bounds.top + padding + radToUse * 2);
                    }
                    path.arcTo(rect, 270, -90, false); // 左上
                } else {
                    path.lineTo(bounds.right - padding, top - topY - dp(2f));
                    if (currentType == TYPE_MEDIA) {
                        path.lineTo(bounds.left + padding, top - topY - dp(2f));
                    } else {
                        path.lineTo(bounds.left + dp(4f), top - topY - dp(2f));
                    }
                }
                if (currentType == TYPE_MEDIA) {
                    if (customPaint || drawFullBottom) {
                        int radToUse = isBottomNear || botButtonsBottom ? nearRad : rad;
                        path.lineTo(bounds.left + padding, bounds.bottom - padding - radToUse); // 左侧向下
                        rect.set(bounds.left + padding, bounds.bottom - padding - radToUse * 2,
                                bounds.left + padding + radToUse * 2, bounds.bottom - padding); // 左下
                        path.arcTo(rect, 180, -90, false);
                    } else {
                        path.lineTo(bounds.left + padding, top - topY + currentBackgroundHeight);
                    }
                } else {
                    if (drawFullBubble || currentType == TYPE_PREVIEW || customPaint || drawFullBottom) {
                        // Figma union.xml 贝塞尔尾巴（收到，左下角）
                        path.lineTo(bounds.left + dp(4f), bounds.bottom - padding - dp(8f)); // 左侧向下到气泡底左角
                        path.cubicTo(
                                bounds.left + dp(4f), bounds.bottom - padding - dp(8f),
                                bounds.left + dp(4f), bounds.bottom - padding - dp(6f),
                                bounds.left + dp(3f), bounds.bottom - padding - dp(4f));
                        path.cubicTo(
                                bounds.left + dp(2f), bounds.bottom - padding - dp(2f),
                                bounds.left,          bounds.bottom - padding,
                                bounds.left,          bounds.bottom - padding);
                    } else {
                        path.lineTo(bounds.left + dp(4f), top - topY + currentBackgroundHeight);
                    }
                }
            }
            path.close(); // 闭合路径
        }

        private int dp(float value) { // 内部 dp，预览模式放大
            if (themePreview) { // 主题预览：与 TG 一致放大坐标刻度
                return (int) Math.ceil(3f * value); // 预览用 3× 换算
            }
            return Theme.dp(appContext, value); // 常态：应用 density
        }

        @Override
        public void setAlpha(int alpha) { // Drawable 透明度
            this.alpha = alpha; // 存 0～255
            paint.setAlpha(alpha); // 同步主画笔
            invalidateSelf(); // 重绘
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) { // 颜色滤镜
            paint.setColorFilter(colorFilter); // 仅主画笔
        }

        @Override
        public int getOpacity() { // 支持透明
            return PixelFormat.TRANSPARENT; // 含 alpha
        }

        @Override
        public void setBounds(int left, int top, int right, int bottom) { // 边界变化
            super.setBounds(left, top, right, bottom); // 先调父类
            invalidateSelf(); // bounds 变则 Path 需重算
        }

        /**
         * 与 Telegram 一致：缓存 Path，减少列表滑动时重复 {@link MessageDrawable#generatePath}。
         */
        public static class PathDrawParams { // 静态缓存容器
            public final Path path = new Path(); // 可复用的 Path 实例
            private final Rect lastRect = new Rect(); // 上次 bounds
            private boolean lastDrawFullTop; // 上次顶全绘标志
            private boolean lastDrawFullBottom; // 上次底全绘标志

            /**
             * bounds 或顶/底是否「整段绘制」变化时返回 true，应重新生成路径。
             */
            public boolean invalidatePath(Rect bounds, boolean drawFullBottom, boolean drawFullTop) { // 失效判断
                boolean invalidate = lastRect.isEmpty() // 首次
                        || lastRect.top != bounds.top // 顶变
                        || lastRect.bottom != bounds.bottom // 底变
                        || lastRect.right != bounds.right // 右变
                        || lastRect.left != bounds.left // 左变
                        || lastDrawFullTop != drawFullTop // 顶策略变
                        || lastDrawFullBottom != drawFullBottom // 底策略变
                        || !drawFullTop // TG 原逻辑：非全顶也失效
                        || !drawFullBottom; // 非全底也失效
                lastDrawFullTop = drawFullTop; // 更新缓存
                lastDrawFullBottom = drawFullBottom;
                lastRect.set(bounds); // 记录 bounds
                return invalidate; // 是否需 generatePath
            }

            @NonNull
            public Path getPath() { // 取内部 Path
                return path;
            }
        }
    }
}
