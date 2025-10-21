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

target_link_libraries(
        webp_jni
        ${LINK_LIBS})