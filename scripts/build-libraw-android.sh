#!/usr/bin/env bash
set -euo pipefail

# Build LibRaw as Android shared library (libraw.so) and install into app jniLibs
# Usage:
#   ./scripts/build-libraw-android.sh [arm64-v8a|armeabi-v7a|x86_64|--all]
# Default ABI: arm64-v8a

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
THIRD_PARTY_DIR="$ROOT_DIR/third_party"
SRC_DIR="$THIRD_PARTY_DIR/libraw-src"
BUILD_DIR_BASE="$ROOT_DIR/build/libraw"
JNI_LIBS_DIR_BASE="$ROOT_DIR/app/src/main/jniLibs"
LIBRAW_CMAKE_DIR="$THIRD_PARTY_DIR/libraw-cmake"

# Detect Android NDK path
detect_ndk() {
  if [[ -n "${ANDROID_NDK_HOME:-}" && -d "$ANDROID_NDK_HOME" ]]; then
    echo "$ANDROID_NDK_HOME"
    return 0
  fi
  local ndk_root
  if [[ -n "${ANDROID_HOME:-}" && -d "$ANDROID_HOME/ndk" ]]; then
    ndk_root="$ANDROID_HOME/ndk"
  else
    ndk_root="$HOME/Library/Android/sdk/ndk"
  fi
  if [[ -d "$ndk_root" ]]; then
    local latest
    latest=$(ls -1 "$ndk_root" | sort -V | tail -n1)
    echo "$ndk_root/$latest"
    return 0
  fi
  echo "ERROR: Android NDK not found. Please set ANDROID_NDK_HOME or install NDK in Android SDK." >&2
  exit 1
}

# Detect CMake binary
detect_cmake() {
  local sdk_cmake_root="$HOME/Library/Android/sdk/cmake"
  if [[ -d "$sdk_cmake_root" ]]; then
    local candidate
    candidate=$(ls -1d "$sdk_cmake_root"/*/bin/cmake 2>/dev/null | sort -V | tail -n1 || true)
    if [[ -x "$candidate" ]]; then
      echo "$candidate"
      return 0
    fi
  fi
  if command -v cmake >/dev/null 2>&1; then
    command -v cmake
    return 0
  fi
  echo "ERROR: CMake not found. Please install Android SDK CMake." >&2
  exit 1
}

NDK_DIR="$(detect_ndk)"
CMAKE_BIN="$(detect_cmake)"
TOOLCHAIN_FILE="$NDK_DIR/build/cmake/android.toolchain.cmake"
ANDROID_PLATFORM="android-24"

# Fetch LibRaw sources and LibRaw-cmake if not present
mkdir -p "$THIRD_PARTY_DIR"
if [[ ! -d "$SRC_DIR" ]]; then
  echo "Fetching LibRaw sources..."
  cd "$THIRD_PARTY_DIR"
  curl -L -o LibRaw-0.21.3.tar.gz https://github.com/LibRaw/LibRaw/archive/refs/tags/0.21.3.tar.gz
  tar -xzf LibRaw-0.21.3.tar.gz
  mv LibRaw-0.21.3 libraw-src
  echo "LibRaw sources in: $SRC_DIR"
fi
if [[ ! -d "$LIBRAW_CMAKE_DIR" ]]; then
  echo "Fetching LibRaw-cmake scripts..."
  cd "$THIRD_PARTY_DIR"
  curl -L -o LibRaw-cmake.tar.gz https://github.com/LibRaw/LibRaw-cmake/archive/refs/heads/master.tar.gz
  tar -xzf LibRaw-cmake.tar.gz
  mv LibRaw-cmake-master libraw-cmake
  echo "LibRaw-cmake in: $LIBRAW_CMAKE_DIR"
fi

build_abi() {
  local ABI="$1"
  local BUILD_DIR="$BUILD_DIR_BASE-$ABI"
  local JNI_LIBS_DIR="$JNI_LIBS_DIR_BASE/$ABI"
  mkdir -p "$BUILD_DIR"
  cd "$BUILD_DIR"
  echo "Configuring LibRaw (via LibRaw-cmake) for ABI=$ABI..."
  "$CMAKE_BIN" \
    -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN_FILE" \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM="$ANDROID_PLATFORM" \
    -DBUILD_SHARED_LIBS=ON \
    -DCMAKE_BUILD_TYPE=Release \
    -DLIBRAW_PATH="$SRC_DIR" \
    -DENABLE_OPENMP=ON \
    "$LIBRAW_CMAKE_DIR"
  echo "Building LibRaw for ABI=$ABI..."
  "$CMAKE_BIN" --build . --config Release -j
  # Find produced libraw.so (or libraw_r.so) and install under jniLibs
  local SO_PATH
  SO_PATH=$(find "$BUILD_DIR" -name 'libraw.so' | head -n1 || true)
  if [[ -z "$SO_PATH" ]]; then
    SO_PATH=$(find "$BUILD_DIR" -name 'libraw_r.so' | head -n1 || true)
  fi
  if [[ -z "$SO_PATH" ]]; then
    SO_PATH=$(find "$BUILD_DIR" -name 'libraw*.so' | head -n1 || true)
  fi
  if [[ -z "$SO_PATH" ]]; then
    echo "ERROR: libraw(.so) not found for ABI=$ABI" >&2
    exit 1
  fi
  mkdir -p "$JNI_LIBS_DIR"
  cp "$SO_PATH" "$JNI_LIBS_DIR/libraw.so"
  echo "Installed: $JNI_LIBS_DIR/libraw.so (from $(basename "$SO_PATH"))"
  # Optionally install OpenMP runtime libomp.so for this ABI
  # Install correct Android OpenMP runtime for ABI
  local arch_dir=""
  case "$ABI" in
    arm64-v8a) arch_dir="aarch64" ;;
    armeabi-v7a) arch_dir="arm" ;;
    x86_64) arch_dir="x86_64" ;;
    x86) arch_dir="i386" ;;
  esac
  local OMP_PATH=""
  if [[ -n "$arch_dir" ]]; then
    # Typical NDK 26 layout: toolchains/llvm/prebuilt/<host>/lib64/clang/<ver>/lib/linux/<arch>/libomp.so
    OMP_PATH=$(ls "$NDK_DIR"/toolchains/llvm/prebuilt/*/lib64/clang/*/lib/linux/"$arch_dir"/libomp.so 2>/dev/null | head -n1 || true)
  fi
  if [[ -z "$OMP_PATH" ]]; then
    # Fallback: search by arch directory name anywhere under NDK
    OMP_PATH=$(find "$NDK_DIR" -type f -name 'libomp.so' -path "*linux*/$arch_dir/*" | head -n1 || true)
  fi
  if [[ -z "$OMP_PATH" ]]; then
    echo "WARNING: libomp.so not found for ABI=$ABI in NDK; OpenMP decode speedups may not load."
  else
    cp "$OMP_PATH" "$JNI_LIBS_DIR/libomp.so"
    echo "Installed: $JNI_LIBS_DIR/libomp.so (from $OMP_PATH)"
  fi
}

ABI_ARG="${1:-arm64-v8a}"
if [[ "$ABI_ARG" == "--all" ]]; then
  ABIS=(arm64-v8a armeabi-v7a x86_64)
else
  ABIS=($ABI_ARG)
fi

for abi in "${ABIS[@]}"; do
  build_abi "$abi"
done

echo "Done. You can now build the app: ./gradlew :app:assembleDebug"