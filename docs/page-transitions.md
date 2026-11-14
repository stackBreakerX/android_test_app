# 页面转场

页面继续使用独立 Activity 和原 ARouter 路径，转场由系统窗口动画执行。进入时前页从屏幕右侧移动到原位，底页左移 28%；返回时前页移出右侧、底页从 -28% 回到原位。两层使用相同的减速曲线，进入 380ms，返回 340ms；RTL 使用镜像资源。

`StudyPageTransitions` 在 Application 中集中注册，不需要每个启动入口单独传动画。API 34 起使用 `overrideActivityTransition`，API 24–33 使用日间、夜间主题中的 `windowAnimationStyle`。动画仍由系统设置控制，不修改设备全局动画倍率。窗口背景在创建时和外观切换时同步，避免露出与应用主题不同的底色。

系统预测返回仍由 Android 接管。本次资源控制普通跳转及返回提交阶段，未实现 iOS 那种贯穿拖动、取消、完成全过程的自定义交互返回。系统选相册、权限、Compose Dialog 和模态组件使用原有逻辑。

Android 对自定义 Activity 转场和预测返回的说明见 [Activity API 文档](https://developer.android.com/reference/android/app/Activity#overrideActivityTransition(int,int,int,int))。

验证：Debug 构建、lintDebug（0 errors）和 20 项设备测试通过。设备为 API 34 arm64 模拟器；API 24–33 主题回退和 RTL 方向已完成资源检查，尚无对应设备上的实测。本次没有证据将帧率提升归因于动画配置，不能据此声称达到 iOS 真机流畅度。

ARTEMIS 在修改前后走了工作台 → Compose 组件馆 → 状态与重组 → 返回 → 聊天 → 返回的路径，修改后确认导航和计数正常，没有观察到白屏或卡死；录屏检查报告新页向左推入、返回方向相反。修改前 trace 为 `f51064a4-0df2-416b-b0d5-4717b251f5c0`，修改后为 `bbc6eae3-bb38-4066-a1c4-a21036a3d356`。gfxinfo 汇总包含启动、实验交互和冷启动差异，不能当作受控的转场帧率对比。
