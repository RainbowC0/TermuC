cmake_minimum_required(VERSION 3.10)
add_executable(main main.cpp)
target_link_libraries(main m)
add_compile_options(-g -Wall)
