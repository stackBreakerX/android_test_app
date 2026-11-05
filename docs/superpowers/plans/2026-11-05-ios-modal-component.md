# iOS 模态组件封装 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 提取独立模态组件，并将所有 Compose 示例统一放入 module_compose 包，由首页的一个目录入口进入。

**Architecture:** 通用 API 放在 `module_compose/components/iosmodal`，保留 visible/onDismissRequest 单向状态管理。视觉容器、默认顶部、行为配置和内部运动状态分别负责独立职责，演示页面直接调用组件。

**Tech Stack:** 现有 Compose Foundation、Material3、AndroidJUnitRunner。

---

### Task 1: 提取组件 API

**Files:**
- Move: `app/src/main/java/com/alex/studydemo/module_compose/modal/IosModalHost.kt` → `app/src/main/java/com/alex/studydemo/module_compose/components/iosmodal/IosModal.kt`
- Create: `app/src/main/java/com/alex/studydemo/module_compose/components/iosmodal/IosModalProperties.kt`
- Create: `app/src/main/java/com/alex/studydemo/module_compose/components/iosmodal/IosModalDefaults.kt`
- Create: `app/src/main/java/com/alex/studydemo/module_compose/components/iosmodal/IosModalMotion.kt`

- [x] 提取内部运动状态与嵌套滚动代码，保留现有的位移阈值、速度阈值和退场生命周期。
- [x] 提供 `IosModalProperties(heightFraction, dismissOnBackPress, dismissOnClickOutside, dragToDismiss)`，四项默认值分别为 0.92f、true、true、true。
- [x] 提供自定义 header、shape、containerColor、contentColor、scrimColor；默认顶部通过 `IosModalDefaults.Header` 提供，允许替换“完成”按钮文案。

组件调用形式：

```kotlin
// 调用方拥有显示状态，关闭请求只修改这一份状态。
var visible by rememberSaveable { mutableStateOf(false) }
IosModal(
    visible = visible,
    onDismissRequest = { visible = false },
    title = "设置",
    properties = IosModalProperties(heightFraction = 0.68f),
    sheetContent = { dismiss ->
        TextButton(onClick = dismiss) { Text("保存") }
    },
) {
    Button(onClick = { visible = true }) { Text("打开设置") }
}
```

### Task 2: 演示迁移与使用说明

**Files:** Create `app/src/main/java/com/alex/studydemo/module_compose/modal/IosModalDemoActivity.kt`

- [x] 将演示迁移为组件消费者，保留首页入口、短卡片和长列表功能。
- [x] 在组件 KDoc 中写明根布局约束、显示状态、关闭回调和滚动内容约定；代码注释继续使用中文。

### Task 3: 验证复用与可配置行为

**Files:** Create `app/src/androidTest/java/com/alex/studydemo/module_compose/components/iosmodal/IosModalComponentTest.kt`

- [x] 独立测试直接调用组件、自定义顶部和内容关闭、关闭开关、运行中修改开关，避免测试仅能通过演示页面。
- [x] 运行 `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --offline`。
- [x] 在 emulator-5580 上使用 AndroidJUnitRunner 执行组件测试与已有 IosModalHostTest，复核 `git diff --check` 和注释。

改动保留在当前工作区，不自动提交或推送。


### Task 4: Compose 包与入口统一

**Files:**
- Modify: `app/src/main/java/com/alex/studydemo/MainActivity.kt`
- Create: `app/src/main/java/com/alex/studydemo/module_compose/ComposeDemoActivity.kt`
- Move: `app/src/main/java/com/alex/studydemo/module_animation/swipe/` → `app/src/main/java/com/alex/studydemo/module_compose/swipe/`
- Replace: 空白的 `compose/MuteStateOfActivity.kt` → `module_compose/state/ComposeStateActivity.kt`
- Modify: `app/src/main/java/com/alex/studydemo/module_animation/AnimationsEntryActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`、`app/src/main/res/values/strings.xml`
- Test: `app/src/androidTest/java/com/alex/studydemo/module_compose/ComposeEntryTest.kt`

- [x] 首页保留一个“Compose 示例”入口，目录提供模态、侧滑、状态示例；更新包路径和 Manifest 注册。
- [x] 原来空白的 XML 状态占位替换为 Compose 计数示例，移除失去引用的空布局。
- [x] 验证目录到各示例的跳转、返回路径和状态更新；检查所有 Compose 导入均位于 module_compose。
- [x] 更新 MI 8 和模拟器上的 APK，确认手机首页可见统一入口。


## 验收记录

- Debug APK 与 AndroidTest APK 均离线构建通过。
- emulator-5580（API 34）执行 `adb shell am instrument -w -r -e package com.alex.studydemo.module_compose com.alex.studydemo.test/androidx.test.runner.AndroidJUnitRunner`，结果为 `OK (14 tests)`。
- 测试包含原有 8 项手势回归、4 项独立组件测试、2 项目录与状态示例测试。
- 已将 APK 更新到 MI 8（3a1162b2）和模拟器；手机首页入口改为“Compose 示例”。
- 已检查旧包引用、Manifest 注册、Compose 源码归属、中文注释与 `git diff --check`。
