package com.ltcpond.datapilot.datasource.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** AES-GCM 凭据加密配置。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "data-pilot.encryption")
public class EncryptionProperties {

    /** Base64 编码的 32 字节 AES-256 密钥。 */
    private String key;
}
