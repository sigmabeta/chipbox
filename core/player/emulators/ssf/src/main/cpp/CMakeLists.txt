# Sets the minimum version of CMake required to build your native library.
# This ensures that a certain set of CMake features is available to
# your build.

cmake_minimum_required(VERSION 3.10)

# Name the library.
set(library_name ssf)

# List of source files that make up ssf
set(libssf_SRCS
        kotlin-jni.cpp
        Ssf.cpp
        common/common.cpp
        Core/arm.c
        Core/dcsound.c
        Core/satsound.c
        Core/sega.c
        Core/yam.c
        Core/m68k/m68kops.c
        Core/m68k/m68kcpu.c
        psflib/psf2fs.c
        psflib/psflib.c
        )

# Appears to just be a flag telling the code we want it to compile...?
add_definitions(
        -DEMU_COMPILE
        -DEMU_LITTLE_ENDIAN
        -DHAVE_STDINT_H
        -DUSE_M68K
        -DLSB_FIRST
        -DHAVE_MPROTECT
)

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
        ${libssf_SRCS})

# Libraries must be listed in here to be accessible in C/C++ code.
target_link_libraries(
        ${library_name}

        # PSFLib wants to know how to unzip stuff.
        z

        # For logging.
        log
)