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
 * <p>若需完整主题系统，请在 Telegram 工程内直接使用原版 Theme。</p>
 */
public final class Theme {

    /** 与 Telegram {@code SharedConfig.bubbleRadius} 默认相近，可按产品修改 */
    public static int bubbleRadiusDp = 17;

    private Theme() {
    }

    public static int dp(Context context, float value) {
        return (int) Math.ceil(value * context.getResources().getDisplayMetrics().density);
    }

    /** 线性插值（不依赖 {@code androidx.core.math.MathUtils}，避免部分 core 版本无 {@code lerp} 或未进 classpath） */
    public static float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }

    /**
     * 消息气泡背景（自 Telegram {@code Theme.MessageDrawable} 剥离渐变/NinePatch/动效后的子集）。
     */
    public static class MessageDrawable extends Drawable {

        public static final int TYPE_TEXT = 0;
        public static final int TYPE_MEDIA = 1;
        public static final int TYPE_PREVIEW = 2;

        private final Context appContext;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path path = new Path();

        private int currentType;
        private boolean isOut;
        public boolean isSelected;
        public boolean themePreview;

        private int topY;
        private boolean isTopNear;
        private boolean isBottomNear;
        private boolean botButtonsBottom;
        private int currentBackgroundHeight;
        private boolean drawFullBubble;
        private int overrideRoundRadius;
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

        private void generatePath(Path path, Rect bounds, int padding, int rad, int smallRad, int nearRad, int top,
                boolean drawFullBottom, boolean drawFullTop, boolean customPaint) {
            path.rewind();
            int heightHalf = (bounds.height() - padding) >> 1;
            if (rad > heightHalf) {
                rad = heightHalf;
            }
            if (isOut) {
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
                        // Figma-style smooth bezier tail (outgoing, bottom-right)
                        path.lineTo(bounds.right - dp(8f), bounds.bottom - padding - dp(14f));
                        path.cubicTo(
                                bounds.right - dp(8f), bounds.bottom - padding - dp(4f),
                                bounds.right - dp(2f), bounds.bottom - padding + dp(3f),
                                bounds.right, bounds.bottom - padding + dp(3f));
                        path.cubicTo(
                                bounds.right - dp(4f), bounds.bottom - padding + dp(2f),
                                bounds.right - dp(8f), bounds.bottom - padding,
                                bounds.right - dp(8f) - nearRad, bounds.bottom - padding);
                    } else {
                        path.lineTo(bounds.right - dp(8f), top - topY + currentBackgroundHeight);
                    }
                }
            } else {
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
                        // Figma-style smooth bezier tail (incoming, bottom-left)
                        path.lineTo(bounds.left + dp(8f), bounds.bottom - padding - dp(14f));
                        path.cubicTo(
                                bounds.left + dp(8f), bounds.bottom - padding - dp(4f),
                                bounds.left + dp(2f), bounds.bottom - padding + dp(3f),
                                bounds.left, bounds.bottom - padding + dp(3f));
                        path.cubicTo(
                                bounds.left + dp(4f), bounds.bottom - padding + dp(2f),
                                bounds.left + dp(8f), bounds.bottom - padding,
                                bounds.left + dp(8f) + nearRad, bounds.bottom - padding);
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
         * 与 Telegram 一致：缓存 Path，减少列表滑动时重复构建。
         */
        public static class PathDrawParams {
            public final Path path = new Path();
            private final Rect lastRect = new Rect();
            private boolean lastDrawFullTop;
            private boolean lastDrawFullBottom;

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
