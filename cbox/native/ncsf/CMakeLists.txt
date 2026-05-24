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
set(library_name ncsf)

# List of source files that make up ncsf: the JNI wrapper, the PSF->SDAT glue, and the
# SSEQ Player (a software Nitro Composer SDAT synthesizer, by Naram Qashat / CyberBotX).
set(libncsf_SRCS
        kotlin-jni.cpp
        ncsf.cpp
        Core/sseqplayer/Channel.cpp
        Core/sseqplayer/FATSection.cpp
        Core/sseqplayer/INFOEntry.cpp
        Core/sseqplayer/INFOSection.cpp
        Core/sseqplayer/NDSStdHeader.cpp
        Core/sseqplayer/Player.cpp
        Core/sseqplayer/SBNK.cpp
        Core/sseqplayer/SDAT.cpp
        Core/sseqplayer/SSEQ.cpp
        Core/sseqplayer/SWAR.cpp
        Core/sseqplayer/SWAV.cpp
        Core/sseqplayer/SYMBSection.cpp
        Core/sseqplayer/Track.cpp
        )

# Shared psflib + PSF file-I/O, formerly vendored under psflib/ and common/.
add_subdirectory(
        ${CMAKE_CURRENT_SOURCE_DIR}/../native-common
        ${CMAKE_BINARY_DIR}/native-common
)

# Specifies a library name, specifies whether the library is STATIC or
# SHARED, and provides relative paths to the source code. When you build
# your app, Gradle automatically packages shared libraries with your APK.
add_library( # Specifies the name of the library.
        ${library_name}

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        ${libncsf_SRCS})

# Libraries must be listed in here to be accessible in C/C++ code.
target_link_libraries(
        ${library_name}

        # Shared psflib + PSF file-I/O. psflib calls into zlib to decompress
        # PSF sections, so this also links libz transitively.
        chipbox_native_common
)
