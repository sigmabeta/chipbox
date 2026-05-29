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

if (EMSCRIPTEN)
    # WASM build: no JNI, no filesystem. Loads from a memory buffer instead of `fopen(path)`
    # via libvgm's `MemoryLoader_Init`. zlib comes from `-sUSE_ZLIB=1` (Emscripten ports);
    # libvgm's util/ links against it for compressed-VGZ support.
    add_executable(${library_name} Vgm_Web.cpp cpconv_stub.c)
    set_target_properties(${library_name} PROPERTIES
            OUTPUT_NAME chipbox_vgm
            SUFFIX ".js")
    target_link_libraries(${library_name} PRIVATE vgm-player vgm-emu vgm-utils)
    target_link_options(${library_name} PRIVATE
            "-sMODULARIZE=1"
            "-sEXPORT_ES6=0"
            "-sEXPORT_NAME=chipboxVgm"
            "-sENVIRONMENT=web,worker"
            "-sALLOW_MEMORY_GROWTH=1"
            "-sUSE_ZLIB=1"
            "-sEXPORTED_FUNCTIONS=['_malloc','_free']"
            "-sEXPORTED_RUNTIME_METHODS=['ccall','cwrap','getValue','UTF8ToString','stringToUTF8','lengthBytesUTF8','HEAP8','HEAPU8','HEAP16','HEAPU16','HEAP32','HEAPU32']"
            "-Oz"
    )
    target_compile_options(${library_name} PRIVATE "-Oz" "-sUSE_ZLIB=1")
else ()
    # JNI / host shared library — the existing Android NDK + apps/jvm host-CMake build path.
    add_subdirectory(
            ${CMAKE_CURRENT_SOURCE_DIR}/../native-common
            ${CMAKE_BINARY_DIR}/native-common
    )
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
endif ()
