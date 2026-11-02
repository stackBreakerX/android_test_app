# 气泡裁剪机制说明

## 两套机制各司其职，互不冗余

| | 控制对象 | 作用 |
|---|---|---|
| `bubbleDrawableType` / `currentType` | 气泡**背景**的形状 | 决定气泡画成什么样（带尾巴 / 纯圆角 / 小圆角） |
| `clipChildrenToBubblePath` | **子 View 内容**是否跟着裁剪 | 决定内容（图片等）是否贴合气泡轮廓 |

### 图片消息（两者都需要）

- `bubbleDrawableType = STYLE_TAIL`：画一个带尾巴的气泡背景 ✓
- `clipChildrenToBubblePath = true`：把图片内容也裁剪成气泡形状，否则图片四角是方的，会"露出"气泡圆角外面 ✓

### 文本消息（只需要背景，不能用 Path 裁剪）

- `bubbleDrawableType = STYLE_TAIL`：画带尾巴的气泡背景 ✓
- `clipChildrenToBubblePath = false`：用矩形裁剪，且右侧留 `clipRightInsetOutDp = 10dp` 给行内时间

---

## 文本消息若改为 `clipChildrenToBubblePath = true` 会出什么问题

### 时间 View 的实际位置

```
时间 View 右边 = layoutRect.right  - bubblePaddingEndOut(16dp)   → 距右 16dp
时间 View 底边 = layoutRect.bottom - bubblePaddingBottom(8dp)    → 距底  8dp
```

### 矩形裁剪的安全边距（当前方案）

```
clip right  = rect.right  - clipRightInsetOutDp(10dp)   → 时间 View 右边在此内 6dp，安全
clip bottom = rect.bottom - clipBottomInsetDp(4dp)      → 时间 View 底边在此内 4dp，安全
```

### Path 裁剪的风险

STYLE_TAIL 发出气泡的尾巴从约 `rect.bottom - 8.5dp` 处开始突出（参考 `union.xml`：
39dp 高视口中尾巴起点约为 y = 30.5dp）。时间 View 底边恰好在 `rect.bottom - 8dp`，
几乎与尾巴起点齐平。

Path 裁剪在右下角跟随尾巴弧线向内弯曲，时间 View 的右下角落在这个弯曲区域，
**有较大概率被裁掉时间文字右侧和底部的几个像素**（双勾图标可能缺角）。

### 对比表

| | 右侧裁剪边界 | 底部裁剪边界 | 右下角 |
|---|---|---|---|
| 当前矩形裁剪 | `right - 10dp`（有安全余量） | `bottom - 4dp` | 直角，不受尾巴影响 |
| Path 裁剪 | 跟随尾巴曲线 | 跟随尾巴/圆角曲线 | 向内弯曲，可能裁掉时间 View |

---

## 结论

- `clipChildrenToBubblePath` 解决"**内容层是否贴合气泡轮廓**"的问题
- `bubbleDrawableType` 解决"**气泡背景层画什么形状**"的问题
- 二者独立工作，缺一不可
- 文本消息必须使用矩形裁剪（`clipChildrenToBubblePath = false`）+ 内缩边距，
  才能保护底部时间 View 不被尾巴弧线裁剪
