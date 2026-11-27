cmake_minimum_required(VERSION 3.22)
project(deg_messages_native LANGUAGES C CXX)

add_subdirectory(androidx/graphics-path)
target_compile_features(androidx.graphics.path PRIVATE cxx_std_17)
target_compile_options(androidx.graphics.path PRIVATE
    -fno-exceptions -fno-unwind-tables -fno-asynchronous-unwind-tables -fno-rtti
    -ffast-math -ffp-contract=fast -fvisibility-inlines-hidden -fvisibility=hidden
    -fomit-frame-pointer -ffunction-sections -fdata-sections
)
target_link_options(androidx.graphics.path PRIVATE
    -nostdlib++ -Wl,--hash-style=both,--gc-sections,-Bsymbolic-functions
)

add_subdirectory(androidx/datastore-counter)
