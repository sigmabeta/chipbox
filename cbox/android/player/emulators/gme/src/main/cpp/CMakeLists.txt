# Sets the minimum version of CMake required to build your native library.
# This ensures that a certain set of CMake features is available to
# your build.

cmake_minimum_required(VERSION 3.10)

# Name the library.
set(library_name gme)

# List of source files that make up gme
set(libgme_SRCS
        kotlin-jni.cpp
        Gme.cpp
        gme/Blip_Buffer.cpp
        gme/Classic_Emu.cpp
        gme/Data_Reader.cpp
        gme/Dual_Resampler.cpp
        gme/Effects_Buffer.cpp
        gme/Fir_Resampler.cpp
        gme/gme.cpp
        gme/Gme_File.cpp
        gme/M3u_Playlist.cpp
        gme/Multi_Buffer.cpp
        gme/Music_Emu.cpp

        # Game Boy
        gme/Gb_Apu.cpp
        gme/Gb_Cpu.cpp
        gme/Gb_Oscs.cpp
        gme/Gbs_Emu.cpp

        # NES
        gme/Nes_Apu.cpp
        gme/Nes_Cpu.cpp
        gme/Nes_Fme7_Apu.cpp
        gme/Nes_Namco_Apu.cpp
        gme/Nes_Oscs.cpp
        gme/Nes_Vrc6_Apu.cpp
        gme/Nsf_Emu.cpp
        gme/Nsfe_Emu.cpp

        # Super NES
        gme/Snes_Spc.cpp
        gme/Spc_Cpu.cpp
        gme/Spc_Dsp.cpp
        gme/Spc_Emu.cpp
        gme/Spc_Filter.cpp
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
        ${libgme_SRCS})

# Libraries must be listed in here to be accessible in C/C++ code.
target_link_libraries(
        ${library_name}

        # PSFLib wants to know how to unzip stuff.
        z
)