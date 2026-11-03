cmake_minimum_required(VERSION 3.22.1)
project(lumi_kindle C)
set(CMAKE_C_STANDARD 11)
set(VENDOR ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/libmobi/src)
set(MOBI_SOURCES buffer compression debug index memory meta parse_rawml read structure util write xmlwriter opf miniz)
set(MOBI_FILES)
foreach(name IN LISTS MOBI_SOURCES)
    list(APPEND MOBI_FILES ${VENDOR}/${name}.c)
endforeach()
# Keep the LGPL library separately replaceable and build it entirely from source.
add_library(mobi SHARED ${MOBI_FILES} mobi_budget.c)
target_include_directories(mobi PUBLIC ${VENDOR} ${CMAKE_CURRENT_LIST_DIR})
target_compile_definitions(mobi PRIVATE USE_XMLWRITER USE_MINIZ HAVE_UNISTD_H HAVE_STRDUP NDEBUG
    PACKAGE_VERSION="0.12" MOBI_INLINE=inline)
# The other units get these flags from upstream miniz.h.
set_property(SOURCE ${VENDOR}/miniz.c APPEND PROPERTY COMPILE_DEFINITIONS
    MINIZ_NO_STDIO MINIZ_NO_TIME MINIZ_NO_ZLIB_COMPATIBLE_NAMES
    MINIZ_NO_ARCHIVE_APIS MINIZ_NO_ARCHIVE_WRITING_APIS)
set_source_files_properties(${MOBI_FILES} PROPERTIES COMPILE_OPTIONS "-include;${CMAKE_CURRENT_LIST_DIR}/mobi_budget.h" COMPILE_DEFINITIONS LUMI_MOBI_WRAP_ALLOC)
if(ANDROID)
    add_library(lumi_kindle SHARED kindle_core.c kindle_jni.c)
    # Explicit for all four ABIs, including older toolchains.
    target_link_options(mobi PRIVATE "-Wl,-z,max-page-size=16384")
    target_link_options(lumi_kindle PRIVATE "-Wl,-z,max-page-size=16384")
else()
    add_executable(kindle_reconstruct kindle_core.c host_main.c)
    set_target_properties(kindle_reconstruct PROPERTIES OUTPUT_NAME kindle_reconstruct)
endif()
if(ANDROID)
    target_link_libraries(lumi_kindle PRIVATE mobi)
else()
    target_link_libraries(kindle_reconstruct PRIVATE mobi)
endif()


# Optional Windows/JVM integration harness; never used in the APK.
if(NOT ANDROID AND LUMI_JAVA_HOME)
    add_library(lumi_kindle SHARED kindle_core.c kindle_jni.c)
    target_include_directories(lumi_kindle PRIVATE "${LUMI_JAVA_HOME}/include" "${LUMI_JAVA_HOME}/include/win32")
    target_link_libraries(lumi_kindle PRIVATE mobi)
    set_target_properties(lumi_kindle PROPERTIES PREFIX "")
endif()
set_property(SOURCE ${VENDOR}/miniz.c APPEND PROPERTY COMPILE_OPTIONS -UNDEBUG)
