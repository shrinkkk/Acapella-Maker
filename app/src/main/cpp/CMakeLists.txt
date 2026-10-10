cmake_minimum_required(VERSION 3.22.1)
project(acapella LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Oboe comes from the Gradle dependency via Prefab.
find_package(oboe REQUIRED CONFIG)

add_library(acapella SHARED
        AudioEngine.cpp
        JniBridge.cpp)

target_link_libraries(acapella oboe::oboe log)
