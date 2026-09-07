cmake_minimum_required(VERSION 3.22)
project(vibe_git LANGUAGES C CXX)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_CLI OFF CACHE BOOL "" FORCE)
set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_FUZZERS OFF CACHE BOOL "" FORCE)
set(USE_HTTPS "OFF" CACHE STRING "" FORCE)
set(USE_SSH "OFF" CACHE STRING "" FORCE)
set(USE_GSSAPI OFF CACHE BOOL "" FORCE)
set(USE_NTLMCLIENT OFF CACHE BOOL "" FORCE)
set(USE_ICONV OFF CACHE BOOL "" FORCE)
set(USE_BUNDLED_ZLIB ON CACHE BOOL "" FORCE)
set(REGEX_BACKEND "builtin" CACHE STRING "" FORCE)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

include(FetchContent)

FetchContent_Declare(
    libgit2
    GIT_REPOSITORY https://github.com/libgit2/libgit2.git
    GIT_TAG v1.9.6
    GIT_SHALLOW TRUE
    GIT_CONFIG http.version=HTTP/1.1
)

FetchContent_MakeAvailable(libgit2)

add_library(vibe_git SHARED vibe_git.c)
target_include_directories(vibe_git PRIVATE ${libgit2_SOURCE_DIR}/include)

find_library(log_lib log)

target_link_libraries(
    vibe_git
    PRIVATE
    libgit2package
    ${log_lib}
)
