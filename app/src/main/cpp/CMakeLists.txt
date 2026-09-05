cmake_minimum_required(VERSION 3.22)
project(vibe_git C)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_CLI OFF CACHE BOOL "" FORCE)
set(USE_SSH OFF CACHE BOOL "" FORCE)
set(USE_HTTPS OFF CACHE BOOL "" FORCE)
set(USE_NTLMCLIENT OFF CACHE BOOL "" FORCE)
set(USE_GSSAPI OFF CACHE BOOL "" FORCE)
set(USE_ICONV OFF CACHE BOOL "" FORCE)
set(USE_BUNDLED_ZLIB ON CACHE BOOL "" FORCE)
set(REGEX_BACKEND "builtin" CACHE STRING "" FORCE)

add_subdirectory(libgit2 EXCLUDE_FROM_ALL)

add_library(vibe_git SHARED vibe_git.c)
target_include_directories(vibe_git PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/libgit2/include)
# libgit2 v1.9 的对外链接 target 是 libgit2package（OUTPUT_NAME=git2）。
target_link_libraries(vibe_git PRIVATE libgit2package)
