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

if (EMSCRIPTEN)
    # WASM build — drop `kotlin-jni.cpp` (JNI surface). vgmstream's libstreamfile uses stdio
    # `fopen`, which resolves against Emscripten MEMFS. `WasmVgmstreamEmulator` mirrors the
    # staged track dir into MEMFS before calling `chipbox_vgmstream_load_file`. INITIAL_MEMORY
    # bumped because some vgmstream formats (FSB banks, large HCA streams) decode multi-MB
    # bitstreams in one shot.
    add_executable(${library_name}
            chipbox_vgmstream.cpp Vgmstream_Web.cpp
    )
    set_target_properties(${library_name} PROPERTIES
            OUTPUT_NAME chipbox_vgmstream
            SUFFIX ".js")
    target_include_directories(${library_name} PRIVATE
            ${VG}/src
            ${CMAKE_CURRENT_SOURCE_DIR}/../native-common/include
    )
    target_link_libraries(${library_name} PRIVATE vgmcore m)
    target_link_options(${library_name} PRIVATE
            "-sMODULARIZE=1"
            "-sEXPORT_ES6=0"
            "-sEXPORT_NAME=chipboxVgmstream"
            "-sENVIRONMENT=web,worker"
            "-sALLOW_MEMORY_GROWTH=1"
            "-sFORCE_FILESYSTEM=1"
            "-sINITIAL_MEMORY=33554432"
            "-sEXPORTED_FUNCTIONS=['_malloc','_free']"
            "-sEXPORTED_RUNTIME_METHODS=['ccall','cwrap','getValue','UTF8ToString','stringToUTF8','lengthBytesUTF8','HEAP8','HEAPU8','HEAP16','HEAPU16','HEAP32','HEAPU32','FS']"
            "-O2"
    )
    target_compile_options(${library_name} PRIVATE "-O2")
else ()
    # The chipbox JNI wrapper. Pulls the vendored libvgmstream.h (Core/vgmstream/src) and the
    # shared JNI bridge header (native-common/include); links the core static lib + libm.
    add_library(${library_name} SHARED
            chipbox_vgmstream.cpp
            kotlin-jni.cpp
    )

    target_include_directories(${library_name} PRIVATE
            ${VG}/src
            ${CMAKE_CURRENT_SOURCE_DIR}/../native-common/include
    )

    target_link_libraries(${library_name} vgmcore m)
endif ()
