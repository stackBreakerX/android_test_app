# Compose 仿 iOS 模态弹窗实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 在现有 Compose 入口提供可运行、可复用的 iOS 底部卡片模态，支持下拉关闭和滚动衔接。

**Architecture:** `IosModalHost` 同时承载背景页面和全屏 Dialog，用统一进度驱动背景缩放和遮罩。卡片内部用 nestedScroll 接收列表到顶后剩余的向下位移，标题拖动区直接接收手势。外部以布尔状态控制显示，关闭动画完成后移除 Dialog。

**Tech Stack:** Kotlin 2.2、现有 Compose BOM、Compose Foundation/Material3、Android 仪器测试。

---

### Task 1: 组件与手势

**Files:** Create `app/src/main/java/com/alex/studydemo/module_compose/modal/IosModalHost.kt`

- [x] 实现受控接口 `IosModalHost(visible, onDismissRequest, title, sheetContent, content)`，提供卡片高度比例和关闭操作槽位。
- [x] 使用单一位移状态，动画中断时从当前位置继续，避免拖动与动画并发写入。速度单位为 px/s，关闭阈值为卡片高度的 25%，快速下滑阈值为 1000 dp/s；明显向上甩动优先回弹。
- [x] 标题直接拖动；内容通过 nestedScroll 在到顶时接管下拉；反向拖动优先复位卡片，再滚动列表。
- [x] 实现返回键、遮罩、按钮关闭，安全区、深浅主题、无障碍关闭动作；在退场期间保留 Dialog。

### Task 2: 可访问的演示入口

**Files:** Create `app/src/main/java/com/alex/studydemo/module_compose/ComposeDemoActivity.kt`

- [x] 补齐首页和 Manifest 已引用但缺失的 Activity，保留现有导航路径。
- [x] 提供普通卡片、长列表两种内容；使用 rememberSaveable 保存可见状态和选择结果。
- [x] 添加中文注释，说明受控状态、手势和动画取消边界。

### Task 3: 验证

**Files:** Create `app/src/androidTest/java/com/alex/studydemo/module_compose/modal/IosModalHostTest.kt`

- [x] 用 Compose 测试验证按钮/遮罩/返回关闭、短拖回弹、长拖关闭、列表滚动与到顶下拉。
- [x] 执行 `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --offline`，修复本次引入的编译问题；记录并处理阻断运行的最小既有编译错误。
- [x] 在现有模拟器执行 `./gradlew :app:connectedDebugAndroidTest --offline`，截图检查卡片布局，复核注释与 `git diff --check`。

不自动提交、推送或合并，改动保留在当前工作区供用户检查。


## 验收记录

- `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --offline` 通过。
- `connectedDebugAndroidTest --offline` 因本机缺少 UTP 缓存组件无法启动；改用下面的设备入口运行同一测试类，结果为 `OK (8 tests)`：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class com.alex.studydemo.module_compose.modal.IosModalHostTest com.alex.studydemo.test/androidx.test.runner.AndroidJUnitRunner
```

- Android API 34 模拟器已检查浅色、深色、横屏布局，并使用实际输入事件验证标题下拉关闭；检查后恢复模拟器原来的主题和旋转设置。
- 注释完整性、准确性和 `git diff --check` 已检查。
- 为恢复项目构建，补齐缺失的 ComposeDemoActivity，增加气泡边界副本接口，并将旧字符串大小写转换替换为忽略大小写比较。
- 改动保留在当前工作区，未提交、推送或合并。
