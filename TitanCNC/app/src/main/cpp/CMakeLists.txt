cmake_minimum_required(VERSION 3.22.1)

project("titancnc")

# Find OpenCV
find_package(OpenCV REQUIRED)

# Create native library
add_library(
    titancnc
    SHARED
    native-lib.cpp
)

# Link OpenCV
find_library(
    log-lib
    log
)

target_link_libraries(
    titancnc
    ${OpenCV_LIBS}
    ${log-lib}
)

# Include OpenCV headers
target_include_directories(titancnc PRIVATE ${OpenCV_INCLUDE_DIRS})
