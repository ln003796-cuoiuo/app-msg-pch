package com.pluschat.security;

import android.util.Base64;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;

/**
 * Клиент для безопасного общения с ПРОТО-шлюзом.
 * URL: https://proto.pluschat.ru/
 */
public class SecureApiClient {

    private static final String GATEWAY_URL = "https://proto.pluschat.ru/"; // Или xn--n1aabel.xn--80avljg2a1c.xn--p1ai

    public static String sendSecureRequest(String jsonData) throws Exception {
        // 1. Генерируем временную пару ключей для этого запроса
        KeyPair clientKeyPair = CryptoManager.generateEphemeralKeyPair();
        
        // 2. Подготавливаем JSON payload
        String payload = jsonData; // Здесь может быть регистрация, сообщение и т.д.

        // 3. Шифруем данные (нужен публичный ключ сервера, который можно получить заранее или по handshake)
        // Для упрощения: считаем, что публичный ключ сервера известен или передается в handshake
        // В реальной схеме: сначала запросить публичный ключ сервера
        
        // Эмуляция получения публичного ключа сервера (в реальности - из сертификата или первого ответа)
        PublicKey serverPubKey = getServerPublicKey(); 

        String encryptedData = CryptoManager.encryptMessage(payload, serverPubKey, clientKeyPair.getPrivate());

        // 4. Отправляем POST запрос на шлюз
        URL url = new URL(GATEWAY_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        
        // Передаем наш публичный ключ в заголовке, чтобы сервер мог вычислить Shared Secret
        String clientPubKeyPem = Base64.encodeToString(clientKeyPair.getPublic().getEncoded(), Base64.NO_WRAP);
        conn.setRequestProperty("X-Public-Key", clientPubKeyPem);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = encryptedData.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        int status = conn.getResponseCode();
        BufferedReader br;
        if (status >= 200 && status < 300) {
            br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        } else {
            br = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
        }

        StringBuilder response = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            response.append(line);
        }
        br.close();
        conn.disconnect();

        if (status != 200) {
            throw new IOException("Server error: " + status + " " + response.toString());
        }

        // 5. Парсим ответ от шлюза
        // Ожидаем: {"gateway_pub_key": "...", "data": "..."}
        // Нужно извлечь data и gateway_pub_key
        String responseBody = response.toString();
        String serverPubKeyFromResponse = extractJsonValue(responseBody, "gateway_pub_key");
        String encryptedResponseData = extractJsonValue(responseBody, "data");

        // 6. Вычисляем общий секрет с сервером используя его новый публичный ключ из ответа
        // Внимание: здесь логика зависит от того, как именно сервер меняет ключи.
        // В данной схеме сервер присылает свой эфемерный публичный ключ.
        // Нам нужно восстановить Shared Secret.
        
        // Упрощение: если сервер использует тот же самый ключ для derive, что и мы ему отправили (клиентский),
        // то мы используем СВОЙ приватный ключ и ЕГО публичный из ответа.
        
        // Конвертируем PEM строку сервера обратно в PublicKey объект
        PublicKey serverResponsePubKey = loadPublicKey(serverPubKeyFromResponse);
        
        // Вычисляем секрет
        byte[] sharedSecret = CryptoManager.deriveSharedSecret(clientKeyPair.getPrivate(), serverResponsePubKey);
        
        // 7. Расшифровываем ответ
        return CryptoManager.decryptResponse(encryptedResponseData, sharedSecret);
    }

    // Заглушка: получение публичного ключа сервера (должно быть реализовано через сертификат или статический ключ)
    private static PublicKey getServerPublicKey() throws Exception {
        // В реальном проекте: загрузить из ресурсов или получить при первом старте
        // Для примера генерируем новый (это НЕ безопасно для продакшена, нужно заменить на реальный ключ сервера)
        KeyPair tempPair = CryptoManager.generateEphemeralKeyPair(); 
        return tempPair.getPublic(); 
    }

    private static PublicKey loadPublicKey(String pemEncoded) throws Exception {
        byte[] keyBytes = Base64.decode(pemEncoded, Base64.NO_WRAP);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("EC");
        return kf.generatePublic(spec);
    }

    private static String extractJsonValue(String json, String key) {
        // Простой парсинг без библиотек (лучше использовать org.json)
        int start = json.indexOf("\"" + key + "\"") + key.length() + 3;
        int end = json.indexOf("\"", start);
        // Обработка случаев, когда значение не строка (например объект) - упрощено
        if (json.charAt(start-1) == ':') {
             // Если это объект или массив, парсинг сложнее, здесь только для строк
             if (json.charAt(start) == '"') {
                 return json.substring(start + 1, end);
             }
        }
        return json.substring(start, end);
    }
}
