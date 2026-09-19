package com.pluschat.security;

import android.util.Base64;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Менеджер безопасности для Android-приложения PlusChat.
 * Реализует схему "Динамический Лабиринт":
 * 1. Генерация временной пары ключей ECC для каждого запроса.
 * 2. ECDH ключевой обмен с сервером (Proto-Gateway).
 * 3. AES-256 шифрование данных.
 */
public class CryptoManager {

    private static final String CURVE_NAME = "secp256r1"; // prime256v1
    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";

    // Генерация новой пары ключей для сессии
    public static KeyPair generateEphemeralKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        ECGenParameterSpec ecSpec = new ECGenParameterSpec(CURVE_NAME);
        kpg.initialize(ecSpec, new SecureRandom());
        return kpg.generateKeyPair();
    }

    // Вычисление общего секрета (Shared Secret) используя приватный ключ клиента и публичный ключ сервера
    public static byte[] deriveSharedSecret(PrivateKey myPrivateKey, PublicKey serverPublicKey) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(myPrivateKey);
        ka.doPhase(serverPublicKey, true);
        return ka.generateSecret();
    }

    // Шифрование сообщения перед отправкой
    public static String encryptMessage(String message, PublicKey serverPublicKey, PrivateKey myPrivateKey) throws Exception {
        // 1. Получаем общий секрет
        byte[] sharedSecret = deriveSharedSecret(myPrivateKey, serverPublicKey);
        SecretKeySpec key = new SecretKeySpec(sha256(sharedSecret), "AES");

        // 2. Генерируем случайный IV
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        // 3. Шифруем
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
        byte[] encryptedBytes = cipher.doFinal(message.getBytes("UTF-8"));

        // 4. Формируем пакет: IV + Зашифрованные данные
        byte[] fullPacket = new byte[iv.length + encryptedBytes.length];
        System.arraycopy(iv, 0, fullPacket, 0, iv.length);
        System.arraycopy(encryptedBytes, 0, fullPacket, iv.length, encryptedBytes.length);

        return Base64.encodeToString(fullPacket, Base64.NO_WRAP);
    }

    // Расшифровка ответа от сервера
    public static String decryptResponse(String base64Data, byte[] sharedSecret) throws Exception {
        SecretKeySpec key = new SecretKeySpec(sha256(sharedSecret), "AES");
        
        byte[] fullPacket = Base64.decode(base64Data, Base64.NO_WRAP);
        byte[] iv = new byte[16];
        byte[] payload = new byte[fullPacket.length - 16];
        
        System.arraycopy(fullPacket, 0, iv, 0, 16);
        System.arraycopy(fullPacket, 16, payload, 0, payload.length);

        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
        
        byte[] decryptedBytes = cipher.doFinal(payload);
        return new String(decryptedBytes, "UTF-8");
    }

    private static byte[] sha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
