# Sets the minimum version of CMake required to build your native library.
# This ensures that a certain set of CMake features is available to
# your build.

cmake_minimum_required(VERSION 3.22)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Name the library.
set(library_name ssf)

# List of source files that make up ssf
set(libssf_SRCS
        kotlin-jni.cpp
        Ssf.cpp
        Core/arm.c
        Core/dcsound.c
        Core/satsound.c
        Core/sega.c
        Core/yam.c
        Core/m68k/m68kops.c
        Core/m68k/m68kcpu.c
        )

# Appears to just be a flag telling the code we want it to compile...?
add_definitions(
        -DEMU_COMPILE
        -DEMU_LITTLE_ENDIAN
        -DHAVE_STDINT_H
        -DUSE_M68K
        -DLSB_FIRST
        -DHAVE_MPROTECT
)

# Shared psflib + PSF file-I/O, formerly vendored under psflib/ and common/.
add_subdirectory(
        ${CMAKE_CURRENT_SOURCE_DIR}/../native-common
        ${CMAKE_BINARY_DIR}/native-common
)

if (EMSCRIPTEN)
    # WASM build — `Ssf.cpp` keeps its existing `loadFile(const char*)` C API; psflib's
    # internal `fopen` calls resolve against Emscripten's MEMFS. zlib comes from `-sUSE_ZLIB=1`
    # at link time. The thin `Ssf_Web.cpp` shim provides `EMSCRIPTEN_KEEPALIVE` exports.
    add_executable(${library_name}
            Ssf.cpp Ssf_Web.cpp
            Core/arm.c Core/dcsound.c Core/satsound.c Core/sega.c Core/yam.c
            Core/m68k/m68kops.c Core/m68k/m68kcpu.c
    )
    set_target_properties(${library_name} PROPERTIES
            OUTPUT_NAME chipbox_ssf
            SUFFIX ".js")
    target_link_libraries(${library_name} PRIVATE chipbox_native_common)
    target_link_options(${library_name} PRIVATE
            "-sMODULARIZE=1"
            "-sEXPORT_ES6=0"
            "-sEXPORT_NAME=chipboxSsf"
            "-sENVIRONMENT=web,worker"
            "-sALLOW_MEMORY_GROWTH=1"
            "-sFORCE_FILESYSTEM=1"
            "-sUSE_ZLIB=1"
            "-sEXPORTED_FUNCTIONS=['_malloc','_free']"
            "-sEXPORTED_RUNTIME_METHODS=['ccall','cwrap','getValue','UTF8ToString','stringToUTF8','lengthBytesUTF8','HEAP8','HEAPU8','HEAP16','HEAPU16','HEAP32','HEAPU32','FS']"
            "-Oz"
    )
    target_compile_options(${library_name} PRIVATE "-Oz" "-sUSE_ZLIB=1")
else ()
    add_library(${library_name} SHARED ${libssf_SRCS})
    target_link_libraries(${library_name}
            # Shared psflib + PSF file-I/O. psflib calls into zlib to decompress PSF sections,
            # so this also links libz transitively.
            chipbox_native_common
    )
endif ()