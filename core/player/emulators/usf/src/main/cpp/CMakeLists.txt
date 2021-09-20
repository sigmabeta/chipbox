# Sets the minimum version of CMake required to build your native library.
# This ensures that a certain set of CMake features is available to
# your build.

cmake_minimum_required(VERSION 3.10)

# Name the library.
set(library_name usf)

# List of source files that make up usf
set(libusf_SRCS
        kotlin-jni.cpp
        Usf.cpp
        common/common.cpp
        psflib/psf2fs.c
        psflib/psflib.c
        lazyusf2/api/callbacks.c
        lazyusf2/debugger/dbg_decoder.c
        lazyusf2/device/cart/cart_rom.c
        lazyusf2/device/controllers/game_controller.c
        lazyusf2/device/memory/memory.c
        lazyusf2/device/pif//pif.c
        lazyusf2/device/pif/cic.c
        lazyusf2/device/pif/n64_cic_nus_6105.c
        lazyusf2/device/r4300/cached_interp.c
        lazyusf2/device/r4300/cp0.c
        lazyusf2/device/r4300/cp1.c
        lazyusf2/device/r4300/interrupt.c
        lazyusf2/device/r4300/pure_interp.c
        lazyusf2/device/r4300/r4300_core.c
        lazyusf2/device/r4300/recomp.c
        lazyusf2/device/r4300/tlb.c
        lazyusf2/device/rcp/ai/ai_controller.c
        lazyusf2/device/rcp/mi/mi_controller.c
        lazyusf2/device/rcp/pi/pi_controller.c
        lazyusf2/device/rcp/rdp/rdp_core.c
        lazyusf2/device/rcp/ri/ri_controller.c
        lazyusf2/device/rcp/rsp/rsp_core.c
        lazyusf2/device/rcp/si/si_controller.c
        lazyusf2/device/rcp/vi/vi_controller.c
        lazyusf2/device/rdram/rdram.c
        lazyusf2/main/main.c
        lazyusf2/main/rom.c
        lazyusf2/main/savestates.c
        lazyusf2/main/util.c
        lazyusf2/rsp_hle/alist.c
        lazyusf2/rsp_hle/alist_audio.c
        lazyusf2/rsp_hle/alist_naudio.c
        lazyusf2/rsp_hle/alist_nead.c
        lazyusf2/rsp_hle/audio.c
        lazyusf2/rsp_hle/cicx105.c
        lazyusf2/rsp_hle/hle.c
        lazyusf2/rsp_hle/jpeg.c
        lazyusf2/rsp_hle/memory.c
        lazyusf2/rsp_hle/mp3.c
        lazyusf2/rsp_hle/musyx.c
        lazyusf2/rsp_hle/plugin.c
        lazyusf2/usf/barray.c
        lazyusf2/usf/resampler.c
        lazyusf2/usf/usf.c
        lazyusf2/usf/usf.c
        )

# Appears to just be a flag telling the code we want it to compile...?
add_definitions(-DEMU_COMPILE -DEMU_LITTLE_ENDIAN)

set(INCLUDE_FOLDER lazyusf2)

# Specifies a library name, specifies whether the library is STATIC or
# SHARED, and provides relative paths to the source code. You can
# define multiple libraries by adding multiple add_library() commands,
# and CMake builds them for you. When you build your app, Gradle
# automatically packages shared libraries with your APK.

add_library( # Specifies the name of the library.
        ${library_name}

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        ${libusf_SRCS}
        )

target_include_directories(${library_name} PRIVATE lazyusf2)

# Libraries must be listed in here to be accessible in C/C++ code.
target_link_libraries(
        ${library_name}

        # PSFLib wants to know how to unzip stuff.
        z
)