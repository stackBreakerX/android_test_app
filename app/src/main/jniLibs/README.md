# jniLibs 目录使用说明（libwebp）

将各 ABI 的 `libwebp.so` 放入以下目录：

- `app/src/main/jniLibs/arm64-v8a/libwebp.so`（大多数 64 位手机）
- `app/src/main/jniLibs/armeabi-v7a/libwebp.so`（部分老旧 32 位手机）
- `app/src/main/jniLibs/x86_64/libwebp.so`（64 位模拟器）
- （可选）`app/src/main/jniLibs/x86/libwebp.so`（老旧 32 位模拟器）

注意事项：
- 文件名必须为 `libwebp.so`。
- 放置后，直接构建即可，无需额外配置。CMake 已配置“存在则链接”。
- 运行时 `LibWebpNative.isLibwebpAvailable()` 应返回 true；WebpLibwebp 页面顶部会显示“libwebp JNI 已加载：使用原生编码”。

验证步骤：
1. 放入对应 ABI 的 `libwebp.so`。
2. 执行 `./gradlew :app:assembleDebug` 构建。
3. 安装运行，打开“libwebp 转换”页面查看顶部状态并进行一次转换测试。

获取 `libwebp.so` 的方式：
- 使用 NDK/CMake 从 libwebp 官方源码编译（推荐，版本可控）。
- 使用可信的预编译二进制（请确保 ABI/平台兼容）。

---

## jniLibs 目录使用说明（libturbojpeg）

将各 ABI 的 `libturbojpeg.so` 放入以下目录：

- `app/src/main/jniLibs/arm64-v8a/libturbojpeg.so`（大多数 64 位手机）
- `app/src/main/jniLibs/armeabi-v7a/libturbojpeg.so`（部分老旧 32 位手机）
- `app/src/main/jniLibs/x86_64/libturbojpeg.so`（64 位模拟器）
- （可选）`app/src/main/jniLibs/x86/libturbojpeg.so`（老旧 32 位模拟器）

注意事项：
- 文件名必须为 `libturbojpeg.so`（代码通过 `dlopen("libturbojpeg.so")` 动态加载）。
- 放置后直接构建即可；CMake 已配置“存在则链接”，即便不显式链接也会随 APK 打包。
- 运行时 `LibJpegNative.isJpegAvailable()` 应返回 true；JPG 转换页面的“扫描并转换为JPG（C 库）”按钮即可使用。
- 若缺失库或加载失败，页面会自动回退到 Java 压缩（质量和性能可能不同）。

验证步骤：
1. 放入对应 ABI 的 `libturbojpeg.so`。
2. 执行 `./gradlew :app:assembleDebug` 构建。
3. 安装运行，打开“JPG 转换”页面，点击“扫描并转换为JPG（C 库）”，观察日志与输出图片。

获取 `libturbojpeg.so` 的方式：
- 使用 NDK/CMake 从 libjpeg-turbo 官方源码编译（推荐）。
- 使用可信的预编译二进制（请确保 ABI/平台兼容）。