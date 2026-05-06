# Sets the minimum version of CMake required to build your native library.
# This ensures that a certain set of CMake features is available to
# your build.

cmake_minimum_required(VERSION 3.10)

# Name the library.
set(library_name aopsf)

# List of source files that make up aopsf
set(libaopsf_SRCS
        kotlin-jni.cpp
        Psf.cpp
        aopsf/eng_psf.c
        aopsf/eng_psf2.c
        aopsf/psx.c
        aopsf/psx_hw.c
        aopsf/r3000dis.c
        aopsf/spu/spu.c
        aopsf/spu/spucore.c
        common/common.cpp
        psflib/psf2fs.c
        psflib/psflib.c
        )

# Appears to just be a flag telling the code we want it to compile...?
add_definitions(-DEMU_COMPILE -DEMU_LITTLE_ENDIAN)

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
        ${libaopsf_SRCS})

# Libraries must be listed in here to be accessible in C/C++ code.
target_link_libraries(
        ${library_name}

        # PSFLib wants to know how to unzip stuff.
        z
)