cmake_minimum_required(VERSION 3.22.1)
project(webp_jni)

add_library(
        webp_jni
        SHARED
        webp_jni.cpp)

find_library(
        log-lib
        log)

find_library(
        jnigraphics-lib
        jnigraphics)

# Optionally link a prebuilt libwebp if present in jniLibs
set(WEBP_PREBUILT "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libwebp.so")
set(LINK_LIBS ${log-lib} ${jnigraphics-lib})
if(EXISTS ${WEBP_PREBUILT})
    add_library(webp SHARED IMPORTED)
    set_target_properties(webp PROPERTIES IMPORTED_LOCATION ${WEBP_PREBUILT})
    list(APPEND LINK_LIBS webp)
endif()

add_library(png_jni SHARED png_jni.cpp)
find_library(z-lib z)

target_link_libraries(
        webp_jni
        ${LINK_LIBS})

target_link_libraries(png_jni ${log-lib} ${jnigraphics-lib} ${z-lib})

# --- JPEG JNI (libturbojpeg) ---
# Optionally link a prebuilt libturbojpeg if present in jniLibs
set(TURBOJPEG_PREBUILT "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libturbojpeg.so")
set(JPEG_LINK_LIBS ${log-lib} ${jnigraphics-lib})
if(EXISTS ${TURBOJPEG_PREBUILT})
    add_library(turbojpeg SHARED IMPORTED)
    set_target_properties(turbojpeg PROPERTIES IMPORTED_LOCATION ${TURBOJPEG_PREBUILT})
    list(APPEND JPEG_LINK_LIBS turbojpeg)
endif()

add_library(jpeg_jni SHARED jpeg_jni.cpp)

target_link_libraries(jpeg_jni ${JPEG_LINK_LIBS})

# --- DNG JNI (libraw) ---
# Do not link libraw directly; load at runtime via dlopen

set(DNG_LINK_LIBS ${log-lib} ${jnigraphics-lib})
add_library(dng_jni SHARED dng_jni.cpp)

target_link_libraries(dng_jni ${DNG_LINK_LIBS})