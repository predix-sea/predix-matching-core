cmake_minimum_required(VERSION 3.20)
project(predix_matching_core LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(PREDIX_BUILD_TESTS "Build unit tests" ON)

find_package(gRPC CONFIG REQUIRED)

set(PROTO_FILE "${CMAKE_CURRENT_SOURCE_DIR}/proto/matching_core.proto")
set(GENERATED_DIR "${CMAKE_CURRENT_BINARY_DIR}/generated")
file(MAKE_DIRECTORY "${GENERATED_DIR}")

set(PROTO_SRCS
    "${GENERATED_DIR}/matching_core.pb.cc"
    "${GENERATED_DIR}/matching_core.grpc.pb.cc")
set(PROTO_HDRS
    "${GENERATED_DIR}/matching_core.pb.h"
    "${GENERATED_DIR}/matching_core.grpc.pb.h")

add_custom_command(
    OUTPUT ${PROTO_SRCS} ${PROTO_HDRS}
    COMMAND protobuf::protoc
    ARGS --proto_path="${CMAKE_CURRENT_SOURCE_DIR}/proto"
         --cpp_out="${GENERATED_DIR}"
         --grpc_out="${GENERATED_DIR}"
         --plugin=protoc-gen-grpc=$<TARGET_FILE:gRPC::grpc_cpp_plugin>
         "${PROTO_FILE}"
    DEPENDS "${PROTO_FILE}"
    COMMENT "Generating gRPC/protobuf sources")

add_library(matching_core_proto STATIC ${PROTO_SRCS})
target_include_directories(matching_core_proto PUBLIC "${GENERATED_DIR}")
target_link_libraries(matching_core_proto PUBLIC gRPC::grpc++)

add_executable(matching_core
    src/main.cpp
    src/grpc_server.cpp)
target_include_directories(matching_core PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/include"
    "${GENERATED_DIR}")
target_link_libraries(matching_core PRIVATE matching_core_proto)

if(PREDIX_BUILD_TESTS)
    find_package(GTest CONFIG QUIET HINTS /opt/homebrew)
    if(NOT GTest_FOUND)
        include(FetchContent)
        FetchContent_Declare(
            googletest
            URL https://github.com/google/googletest/archive/refs/tags/v1.15.2.zip
        )
        set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)
        FetchContent_MakeAvailable(googletest)
    endif()

    enable_testing()
    add_executable(matching_core_tests tests/order_book_test.cpp)
    target_include_directories(matching_core_tests PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}/include")
    if(TARGET GTest::gtest)
        target_link_libraries(matching_core_tests PRIVATE GTest::gtest GTest::gtest_main)
    else()
        target_link_libraries(matching_core_tests PRIVATE gtest gtest_main)
    endif()
    include(GoogleTest)
    gtest_discover_tests(matching_core_tests)
endif()
