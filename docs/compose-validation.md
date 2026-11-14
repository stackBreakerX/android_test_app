# Compose 迁移验证

2026-11-14，在 `studydemo_compose_review`（Android 14 / API 34、arm64）验证。

- `:app:assembleDebug`、`:app:assembleDebugAndroidTest` 通过。
- `:app:lintDebug` 通过：0 errors，269 warnings，7 hints。仍有旧实验 API 和保留 XML 资源相关的警告。`app/lint.xml` 只排除未使用的 utilcode 通知类所触发的通知权限提示。
- `:app:connectedDebugAndroidTest`：20 项通过，0 失败、0 跳过。新增搜索筛选、收藏及实验页往返、外观标签点击三项测试，其余覆盖 Compose 目录和模态组件。
- `:app:testDebugUnitTest`：NO-SOURCE，项目没有本地单元测试，不能计作通过了单元测试用例。
- 静态检查确认 44 个 Activity 使用 Compose，37 个直接实验入口已在 Manifest 注册；`git diff --check` 通过。

ARTEMIS 设备操作确认：搜索与收藏、计数页往返、普通/监督作用域、Room DAO 插入查询、WebP 选择器取消、性能监控暂停恢复，以及聊天发送编辑、文件气泡和显示设置。

设备 trace：`393b9f5f-faed-4368-ac78-f38affa0b50b`（Room、多媒体、工程）、`9ed9c4bd-1055-4da6-af60-23f260810209`（聊天）。初次 Pro 探索记录 `1c460474-922d-4e91-8c9b-31fb9a0b2cc2` 在搜索与收藏、作用域实验后因自动化规划反复而停止，未将其整段任务计为通过。

本次未逐项验证所有媒体文件格式、Native 库组合、Whisper 模型和网络识别服务。原有依赖条件仍适用。RecyclerView、ListView、自定义 View 专题的实验控件继续通过 AndroidView 使用，页面壳、导航和控制区使用 Compose。

外观验证发现文字标签没有绑定单选事件，已将整行设为单选项并补充测试；最终重新运行 20 项设备测试全部通过。

最终 APK 已重新安装到模拟器，ARTEMIS trace `0859a868-f036-4c50-9946-213ed03b136f` 确认点击深色标签会改变背景，系统栏图标清晰，并已恢复跟随系统。
