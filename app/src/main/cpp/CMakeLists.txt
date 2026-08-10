cmake_minimum_required(VERSION 3.22)

project(tigoj LANGUAGES C CXX)

# Configuración ligera para Android
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)

set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

# Compilar llama.cpp incluido como submódulo
add_subdirectory(
    llama.cpp
    llama-build
)

# Biblioteca JNI de TIGOJ
add_library(
    tigoj
    SHARED
    native-lib.cpp
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(
    tigoj
    PRIVATE
    llama
    ${log-lib}
    ${android-lib}
)
