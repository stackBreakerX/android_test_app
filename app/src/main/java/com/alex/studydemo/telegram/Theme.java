package com.alex.studydemo.telegram;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

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
 */
public final class Theme {

    /**
     * 气泡主圆角半径（dp），与 Telegram {@code SharedConfig.bubbleRadius} 默认相近；演示页可通过
     * {@code TgSharedConfig} 等与产品一致。
     */
    public static int bubbleRadiusDp = 17;

    private Theme() {
    }

    /**
     * dp → 像素，向上取整，与列表测量里「至少 1px」策略一致。
     */
    public static int dp(Context context, float value) {
        return (int) Math.ceil(value * context.getResources().getDisplayMetrics().density);
    }

    /**
     * 线性插值（不依赖 {@code androidx.core.math.MathUtils}，避免部分 core 版本无 {@code lerp} 或未进 classpath）。
     *
     * @param amount 0～1，0 返回 start，1 返回 end
     */
    public static float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
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
    public static class MessageDrawable extends Drawable {

        /** 文本类气泡（含右下角/左下角尾巴） */
        public static final int TYPE_TEXT = 0;
        /** 媒体类气泡：仅圆角，无尾巴 */
        public static final int TYPE_MEDIA = 1;
        /** 主题预览用小圆角 */
        public static final int TYPE_PREVIEW = 2;

        private final Context appContext;
        /** 主填充：纯色或外部传入的替代 Paint */
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        /** 选中时叠加在路径上的半透明层 */
        private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path path = new Path();

        private int currentType;
        /** true = 发出（右侧），false = 收到（左侧）；决定尾巴在右下还是左下 */
        private boolean isOut;
        public boolean isSelected;
        public boolean themePreview;

        /** 当前片段在「整段消息背景」里的纵向偏移（全量客户端分片绘制用） */
        private int topY;
        private boolean isTopNear;
        private boolean isBottomNear;
        private boolean botButtonsBottom;
        /** 整段消息背景高度；与 bounds 高度比较用于判断是否画全角/全尾巴 */
        private int currentBackgroundHeight;
        private boolean drawFullBubble;
        private int overrideRoundRadius;
        /** 0～1，与最小边一半之间插值圆角 */
        private float overrideRounding;
        private int alpha = 255;

        private PathDrawParams pathDrawCacheParams;

        private int colorIn;
        private int colorOut;
        private int colorInSelected;
        private int colorOutSelected;
        private int selectedOverlayColor = 0x33000000;

        public MessageDrawable(Context context, int type, boolean out, boolean selected) {
            appContext = context.getApplicationContext();
            currentType = type;
            isOut = out;
            isSelected = selected;
            colorIn = 0xFFFFFFFF;
            colorOut = 0xFFE1FFC7;
            colorInSelected = ColorUtils.blendARGB(colorIn, 0xFF000000, 0.08f);
            colorOutSelected = ColorUtils.blendARGB(colorOut, 0xFF000000, 0.08f);
            selectedPaint.setStyle(Paint.Style.FILL);
            applyMainColor();
        }

        /** 切换发出/收到，刷新填充色 */
        public void setOutgoing(boolean out) {
            if (this.isOut == out) {
                return;
            }
            this.isOut = out;
            applyMainColor();
            invalidateSelf();
        }

        /**
         * 设置收发气泡颜色；选中态在基础上做轻度压暗，也可用 {@link #setSelectedOverlayColor(int)} 覆写选中遮罩。
         */
        public void setBubbleColors(int incoming, int outgoing) {
            colorIn = incoming;
            colorOut = outgoing;
            colorInSelected = ColorUtils.blendARGB(incoming, 0xFF000000, 0.08f);
            colorOutSelected = ColorUtils.blendARGB(outgoing, 0xFF000000, 0.08f);
            applyMainColor();
            invalidateSelf();
        }

