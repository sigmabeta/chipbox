# List of source files required by libpsf and any emulators

set(libpsf_SRCS PsxBios.c
        PsxCounters.c
        PsxDma.c
        Spu.c
        PsxHw.c
        PsxMem.c
        Misc.c
        R3000A.c
        PsxInterpreter.c
        PsxHLE.c
        spu/spu.c
        android/Backend.cpp)

# Add library to be compiled.
add_library(psf SHARED ${libpsf_SRCS})

# Not sure what most of this means, but apparently it links to "android.h", "zlib.h" and "log.h"
if (ANDROID)
    target_link_libraries(psf
            samplerate
            log
            android
            z
            "-Wl,--no-warn-mismatch"
            "-Wl,--whole-archive"
            ${LIBS}
            "-Wl,--no-whole-archive"
            )
endif ()