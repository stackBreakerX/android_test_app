# Compose 全应用迁移 Implementation Plan

> **For agentic workers:** 使用并行子代理按独立功能目录实施，主代理集成、构建并在模拟器验证。

**Goal:** 全部可达页面采用 Compose，学习内容通过四个底部 Tab 和专题工作台组织。

**Architecture:** MainActivity 提供交互、数据、多媒体、工程四个工作台；保留各 Activity 的路由身份和底层技术实现，改用 Compose 状态和布局。原生控件仅在技术实验本身需要时通过 AndroidView 展示，不能用整页 XML 包装冒充迁移。

**Tech Stack:** Kotlin 2.2、现有 Compose BOM、Material3、Room、Coroutines、JNI。

- [x] 主代理：创建统一主题与导航工作台，改写 MainActivity，覆盖全部有效功能入口。
- [x] 媒体代理：迁移 module_media 所有页面，保留 URI、权限、转换和语音生命周期。
- [x] 图片代理：迁移 module_image 所有页面，保留图库、缩略图和 DNG 处理。
- [x] 数据代理：迁移 module_coroutine、module_flow、module_room、network、arouter、module_advanced_function、hilt 页面。
- [x] 交互代理：迁移 module_view、module_recyclerview、module_animation、listdemo 页面。
- [x] 聊天代理：迁移 chat_tg 的聊天界面，保留消息类型与交互。
- [x] 集成：检查可达 Activity 无 XML 页面壳，修复 Manifest 与编译问题，执行 assembleDebug、testDebugUnitTest、lintDebug。
- [x] 设备验证：ARTEMIS 探索四个 Tab、导航返回、代表性交互、聊天、数据、多媒体页面。根据设备实际路径决定是否需要补充可执行测试。

注释检查：新增与修改的类、函数、异步边界、错误路径使用中文说明，核对注释与行为一致。保留学习 RecyclerView/自定义 View 的底层代码作为研究材料，Compose 页面明确其技术作用。

已确认 44 个 Compose Activity，37 个直接实验入口。Debug 构建和 lintDebug 通过（0 errors，保留旧资源/API 警告）；testDebugUnitTest 无源文件。新增 3 项工作台设备测试，连同既有目录、模态与手势测试共 20 项，均在 API 34 模拟器通过。
