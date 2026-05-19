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
set(library_name twosf)

# List of source files that make up twosf
set(libtwosf_SRCS
        kotlin-jni.cpp
        2sf.cpp
        Core/vio2sf/vio2sf.c
        Core/vio2sf/desmume/FIFO.c
        Core/vio2sf/desmume/GPU.c
        Core/vio2sf/desmume/MMU.c
        Core/vio2sf/desmume/NDSSystem.c
        Core/vio2sf/desmume/SPU.cpp
        Core/vio2sf/desmume/arm_instructions.c
        Core/vio2sf/desmume/armcpu.c
        Core/vio2sf/desmume/barray.c
        Core/vio2sf/desmume/bios.c
        Core/vio2sf/desmume/cp15.c
        Core/vio2sf/desmume/isqrt.c
        Core/vio2sf/desmume/matrix.c
        Core/vio2sf/desmume/mc.c
        Core/vio2sf/desmume/resampler.c
        Core/vio2sf/desmume/state.c
        Core/vio2sf/desmume/thumb_instructions.c

        )

# Appears to just be a flag telling the code we want it to compile...?
add_definitions(
        -DEMU_COMPILE
        -DEMU_LITTLE_ENDIAN
)

# Shared psflib + PSF file-I/O, formerly vendored under psflib/ and common/.
add_subdirectory(
        ${CMAKE_CURRENT_SOURCE_DIR}/../native-common
        ${CMAKE_BINARY_DIR}/native-common
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
        ${libtwosf_SRCS})

# Libraries must be listed in here to be accessible in C/C++ code.
target_link_libraries(
        ${library_name}

        # Shared psflib + PSF file-I/O. psflib calls into zlib to decompress
        # PSF sections, so this also links libz transitively.
        chipbox_native_common
)