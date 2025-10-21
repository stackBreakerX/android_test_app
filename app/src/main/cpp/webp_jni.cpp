#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <cstdlib>
#include <cstdint>

#define LOG_TAG "webp_jni"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

typedef size_t (*WebPEncodeRGBA_t)(const uint8_t* rgba, int width, int height, int stride, float quality_factor, uint8_t** output);

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