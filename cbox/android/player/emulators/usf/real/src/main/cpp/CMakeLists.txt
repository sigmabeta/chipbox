cmake_minimum_required(VERSION 3.22)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(library_name usf)

# Android is ARM: the x86/x86_64 recompilers and new_dynarec are unusable here
# (see README.md), so we build the cached-interpreter configuration. This source
# list mirrors the OBJS in Emscripten.Makefile, the canonical non-x86 build.
set(usf_SRCS
        kotlin-jni.cpp
        Usf.cpp
        common.cpp
        psflib/psflib.c

        r4300/empty_dynarec.c
        ai/ai_controller.c
        api/callbacks.c
        debugger/dbg_decoder.c
        main/main.c
        main/rom.c
        main/savestates.c
        main/util.c
        memory/memory.c
        pi/cart_rom.c
        pi/pi_controller.c
        r4300/cached_interp.c
        r4300/cp0.c
        r4300/cp1.c
        r4300/exception.c
        r4300/interupt.c
        r4300/mi_controller.c
        r4300/pure_interp.c
        r4300/r4300.c
        r4300/r4300_core.c
        r4300/recomp.c
        r4300/reset.c
        r4300/tlb.c
        rdp/rdp_core.c
        ri/rdram.c
        ri/rdram_detection_hack.c
        ri/ri_controller.c
        rsp/rsp_core.c
        rsp_hle/alist.c
        rsp_hle/alist_audio.c
        rsp_hle/alist_naudio.c
        rsp_hle/alist_nead.c
        rsp_hle/audio.c
        rsp_hle/cicx105.c
        rsp_hle/hle.c
        rsp_hle/jpeg.c
        rsp_hle/memory.c
        rsp_hle/mp3.c
        rsp_hle/musyx.c
        rsp_hle/plugin.c
        rsp_lle/rsp.c
        si/cic.c
        si/game_controller.c
        si/n64_cic_nus_6105.c
        si/pif.c
        si/si_controller.c
        usf/usf.c
        usf/barray.c
        usf/resampler.c
        vi/vi_controller.c
        )

# Mirrors Emscripten.Makefile CFLAGS. ARCH_MIN_SSE2 is intentionally NOT set
# (that is an x86-only fast path).
add_definitions(-DEMU_COMPILE -DEMU_LITTLE_ENDIAN -DHAVE_STDINT_H -DNO_DEBUG_LOGS)
add_compile_options(-fno-strict-aliasing -Wno-pointer-sign)

include_directories(
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/psflib
)

add_library(${library_name} SHARED ${usf_SRCS})

target_link_libraries(
        ${library_name}

        # PSFLib wants to know how to unzip stuff.
        z
)
