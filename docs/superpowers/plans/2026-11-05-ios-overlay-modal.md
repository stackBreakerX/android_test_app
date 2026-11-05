# 背景固定的浮层模态 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有模态组件增加背景保持原尺寸的 Overlay 模式，并保留 Stacked 模式用于对比。

**Architecture:** IosModalProperties 增加 presentationStyle；Overlay 模式绕过背景变换，卡片增加安全区外的留白和完整圆角，手势与退出生命周期共用现有实现。Compose 目录提供两个入口，通过 Intent 配置同一个演示页面。

**Tech Stack:** 现有 Compose Material3/Foundation，AndroidJUnitRunner。

### Task 1: 呈现样式

**Files:** `module_compose/components/iosmodal/IosModalProperties.kt`、`IosModal.kt`、`IosModalMotion.kt`

- [x] 添加 `IosModalPresentationStyle.Stacked` 和 `Overlay`，默认继续使用 Stacked。
- [x] Overlay 背景在打开、拖动、回弹和退出期间保持原来的缩放、位置和裁剪状态。
- [x] Overlay 卡片采用 12dp 外部留白、四角圆角与阴影，底部避开导航栏和键盘；隐藏距离包含底部留白以完整退出窗口。

调用方式：`IosModalProperties(presentationStyle = IosModalPresentationStyle.Overlay)`。

### Task 2: 演示入口

**Files:** `module_compose/ComposeDemoActivity.kt`、`module_compose/modal/IosModalDemoActivity.kt`

- [x] 在统一 Compose 目录增加“iOS 模态弹窗（背景固定）”，原入口标注“背景缩放”。
- [x] 复用同一份短卡片与长列表内容；配置通过 Intent 传入，未知值退回默认样式。

### Task 3: 验证

**Files:** `app/src/androidTest/java/com/alex/studydemo/module_compose/components/iosmodal/IosModalPresentationTest.kt`、`module_compose/ComposeEntryTest.kt`

- [x] 比较背景在打开前、打开后、拖动中与退出后的实际边界，验证 Overlay 固定与 Stacked 缩放。
- [x] 验证新增目录入口、退出与旧有手势测试，运行 Debug 与测试 APK 构建及设备测试。
- [x] 截图检查半屏与长卡片留白，更新 MI 8 上的 APK，检查中文注释及 diff 空白。

参考：[Apple 关于半高 sheet 留白与全高 sheet 贴边的说明](https://developer.apple.com/videos/play/wwdc2025/323/)。本次实现背景固定的浮层效果，不包含 Liquid Glass 材质。


## 验收记录

- 已从 main 快进同步到 b7fd1b2 后实施，保留主分支上的气泡绘制接口。
- Debug 与 AndroidTest APK 构建通过；API 34 模拟器运行 Compose 测试包，结果为 `OK (17 tests)`。
- 新增根坐标手势处理 `IosModalDragHandle.kt`，避免移动中的标题使用局部坐标抵消拖动距离；测试验证短拖确实移动、回弹恢复、长拖越过阈值并关闭。
- 背景边界在打开前、打开后、拖动中、回弹后与关闭后保持一致；原 Stacked 缩放行为仍通过测试。
- 半屏和长列表截图已检查，浮层均保留水平及底部安全留白。
- 最终 APK 已更新到 MI 8；新增入口为“Compose 示例 → iOS 模态弹窗（背景固定）”。
- 改动保留在当前工作区，未自动提交或合并。
