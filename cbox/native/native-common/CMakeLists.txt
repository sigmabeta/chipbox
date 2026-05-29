# Shared native code for all Chipbox emulators: the vendored psflib/psf2fs
# (byte-identical across every PSF-derived emulator) plus the PSF file-I/O
# callback layer. Consumed via add_subdirectory + linking chipbox_native_common.

cmake_minimum_required(VERSION 3.22)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if (EMSCRIPTEN)
    # WASM build — no JNI surface. The `chipbox_jni_bridge` header includes <jni.h>, which
    # Emscripten's sysroot doesn't ship; the WASM entry points use plain `extern "C"` exports
    # in each emulator's `<Name>_Web.cpp` instead. `chipbox_psf_io.cpp`'s `fopen`/`fread` calls
    # resolve against Emscripten's MEMFS — the Kotlin layer writes the staged chain files into
    # MEMFS via `FS.writeFile` before each `loadTrackInternal`.
    add_library(chipbox_native_common STATIC
            psflib/psflib.c
            psflib/psf2fs.c
            chipbox_psf_io.cpp
    )
    target_include_directories(chipbox_native_common PUBLIC
            ${CMAKE_CURRENT_SOURCE_DIR}/psflib
            ${CMAKE_CURRENT_SOURCE_DIR}/include
    )
    # zlib is provided per-executable via `-sUSE_ZLIB=1` link option; nothing to wire here.
else ()
    # Header-only shared JNI bridge. Every emulator links this (the 5 PSF-based ones get it
    # transitively via chipbox_native_common below).
    add_library(chipbox_jni_bridge INTERFACE)
    target_include_directories(chipbox_jni_bridge INTERFACE
            ${CMAKE_CURRENT_SOURCE_DIR}/include
    )

    add_library(chipbox_native_common STATIC
            psflib/psflib.c
            psflib/psf2fs.c
            chipbox_psf_io.cpp
    )

    # PUBLIC so consumers can #include <psflib.h>, <psf2fs.h>, <chipbox_psf_io.h> and
    # <chipbox_jni_bridge.h> without knowing this module's layout.
    target_include_directories(chipbox_native_common PUBLIC
            ${CMAKE_CURRENT_SOURCE_DIR}/psflib
            ${CMAKE_CURRENT_SOURCE_DIR}/include
    )

    # psflib uses zlib; PUBLIC so the consuming .so links it too. The interface bridge target
    # is propagated so PSF consumers get the bridge header as well.
    target_link_libraries(chipbox_native_common PUBLIC z chipbox_jni_bridge)
endif ()
