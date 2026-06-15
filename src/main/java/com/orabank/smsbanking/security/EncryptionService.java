package com.orabank.smsbanking.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for encrypting and decrypting sensitive data.
 * Uses AES-256-GCM encryption for secure data protection.
 */
@Slf4j
@Service
public class EncryptionService {
    
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    
    private final SecretKey secretKey;
    
    public EncryptionService(@Value("${encryption.key}") String encryptionKey) {
        if (encryptionKey == null || encryptionKey.isEmpty()) {
            throw new IllegalArgumentException("Encryption key cannot be null or empty");
        }
        
        if (encryptionKey.length() != 32) {
            throw new IllegalArgumentException("Encryption key must be exactly 32 characters long for AES-256");
        }
        
        this.secretKey = new SecretKeySpec(encryptionKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }
    
    /**
     * Encrypts the provided plain text.
     *
     * @param plainText the text to encrypt
     * @return the encrypted text as a Base64 encoded string
     */
    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);
            
            byte[] encryptedData = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            
            byte[] result = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encryptedData, 0, result, iv.length, encryptedData.length);
            
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            log.error("Error encrypting data", e);
            throw new RuntimeException("Error encrypting data", e);
        }
    }
    
    /**
     * Decrypts the provided encrypted text.
     *
     * @param encryptedText the Base64 encoded encrypted text
     * @return the decrypted plain text
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null) {
            return null;
        }
        
        try {
            byte[] data = Base64.getDecoder().decode(encryptedText);
            
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedData = new byte[data.length - GCM_IV_LENGTH];
            
            System.arraycopy(data, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(data, GCM_IV_LENGTH, encryptedData, 0, encryptedData.length);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);
            
            byte[] decryptedData = cipher.doFinal(encryptedData);
            
            return new String(decryptedData, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Error decrypting data", e);
            throw new RuntimeException("Error decrypting data", e);
        }
    }
    
    public static String generateNewKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(256);
            SecretKey secretKey = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            log.error("Error generating new key", e);
            throw new RuntimeException("Error generating new key", e);
        }
    }
    
    public static boolean isValidKey(String key) {
        if (key == null) {
            return false;
        }
        
        if (key.length() == 32) {
            return true;
        }
        
        try {
            byte[] decodedKey = Base64.getDecoder().decode(key);
            return decodedKey.length == 32;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
