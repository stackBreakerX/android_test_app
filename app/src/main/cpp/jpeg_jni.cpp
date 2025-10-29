#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <dlfcn.h>
#include <unistd.h>
#include <vector>
#include <cstdint>

#define LOG_TAG "jpeg_jni"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// TurboJPEG function pointer types
typedef void* tjhandle;
typedef tjhandle (*tjInitCompress_t)(void);
typedef int (*tjDestroy_t)(tjhandle);
typedef int (*tjCompress2_t)(tjhandle, unsigned char* srcBuf, int width, int pitch, int height,
                             int pixelFormat, unsigned char** jpegBuf, unsigned long* jpegSize,
                             int jpegSubsamp, int jpegQual, int flags);
typedef void (*tjFree_t)(unsigned char*);

// Add decompress-related typedefs
typedef tjhandle (*tjInitDecompress_t)(void);
typedef int (*tjDecompressHeader2_t)(tjhandle, unsigned char* jpegBuf, unsigned long jpegSize, int* width, int* height, int* jpegSubsamp);
typedef int (*tjDecompress2_t)(tjhandle, unsigned char* jpegBuf, unsigned long jpegSize, unsigned char* dstBuf, int width, int pitch, int height, int pixelFormat, int flags);

// Constants from turbojpeg.h
static const int TJPF_RGB = 0;       // confirmed via documentation
static const int TJSAMP_444 = 0;     // confirmed via documentation
static const int TJFLAG_FASTDCT = 2048; // confirmed via documentation

static bool write_all(int fd, const void* data, size_t len) {
    const uint8_t* p = (const uint8_t*)data;
    size_t off = 0;
    while (off < len) {
        ssize_t w = ::write(fd, p + off, len - off);
        if (w < 0) return false;
        off += (size_t)w;
    }
    return true;
}

struct TjSymbols {
    tjInitCompress_t initCompress = nullptr;
    tjDestroy_t destroy = nullptr;
    tjCompress2_t compress2 = nullptr;
    tjFree_t tjfree = nullptr;
    // Decompress
    tjInitDecompress_t initDecompress = nullptr;
    tjDecompressHeader2_t decompressHeader2 = nullptr;
    tjDecompress2_t decompress2 = nullptr;
};