        public void setSelectedOverlayColor(int color) {
            selectedOverlayColor = color;
            invalidateSelf();
        }

        /** 根据 {@link #isOut}、{@link #isSelected} 写入 {@link #paint} */
        private void applyMainColor() {
            int c;
            if (isOut) {
                c = isSelected ? colorOutSelected : colorOut;
            } else {
                c = isSelected ? colorInSelected : colorIn;
            }
            paint.setColor(c);
            paint.setShader(null);
            paint.setAlpha(alpha);
        }

        public void setType(int type) {
            currentType = type;
            invalidateSelf();
        }

        public Path getPath() {
            return path;
        }

        public void setBotButtonsBottom(boolean v) {
            botButtonsBottom = v;
        }

        public void setTopBottomNear(boolean topNear, boolean bottomNear) {
            isTopNear = topNear;
            isBottomNear = bottomNear;
        }

        public void setTop(int top, int backgroundWidth, int backgroundHeight, boolean topNear, boolean bottomNear) {
            setTop(top, backgroundWidth, backgroundHeight, backgroundHeight, topNear, bottomNear);
        }

        /**
         * 与 Telegram 一致：记录列表中的垂直位置与上下是否“挨条消息”，供 {@link #generatePath} 裁剪分支使用。
         */
        public void setTop(int top, int backgroundWidth, int backgroundHeight, int heightOffset,
                boolean topNear, boolean bottomNear) {
            currentBackgroundHeight = backgroundHeight;
            topY = top;
            isTopNear = topNear;
            isBottomNear = bottomNear;
        }

        public void setDrawFullBubble(boolean v) {
            drawFullBubble = v;
        }

        public void setRoundRadius(int radius) {
            overrideRoundRadius = radius;
            invalidateSelf();
        }

        public void setRoundingRadius(float rounding) {
            overrideRounding = rounding;
            invalidateSelf();
        }

        /**
         * 使用外部传入的 {@link PathDrawParams} 绘制，避免与实例内 {@link #path} 冲突（缓存路径场景）。
         */
        public void drawCached(@NonNull Canvas canvas, PathDrawParams params, Paint paintToUse) {
            pathDrawCacheParams = params;
            draw(canvas, paintToUse);
            pathDrawCacheParams = null;
        }

        public void drawCached(@NonNull Canvas canvas, PathDrawParams params) {
            drawCached(canvas, params, null);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            draw(canvas, null);
        }

        /**
         * @param paintToUse 非 null 时用其替代内部 {@link #paint}（例如预览或着色）
         */
        public void draw(@NonNull Canvas canvas, Paint paintToUse) {
            Rect bounds = getBounds();
            if (bounds.isEmpty()) {
                return;
            }
            // 与 ChatMessageCell 中 setTop 一致：独立 Drawable 绘制时也要同步，否则 generatePath/makePath 缺尾巴
            setTop(bounds.top, bounds.width(), bounds.height(), false, false);
            int padding = dp(2f);
            int rad;
            int nearRad;
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
            int smallRad = dp(6f);

            Paint p = paintToUse == null ? paint : paintToUse;
            int top = Math.max(bounds.top, 0);
            boolean drawFullBottom;
            boolean drawFullTop;
            if (pathDrawCacheParams != null && bounds.height() < currentBackgroundHeight) {
                drawFullBottom = true;
                drawFullTop = true;
            } else {
                drawFullBottom = true;
                drawFullTop = true;
            }

            Path pathToDraw;
            boolean invalidatePath;
            if (pathDrawCacheParams != null) {
                pathToDraw = pathDrawCacheParams.path;
                invalidatePath = pathDrawCacheParams.invalidatePath(bounds, drawFullBottom, drawFullTop);
            } else {
                pathToDraw = this.path;
                invalidatePath = true;
            }
            if (invalidatePath || overrideRoundRadius != 0) {
                generatePath(pathToDraw, bounds, padding, rad, smallRad, nearRad, top, drawFullBottom, drawFullTop, paintToUse != null);
            }
            canvas.drawPath(pathToDraw, p);

            if (paintToUse == null && isSelected) {
                selectedPaint.setColor(ColorUtils.setAlphaComponent(selectedOverlayColor,
                        (int) (Color.alpha(selectedOverlayColor) * (alpha / 255f))));
                canvas.drawPath(pathToDraw, selectedPaint);
            }
        }

