package com.spendlocker.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;

/**
 * Envelope key storage: the real SQLCipher key ("vault key") is a random secret that never
 * changes. This file stores it twice, each wrapped (AES-GCM) under a key derived (PBKDF2) from
 * a different secret — the master password, and the recovery key. Either secret unwraps the
 * same vault key. Changing the master password only re-wraps the "master" slot; the recovery
 * key keeps working without ever needing to be regenerated.
 */
public class KeyWrapStore {

    private static final int PBKDF2_ITERATIONS = 150_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static boolean exists(String path) {
        return new File(path).exists();
    }

    public static void init(String path, String vaultKey, String masterPassword, String recoveryKey) throws IOException {
        Properties props = new Properties();
        wrapInto(props, "master", vaultKey, masterPassword);
        wrapInto(props, "recovery", vaultKey, recoveryKey);
        save(path, props);
    }

    public static String unwrapMaster(String path, String password) {
        return unwrap(path, "master", password);
    }

    public static String unwrapRecovery(String path, String recoveryKey) {
        return unwrap(path, "recovery", recoveryKey);
    }

    public static void rewrapMaster(String path, String vaultKey, String newPassword) throws IOException {
        Properties props = load(path);
        wrapInto(props, "master", vaultKey, newPassword);
        save(path, props);
    }

    public static void rewrapRecovery(String path, String vaultKey, String newRecoveryKey) throws IOException {
        Properties props = load(path);
        wrapInto(props, "recovery", vaultKey, newRecoveryKey);
        save(path, props);
    }

    private static void wrapInto(Properties props, String slot, String vaultKey, String secret) {
        byte[] salt = randomBytes(16);
        byte[] iv = randomBytes(GCM_IV_BYTES);
        SecretKey wrapKey = deriveKey(secret, salt);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, wrapKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(vaultKey.getBytes(StandardCharsets.UTF_8));
            props.setProperty(slot + ".salt", Base64.getEncoder().encodeToString(salt));
            props.setProperty(slot + ".iv", Base64.getEncoder().encodeToString(iv));
            props.setProperty(slot + ".ciphertext", Base64.getEncoder().encodeToString(ciphertext));
        } catch (Exception e) {
            throw new RuntimeException("Failed to wrap vault key", e);
        }
    }

    private static String unwrap(String path, String slot, String secret) {
        try {
            Properties props = load(path);
            String saltProp = props.getProperty(slot + ".salt");
            String ivProp = props.getProperty(slot + ".iv");
            String ctProp = props.getProperty(slot + ".ciphertext");
            if (saltProp == null || ivProp == null || ctProp == null) return null;

            byte[] salt = Base64.getDecoder().decode(saltProp);
            byte[] iv = Base64.getDecoder().decode(ivProp);
            byte[] ciphertext = Base64.getDecoder().decode(ctProp);

            SecretKey wrapKey = deriveKey(secret, salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, wrapKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Wrong secret, corrupted file, tampered ciphertext: all look the same from outside.
            return null;
        }
    }

    private static SecretKey deriveKey(String secret, byte[] salt) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive wrap key", e);
        }
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static Properties load(String path) throws IOException {
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(path)) {
            props.load(in);
        }
        return props;
    }

    private static void save(String path, Properties props) throws IOException {
        try (OutputStream out = new FileOutputStream(path)) {
            props.store(out, "SpendLocker vault key wraps - do not edit");
        }
    }
}
