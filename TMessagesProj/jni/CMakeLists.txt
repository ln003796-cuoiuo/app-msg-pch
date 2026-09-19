cmake_minimum_required(VERSION 3.22.1)

# Принудительно устанавливаем версию C++
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

project("tmessages")

# Включаем поддержку RTTI (необходимо для некоторых частей кода Telegram)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -frtti -fexceptions -DANDROID")

# Оптимизации для релиза
if(NOT CMAKE_BUILD_TYPE MATCHES "Debug")
    set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -DNDEBUG")
endif()

# Пути к исходникам
set(TMESSAGES_DIR "${CMAKE_CURRENT_SOURCE_DIR}/..")
set(NATIVE_DIR "${CMAKE_CURRENT_SOURCE_DIR}")

# Исключаем проблемные файлы, если они есть (опционально)
# add_definitions(-DNO_LLVM)

# Основные исходные файлы (автоматический поиск может быть медленным, лучше явно указать папки)
# Telegram использует сложную структуру, поэтому подключаем основные директории

include_directories(
    ${NATIVE_DIR}
    ${NATIVE_DIR}/../src/main/cpp
    ${NATIVE_DIR}/../src/main/cpp/includes
    ${NATIVE_DIR}/openssl/include
    ${NATIVE_DIR}/libjpeg
    ${NATIVE_DIR}/libwebp
    ${NATIVE_DIR}/libvpx
    ${NATIVE_DIR}/opus/include
    ${NATIVE_DIR}/sqlite
    ${NATIVE_DIR}/breakpad
    ${NATIVE_DIR}/curl/include
)

# Ссылки на системные библиотеки Android
find_library(log-lib log)
find_library(android-lib android)
find_library(m-lib m)
find_library(z-lib z)

# === Сборка OpenSSL (упрощенная, предполагается что исходники есть) ===
# Если у вас нет собранных либ openssl, раскомментируйте блок ниже и добавьте исходники
# add_subdirectory(openssl) 

# === Основная библиотека tmessages ===
# Собираем все .cpp файлы из основной папки нативного кода
# Примечание: В оригинальном проекте используется генерация списка файлов. 
# Здесь мы используем рекурсивный поиск для простоты, но это может замедлить конфигурацию.

file(GLOB_RECURSE NATIVE_SOURCES
    "${CMAKE_CURRENT_SOURCE_DIR}/../src/main/cpp/*.cpp"
    "${CMAKE_CURRENT_SOURCE_DIR}/../src/main/cpp/*.c"
    "${CMAKE_CURRENT_SOURCE_DIR}/../src/main/cpp/**/*.cpp"
    "${CMAKE_CURRENT_SOURCE_DIR}/../src/main/cpp/**/*.c"
)

# Исключаем тесты или ненужные файлы, если они попали в выборку
list(FILTER NATIVE_SOURCES EXCLUDE REGEX ".*test.*")

# Добавляем конкретные реализации библиотек, если они не входят в общий список
# (Часто в Telegram они лежат отдельно)

# Пример явного добавления ключевых файлов, если GLOB не сработает корректно:
# set(NATIVE_SOURCES 
#     ${NATIVE_SOURCES}
#     ${CMAKE_CURRENT_SOURCE_DIR}/../src/main/cpp/native.cpp
#     ${CMAKE_CURRENT_SOURCE_DIR}/../src/main/cpp/tgnet.cpp
# )

add_library(
    tmessages.49
    SHARED
    ${NATIVE_SOURCES}
)

# Линковка библиотек
target_link_libraries(
    tmessages.49
    ${log-lib}
    ${android-lib}
    ${m-lib}
    ${z-lib}
    
    # Если вы собрали OpenSSL, раскомментируйте:
    # crypto
    # ssl
    
    # Другие статические библиотеки, если они есть в проекте:
    # jpeg
    # webp
    # vpx
    # opus
    # sqlite
)

# Определение макросов для сборки
target_compile_definitions(
    tmessages.49
    PRIVATE
    APP_PACKAGE="com.pluschat.messenger"
    OS_ANDROID=1
    __STDC_FORMAT_MACROS
)

# Настройка путей для заголовков внутри таргета
target_include_directories(
    tmessages.49
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../src/main/cpp/includes
)