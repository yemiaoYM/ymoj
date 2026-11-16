package com.ymcloud.core.storage.security;

import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.storage.config.StorageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: AES-GCM 存储凭据加密，将配置名和字段名绑定到密文
 */
@Component
public class StorageCredentialCipher {

    /**
     * 凭据认证上下文，绑定配置与字段，不允许外部配置修改
     */
    private static final String AUTHENTICATION_CONTEXT = "ymcloud.storage:";

    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int AUTHENTICATION_TAG_BITS = 128;

    private final StorageProperties properties;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    private StorageMasterKeyStore keyStore;

    /**
     * 绑定显式主密钥配置，未配置时使用注入的本地密钥库
     * @param properties 存储配置，显式主密钥为 Base64 编码的 32 字节值
     */
    public StorageCredentialCipher(StorageProperties properties) {
        this.properties = properties;
    }

    /**
     * 确保加密主密钥可用，首次保存允许初始化本地密钥
     * @throws FileException 主密钥非法、丢失或无法安全保存
     */
    public void requireKey() {
        byte[] bytes = key(true);
        Arrays.fill(bytes, (byte) 0);
    }

    /**
     * 以随机 nonce 加密凭据，并将配置名与字段名绑定到认证数据
     * @param configKey 凭据所属配置标识
     * @param field 凭据字段名
     * @param value 待加密的明文凭据
     * @return 包含 nonce 和认证密文的 Base64 文本
     * @throws FileException 主密钥不可用或加密失败
     */
    public String encrypt(String configKey, String field, String value) {
        byte[] key = key(true);
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, key, nonce, configKey, field);
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] envelope = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, envelope, 0, nonce.length);
            System.arraycopy(encrypted, 0, envelope, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(envelope);
        } catch (GeneralSecurityException e) {
            throw new FileException("存储凭据加密失败", e);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    /**
     * 校验凭据归属并解密，解密时不创建新主密钥
     * @param configKey 密文所属配置标识
     * @param field 密文对应的凭据字段名
     * @param value 包含 nonce 和认证密文的 Base64 文本
     * @return 仅供内部客户端使用的明文凭据
     * @throws FileException 密钥不可用、密文损坏或归属校验失败
     */
    public String decrypt(String configKey, String field, String value) {
        byte[] key = key(false);
        try {
            byte[] envelope = Base64.getDecoder().decode(value);
            if (envelope.length < NONCE_BYTES + AUTHENTICATION_TAG_BITS / Byte.SIZE + 1) {
                throw new IllegalArgumentException();
            }
            Cipher cipher = cipher(Cipher.DECRYPT_MODE, key, Arrays.copyOf(envelope, NONCE_BYTES), configKey, field);
            return new String(cipher.doFinal(envelope, NONCE_BYTES, envelope.length - NONCE_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new FileException("存储凭据解密失败", e);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    /**
     * 初始化 AES-GCM 并绑定凭据所属配置和字段
     * @param mode 加密或解密模式
     * @param key 32 字节主密钥
     * @param nonce 12 字节随机 nonce
     * @param configKey 配置标识
     * @param field 凭据字段名
     * @return 已初始化且绑定认证数据的加解密器
     * @throws GeneralSecurityException 加解密器初始化失败
     */
    private Cipher cipher(int mode, byte[] key, byte[] nonce, String configKey, String field)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(AUTHENTICATION_TAG_BITS, nonce));
        cipher.updateAAD((AUTHENTICATION_CONTEXT + configKey + ":" + field).getBytes(StandardCharsets.UTF_8));
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
            throw new FileException("存储主密钥格式无效", e);
        }
    }
}
