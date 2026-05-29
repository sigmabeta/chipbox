# Sets the minimum version of CMake required to build your native library.
# This ensures that a certain set of CMake features is available to
# your build.

cmake_minimum_required(VERSION 3.22)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# "slopsf" -- this PSX (PSF1/PSF2) audio emulation system. The backing
# PS1/PS2 core in he/ (Neill Corlett's PSXCore0008) replaced the prior
# PSF core.
set(library_name slopsf)

# List of source files that make up slopsf.
#
# he/ is the PS1/PS2 core (PSXCore0008), run purely via the HLE IOP-
# kernel path -- no copyrighted PS2/PS1 BIOS blob. The real-BIOS path
# (hebios_data.c, he/bios.c) has been removed.
set(slopsf_SRCS
        kotlin-jni.cpp
        Psf.cpp
        slop/psx.c
        slop/hle.c
        slop/hle_ps2.c
        slop/iop.c
        slop/ioptimer.c
        slop/r3000.c
        slop/vfs.c
        slop/spu.c
        slop/spucore.c
        )

# EMU_COMPILE enables library compilation; the core needs an explicit byte
# order; HAVE_STDINT_H makes emuconfig.h use <stdint.h> fixed-width types.
add_definitions(-DEMU_COMPILE -DEMU_LITTLE_ENDIAN -DHAVE_STDINT_H)

# Shared psflib + PSF file-I/O, formerly vendored under psflib/ and common/.
add_subdirectory(
        ${CMAKE_CURRENT_SOURCE_DIR}/../native-common
        ${CMAKE_BINARY_DIR}/native-common
)

# Specifies a library name, specifies whether the library is STATIC or
# SHARED, and provides relative paths to the source code. You can
# define multiple libraries by adding multiple add_library() commands,
# and CMake builds them for you. When you build your app, Gradle
# automatically packages shared libraries with your APK.

if (EMSCRIPTEN)
    # WASM build — drop `kotlin-jni.cpp` (JNI surface, Android-only). slop/* compiles unchanged
    # since `hle_ps2_compat.h` already stubs `__android_log_print` to a no-op on non-Android
    # hosts (and now also stubs `ANDROID_LOG_*` priority constants), so no `-llog` dep is needed.
    list(REMOVE_ITEM slopsf_SRCS kotlin-jni.cpp)
    add_executable(${library_name} ${slopsf_SRCS} Psf_Web.cpp)
    set_target_properties(${library_name} PROPERTIES
            OUTPUT_NAME chipbox_psf
            SUFFIX ".js")
    target_link_libraries(${library_name} PRIVATE chipbox_native_common)
    target_link_options(${library_name} PRIVATE
            "-sMODULARIZE=1"
            "-sEXPORT_ES6=0"
            "-sEXPORT_NAME=chipboxPsf"
            "-sENVIRONMENT=web,worker"
            "-sALLOW_MEMORY_GROWTH=1"
            "-sFORCE_FILESYSTEM=1"
            "-sUSE_ZLIB=1"
            # PSF2 wavebanks for big games (Valkyrie Profile 2, FF X) cross 30 MB; PSX state
            # + audio buffers add ~5 MB. 64 MB initial keeps malloc on the happy path.
            "-sINITIAL_MEMORY=67108864"
            "-sEXPORTED_FUNCTIONS=['_malloc','_free']"
            "-sEXPORTED_RUNTIME_METHODS=['ccall','cwrap','getValue','UTF8ToString','stringToUTF8','lengthBytesUTF8','HEAP8','HEAPU8','HEAP16','HEAPU16','HEAP32','HEAPU32','FS']"
            "-O2"
    )
    target_compile_options(${library_name} PRIVATE "-O2" "-sUSE_ZLIB=1")
else ()
    add_library(${library_name} SHARED ${slopsf_SRCS})
    target_link_libraries(${library_name}
            # Shared psflib + PSF file-I/O. psflib calls into zlib to decompress PSF sections,
            # so this also links libz transitively.
            chipbox_native_common
            # __android_log_print, used by debug probes in psx_hw.c and spu/spu.c.
            log
    )
endif ()