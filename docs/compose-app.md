# Compose 学习工作台

应用从四个底部 Tab 进入实验，每个方向用专题卡片呈现。搜索限定当前方向，专题筛选和“我的收藏”可以叠加；最近访问只展示当前方向的上一次实验。收藏、最近访问和工作台外观保存在 SharedPreferences，Tab、搜索和筛选使用可保存状态。

| Tab | 内容 |
| --- | --- |
| 交互 | 聊天、Compose 组件馆、模态、侧滑、状态、文本排版、双阶段页头、列表和动画 |
| 数据 | Channel、Flow、异常取消、Room、网络请求和网络状态 |
| 多媒体 | 图库、缩略图、DNG、WebP、libwebp、PNG、JPEG、透明通道和语音识别 |
| 工程 | JankStats、ARouter、高阶函数 |

`ui/LearningCatalog.kt` 是实验目录。增加实验时填写名称、摘要、专题、Activity 类名和 Tab 编号，并在 Manifest 注册 Activity。原 Activity 的路径和公开启动方法保留，用于学习 ARouter 和 Android 页面生命周期。

全部活动页面由 `ComponentActivity.setContent` 挂载 Compose。列表复用、ItemDecoration、ListView 和自定义 View 的实验需要原控件本身，因此仅实验区域使用 `AndroidView`；页面标题、操作区和导航使用 Compose。旧 XML 和 ViewBinding 仍供这些技术实验的条目适配器使用。聊天页使用 Compose 气泡和 LazyColumn，旧聊天绘制代码保留供研究，未用于包装新页面。

媒体操作保留原有 JNI 和系统能力的前置条件：部分转换需要对应的预编译库；DNG 需要 LibRaw；Whisper 需要模型或集成实现；系统语音识别需要可用的识别服务。页面会展示失败信息，模拟器不具备这些能力时不能当作转换或识别成功。

验证命令：

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

设备交互验证使用 ARTEMIS；当前设备的操作记录与结果单独保存在执行 trace 中，不把自动化代理的主观判断当作每个原生后端均已通过。

## 公共导航组件

导航入口集中在 `ui/StudyNavigation.kt`：

- `StudyTopBar`：居中标题、单行副标题、支持 RTL 的返回图标和右侧操作槽。根页面传 `onBack = null`；返回与关闭行为由宿主提供。
- `StudyScaffold`：固定顶部栏与底部槽，向内容传入安全区内边距，沿用调用方主题。黑底预览可分别指定 `barColor` 与 `barContentColor`。
- `StudyPage`：常规实验页面的 Column，集中消费安全区和键盘边距。包含 RecyclerView 或 LazyColumn 的页面传 `scrollable = false`，保留有界高度。
- `StudyBottomBar`：统一底部 Tab，接收条目、选中索引和回调，不持有业务导航状态。

业务页面不直接创建 Material 顶部栏或底部栏。媒体、图片和交互模块的薄包装只保留各自内容布局策略。模态默认标题栏使用零 `windowInsets`，因为浮层安全区由模态宿主处理；手柄和关闭回调继续由模态组件管理。
