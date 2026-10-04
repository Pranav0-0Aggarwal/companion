cmake_minimum_required(VERSION 3.22.1)
project(needle_jni C CXX)

add_library(needle_jni SHARED needle_jni.c)
target_include_directories(needle_jni PRIVATE ${NEEDLE_DIR})
target_compile_options(needle_jni PRIVATE -Wall -Werror -fvisibility=hidden)
target_link_libraries(needle_jni PRIVATE ${NEEDLE_DIR}/libneedle.a)
target_link_options(needle_jni PRIVATE -Wl,--exclude-libs,ALL -Wl,-z,max-page-size=16384)
set_target_properties(needle_jni PROPERTIES LINKER_LANGUAGE CXX)
