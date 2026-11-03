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
    #
    # Optimization is throughput-oriented and uniform across the target: the core renders 44.1 kHz
    # audio in real time in the browser, so size-for-speed is the wrong trade. Measured across six
    # browser scenarios (ticket 10), all-`-O3` renders 21.9% faster than the mixed `-Oz` core /
    # `-O3` common build (DSP-heavy tracks 21–33% faster) for +271,867 bytes (+88.9%) of `.wasm`.
    # `chipbox_native_common` already builds `-O3` from `CMAKE_BUILD_TYPE=Release`, so `-O3` here
    # makes the whole target one size class.
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
            "-O3"
    )
    target_compile_options(${library_name} PRIVATE "-O3" "-sUSE_ZLIB=1")
else ()
    add_library(${library_name} SHARED ${libssf_SRCS})
    target_link_libraries(${library_name}
            # Shared psflib + PSF file-I/O. psflib calls into zlib to decompress PSF sections,
            # so this also links libz transitively.
            chipbox_native_common
    )
endif ()

# --- Symbol visibility (JNI/host builds only) ---
#
# The `.so`/`.dylib`/`.dll` surface is exactly the `Java_*` JNI entry points (JNIEXPORT, i.e.
# default visibility). Everything else — the emulator core (`yam_*`, `sega_*`, ...), the JNI-free
# C++ helpers in Ssf.cpp and psflib in chipbox_native_common — is internal, so hide it: that also
# makes every intra-library call non-preemptible, which is what turns the internal PLT calls into
# direct calls. The WASM build is deliberately excluded: its entry points are
# `EMSCRIPTEN_KEEPALIVE` (default-visibility) symbols and ticket 10 owns that branch.
if (NOT EMSCRIPTEN)
    foreach (visibility_target ${library_name} chipbox_native_common)
        set_target_properties(${visibility_target} PROPERTIES
                C_VISIBILITY_PRESET hidden
                CXX_VISIBILITY_PRESET hidden
                VISIBILITY_INLINES_HIDDEN YES)
    endforeach ()
    # LTO was evaluated here and rejected: on the host (GCC -flto) it changed neither RTF nor wall
    # time beyond run-to-run noise, and on the NDK (clang -flto=thin) it grew `.text` by 32% — an
    # i-cache risk that cannot be measured without a device. See the ticket's Comments for numbers.
endif ()
