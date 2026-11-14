# Unified navigation Implementation Plan

> **For agentic workers:** 按公共组件、普通页面、特殊页面、设备验证依次实施并检查。

**Goal:** 顶部栏、返回按钮、底部 Tab 和页面安全区使用同一套 Compose 封装。

**Architecture:** StudyTopBar 统一标题、副标题、返回语义和操作槽；StudyScaffold 统一导航布局；StudyPage 组合可滚动或固定 Column；StudyBottomBar 只呈现 Tab，不持有导航状态。业务页面保留主题和事件回调。

**Tech Stack:** 现有 Compose / Material3 / Activity。

- [x] 新建 ui/StudyNavigation.kt：顶部栏、底部栏、Scaffold 和页面内容容器。
- [x] 替换 12 个私有 Page 的重复实现，以及 ExperimentPage、MediaPage、ImageDemoScaffold。
- [x] 将工作台、Compose 目录/状态/模态、聊天/预览、侧滑、性能、协程异常页接入公共导航。
- [x] 保留测试 tag 和页面业务；静态检查业务目录不再直接创建 TopAppBar、NavigationBar 或独立返回标题栏。
- [x] 构建、lintDebug、既有设备测试；用 ARTEMIS 走工作台、子页返回、聊天操作菜单和模态路径。

首轮 20 项设备测试通过；新增统一返回箭头回归用例后，最终 21 项设备测试全部通过；Debug 构建和 lintDebug 通过。ARTEMIS trace：aa2c9653-9cb8-4878-b805-8939d97cea81。
