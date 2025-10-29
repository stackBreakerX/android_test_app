/*
 * dng_jni.cpp — JNI bridge for LibRaw DNG decoding
 *
 * 能力：
 * - isDngAvailable(): 检查关键 C API 符号是否可解析，判断 libraw 可用性。
 * - decodeDngToBitmapFd(): 从 FileDescriptor 读取 DNG 原始字节，使用 libraw 解码到 ARGB_8888 Bitmap。
 *
 * 说明：
 * - 通过 dlopen("libraw.so") + dlsym 动态解析 C API，若失败则从 RTLD_DEFAULT 回退解析。
 * - 使用 libraw_open_buffer + libraw_unpack + libraw_dcraw_process + libraw_dcraw_make_mem_image 流程。
 * - 优先设置输出位深为 8bit；如返回 16bit，则在 JNI 中降采样到 8bit。
 * - libraw_dcraw_make_mem_image 返回的结构需要使用 libraw_dcraw_clear_mem 释放。
 */
#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <dlfcn.h>
#include <unistd.h>
#include <vector>
#include <cstdint>
#include <cstring>
#include <time.h>

#define LOG_TAG "dng_jni"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)

static inline int64_t now_ms() {
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000 + (int64_t)ts.tv_nsec / 1000000;
}

// 新增：记录最近一次 DNG 解码各阶段耗时与输出信息（非线程安全）
static long long s_last_read_ms = 0;
static long long s_last_open_ms = 0;
static long long s_last_unpack_ms = 0;
static long long s_last_process_ms = 0;
static long long s_last_make_ms = 0;
static long long s_last_copy_ms = 0;
static int s_last_w = 0;
static int s_last_h = 0;
static int s_last_colors = 0;
static int s_last_bits = 0;
// 兼容 libraw_processed_image_t 的内存布局（不依赖头文件）。
// 注意：某些版本将 data 定义为灵活数组（unsigned char data[1]），
// 我们仅将其视为紧随其后的字节块入口。
struct libraw_processed_image_compat_t {
    uint32_t type;      // enum LibRaw_image_formats
    uint16_t height;
    uint16_t width;
    uint16_t colors;    // 3 (RGB) 或 4
    uint16_t bits;      // 8 或 16
    uint32_t data_size; // 字节数
    unsigned char data[1]; // 紧随其后的字节数据
};

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_alex_studydemo_module_1media_LibDngNative_isDngAvailable(JNIEnv*, jobject) {
    void* handle = dlopen("libraw.so", RTLD_NOW);
    auto find = [&](const char* sym) -> void* {
        void* p = nullptr;
        if (handle) p = dlsym(handle, sym);
        if (!p) p = dlsym(RTLD_DEFAULT, sym);
        return p;
    };
    void* p_init = find("libraw_init");
    void* p_open_buffer = find("libraw_open_buffer");
    void* p_unpack = find("libraw_unpack");
    void* p_process = find("libraw_dcraw_process");
    void* p_make = find("libraw_dcraw_make_mem_image");
    void* p_clear = find("libraw_dcraw_clear_mem");
    void* p_close = find("libraw_close");
    if (handle) dlclose(handle);
    return (p_init && p_open_buffer && p_unpack && p_process && p_make && p_clear && p_close) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobject JNICALL
Java_com_alex_studydemo_module_1media_LibDngNative_decodeDngToBitmapFd(
        JNIEnv* env,
        jobject /*thiz*/,
        jobject fileDescriptor
) {
    // 读取 FD
    jclass fdCls = env->FindClass("java/io/FileDescriptor");
    if (!fdCls) return nullptr;
    jfieldID fid = env->GetFieldID(fdCls, "descriptor", "I");
    if (!fid) return nullptr;
    jint jfd = env->GetIntField(fileDescriptor, fid);
    int fd = (int)jfd;
    if (fd < 0) return nullptr;

    (void)lseek(fd, 0, SEEK_SET);

    // 读入内存缓冲
    int64_t t_read0 = now_ms();
    std::vector<uint8_t> bytes;
    const size_t CHUNK = 128 * 1024;
    std::vector<uint8_t> tmp(CHUNK);
    for (;;) {
        ssize_t r = ::read(fd, tmp.data(), CHUNK);
        if (r < 0) {
            LOGE("read(fd) failed");
            return nullptr;
        } else if (r == 0) {
            break;
        } else {
            bytes.insert(bytes.end(), tmp.data(), tmp.data() + r);
        }
    }
    int64_t t_read1 = now_ms();
    LOGI("dng read_fd %lld ms, size=%zu", (long long)(t_read1 - t_read0), bytes.size());
    s_last_read_ms = (long long)(t_read1 - t_read0);
    if (bytes.empty()) {
        LOGE("empty input");
        return nullptr;
    }

    // 解析 libraw C API 符号
    void* handle = dlopen("libraw.so", RTLD_NOW);
    auto sym = [&](const char* s) -> void* {
        void* p = nullptr;
        if (handle) p = dlsym(handle, s);
        if (!p) p = dlsym(RTLD_DEFAULT, s);
        return p;
    };

    typedef void* (*libraw_init_t)(unsigned int);
    typedef int   (*libraw_open_buffer_t)(void*, void*, size_t);
    typedef int   (*libraw_unpack_t)(void*);
    typedef int   (*libraw_dcraw_process_t)(void*);
    typedef void* (*libraw_dcraw_make_mem_image_t)(void*, int*);
    typedef void  (*libraw_dcraw_clear_mem_t)(void*);
    typedef void  (*libraw_close_t)(void*);
    typedef void  (*libraw_set_output_bps_t)(void*, int);
    typedef void  (*libraw_set_output_color_t)(void*, int);
    typedef void  (*libraw_set_no_auto_bright_t)(void*, int);
    typedef void  (*libraw_set_gamma_t)(void*, float, float);
    typedef void  (*libraw_set_demosaic_t)(void*, int);
    typedef void  (*libraw_set_use_camera_wb_t)(void*, int);
    typedef void  (*libraw_set_half_size_t)(void*, int);

    libraw_init_t                libraw_init_fn = (libraw_init_t)sym("libraw_init");
    libraw_open_buffer_t         libraw_open_buffer_fn = (libraw_open_buffer_t)sym("libraw_open_buffer");
    libraw_unpack_t              libraw_unpack_fn = (libraw_unpack_t)sym("libraw_unpack");
    libraw_dcraw_process_t       libraw_dcraw_process_fn = (libraw_dcraw_process_t)sym("libraw_dcraw_process");
    libraw_dcraw_make_mem_image_t libraw_dcraw_make_mem_image_fn = (libraw_dcraw_make_mem_image_t)sym("libraw_dcraw_make_mem_image");
    libraw_dcraw_clear_mem_t     libraw_dcraw_clear_mem_fn = (libraw_dcraw_clear_mem_t)sym("libraw_dcraw_clear_mem");
    libraw_close_t               libraw_close_fn = (libraw_close_t)sym("libraw_close");
    libraw_set_output_bps_t      libraw_set_output_bps_fn = (libraw_set_output_bps_t)sym("libraw_set_output_bps");
    libraw_set_output_color_t    libraw_set_output_color_fn = (libraw_set_output_color_t)sym("libraw_set_output_color");
    libraw_set_no_auto_bright_t  libraw_set_no_auto_bright_fn = (libraw_set_no_auto_bright_t)sym("libraw_set_no_auto_bright");
    libraw_set_gamma_t           libraw_set_gamma_fn = (libraw_set_gamma_t)sym("libraw_set_gamma");
    libraw_set_demosaic_t        libraw_set_demosaic_fn = (libraw_set_demosaic_t)sym("libraw_set_demosaic");
    libraw_set_use_camera_wb_t   libraw_set_use_camera_wb_fn = (libraw_set_use_camera_wb_t)sym("libraw_set_use_camera_wb");
    libraw_set_half_size_t       libraw_set_half_size_fn = (libraw_set_half_size_t)sym("libraw_set_half_size");

    if (!libraw_init_fn || !libraw_open_buffer_fn || !libraw_unpack_fn ||
        !libraw_dcraw_process_fn || !libraw_dcraw_make_mem_image_fn ||
        !libraw_dcraw_clear_mem_fn || !libraw_close_fn) {
        LOGE("libraw symbols missing");
        return nullptr;
    }

    // 初始化处理器
    void* lr = libraw_init_fn(0);
    if (!lr) {
        LOGE("libraw_init failed");
        return nullptr;
    }

    // 快速参数：优先速度
    if (libraw_set_no_auto_bright_fn) libraw_set_no_auto_bright_fn(lr, 1);
    if (libraw_set_use_camera_wb_fn)  libraw_set_use_camera_wb_fn(lr, 1);
    if (libraw_set_gamma_fn)          libraw_set_gamma_fn(lr, 1.0f, 1.0f); // 线性 gamma
    if (libraw_set_demosaic_fn)       libraw_set_demosaic_fn(lr, 1 /*linear*/);
    if (libraw_set_half_size_fn)      libraw_set_half_size_fn(lr, 1);       // 半尺寸处理大幅提速

    // 设置输出为 8bit sRGB（尽量保证 ARGB_8888）
    if (libraw_set_output_bps_fn) libraw_set_output_bps_fn(lr, 8);
    if (libraw_set_output_color_fn) libraw_set_output_color_fn(lr, 1 /*sRGB*/);

    int64_t t0 = now_ms();
    int ret = libraw_open_buffer_fn(lr, (void*)bytes.data(), bytes.size());
    int64_t t1 = now_ms(); LOGI("dng open_buffer %lld ms", (long long)(t1 - t0));
    s_last_open_ms = (long long)(t1 - t0);
    if (ret != 0) {
        LOGE("libraw_open_buffer=%d", ret);
        libraw_close_fn(lr);
        return nullptr;
    }
    ret = libraw_unpack_fn(lr);
    int64_t t2 = now_ms(); LOGI("dng unpack %lld ms", (long long)(t2 - t1));
    s_last_unpack_ms = (long long)(t2 - t1);
    if (ret != 0) {
        LOGE("libraw_unpack=%d", ret);
        libraw_close_fn(lr);
        return nullptr;
    }
    ret = libraw_dcraw_process_fn(lr);
    int64_t t3 = now_ms(); LOGI("dng dcraw_process %lld ms", (long long)(t3 - t2));
    s_last_process_ms = (long long)(t3 - t2);
    if (ret != 0) {
        LOGE("libraw_dcraw_process=%d", ret);
        libraw_close_fn(lr);
        return nullptr;
    }

    int errcode = 0;
    void* img_ptr = libraw_dcraw_make_mem_image_fn(lr, &errcode);
    int64_t t4 = now_ms(); LOGI("dng make_mem_image %lld ms", (long long)(t4 - t3));
    s_last_make_ms = (long long)(t4 - t3);
    if (!img_ptr || errcode != 0) {
        LOGE("dcraw_make_mem_image err=%d", errcode);
        libraw_close_fn(lr);
        return nullptr;
    }

    libraw_processed_image_compat_t* img = (libraw_processed_image_compat_t*)img_ptr;
    const int width = (int)img->width;
    const int height = (int)img->height;
    const int colors = (int)img->colors;
    const int bits = (int)img->bits;
    const size_t data_size = (size_t)img->data_size;
    s_last_w = width;
    s_last_h = height;
    s_last_colors = colors;
    s_last_bits = bits;

    if (width <= 0 || height <= 0 || (colors != 3 && colors != 4)) {
        LOGE("unexpected image params: w=%d h=%d colors=%d bits=%d", width, height, colors, bits);
        libraw_dcraw_clear_mem_fn(img_ptr);
        libraw_close_fn(lr);
        return nullptr;
    }

    // 创建 Bitmap
    jclass bitmapCls = env->FindClass("android/graphics/Bitmap");
    jclass configCls = env->FindClass("android/graphics/Bitmap$Config");
    if (!bitmapCls || !configCls) {
        libraw_dcraw_clear_mem_fn(img_ptr);
        libraw_close_fn(lr);
        return nullptr;
    }
    jfieldID argb8888Field = env->GetStaticFieldID(configCls, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    if (!argb8888Field) {
        libraw_dcraw_clear_mem_fn(img_ptr);
        libraw_close_fn(lr);
        return nullptr;
    }
    jobject argb8888 = env->GetStaticObjectField(configCls, argb8888Field);
    if (!argb8888) {
        libraw_dcraw_clear_mem_fn(img_ptr);
        libraw_close_fn(lr);
        return nullptr;
    }
    jmethodID createMid = env->GetStaticMethodID(bitmapCls, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    if (!createMid) {
        libraw_dcraw_clear_mem_fn(img_ptr);
        libraw_close_fn(lr);
        return nullptr;
    }
    jobject bitmap = env->CallStaticObjectMethod(bitmapCls, createMid, (jint)width, (jint)height, argb8888);
    if (!bitmap) {
        libraw_dcraw_clear_mem_fn(img_ptr);
        libraw_close_fn(lr);
        return nullptr;
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS || !pixels) {
        libraw_dcraw_clear_mem_fn(img_ptr);
        libraw_close_fn(lr);
        return nullptr;
    }

    // 拷贝并做 16->8 位降采样（如需要）
    const size_t stride = (size_t)width * 4;
    if (bits == 8 && colors == 4 && data_size == (size_t)width * (size_t)height * 4) {
        // ARGB/RGBA 8bit 直接拷贝
        std::memcpy(pixels, img->data, data_size);
    } else if (bits == 16 && colors == 4 && data_size == (size_t)width * (size_t)height * 8) {
        // 每通道 16bit，降采样为 8bit
        const uint16_t* src = (const uint16_t*)img->data;
        uint8_t* dst = (uint8_t*)pixels;
        for (int y = 0; y < height; ++y) {
            const uint16_t* srow = src + (size_t)y * (size_t)width * 4;
            uint8_t* drow = dst + (size_t)y * (size_t)width * 4;
            for (int x = 0; x < width; ++x) {
                const uint16_t a = srow[x*4 + 0];
                const uint16_t r = srow[x*4 + 1];
                const uint16_t g = srow[x*4 + 2];
                const uint16_t b = srow[x*4 + 3];
                drow[x*4 + 0] = (uint8_t)(a >> 8);
                drow[x*4 + 1] = (uint8_t)(r >> 8);
                drow[x*4 + 2] = (uint8_t)(g >> 8);
                drow[x*4 + 3] = (uint8_t)(b >> 8);
            }
        }
    } else if (bits == 8 && colors == 3 && data_size == (size_t)width * (size_t)height * 3) {
        // RGB 8bit: 填充 A 通道为 255
        const uint8_t* src = (const uint8_t*)img->data;
        uint8_t* dst = (uint8_t*)pixels;
        for (int y = 0; y < height; ++y) {
            const uint8_t* srow = src + (size_t)y * (size_t)width * 3;
            uint8_t* drow = dst + (size_t)y * (size_t)width * 4;
            for (int x = 0; x < width; ++x) {
                drow[x*4 + 0] = 255;
                drow[x*4 + 1] = srow[x*3 + 0];
                drow[x*4 + 2] = srow[x*3 + 1];
                drow[x*4 + 3] = srow[x*3 + 2];
            }
        }
    } else if (bits == 16 && colors == 3 && data_size == (size_t)width * (size_t)height * 6) {
        // RGB 16bit: 降采样并填充 A=255
        const uint16_t* src = (const uint16_t*)img->data;
        uint8_t* dst = (uint8_t*)pixels;
        for (int y = 0; y < height; ++y) {
            const uint16_t* srow = src + (size_t)y * (size_t)width * 3;
            uint8_t* drow = dst + (size_t)y * (size_t)width * 4;
            for (int x = 0; x < width; ++x) {
                drow[x*4 + 0] = 255;
                drow[x*4 + 1] = (uint8_t)(srow[x*3 + 0] >> 8);
                drow[x*4 + 2] = (uint8_t)(srow[x*3 + 1] >> 8);
                drow[x*4 + 3] = (uint8_t)(srow[x*3 + 2] >> 8);
            }
        }
    } else {
        LOGE("unsupported format bits=%d colors=%d size=%zu", bits, colors, data_size);
        AndroidBitmap_unlockPixels(env, bitmap);
        libraw_dcraw_clear_mem_fn(img_ptr);
        libraw_close_fn(lr);
        return nullptr;
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    int64_t t5 = now_ms();
    LOGI("dng copy_to_bitmap %lld ms, out=%dx%d colors=%d bits=%d", (long long)(t5 - t4), width, height, colors, bits);
    s_last_copy_ms = (long long)(t5 - t4);

    // 释放 libraw 资源
    libraw_dcraw_clear_mem_fn(img_ptr);
    libraw_close_fn(lr);

    // 返回 Bitmap
    return bitmap;
}

} // extern "C"

// 新增：返回最近一次 DNG 阶段耗时摘要字符串
extern "C" JNIEXPORT jstring JNICALL
Java_com_alex_studydemo_module_1media_LibDngNative_getLastDngTimingInfo(JNIEnv* env, jobject) {
    char buf[256];
    snprintf(buf, sizeof(buf),
             "read=%lldms, open=%lldms, unpack=%lldms, process=%lldms, make=%lldms, copy=%lldms | out=%dx%d colors=%d bits=%d",
             s_last_read_ms, s_last_open_ms, s_last_unpack_ms, s_last_process_ms, s_last_make_ms, s_last_copy_ms,
             s_last_w, s_last_h, s_last_colors, s_last_bits);
    return env->NewStringUTF(buf);
}

// 新增：返回最近一次 DNG 阶段耗时 JSON 字符串
extern "C" JNIEXPORT jstring JNICALL
Java_com_alex_studydemo_module_1media_LibDngNative_getLastDngTimingInfoJson(JNIEnv* env, jobject) {
    char buf[256];
    // 紧凑 JSON，避免过长
    snprintf(buf, sizeof(buf),
             "{\"read_ms\":%lld,\"open_ms\":%lld,\"unpack_ms\":%lld,\"process_ms\":%lld,\"make_ms\":%lld,\"copy_ms\":%lld,\"out\":{\"w\":%d,\"h\":%d,\"colors\":%d,\"bits\":%d}}",
             s_last_read_ms, s_last_open_ms, s_last_unpack_ms, s_last_process_ms, s_last_make_ms, s_last_copy_ms,
             s_last_w, s_last_h, s_last_colors, s_last_bits);
    return env->NewStringUTF(buf);
}