        @NonNull
        public Path makePath() {
            return makePath(pathDrawCacheParams);
        }

        /**
         * 生成与当前绘制一致的闭合路径，供 {@code Canvas.clipPath} 裁剪子 View（与气泡轮廓一致）。
         */
        @NonNull
        public Path makePath(PathDrawParams cacheParams) {
            Rect bounds = getBounds();
            int padding = dp(2f);
            int rad;
            int nearRad;
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
            int smallRad = dp(6f);
            int top = Math.max(bounds.top, 0);
            boolean drawFullBottom;
            boolean drawFullTop;
            if (cacheParams != null && bounds.height() < currentBackgroundHeight) {
                drawFullBottom = true;
                drawFullTop = true;
            } else if (currentBackgroundHeight <= 0) {
                // 独立 Cell、未调用 setTop 时：按完整气泡绘制（含文本尾巴），避免误判为裁剪块而丢失尾巴
                drawFullBottom = true;
                drawFullTop = true;
            } else {
                drawFullBottom = currentType == TYPE_MEDIA
                        ? topY + bounds.bottom - smallRad * 2 < currentBackgroundHeight
                        : topY + bounds.bottom - rad < currentBackgroundHeight;
                drawFullTop = topY + rad * 2 >= 0;
            }
            Path outPath;
            boolean invalidatePath;
            if (cacheParams != null) {
                outPath = cacheParams.path;
                invalidatePath = cacheParams.invalidatePath(bounds, drawFullBottom, drawFullTop);
            } else {
                outPath = this.path;
                invalidatePath = true;
            }
            if (invalidatePath || overrideRoundRadius != 0) {
                generatePath(outPath, bounds, padding, rad, smallRad, nearRad, top, drawFullBottom, drawFullTop, true);
            }
            return outPath;
        }

