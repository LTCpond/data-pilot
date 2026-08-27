package com.ltcpond.datapilot.datasource.crypto;

import com.ltcpond.datapilot.datasource.config.EncryptionProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** 使用带认证标签的 AES-256-GCM 加密数据源密码。 */
@Component
public class AesGcmCredentialCipher implements CredentialCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    /** 从配置读取并校验 AES-256 密钥。 */
    public AesGcmCredentialCipher(EncryptionProperties properties) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(properties.getKey());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("DATA_PILOT_ENCRYPTION_KEY 必须是有效的 Base64 字符串", exception);
        }
        if (decoded.length != 32) {
            throw new IllegalArgumentException("DATA_PILOT_ENCRYPTION_KEY 解码后必须为 32 字节");
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    /** 使用随机 IV 加密凭据，并在密文前加版本前缀。 */
    @Override
    public String encrypt(String plaintext) {
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("凭据加密失败", exception);
        }
    }

    /** 解密当前版本密文，并校验 GCM 认证标签。 */
    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null || !ciphertext.startsWith(PREFIX)) {
            throw new IllegalArgumentException("不支持的加密凭据格式");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext.substring(PREFIX.length()));
            if (payload.length <= IV_LENGTH) {
            throw new IllegalArgumentException("加密凭据无效");
            }
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("凭据解密失败", exception);
        }
    }
}
