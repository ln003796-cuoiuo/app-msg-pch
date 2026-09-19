# PlusChat Secure Android Client

Это модуль безопасного клиента для мессенджера PlusChat.
Он заменяет стандартный сетевой стек Telegram на защищенный протокол "Динамический Лабиринт".

## Архитектура безопасности

1. **Клиент (App)**:
   - Генерирует эфемерную пару ключей ECC (secp256r1) для КАЖДОГО запроса.
   - Вычисляет Shared Secret через ECDH.
   - Шифрует payload алгоритмом AES-256-CBC.
   - Отправляет данные на `proto.pluschat.ru`.

2. **Прото-шлюз (proto.pluschat.ru)**:
   - Принимает публичный ключ клиента в заголовке `X-Public-Key`.
   - Генерирует свою эфемерную пару ключей.
   - Вычисляет Shared Secret.
   - Расшифровывает внешний слой.
   - Пересылает чистые данные на `pluschat.ru/api/receive.php`.
   - Шифрует ответ и возвращает клиенту вместе со своим публичным ключом.

3. **Основной хост (pluschat.ru)**:
   - Получает данные только от шлюза (проверка заголовка `X-Gateway-Status`).
   - Обрабатывает бизнес-логику (регистрация, сообщения).

## Как интегрировать в проект Telegram Android

1. Скопируйте папку `src/main/java/com/pluschat/security` в исходники вашего проекта Telegram Android.
   Путь: `TMessagesProj/src/main/java/com/pluschat/security/`

2. Найдите класс, отвечающий за сетевые запросы (обычно `ConnectionsManager.java` или аналогичный в пакете `org.tgnet`).

3. Замените логику отправки данных:
   - Вместо прямой отправки JSON/байтов на сервер Telegram, вызывайте:
     ```java
     String response = SecureApiClient.sendSecureRequest(jsonPayload);
     ```

4. Убедитесь, что в `AndroidManifest.xml` есть разрешение на интернет:
   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   ```

## Сборка APK

Используйте GitHub Actions для сборки:
1. Создайте репозиторий с кодом.
2. Добавьте файл `.github/workflows/build.yml` (пример ниже).
3. Push код -> APK соберется автоматически.

## Важные замечания

- **Публичный ключ сервера**: В файле `SecureApiClient.java` метод `getServerPublicKey()` сейчас заглушка. Вам нужно внедрить реальный публичный ключ вашего сервера (или механизм его получения) перед компиляцией.
- **Сертификаты HTTPS**: Убедитесь, что на `proto.pluschat.ru` установлен валидный SSL-сертификат.
