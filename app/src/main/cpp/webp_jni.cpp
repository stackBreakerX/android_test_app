/*
 * webp_jni.cpp — JNI bridge for libwebp encoding
 *
 * 提供能力：
 * - isLibwebpAvailable(): 动态查找 WebPEncodeRGBA 符号，判断 libwebp 是否可用。
 * - encodeRgbaToWebp(): 将原始 RGBA 字节数组编码为 WEBP。
 * - encodeBitmapToWebp(): 将 Android Bitmap (RGBA_8888) 编码为 WEBP。
 *
 * 重要说明：
 * - 输入 RGBA 为每像素 4 字节（R,G,B,A），行步长以字节为单位。
 * - 质量 `quality` 为 [0,100] 的浮点值，越大质量越高、体积越大。
 * - WebPEncodeRGBA 返回的输出缓冲区由堆分配（malloc），使用 free() 释放。
 * - Bitmap 需为 RGBA_8888，使用 jnigraphics 锁定/解锁像素，必须在所有路径上解锁。
 * - 首选通过 dlopen("libwebp.so") + dlsym 解析符号，若失败则从 RTLD_DEFAULT 回退解析。
 * - 无全局状态，线程安全；错误通过 log 输出并返回空/失败。
 */
#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <cstdlib>
#include <cstdint>
#include <vector>
#include <unistd.h>
#include <cstring>

#define LOG_TAG "webp_jni"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

typedef size_t (*WebPEncodeRGBA_t)(const uint8_t* rgba, int width, int height, int stride, float quality_factor, uint8_t** output);

// 检查 libwebp 是否可用：通过 dlopen+dlsym 或 RTLD_DEFAULT 查找 WebPEncodeRGBA 符号
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_alex_studydemo_module_1media_LibWebpNative_isLibwebpAvailable(
        JNIEnv* env,
        jobject /*thiz*/
) {
    void* handle = dlopen("libwebp.so", RTLD_NOW);
    WebPEncodeRGBA_t fn = nullptr;
    if (handle) {
        fn = (WebPEncodeRGBA_t) dlsym(handle, "WebPEncodeRGBA");
        dlclose(handle);
    }
    if (!fn) {
        fn = (WebPEncodeRGBA_t) dlsym(RTLD_DEFAULT, "WebPEncodeRGBA");
    }
    return fn != nullptr ? JNI_TRUE : JNI_FALSE;
}

// 将原始 RGBA 字节数组编码为 WEBP
// 参数说明：
// - jrgba: 长度应为 width*height*4 的 RGBA 数据（每像素4字节，R,G,B,A）
// - width/height: 像素宽高
// - stride: 每行字节步长（通常为 width*4，但可能更大）
// - quality: 质量因子 [0,100]
// 内存管理：
// - GetByteArrayElements 获取指针，ReleaseByteArrayElements 使用 JNI_ABORT（不回写）释放
// - WebPEncodeRGBA 返回的 output 由 malloc 分配，使用 free() 释放
extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_alex_studydemo_module_1media_LibWebpNative_encodeRgbaToWebp(
        JNIEnv* env,
        jobject /*thiz*/,
        jbyteArray jrgba,
        jint width,
        jint height,
        jint stride,
        jfloat quality
) {
    jsize len = env->GetArrayLength(jrgba);
    if (len < width * height * 4) {
        LOGE("rgba length mismatch: %d vs %d", len, width * height * 4);
        return nullptr;
    }

    jboolean is_copy = JNI_FALSE;
    jbyte* rgba_bytes = env->GetByteArrayElements(jrgba, &is_copy);
    if (!rgba_bytes) return nullptr;

    void* handle = dlopen("libwebp.so", RTLD_NOW);
    WebPEncodeRGBA_t WebPEncodeRGBA_fn = nullptr;
    if (handle) {
        WebPEncodeRGBA_fn = (WebPEncodeRGBA_t) dlsym(handle, "WebPEncodeRGBA");
    } else {
        // Fallback: try to resolve symbol from already loaded libraries
        WebPEncodeRGBA_fn = (WebPEncodeRGBA_t) dlsym(RTLD_DEFAULT, "WebPEncodeRGBA");
    }
    if (!WebPEncodeRGBA_fn) {
        LOGE("WebPEncodeRGBA symbol not found");
        if (handle) dlclose(handle);
        env->ReleaseByteArrayElements(jrgba, rgba_bytes, JNI_ABORT);
        return nullptr;
    }

    uint8_t* output = nullptr;
    size_t out_size = WebPEncodeRGBA_fn(reinterpret_cast<const uint8_t*>(rgba_bytes),
                                        width, height, stride, quality, &output);
    env->ReleaseByteArrayElements(jrgba, rgba_bytes, JNI_ABORT);

    if (out_size == 0 || output == nullptr) {
        LOGE("WebPEncodeRGBA returned 0");
        dlclose(handle);
        return nullptr;
    }

    jbyteArray jret = env->NewByteArray((jsize) out_size);
    if (!jret) {
        free(output);
        dlclose(handle);
        return nullptr;
    }
    env->SetByteArrayRegion(jret, 0, (jsize) out_size, reinterpret_cast<const jbyte*>(output));
    free(output);
    if (handle) dlclose(handle);
    return jret;
}

