cmake_minimum_required(VERSION 3.22)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(library_name vgm)

# --- libvgm setup ---
# libvgm's per-subdir CMakeLists call pkgcfg_configure() from cmake/utils.cmake
# and resolve modules under libs/cmake_modules. They also rely on these vars
# being set in the parent scope.
list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/libs/cmake_modules/")
include(GNUInstallDirs)
include("${CMAKE_CURRENT_SOURCE_DIR}/cmake/utils.cmake")
set(LIBVGM_NAME "libvgm")
set(LIBVGM_VERSION "0.1")
set(LIBVGM_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}")
set(LIBVGM_INSTALL_INCLUDE_DIR "${CMAKE_INSTALL_INCLUDEDIR}/vgm")
set(LIBVGM_INSTALL_TARGETS)
set(LIBVGM_DEPS)

# libvgm options. Everything off except what we need: static libs, zlib loaders,
# all chip cores. No audio drivers, no threading, no charset conversion (so we
# don't need iconv) — GD3 metadata is handled by Kotlin VgmReader, not here.
set(LIBRARY_TYPE STATIC CACHE STRING "" FORCE)
set(BUILD_LIBAUDIO OFF CACHE BOOL "" FORCE)
set(BUILD_PLAYER OFF CACHE BOOL "" FORCE)
set(BUILD_VGM2WAV OFF CACHE BOOL "" FORCE)
set(AUDIODRV_WAVEWRITE OFF CACHE BOOL "" FORCE)
set(UTIL_THREADING OFF CACHE BOOL "" FORCE)
set(UTIL_CHARSET_CONV OFF CACHE BOOL "" FORCE)
set(UTIL_LOADERS ON CACHE BOOL "" FORCE)
set(SNDEMU__ALL ON CACHE BOOL "" FORCE)

# Defines required by libvgm source files.
add_definitions(
        -DVGM_LITTLE_ENDIAN
        -DEMU_LITTLE_ENDIAN
        -DEMU_COMPILE
        -DHAVE_STDINT_H
)

# Build libvgm static libraries. Order matters: utils first, then emu, then
# player (which depends on emu+utils). We skip audio/ entirely.
add_subdirectory(utils)
add_subdirectory(emu)
add_subdirectory(player)

# Shared header-only JNI bridge (no psflib: vgm is not a PSF format).
add_subdirectory(
        ${CMAKE_CURRENT_SOURCE_DIR}/../native-common
        ${CMAKE_BINARY_DIR}/native-common
)

# Our shared library that Android loads via System.loadLibrary("vgm").
add_library(${library_name} SHARED
        kotlin-jni.cpp
        Vgm.cpp
        cpconv_stub.c
)

target_link_libraries(${library_name} PRIVATE
        vgm-player
        vgm-emu
        vgm-utils
        z
        chipbox_jni_bridge
)