        /**
         * 按 Telegram 几何规则拼接 Path：先底边再侧边再顶边，最后闭合；{@code isOut} 分支决定尾巴在左还是右。
         *
         * @param customPaint true 表示 makePath/裁剪用途，部分 MEDIA 分支与 draw 一致
         */
        private void generatePath(Path path, Rect bounds, int padding, int rad, int smallRad, int nearRad, int top,
                boolean drawFullBottom, boolean drawFullTop, boolean customPaint) {
            path.rewind();
            int heightHalf = (bounds.height() - padding) >> 1;
            if (rad > heightHalf) {
                rad = heightHalf;
            }
            if (isOut) {
                // ——— 发出消息：尾巴在右下角 ———
                if (drawFullBubble || currentType == TYPE_PREVIEW || customPaint || drawFullBottom) {
                    int radToUse = botButtonsBottom ? nearRad : rad;
                    if (currentType == TYPE_MEDIA) {
                        path.moveTo(bounds.right - dp(8f) - radToUse, bounds.bottom - padding);
                    } else {
                        path.moveTo(bounds.right - dp(2.6f), bounds.bottom - padding);
                    }
                    path.lineTo(bounds.left + padding + radToUse, bounds.bottom - padding);
                    rect.set(bounds.left + padding, bounds.bottom - padding - radToUse * 2,
                            bounds.left + padding + radToUse * 2, bounds.bottom - padding);
                    path.arcTo(rect, 90, 90, false);
                } else {
                    path.moveTo(bounds.right - dp(8f), top - topY + currentBackgroundHeight);
                    path.lineTo(bounds.left + padding, top - topY + currentBackgroundHeight);
                }
                if (drawFullBubble || currentType == TYPE_PREVIEW || customPaint || drawFullTop) {
                    path.lineTo(bounds.left + padding, bounds.top + padding + rad);
                    rect.set(bounds.left + padding, bounds.top + padding, bounds.left + padding + rad * 2, bounds.top + padding + rad * 2);
                    path.arcTo(rect, 180, 90, false);
                    int radToUse = isTopNear ? nearRad : rad;
                    if (currentType == TYPE_MEDIA) {
                        path.lineTo(bounds.right - padding - radToUse, bounds.top + padding);
                        rect.set(bounds.right - padding - radToUse * 2, bounds.top + padding, bounds.right - padding, bounds.top + padding + radToUse * 2);
                    } else {
                        path.lineTo(bounds.right - dp(8f) - radToUse, bounds.top + padding);
                        rect.set(bounds.right - dp(8f) - radToUse * 2, bounds.top + padding, bounds.right - dp(8f), bounds.top + padding + radToUse * 2);
                    }
                    path.arcTo(rect, 270, 90, false);
                } else {
                    path.lineTo(bounds.left + padding, top - topY - dp(2f));
                    if (currentType == TYPE_MEDIA) {
                        path.lineTo(bounds.right - padding, top - topY - dp(2f));
                    } else {
                        path.lineTo(bounds.right - dp(8f), top - topY - dp(2f));
                    }
                }
                if (currentType == TYPE_MEDIA) {
                    if (customPaint || drawFullBottom) {
                        int radToUse = isBottomNear ? nearRad : rad;
                        path.lineTo(bounds.right - padding, bounds.bottom - padding - radToUse);
                        rect.set(bounds.right - padding - radToUse * 2, bounds.bottom - padding - radToUse * 2,
                                bounds.right - padding, bounds.bottom - padding);
                        path.arcTo(rect, 0, 90, false);
                    } else {
                        path.lineTo(bounds.right - padding, top - topY + currentBackgroundHeight);
                    }
                } else {
                    if (drawFullBubble || currentType == TYPE_PREVIEW || customPaint || drawFullBottom) {
                        path.lineTo(bounds.right - dp(8f), bounds.bottom - padding - smallRad - dp(3f));
                        rect.set(bounds.right - dp(8f), bounds.bottom - padding - smallRad * 2 - dp(9f),
                                bounds.right - dp(7f) + smallRad * 2, bounds.bottom - padding - dp(1f));
                        path.arcTo(rect, 180, -83, false);
                    } else {
                        path.lineTo(bounds.right - dp(8f), top - topY + currentBackgroundHeight);
                    }
                }
            } else {
                // ——— 收到消息：尾巴在左下角（与上面对称） ———
                if (drawFullBubble || currentType == TYPE_PREVIEW || customPaint || drawFullBottom) {
                    int radToUse = botButtonsBottom ? nearRad : rad;
                    if (currentType == TYPE_MEDIA) {
                        path.moveTo(bounds.left + dp(8f) + radToUse, bounds.bottom - padding);
                    } else {
                        path.moveTo(bounds.left + dp(2.6f), bounds.bottom - padding);
                    }
                    path.lineTo(bounds.right - padding - radToUse, bounds.bottom - padding);
                    rect.set(bounds.right - padding - radToUse * 2, bounds.bottom - padding - radToUse * 2,
                            bounds.right - padding, bounds.bottom - padding);
                    path.arcTo(rect, 90, -90, false);
                } else {
                    path.moveTo(bounds.left + dp(8f), top - topY + currentBackgroundHeight);
                    path.lineTo(bounds.right - padding, top - topY + currentBackgroundHeight);
                }
                if (drawFullBubble || currentType == TYPE_PREVIEW || customPaint || drawFullTop) {
                    path.lineTo(bounds.right - padding, bounds.top + padding + rad);
                    rect.set(bounds.right - padding - rad * 2, bounds.top + padding, bounds.right - padding, bounds.top + padding + rad * 2);
                    path.arcTo(rect, 0, -90, false);
                    int radToUse = isTopNear ? nearRad : rad;
                    if (currentType == TYPE_MEDIA) {
                        path.lineTo(bounds.left + padding + radToUse, bounds.top + padding);
                        rect.set(bounds.left + padding, bounds.top + padding, bounds.left + padding + radToUse * 2, bounds.top + padding + radToUse * 2);
                    } else {
                        path.lineTo(bounds.left + dp(8f) + radToUse, bounds.top + padding);
                        rect.set(bounds.left + dp(8f), bounds.top + padding, bounds.left + dp(8f) + radToUse * 2, bounds.top + padding + radToUse * 2);
                    }
                    path.arcTo(rect, 270, -90, false);
                } else {
                    path.lineTo(bounds.right - padding, top - topY - dp(2f));
                    if (currentType == TYPE_MEDIA) {
                        path.lineTo(bounds.left + padding, top - topY - dp(2f));
                    } else {
                        path.lineTo(bounds.left + dp(8f), top - topY - dp(2f));
                    }
                }
                if (currentType == TYPE_MEDIA) {
                    if (customPaint || drawFullBottom) {
                        int radToUse = isBottomNear || botButtonsBottom ? nearRad : rad;
                        path.lineTo(bounds.left + padding, bounds.bottom - padding - radToUse);
                        rect.set(bounds.left + padding, bounds.bottom - padding - radToUse * 2,
                                bounds.left + padding + radToUse * 2, bounds.bottom - padding);
                        path.arcTo(rect, 180, -90, false);
                    } else {
                        path.lineTo(bounds.left + padding, top - topY + currentBackgroundHeight);
                    }
                } else {
                    if (drawFullBubble || currentType == TYPE_PREVIEW || customPaint || drawFullBottom) {
                        path.lineTo(bounds.left + dp(8f), bounds.bottom - padding - smallRad - dp(3f));
                        rect.set(bounds.left + dp(7f) - smallRad * 2, bounds.bottom - padding - smallRad * 2 - dp(9f),
                                bounds.left + dp(8f), bounds.bottom - padding - dp(1f));
                        path.arcTo(rect, 0, 83, false);
                    } else {
                        path.lineTo(bounds.left + dp(8f), top - topY + currentBackgroundHeight);
                    }
                }
            }
            path.close();
        }

