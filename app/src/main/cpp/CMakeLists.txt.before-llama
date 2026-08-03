cmake_minimum_required(VERSION 3.22)

project(tigoj)

add_library(
    tigoj
    SHARED
    native-lib.cpp
)

find_library(log-lib log)

target_link_libraries(
    tigoj
    ${log-lib}
)
