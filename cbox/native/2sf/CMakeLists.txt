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
set(library_name twosf)

# List of source files that make up twosf
set(libtwosf_SRCS
        kotlin-jni.cpp
        2sf.cpp
        Core/vio2sf/vio2sf.c
        Core/vio2sf/desmume/FIFO.c
        Core/vio2sf/desmume/GPU.c
        Core/vio2sf/desmume/MMU.c
        Core/vio2sf/desmume/NDSSystem.c
        Core/vio2sf/desmume/SPU.cpp
        Core/vio2sf/desmume/arm_instructions.c
        Core/vio2sf/desmume/armcpu.c
        Core/vio2sf/desmume/barray.c
        Core/vio2sf/desmume/bios.c
        Core/vio2sf/desmume/cp15.c
        Core/vio2sf/desmume/isqrt.c
        Core/vio2sf/desmume/matrix.c
        Core/vio2sf/desmume/mc.c
        Core/vio2sf/desmume/resampler.c
        Core/vio2sf/desmume/state.c
        Core/vio2sf/desmume/thumb_instructions.c

        )

# Appears to just be a flag telling the code we want it to compile...?
add_definitions(
        -DEMU_COMPILE
        -DEMU_LITTLE_ENDIAN
)

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
    # WASM build — drop `kotlin-jni.cpp` (Android-only JNI surface). psflib's `fopen` chain-file
    # loader resolves against Emscripten MEMFS, populated per-track by `WasmTwosfEmulator`.
    # NDS state + sample buffers are modest (~1 MB peak); the default initial heap is enough.
    list(REMOVE_ITEM libtwosf_SRCS kotlin-jni.cpp)
    add_executable(${library_name} ${libtwosf_SRCS} Twosf_Web.cpp)
    set_target_properties(${library_name} PROPERTIES
            OUTPUT_NAME chipbox_twosf
            SUFFIX ".js")
    target_link_libraries(${library_name} PRIVATE chipbox_native_common)
    target_link_options(${library_name} PRIVATE
            "-sMODULARIZE=1"
            "-sEXPORT_ES6=0"
            "-sEXPORT_NAME=chipboxTwosf"
            "-sENVIRONMENT=web,worker"
            "-sALLOW_MEMORY_GROWTH=1"
            "-sFORCE_FILESYSTEM=1"
            "-sUSE_ZLIB=1"
            "-sEXPORTED_FUNCTIONS=['_malloc','_free']"
            "-sEXPORTED_RUNTIME_METHODS=['ccall','cwrap','getValue','UTF8ToString','stringToUTF8','lengthBytesUTF8','HEAP8','HEAPU8','HEAP16','HEAPU16','HEAP32','HEAPU32','FS']"
            "-O2"
    )
    target_compile_options(${library_name} PRIVATE "-O2" "-sUSE_ZLIB=1")
else ()
    add_library(${library_name} SHARED ${libtwosf_SRCS})
    target_link_libraries(${library_name}
            # Shared psflib + PSF file-I/O. psflib calls into zlib to decompress PSF sections,
            # so this also links libz transitively.
            chipbox_native_common
    )
endif ()