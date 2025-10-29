#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <vector>
#include <cstdint>
#include <cstring>
#include <zlib.h>
#include <unistd.h>
#include <dlfcn.h>

#define LOG_TAG "png_jni"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static void write_u32(std::vector<uint8_t>& out, uint32_t v) {
    out.push_back((v >> 24) & 0xFF);
    out.push_back((v >> 16) & 0xFF);
    out.push_back((v >> 8) & 0xFF);
    out.push_back(v & 0xFF);
}

static void write_chunk(std::vector<uint8_t>& out, const char* type, const uint8_t* data, size_t len) {
    write_u32(out, (uint32_t)len);
    out.insert(out.end(), type, type + 4);
    if (len) out.insert(out.end(), data, data + len);
    uLong crc = crc32(0, Z_NULL, 0);
    crc = crc32(crc, (const Bytef*)type, 4);
    if (len) crc = crc32(crc, (const Bytef*)data, (uInt)len);
    write_u32(out, (uint32_t)crc);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_alex_studydemo_module_1media_LibPngNative_isPngAvailable(JNIEnv*, jobject) {
    return JNI_TRUE;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_alex_studydemo_module_1media_LibPngNative_encodeBitmapToPng2(
        JNIEnv*, jobject, jobject, jint, jboolean);

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_alex_studydemo_module_1media_LibPngNative_encodeBitmapToPng(
        JNIEnv* env, jobject thiz, jobject bitmap, jint level) {
    return Java_com_alex_studydemo_module_1media_LibPngNative_encodeBitmapToPng2(env, thiz, bitmap, level, JNI_FALSE);
}
extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_alex_studydemo_module_1media_LibPngNative_encodeBitmapToPng2(
        JNIEnv* env, jobject, jobject bitmap, jint level, jboolean dropAlpha) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("AndroidBitmap_getInfo failed");
        return nullptr;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap not RGBA_8888");
        return nullptr;
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS || !pixels) {
        LOGE("AndroidBitmap_lockPixels failed");
        return nullptr;
    }

    const int w = (int)info.width;
    const int h = (int)info.height;
    const bool drop = (dropAlpha == JNI_TRUE);
    const int comp = drop ? 3 : 4;

    std::vector<uint8_t> raw;
    raw.resize((size_t)h * (size_t)(1 + w * comp));
    for (int y = 0; y < h; ++y) {
        uint8_t* dst = raw.data() + (size_t)y * (size_t)(1 + w * comp);
        dst[0] = 1; // filter=SUB
        const uint8_t* src = (const uint8_t*)pixels + (size_t)y * (size_t)info.stride;
        if (!drop) {
            // RGBA with SUB filter
            for (int x = 0; x < w; ++x) {
                const uint8_t* s = src + x * 4;
                const uint8_t* l = (x > 0) ? (src + (x - 1) * 4) : nullptr;
                uint8_t* d = dst + 1 + x * 4;
                d[0] = (uint8_t)(s[0] - (l ? l[0] : 0));
                d[1] = (uint8_t)(s[1] - (l ? l[1] : 0));
                d[2] = (uint8_t)(s[2] - (l ? l[2] : 0));
                d[3] = (uint8_t)(s[3] - (l ? l[3] : 0));
            }
        } else {
            // RGB with SUB filter
            for (int x = 0; x < w; ++x) {
                const uint8_t* s = src + x * 4;
                const uint8_t* l = (x > 0) ? (src + (x - 1) * 4) : nullptr;
                uint8_t* d = dst + 1 + x * 3;
                d[0] = (uint8_t)(s[0] - (l ? l[0] : 0));
                d[1] = (uint8_t)(s[1] - (l ? l[1] : 0));
                d[2] = (uint8_t)(s[2] - (l ? l[2] : 0));
            }
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);

    // Compress using zlib streaming with RLE strategy for speed
    std::vector<uint8_t> compVec;
    const size_t OUT_CHUNK = 256 * 1024;
    uint8_t outBuf[OUT_CHUNK];
    z_stream strm;
    memset(&strm, 0, sizeof(strm));
    int lvl = level;
    if (lvl < 0) lvl = 0;
    if (lvl > 9) lvl = 9;
    int zret = deflateInit2(&strm, lvl, Z_DEFLATED, 15, 9, Z_RLE);
    if (zret != Z_OK) {
        LOGE("deflateInit2 failed: %d", zret);
        return nullptr;
    }

    strm.next_in = raw.data();
    strm.avail_in = (uInt)raw.size();
    while (strm.avail_in > 0) {
        strm.next_out = outBuf;
        strm.avail_out = (uInt)OUT_CHUNK;
        zret = deflate(&strm, Z_NO_FLUSH);
        if (zret != Z_OK) {
            deflateEnd(&strm);
            LOGE("deflate Z_NO_FLUSH failed: %d", zret);
            return nullptr;
        }
        size_t produced = (size_t)OUT_CHUNK - (size_t)strm.avail_out;
        if (produced > 0) {
            compVec.insert(compVec.end(), outBuf, outBuf + produced);
        }
    }

    int flush_ret;
    do {
        strm.next_out = outBuf;
        strm.avail_out = (uInt)OUT_CHUNK;
        flush_ret = deflate(&strm, Z_FINISH);
        if (flush_ret != Z_STREAM_END && flush_ret != Z_OK) {
            deflateEnd(&strm);
            LOGE("deflate Z_FINISH failed: %d", flush_ret);
            return nullptr;
        }
        size_t produced = (size_t)OUT_CHUNK - (size_t)strm.avail_out;
        if (produced > 0) {
            compVec.insert(compVec.end(), outBuf, outBuf + produced);
        }
    } while (flush_ret != Z_STREAM_END);

    deflateEnd(&strm);

    // Build PNG
    std::vector<uint8_t> out;
    const uint8_t sig[8] = {0x89,'P','N','G',0x0D,0x0A,0x1A,0x0A};
    out.insert(out.end(), sig, sig + 8);

    // IHDR
    uint8_t ihdr[13];
    ihdr[0] = (w >> 24) & 0xFF; ihdr[1] = (w >> 16) & 0xFF; ihdr[2] = (w >> 8) & 0xFF; ihdr[3] = w & 0xFF;
    ihdr[4] = (h >> 24) & 0xFF; ihdr[5] = (h >> 16) & 0xFF; ihdr[6] = (h >> 8) & 0xFF; ihdr[7] = h & 0xFF;
    ihdr[8] = 8; // bit depth
    ihdr[9] = drop ? 2 : 6; // 2=RGB, 6=RGBA
    ihdr[10] = 0;
    ihdr[11] = 0;
    ihdr[12] = 0;
    write_chunk(out, "IHDR", ihdr, 13);

    write_chunk(out, "IDAT", compVec.data(), compVec.size());
    write_chunk(out, "IEND", nullptr, 0);

    jbyteArray jret = env->NewByteArray((jsize)out.size());
    if (!jret) return nullptr;
    env->SetByteArrayRegion(jret, 0, (jsize)out.size(), (const jbyte*)out.data());
    return jret;
}

static inline void be32(uint8_t* b, uint32_t v) {
    b[0] = (uint8_t)((v >> 24) & 0xFF);
    b[1] = (uint8_t)((v >> 16) & 0xFF);
    b[2] = (uint8_t)((v >> 8) & 0xFF);
    b[3] = (uint8_t)(v & 0xFF);
}

static bool write_all(int fd, const void* data, size_t len) {
    const uint8_t* p = (const uint8_t*)data;
    size_t left = len;
    while (left > 0) {
        ssize_t n = write(fd, p, left);
        if (n < 0) {
            return false;
        }
        p += (size_t)n;
        left -= (size_t)n;
    }
    return true;
}

static bool write_chunk_fd(int fd, const char* type, const uint8_t* data, size_t len) {
    uint8_t hdr[8];
    be32(hdr, (uint32_t)len);
    memcpy(hdr + 4, type, 4);
    if (!write_all(fd, hdr, 8)) return false;
    if (len > 0) {
        if (!write_all(fd, data, len)) return false;
    }
    uLong crc = crc32(0, Z_NULL, 0);
    crc = crc32(crc, (const Bytef*)type, 4);
    if (len) crc = crc32(crc, (const Bytef*)data, (uInt)len);
    uint8_t crcbuf[4];
    be32(crcbuf, (uint32_t)crc);
    return write_all(fd, crcbuf, 4);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_alex_studydemo_module_1media_LibPngNative_encodeBitmapToPngFd(
        JNIEnv* env,
        jobject /*thiz*/,
        jobject bitmap,
        jint level,
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
    const int h = (int)info.height;
    const bool drop = (dropAlpha == JNI_TRUE);
    const int comp = drop ? 3 : 4;

    // write PNG signature
    const uint8_t sig[8] = {0x89,'P','N','G',0x0D,0x0A,0x1A,0x0A};
    if (!write_all(fd, sig, 8)) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return JNI_FALSE;
    }

    // IHDR
    uint8_t ihdr[13];
    ihdr[0] = (w >> 24) & 0xFF; ihdr[1] = (w >> 16) & 0xFF; ihdr[2] = (w >> 8) & 0xFF; ihdr[3] = w & 0xFF;
    ihdr[4] = (h >> 24) & 0xFF; ihdr[5] = (h >> 16) & 0xFF; ihdr[6] = (h >> 8) & 0xFF; ihdr[7] = h & 0xFF;
    ihdr[8] = 8;
    ihdr[9] = drop ? 2 : 6;
    ihdr[10] = 0;
    ihdr[11] = 0;
    ihdr[12] = 0;
    if (!write_chunk_fd(fd, "IHDR", ihdr, 13)) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return JNI_FALSE;
    }

    // zlib streaming
    z_stream strm;
    memset(&strm, 0, sizeof(strm));
    int lvl = level;
    if (lvl < 0) lvl = 0;
    if (lvl > 9) lvl = 9;
    int zret = deflateInit2(&strm, lvl, Z_DEFLATED, 15, 9, Z_RLE);
    if (zret != Z_OK) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return JNI_FALSE;
    }

    const size_t OUT_CHUNK = 256 * 1024; // 256KB per IDAT chunk
    std::vector<uint8_t> outChunk;
    outChunk.reserve(OUT_CHUNK);
    uint8_t outBuf[OUT_CHUNK];

    // Each row: filter byte + pixel data
    std::vector<uint8_t> rowBuf;
    rowBuf.resize((size_t)(1 + w * comp));

    for (int y = 0; y < h; ++y) {
        rowBuf[0] = 1; // filter=SUB
        const uint8_t* src = (const uint8_t*)pixels + (size_t)y * (size_t)info.stride;
        if (!drop) {
            // RGBA with SUB filter
            for (int x = 0; x < w; ++x) {
                const uint8_t* s = src + x * 4;
                const uint8_t* l = (x > 0) ? (src + (x - 1) * 4) : nullptr;
                uint8_t* d = rowBuf.data() + 1 + x * 4;
                d[0] = (uint8_t)(s[0] - (l ? l[0] : 0));
                d[1] = (uint8_t)(s[1] - (l ? l[1] : 0));
                d[2] = (uint8_t)(s[2] - (l ? l[2] : 0));
                d[3] = (uint8_t)(s[3] - (l ? l[3] : 0));
            }
        } else {
            // RGB with SUB filter
            for (int x = 0; x < w; ++x) {
                const uint8_t* s = src + x * 4;
                const uint8_t* l = (x > 0) ? (src + (x - 1) * 4) : nullptr;
                uint8_t* d = rowBuf.data() + 1 + x * 3;
                d[0] = (uint8_t)(s[0] - (l ? l[0] : 0));
                d[1] = (uint8_t)(s[1] - (l ? l[1] : 0));
                d[2] = (uint8_t)(s[2] - (l ? l[2] : 0));
            }
        }

        strm.next_in = rowBuf.data();
        strm.avail_in = (uInt)rowBuf.size();
        while (strm.avail_in > 0) {
            strm.next_out = outBuf;
            strm.avail_out = (uInt)OUT_CHUNK;
            zret = deflate(&strm, Z_NO_FLUSH);
            if (zret != Z_OK) {
                deflateEnd(&strm);
                AndroidBitmap_unlockPixels(env, bitmap);
                return JNI_FALSE;
            }
            size_t produced = (size_t)OUT_CHUNK - (size_t)strm.avail_out;
            if (produced > 0) {
                outChunk.insert(outChunk.end(), outBuf, outBuf + produced);
                if (outChunk.size() >= OUT_CHUNK) {
                    if (!write_chunk_fd(fd, "IDAT", outChunk.data(), outChunk.size())) {
                        deflateEnd(&strm);
                        AndroidBitmap_unlockPixels(env, bitmap);
                        return JNI_FALSE;
                    }
                    outChunk.clear();
                }
            }
        }
    }

    // finish
    int flush_ret;
    do {
        strm.next_out = outBuf;
        strm.avail_out = (uInt)OUT_CHUNK;
        flush_ret = deflate(&strm, Z_FINISH);
        if (flush_ret != Z_STREAM_END && flush_ret != Z_OK) {
            deflateEnd(&strm);
            AndroidBitmap_unlockPixels(env, bitmap);
            return JNI_FALSE;
        }
        size_t produced = (size_t)OUT_CHUNK - (size_t)strm.avail_out;
        if (produced > 0) {
            outChunk.insert(outChunk.end(), outBuf, outBuf + produced);
            if (outChunk.size() >= OUT_CHUNK) {
                if (!write_chunk_fd(fd, "IDAT", outChunk.data(), outChunk.size())) {
                    deflateEnd(&strm);
                    AndroidBitmap_unlockPixels(env, bitmap);
                    return JNI_FALSE;
                }
                outChunk.clear();
            }
        }
    } while (flush_ret != Z_STREAM_END);

    deflateEnd(&strm);

    if (!outChunk.empty()) {
        if (!write_chunk_fd(fd, "IDAT", outChunk.data(), outChunk.size())) {
            AndroidBitmap_unlockPixels(env, bitmap);
            return JNI_FALSE;
        }
        outChunk.clear();
    }

    // IEND
    if (!write_chunk_fd(fd, "IEND", nullptr, 0)) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return JNI_FALSE;
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_alex_studydemo_module_1media_LibPngNative_decodePngToBitmapFd(
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

    // Dynamically load AImageDecoder symbols from libandroid.so
    void* handle = dlopen("libandroid.so", RTLD_NOW);
    if (!handle) {
        LOGE("libandroid.so not available");
        return nullptr;
    }

    typedef struct AImageDecoder AImageDecoder;
    typedef struct AImageDecoderHeaderInfo AImageDecoderHeaderInfo;
    typedef int (*AImageDecoder_createFromFd_t)(int, AImageDecoder**);
    typedef const AImageDecoderHeaderInfo* (*AImageDecoder_getHeaderInfo_t)(AImageDecoder*);
    typedef int32_t (*AImageDecoderHeaderInfo_getWidth_t)(const AImageDecoderHeaderInfo*);
    typedef int32_t (*AImageDecoderHeaderInfo_getHeight_t)(const AImageDecoderHeaderInfo*);
    typedef int32_t (*AImageDecoderHeaderInfo_getAndroidBitmapFormat_t)(const AImageDecoderHeaderInfo*);
    typedef int (*AImageDecoder_setAndroidBitmapFormat_t)(AImageDecoder*, int32_t);
    typedef size_t (*AImageDecoder_getMinimumStride_t)(AImageDecoder*);
    typedef int (*AImageDecoder_decodeImage_t)(AImageDecoder*, void*, size_t, size_t);
    typedef void (*AImageDecoder_delete_t)(AImageDecoder*);

    AImageDecoder_createFromFd_t createFromFd = (AImageDecoder_createFromFd_t)dlsym(handle, "AImageDecoder_createFromFd");
    AImageDecoder_getHeaderInfo_t getHeaderInfo = (AImageDecoder_getHeaderInfo_t)dlsym(handle, "AImageDecoder_getHeaderInfo");
    AImageDecoderHeaderInfo_getWidth_t getWidth = (AImageDecoderHeaderInfo_getWidth_t)dlsym(handle, "AImageDecoderHeaderInfo_getWidth");
    AImageDecoderHeaderInfo_getHeight_t getHeight = (AImageDecoderHeaderInfo_getHeight_t)dlsym(handle, "AImageDecoderHeaderInfo_getHeight");
    AImageDecoderHeaderInfo_getAndroidBitmapFormat_t getFormat = (AImageDecoderHeaderInfo_getAndroidBitmapFormat_t)dlsym(handle, "AImageDecoderHeaderInfo_getAndroidBitmapFormat");
    AImageDecoder_setAndroidBitmapFormat_t setFormat = (AImageDecoder_setAndroidBitmapFormat_t)dlsym(handle, "AImageDecoder_setAndroidBitmapFormat");
    AImageDecoder_getMinimumStride_t getMinimumStride = (AImageDecoder_getMinimumStride_t)dlsym(handle, "AImageDecoder_getMinimumStride");
    AImageDecoder_decodeImage_t decodeImage = (AImageDecoder_decodeImage_t)dlsym(handle, "AImageDecoder_decodeImage");
    AImageDecoder_delete_t deleteDecoder = (AImageDecoder_delete_t)dlsym(handle, "AImageDecoder_delete");

    if (!createFromFd || !getHeaderInfo || !getWidth || !getHeight || !getMinimumStride || !decodeImage || !deleteDecoder) {
        LOGE("AImageDecoder symbols missing");
        dlclose(handle);
        return nullptr;
    }

    AImageDecoder* decoder = nullptr;
    if (createFromFd(fd, &decoder) != 0 || !decoder) {
        dlclose(handle);
        return nullptr;
    }

    const AImageDecoderHeaderInfo* info = getHeaderInfo(decoder);
    if (!info) { deleteDecoder(decoder); dlclose(handle); return nullptr; }
    int32_t width = getWidth(info);
    int32_t height = getHeight(info);
    if (width <= 0 || height <= 0) { deleteDecoder(decoder); dlclose(handle); return nullptr; }

    if (setFormat) {
        // Force RGBA_8888 to match AndroidBitmap
        setFormat(decoder, ANDROID_BITMAP_FORMAT_RGBA_8888);
    }

    size_t stride = getMinimumStride(decoder);
    size_t size = (size_t)height * stride;
    std::vector<uint8_t> rgba;
    rgba.resize(size);
    int decRet = decodeImage(decoder, rgba.data(), stride, size);
    deleteDecoder(decoder);
    dlclose(handle);
    if (decRet != 0) {
        return nullptr;
    }

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

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS || !pixels) {
        return nullptr;
    }
    AndroidBitmapInfo binfo;
    if (AndroidBitmap_getInfo(env, bitmap, &binfo) != ANDROID_BITMAP_RESULT_SUCCESS) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return nullptr;
    }
    if (binfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return nullptr;
    }

    // Copy row by row, as strides may differ
    const size_t srcRow = stride;
    for (int y = 0; y < height; ++y) {
        const uint8_t* src = rgba.data() + (size_t)y * srcRow;
        uint8_t* dst = (uint8_t*)pixels + (size_t)y * (size_t)binfo.stride;
        memcpy(dst, src, (size_t)width * 4);
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return bitmap;
}