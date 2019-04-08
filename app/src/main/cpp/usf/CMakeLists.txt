# List of source files required by libusf and any emulators

set(libusf_SRCS)

include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# Add library to be compiled.
add_library(usf SHARED ${libusf_SRCS}
        android/Backend.cpp
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
        r4300/empty_dynarec.c
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
        vi/vi_controller.c)

# Not sure what most of this means, but apparently it links to "android.h", "zlib.h" and "log.h"
if (ANDROID)
    target_link_libraries(usf
            log
            android
            z
            psflib
            "-Wl,--no-warn-mismatch"
            "-Wl,--whole-archive"
            ${LIBS}
            "-Wl,--no-whole-archive"
            )
endif ()