        private int dp(float value) {
            if (themePreview) {
                return (int) Math.ceil(3f * value);
            }
            return Theme.dp(appContext, value);
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }

        @Override
        public void setBounds(int left, int top, int right, int bottom) {
            super.setBounds(left, top, right, bottom);
            invalidateSelf();
        }

        /**
         * 与 Telegram 一致：缓存 Path，减少列表滑动时重复 {@link MessageDrawable#generatePath}。
         */
        public static class PathDrawParams {
            public final Path path = new Path();
            private final Rect lastRect = new Rect();
            private boolean lastDrawFullTop;
            private boolean lastDrawFullBottom;

            /**
             * bounds 或顶/底是否「整段绘制」变化时返回 true，应重新生成路径。
             */
            public boolean invalidatePath(Rect bounds, boolean drawFullBottom, boolean drawFullTop) {
                boolean invalidate = lastRect.isEmpty()
                        || lastRect.top != bounds.top
                        || lastRect.bottom != bounds.bottom
                        || lastRect.right != bounds.right
                        || lastRect.left != bounds.left
                        || lastDrawFullTop != drawFullTop
                        || lastDrawFullBottom != drawFullBottom
                        || !drawFullTop
                        || !drawFullBottom;
                lastDrawFullTop = drawFullTop;
                lastDrawFullBottom = drawFullBottom;
                lastRect.set(bounds);
                return invalidate;
            }

            @NonNull
            public Path getPath() {
                return path;
            }
        }
    }
}
