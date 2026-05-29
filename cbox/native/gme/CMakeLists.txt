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
set(library_name gme)

# libgme itself — vendored sources used by both the JNI/host build (apps/jvm, Android) and the
# Emscripten/WASM build (apps/js). The platform-specific wrappers (kotlin-jni.cpp + Gme.cpp for
# JNI; Gme_Web.cpp for WASM) are conditional below.
set(libgme_SRCS
        gme/Blip_Buffer.cpp
        gme/Classic_Emu.cpp
        gme/Data_Reader.cpp
        gme/Dual_Resampler.cpp
        gme/Effects_Buffer.cpp
        gme/Fir_Resampler.cpp
        gme/gme.cpp
        gme/Gme_File.cpp
        gme/M3u_Playlist.cpp
        gme/Multi_Buffer.cpp
        gme/Music_Emu.cpp

        # Game Boy
        gme/Gb_Apu.cpp
        gme/Gb_Cpu.cpp
        gme/Gb_Oscs.cpp
        gme/Gbs_Emu.cpp

        # NES
        gme/Nes_Apu.cpp
        gme/Nes_Cpu.cpp
        gme/Nes_Fme7_Apu.cpp
        gme/Nes_Namco_Apu.cpp
        gme/Nes_Oscs.cpp
        gme/Nes_Vrc6_Apu.cpp
        gme/Nsf_Emu.cpp
        gme/Nsfe_Emu.cpp

        # Super NES
        gme/Snes_Spc.cpp
        gme/Spc_Cpu.cpp
        gme/Spc_Dsp.cpp
        gme/Spc_Emu.cpp
        gme/Spc_Filter.cpp
        )

# Appears to just be a flag telling the code we want it to compile...?
add_definitions(-DEMU_COMPILE -DEMU_LITTLE_ENDIAN)

if (EMSCRIPTEN)
    # WASM build: the browser has no JNI and no filesystem, so we use the byte-buffer wrapper
    # (Gme_Web.cpp → gme_open_data) instead of the JNI shim (kotlin-jni.cpp + Gme.cpp →
    # gme_load_file). No PSF/zlib glue — gme isn't a PSF format. Emit a single self-contained
    # `.js`+`.wasm` pair the Kotlin/JS bindings can `require()`.
    add_executable(${library_name} ${libgme_SRCS} Gme_Web.cpp)
    set_target_properties(${library_name} PROPERTIES
            OUTPUT_NAME chipbox_gme
            SUFFIX ".js")
    target_link_options(${library_name} PRIVATE
            "-sMODULARIZE=1"
            "-sEXPORT_ES6=0"
            "-sEXPORT_NAME=chipboxGme"
            "-sENVIRONMENT=web,worker"
            "-sALLOW_MEMORY_GROWTH=1"
            "-sEXPORTED_FUNCTIONS=['_malloc','_free']"
            "-sEXPORTED_RUNTIME_METHODS=['ccall','cwrap','getValue','HEAP8','HEAPU8','HEAP16','HEAPU16','HEAP32','HEAPU32']"
            "-Oz"
    )
    target_compile_options(${library_name} PRIVATE "-Oz")
else ()
    # JNI / host shared library — the existing Android NDK + apps/jvm host-CMake build path.
    list(APPEND libgme_SRCS kotlin-jni.cpp Gme.cpp)

    # Shared header-only JNI bridge (no psflib: gme is not a PSF format).
    add_subdirectory(
            ${CMAKE_CURRENT_SOURCE_DIR}/../native-common
            ${CMAKE_BINARY_DIR}/native-common
    )

    add_library(${library_name} SHARED ${libgme_SRCS})

    target_link_libraries(${library_name}
            # PSFLib wants to know how to unzip stuff.
            z
            # Shared header-only JNI bridge.
            chipbox_jni_bridge
    )
endif ()