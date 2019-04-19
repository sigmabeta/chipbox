# List of source files required by libpsflib and any emulators

set(libpsflib_SRCS)

include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# Add library to be compiled.
add_library(psflib SHARED ${libpsflib_SRCS}
        psflib.c 
        psf2fs.c)

# Not sure what most of this means, but apparently it links to "android.h", "zlib.h" and "log.h"
if (ANDROID)
    target_link_libraries(psflib
            log
            android
            z
            "-Wl,--no-warn-mismatch"
            "-Wl,--whole-archive"
            ${LIBS}
            "-Wl,--no-whole-archive"
            )
endif ()

# These headers are part of the generic psflib interface.
set (EXPORTED_HEADERS psflib.h)
