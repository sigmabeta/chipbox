cmake_minimum_required(VERSION 3.22)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(library_name vgmstream)

# Vendored vgmstream tree. Built "core only": no VGM_USE_* defines, so every codec that needs an
# external library (FFmpeg, Vorbis, MPEG, ATRAC9, CELT, Speex, G.719) #ifdefs itself out. This
# still covers hundreds of formats whose codecs are built in (PCM, ADX, HCA, NGC_DSP/BRSTM, the
# IMA/MSADPCM/PSX/XA families, STRM, FSB, ...).
set(VG ${CMAKE_CURRENT_SOURCE_DIR}/Core/vgmstream)

# CONFIGURE_DEPENDS so CMake re-globs when files are added/removed (e.g. trimming unused codec
# sources, or an upstream resync) instead of building a stale cached file list.
file(GLOB vgmcore_SRCS CONFIGURE_DEPENDS
        ${VG}/src/base/*.c
        ${VG}/src/coding/*.c
        ${VG}/src/coding/libs/*.c
        ${VG}/src/layout/*.c
        ${VG}/src/meta/*.c
        ${VG}/src/util/*.c
        ${VG}/src/*.c
        )

add_library(vgmcore STATIC ${vgmcore_SRCS})
target_include_directories(vgmcore PRIVATE ${VG}/src ${VG}/ext_includes)
target_compile_definitions(vgmcore PRIVATE VGM_LOG_OUTPUT)

# The chipbox JNI wrapper. Pulls the vendored libvgmstream.h (Core/vgmstream/src) and the shared
# JNI bridge header (native-common/include); links the core static lib + libm.
add_library(${library_name} SHARED
        chipbox_vgmstream.cpp
        kotlin-jni.cpp
        )

target_include_directories(${library_name} PRIVATE
        ${VG}/src
        ${CMAKE_CURRENT_SOURCE_DIR}/../native-common/include
        )

target_link_libraries(${library_name} vgmcore m)