// 将 Android Bitmap (RGBA_8888) 编码为 WEBP
// 要求：
// - Bitmap 配置为 RGBA_8888，pixels 指向每像素 4 字节的像素数据
// - 使用 AndroidBitmap_lockPixels/UnlockPixels 获取与释放像素指针
// - stride/width/height 取自 AndroidBitmapInfo，用于 WebPEncodeRGBA
// 内存管理：
// - WebPEncodeRGBA 输出使用 free() 释放
// - 必须在所有返回路径上调用 AndroidBitmap_unlockPixels
extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_alex_studydemo_module_1media_LibWebpNative_encodeBitmapToWebp(
        JNIEnv* env,
        jobject /*thiz*/,
        jobject bitmap,
        jfloat quality
) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("AndroidBitmap_getInfo failed");
        return nullptr;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap format not RGBA_8888");
        return nullptr;
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS || !pixels) {
        LOGE("AndroidBitmap_lockPixels failed");
        return nullptr;
    }

    void* handle = dlopen("libwebp.so", RTLD_NOW);
    WebPEncodeRGBA_t WebPEncodeRGBA_fn = nullptr;
    if (handle) {
        WebPEncodeRGBA_fn = (WebPEncodeRGBA_t) dlsym(handle, "WebPEncodeRGBA");
    } else {
        // Fallback: try to resolve symbol from already loaded libraries
        WebPEncodeRGBA_fn = (WebPEncodeRGBA_t) dlsym(RTLD_DEFAULT, "WebPEncodeRGBA");
    }
    if (!WebPEncodeRGBA_fn) {
        LOGE("WebPEncodeRGBA symbol not found");
        if (handle) dlclose(handle);
        AndroidBitmap_unlockPixels(env, bitmap);
        return nullptr;
    }

    uint8_t* output = nullptr;
    size_t out_size = WebPEncodeRGBA_fn(reinterpret_cast<const uint8_t*>(pixels),
                                        static_cast<int>(info.width),
                                        static_cast<int>(info.height),
                                        static_cast<int>(info.stride),
                                        quality,
                                        &output);

    AndroidBitmap_unlockPixels(env, bitmap);

    if (out_size == 0 || output == nullptr) {
        LOGE("WebPEncodeRGBA returned 0");
        dlclose(handle);
        return nullptr;
    }

    jbyteArray jret = env->NewByteArray((jsize) out_size);
    if (!jret) {
        free(output);
        dlclose(handle);
        return nullptr;
    }
    env->SetByteArrayRegion(jret, 0, (jsize) out_size, reinterpret_cast<const jbyte*>(output));
    free(output);
    if (handle) dlclose(handle);
    return jret;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_alex_studydemo_module_1media_LibWebpNative_decodeWebpToBitmapFd(
        JNIEnv* env,
        jobject /*thiz*/,
        jobject fileDescriptor
) {
    jclass fdCls = env->FindClass("java/io/FileDescriptor");
    if (!fdCls) return nullptr;
    jfieldID fid = env->GetFieldID(fdCls, "descriptor", "I");
    if (!fid) return nullptr;
    jint jfd = env->GetIntField(fileDescriptor, fid);
    int fd = (int)jfd;
    if (fd < 0) return nullptr;

    (void)lseek(fd, 0, SEEK_SET);

    // Read WebP bytes into memory
    std::vector<uint8_t> bytes;
    const size_t CHUNK = 64 * 1024;
    std::vector<uint8_t> tmp;
    tmp.resize(CHUNK);
    for (;;) {
        ssize_t r = ::read(fd, tmp.data(), CHUNK);
        if (r < 0) {
            return nullptr;
        } else if (r == 0) {
            break;
        } else {
            bytes.insert(bytes.end(), tmp.data(), tmp.data() + r);
        }
    }
    if (bytes.empty()) return nullptr;

    // Resolve symbols dynamically
    void* handle = dlopen("libwebp.so", RTLD_NOW);
    typedef int (*WebPGetInfo_t)(const uint8_t*, size_t, int*, int*);
    typedef uint8_t* (*WebPDecodeRGBA_t)(const uint8_t*, size_t, int*, int*);
    typedef void (*WebPFree_t)(void*);

    WebPGetInfo_t WebPGetInfo_fn = nullptr;
    WebPDecodeRGBA_t WebPDecodeRGBA_fn = nullptr;
    WebPFree_t WebPFree_fn = nullptr;

    if (handle) {
        WebPGetInfo_fn = (WebPGetInfo_t) dlsym(handle, "WebPGetInfo");
        WebPDecodeRGBA_fn = (WebPDecodeRGBA_t) dlsym(handle, "WebPDecodeRGBA");
        WebPFree_fn = (WebPFree_t) dlsym(handle, "WebPFree");
    }
    if (!WebPGetInfo_fn) WebPGetInfo_fn = (WebPGetInfo_t) dlsym(RTLD_DEFAULT, "WebPGetInfo");
    if (!WebPDecodeRGBA_fn) WebPDecodeRGBA_fn = (WebPDecodeRGBA_t) dlsym(RTLD_DEFAULT, "WebPDecodeRGBA");
    if (!WebPFree_fn) WebPFree_fn = (WebPFree_t) dlsym(RTLD_DEFAULT, "WebPFree");

    if (handle) dlclose(handle);
    if (!WebPGetInfo_fn || !WebPDecodeRGBA_fn || !WebPFree_fn) {
        LOGE("libwebp decode symbols not found");
        return nullptr;
    }

    int width = 0, height = 0;
    if (!WebPGetInfo_fn(bytes.data(), bytes.size(), &width, &height) || width <= 0 || height <= 0) {
        return nullptr;
    }

    int outW = 0, outH = 0;
    uint8_t* rgba = WebPDecodeRGBA_fn(bytes.data(), bytes.size(), &outW, &outH);
    if (!rgba || outW != width || outH != height) {
        if (rgba) WebPFree_fn(rgba);
        return nullptr;
    }

    // Create Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    jclass bitmapCls = env->FindClass("android/graphics/Bitmap");
    jclass configCls = env->FindClass("android/graphics/Bitmap$Config");
    if (!bitmapCls || !configCls) { WebPFree_fn(rgba); return nullptr; }
    jfieldID argb8888Field = env->GetStaticFieldID(configCls, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    if (!argb8888Field) { WebPFree_fn(rgba); return nullptr; }
    jobject argb8888 = env->GetStaticObjectField(configCls, argb8888Field);
    if (!argb8888) { WebPFree_fn(rgba); return nullptr; }
    jmethodID createMid = env->GetStaticMethodID(bitmapCls, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    if (!createMid) { WebPFree_fn(rgba); return nullptr; }
    jobject bitmap = env->CallStaticObjectMethod(bitmapCls, createMid, (jint)width, (jint)height, argb8888);
    if (!bitmap) { WebPFree_fn(rgba); return nullptr; }

    // Copy RGBA into Bitmap pixels
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS || !pixels) {
        WebPFree_fn(rgba);
        return nullptr;
    }
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        AndroidBitmap_unlockPixels(env, bitmap);
        WebPFree_fn(rgba);
        return nullptr;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        AndroidBitmap_unlockPixels(env, bitmap);
        WebPFree_fn(rgba);
        return nullptr;
    }
    const size_t srcStride = (size_t)width * 4;
    for (int y = 0; y < height; ++y) {
        const uint8_t* src = rgba + (size_t)y * srcStride;
        uint8_t* dst = (uint8_t*)pixels + (size_t)y * (size_t)info.stride;
        memcpy(dst, src, srcStride);
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    WebPFree_fn(rgba);
    return bitmap;
}