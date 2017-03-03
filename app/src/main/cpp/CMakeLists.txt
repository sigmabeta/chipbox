# CMake project definition file.
project(libchipbox)

include (CheckCXXCompilerFlag)

# When version is changed, also change the one in gme/gme.h to match
set(GME_VERSION 0.6.0 CACHE INTERNAL "libgme Version")

# 2.6+ always assumes FATAL_ERROR, but 2.4 and below don't.
# Of course, 2.4 might work, in which case you're welcome to drop
# down the requirement, but I can't test that.
cmake_minimum_required(VERSION 2.6 FATAL_ERROR)


# Check for GCC "visibility" support.
if (CMAKE_COMPILER_IS_GNUCXX)
   check_cxx_compiler_flag (-fvisibility=hidden __LIBGME_TEST_VISIBILITY)
   set (ENABLE_VISIBILITY OFF)
   if (__LIBGME_TEST_VISIBILITY)
      # get the gcc version
      exec_program(${CMAKE_CXX_COMPILER} ARGS --version OUTPUT_VARIABLE _gcc_version_info)
      string (REGEX MATCH "[3-9]\\.[0-9]\\.[0-9]" _gcc_version "${_gcc_version_info}")

      # gcc <4.1 had poor support for symbol visibility
      #if ((${_gcc_version} VERSION_GREATER "4.1") OR (${_gcc_version} VERSION_EQUAL "4.1"))
         set (CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden")
         set (ENABLE_VISIBILITY ON)
         add_definitions (-DLIBGME_VISIBILITY)

         # GCC >= 4.2 also correctly supports making inline members have hidden
         # visibility by default.

         #if ((${_gcc_version} VERSION_GREATER "4.2") OR (${_gcc_version} VERSION_EQUAL "4.2"))
            set (CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility-inlines-hidden")
         #endif()
      #endif()
   endif() # test visibility
endif (CMAKE_COMPILER_IS_GNUCXX)

# Cache this result
set( LIBGME_HAVE_GCC_VISIBILITY ${ENABLE_VISIBILITY} CACHE BOOL "GCC support for hidden visibility")

# Shared library defined here
add_subdirectory(gme)
add_subdirectory(VGMPlay)