static bool load_turbojpeg(TjSymbols& syms) {
    void* handle = dlopen("libturbojpeg.so", RTLD_NOW);
    if (handle) {
        syms.initCompress = (tjInitCompress_t)dlsym(handle, "tjInitCompress");
        syms.destroy = (tjDestroy_t)dlsym(handle, "tjDestroy");
        syms.compress2 = (tjCompress2_t)dlsym(handle, "tjCompress2");
        syms.tjfree = (tjFree_t)dlsym(handle, "tjFree");
        // Decompress
        syms.initDecompress = (tjInitDecompress_t)dlsym(handle, "tjInitDecompress");
        syms.decompressHeader2 = (tjDecompressHeader2_t)dlsym(handle, "tjDecompressHeader2");
        syms.decompress2 = (tjDecompress2_t)dlsym(handle, "tjDecompress2");
        dlclose(handle);
    }
    // Fallback to global symbol resolution
    if (!syms.initCompress) syms.initCompress = (tjInitCompress_t)dlsym(RTLD_DEFAULT, "tjInitCompress");
    if (!syms.destroy) syms.destroy = (tjDestroy_t)dlsym(RTLD_DEFAULT, "tjDestroy");
    if (!syms.compress2) syms.compress2 = (tjCompress2_t)dlsym(RTLD_DEFAULT, "tjCompress2");
    if (!syms.tjfree) syms.tjfree = (tjFree_t)dlsym(RTLD_DEFAULT, "tjFree");
    // Decompress
    if (!syms.initDecompress) syms.initDecompress = (tjInitDecompress_t)dlsym(RTLD_DEFAULT, "tjInitDecompress");
    if (!syms.decompressHeader2) syms.decompressHeader2 = (tjDecompressHeader2_t)dlsym(RTLD_DEFAULT, "tjDecompressHeader2");
    if (!syms.decompress2) syms.decompress2 = (tjDecompress2_t)dlsym(RTLD_DEFAULT, "tjDecompress2");

    return syms.initCompress && syms.destroy && syms.compress2; // tjFree is optional
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_alex_studydemo_module_1media_LibJpegNative_isJpegAvailable(JNIEnv*, jobject) {
    TjSymbols syms;
    bool ok = load_turbojpeg(syms);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_alex_studydemo_module_1media_LibJpegNative_encodeBitmapToJpegFd(
        JNIEnv* env,
        jobject /*thiz*/,
        jobject bitmap,
        jint quality,
        jboolean dropAlpha,
        jobject fileDescriptor
) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("AndroidBitmap_getInfo failed");
        return JNI_FALSE;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap not RGBA_8888");
        return JNI_FALSE;
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS || !pixels) {
        LOGE("AndroidBitmap_lockPixels failed");
        return JNI_FALSE;
    }

    jclass fdCls = env->FindClass("java/io/FileDescriptor");
    if (!fdCls) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return JNI_FALSE;
    }
    jfieldID fid = env->GetFieldID(fdCls, "descriptor", "I");
    if (!fid) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return JNI_FALSE;
    }
    jint jfd = env->GetIntField(fileDescriptor, fid);
    int fd = (int)jfd;
    if (fd < 0) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return JNI_FALSE;
    }

    const int w = (int)info.width;
    const int height = (int)info.height;

    // Convert RGBA to RGB (drop alpha). We rely on Kotlin side to flatten alpha visually if needed.
    std::vector<unsigned char> rgb;
    rgb.resize((size_t)w * (size_t)height * 3);
    for (int y = 0; y < height; ++y) {
        const uint8_t* src = (const uint8_t*)pixels + (size_t)y * (size_t)info.stride;
        unsigned char* dst = rgb.data() + (size_t)y * (size_t)(w * 3);
        for (int x = 0; x < w; ++x) {
            const uint8_t* s = src + x * 4; // RGBA
            unsigned char* d = dst + x * 3; // RGB
            d[0] = s[0]; // R
            d[1] = s[1]; // G
            d[2] = s[2]; // B
        }
    }

    TjSymbols syms;
    if (!load_turbojpeg(syms)) {
        AndroidBitmap_unlockPixels(env, bitmap);
        LOGE("Failed to load libturbojpeg symbols");
        return JNI_FALSE;
    }

    tjhandle tj = syms.initCompress();
    if (!tj) {
        AndroidBitmap_unlockPixels(env, bitmap);
        LOGE("tjInitCompress failed");
        return JNI_FALSE;
    }

    unsigned char* jpegBuf = nullptr;
    unsigned long jpegSize = 0;
    int q = (int)quality;
    if (q < 1) q = 1;
    if (q > 100) q = 100;

    int ret = syms.compress2(tj, rgb.data(), w, 0, height, TJPF_RGB, &jpegBuf, &jpegSize, TJSAMP_444, q, TJFLAG_FASTDCT);
    if (ret != 0 || !jpegBuf || jpegSize == 0) {
        syms.destroy(tj);
        AndroidBitmap_unlockPixels(env, bitmap);
        LOGE("tjCompress2 failed");
        return JNI_FALSE;
    }

    bool wret = write_all(fd, jpegBuf, (size_t)jpegSize);

    // Free JPEG buffer
    if (syms.tjfree) syms.tjfree(jpegBuf);
    else free(jpegBuf);

    syms.destroy(tj);
    AndroidBitmap_unlockPixels(env, bitmap);

    return wret ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_alex_studydemo_module_1media_LibJpegNative_decodeJpegToBitmapFd(
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

    // Ensure reading from start
    (void)lseek(fd, 0, SEEK_SET);

    // Read JPEG bytes into memory
    std::vector<unsigned char> jpeg;
    const size_t CHUNK = 64 * 1024;
    std::vector<unsigned char> tmp;
    tmp.resize(CHUNK);
    for (;;) {
        ssize_t r = ::read(fd, tmp.data(), CHUNK);
        if (r < 0) {
            return nullptr;
        } else if (r == 0) {
            break;
        } else {
            jpeg.insert(jpeg.end(), tmp.data(), tmp.data() + r);
        }
    }
    if (jpeg.empty()) return nullptr;

    TjSymbols syms;
    load_turbojpeg(syms);
    if (!syms.initDecompress || !syms.decompressHeader2 || !syms.decompress2 || !syms.destroy) {
        return nullptr;
    }

    tjhandle tj = syms.initDecompress();
    if (!tj) return nullptr;

    int width = 0, height = 0, subsamp = 0;
    unsigned long jsize = (unsigned long)jpeg.size();
    if (syms.decompressHeader2(tj, jpeg.data(), jsize, &width, &height, &subsamp) != 0 || width <= 0 || height <= 0) {
        syms.destroy(tj);
        return nullptr;
    }

    // Decompress to RGB
    std::vector<unsigned char> rgb;
    rgb.resize((size_t)width * (size_t)height * 3);
    if (syms.decompress2(tj, jpeg.data(), jsize, rgb.data(), width, 0, height, TJPF_RGB, TJFLAG_FASTDCT) != 0) {
        syms.destroy(tj);
        return nullptr;
    }
    syms.destroy(tj);

    // Create Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    jclass bitmapCls = env->FindClass("android/graphics/Bitmap");
    jclass configCls = env->FindClass("android/graphics/Bitmap$Config");
    if (!bitmapCls || !configCls) return nullptr;
    jfieldID argb8888Field = env->GetStaticFieldID(configCls, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    if (!argb8888Field) return nullptr;
    jobject argb8888 = env->GetStaticObjectField(configCls, argb8888Field);
    if (!argb8888) return nullptr;
    jmethodID createMid = env->GetStaticMethodID(bitmapCls, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    if (!createMid) return nullptr;
    jobject bitmap = env->CallStaticObjectMethod(bitmapCls, createMid, (jint)width, (jint)height, argb8888);
    if (!bitmap) return nullptr;

    // Fill Bitmap pixels with RGB (alpha=255)
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS || !pixels) {
        return nullptr;
    }
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return nullptr;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return nullptr;
    }
    const int w = width;
    const int h = height;
    for (int y = 0; y < h; ++y) {
        uint8_t* dst = (uint8_t*)pixels + (size_t)y * (size_t)info.stride;
        const uint8_t* src = rgb.data() + (size_t)y * (size_t)(w * 3);
        for (int x = 0; x < w; ++x) {
            const uint8_t* s = src + x * 3;
            uint8_t* d = dst + x * 4;
            d[0] = s[0]; // R
            d[1] = s[1]; // G
            d[2] = s[2]; // B
            d[3] = 255;  // A
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);

    return bitmap;
}