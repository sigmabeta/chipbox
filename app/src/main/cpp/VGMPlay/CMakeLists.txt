# List of source files required by libvgm and any emulators

AUX_SOURCE_DIRECTORY(. VGM_SRCS)
AUX_SOURCE_DIRECTORY(chips CHIPS_SRCS)
set(libvgm_SRCS ${CHIPS_SRCS}
                ${VGM_SRCS}
                android/Scanner.cpp
                android/Backend.cpp )

# Add library to be compiled.
add_library(vgm SHARED ${libvgm_SRCS})

# Not sure what most of this means, but apparently it links to "android.h", "zlib.h" and "log.h"
if(ANDROID)
    target_link_libraries(vgm
        log
        android
        z
        "-Wl,--no-warn-mismatch"
        "-Wl,--whole-archive"
        ${LIBS}
        "-Wl,--no-whole-archive"
        )
endif()