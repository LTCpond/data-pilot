package com.ltcpond.datapilot.ai.rag;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Schema RAG 的检索阈值和独立 PostgreSQL 连接配置。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "data-pilot.rag")
public class RagProperties {

    private boolean enabled = true;
    private Mode mode = Mode.AUTO;
    private int fullSchemaThreshold = 10;
    private int topK = 6;
    private int maxPromptTables = 12;
    private int dimensions = 1024;
    private final Postgres postgres = new Postgres();

    public enum Mode {
        AUTO,
        FULL_SCHEMA,
        RAG
    }

    @Getter
    @Setter
    public static class Postgres {
        private String jdbcUrl;
        private String username;
        private String password;
        private int maximumPoolSize = 4;
        private long connectionTimeout = 3000;
    }
}
