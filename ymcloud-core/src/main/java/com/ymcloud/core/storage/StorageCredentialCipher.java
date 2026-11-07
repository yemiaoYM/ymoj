package com.ymcloud.core.storage;

import com.ymcloud.common.exception.file.FileException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: AES-GCM 存储凭据加密，将配置名和字段名绑定到密文
 */
@Component
public class StorageCredentialCipher {

    private final StorageProperties properties;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    private StorageMasterKeyStore keyStore;

    public StorageCredentialCipher(StorageProperties properties) {
        this.properties = properties;
    }

    public void requireKey() {
        byte[] bytes = key(true);
        Arrays.fill(bytes, (byte) 0);
    }

    public String encrypt(String configKey, String field, String value) {
        byte[] key = key(true);
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, key, nonce, configKey, field);
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] envelope = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, envelope, 0, nonce.length);
            System.arraycopy(encrypted, 0, envelope, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(envelope);
        } catch (GeneralSecurityException e) {
            throw new FileException("存储凭据加密失败");
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    public String decrypt(String configKey, String field, String value) {
        byte[] key = key(false);
        try {
            byte[] envelope = Base64.getDecoder().decode(value);
            if (envelope.length < 29) {
                throw new IllegalArgumentException();
            }
            Cipher cipher = cipher(Cipher.DECRYPT_MODE, key, Arrays.copyOf(envelope, 12), configKey, field);
            return new String(cipher.doFinal(envelope, 12, envelope.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new FileException("存储凭据无法解密，请检查配置主密钥");
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private Cipher cipher(int mode, byte[] key, byte[] nonce, String configKey, String field)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(("ymcloud.storage.v1:" + configKey + ":" + field).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    /**
     * 优先读取显式主密钥，否则交由本地密钥库加载
     *
     * @param allowCreate 是否允许首次初始化本地主密钥
     * @return 调用方负责清零的主密钥字节
     */
    private byte[] key(boolean allowCreate) {
        try {
            String value = properties.getConfigEncryptionKey();
            if (value == null || value.isBlank()) {
                return keyStore.load(allowCreate);
            }
            byte[] bytes = Base64.getDecoder().decode(value);
            if (bytes.length != 32) {
                Arrays.fill(bytes, (byte) 0);
                throw new IllegalArgumentException();
            }
            return bytes;
        } catch (IllegalArgumentException e) {
            throw new FileException("动态存储配置需要配置 Base64 编码的 32 字节主密钥");
        }
    }
}
