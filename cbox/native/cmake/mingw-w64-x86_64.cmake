# MinGW-w64 cross-compile toolchain — Windows x86_64 — for the apps/jvm host native build.
# Selected by ChipboxNativeHostPlugin when -Pchipbox.jvm.nativeTarget=windows-x64. Kept distro-
# agnostic: the cross-compilers are found on PATH and CMake adds their built-in sysroot to the
# search path automatically, so no hard-coded /usr/x86_64-w64-mingw32 path (which differs between
# Fedora and Debian/Ubuntu CI images).
set(CMAKE_SYSTEM_NAME Windows)
set(CMAKE_SYSTEM_PROCESSOR x86_64)

set(CMAKE_C_COMPILER   x86_64-w64-mingw32-gcc)
set(CMAKE_CXX_COMPILER x86_64-w64-mingw32-g++)
set(CMAKE_RC_COMPILER  x86_64-w64-mingw32-windres)

# Point find_package/find_library at the MinGW sysroot so e.g. find_package(ZLIB) resolves. Derive
# it from the compiler (the dir holding libmingw32.a → up to the sysroot prefix) instead of a fixed
# path, so it works on any distro: Fedora's .../sys-root/mingw vs Debian/Ubuntu's /usr/x86_64-w64-mingw32.
execute_process(
    COMMAND x86_64-w64-mingw32-gcc -print-file-name=libmingw32.a
    OUTPUT_VARIABLE _mingw_crt OUTPUT_STRIP_TRAILING_WHITESPACE)
get_filename_component(_mingw_libdir "${_mingw_crt}" DIRECTORY)
get_filename_component(CMAKE_FIND_ROOT_PATH "${_mingw_libdir}" DIRECTORY)

# Resolve target libraries/headers against that sysroot, but always run build tools (cmake, the
# compilers) from the host — never try to execute a Windows binary during configure.
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)

# Prefer static archives (.a) over import libs (.dll.a) so find_package'd deps (zlib) link into the
# DLL statically, matching the -static link — no zlib1.dll to ship.
set(CMAKE_FIND_LIBRARY_SUFFIXES .a .dll.a)
