package com.ltcpond.datapilot.datasource.crypto;

import com.ltcpond.datapilot.datasource.config.EncryptionProperties;
import com.ltcpond.datapilot.datasource.entity.DatasourceEntity;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmCredentialCipherTest {

    @Test
    void shouldUseRandomIvAndDecryptBothCiphertexts() {
        AesGcmCredentialCipher cipher = cipherWithByte((byte) 7);

        String first = cipher.encrypt("test-secret");
        String second = cipher.encrypt("test-secret");

        assertThat(first).startsWith("v1:").isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("test-secret");
        assertThat(cipher.decrypt(second)).isEqualTo("test-secret");
    }

    @Test
    void shouldRejectCiphertextEncryptedByAnotherKey() {
        String ciphertext = cipherWithByte((byte) 1).encrypt("secret");

        assertThatThrownBy(() -> cipherWithByte((byte) 2).decrypt(ciphertext))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Credential decryption failed");
    }

    @Test
    void shouldExcludeEncryptedPasswordFromEntityToString() {
        DatasourceEntity entity = new DatasourceEntity();
        entity.setName("demo");
        entity.setEncryptedPassword("v1:must-not-leak");

        assertThat(entity.toString()).contains("demo").doesNotContain("must-not-leak");
    }

    private AesGcmCredentialCipher cipherWithByte(byte value) {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, value);
        EncryptionProperties properties = new EncryptionProperties();
        properties.setKey(Base64.getEncoder().encodeToString(key));
        return new AesGcmCredentialCipher(properties);
    }
}
