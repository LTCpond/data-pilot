package com.ltcpond.datapilot.ai.rag;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/** 显式创建 pgvector 次数据源，避免 Spring 将 MySQL 主数据源误作向量库。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RagProperties.class)
public class PgVectorRagConfiguration {

    public static final String VECTOR_TABLE = "dp_schema_vector";

    /** 在启用 Ollama embedding 时创建独立的 pgvector 数据源。 */
    @Bean(name = "ragDataSource", destroyMethod = "close")
    @ConditionalOnProperty(prefix = "spring.ai.model", name = "embedding", havingValue = "ollama")
    HikariDataSource ragDataSource(RagProperties properties) {
        RagProperties.Postgres postgres = properties.getPostgres();
        HikariConfig config = new HikariConfig();
        config.setPoolName("data-pilot-pgvector");
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(postgres.getMaximumPoolSize());
        config.setConnectionTimeout(postgres.getConnectionTimeout());
        return new HikariDataSource(config);
    }

    /** 创建访问 pgvector 表的 JdbcTemplate。 */
    @Bean(name = "ragJdbcTemplate")
    @ConditionalOnProperty(prefix = "spring.ai.model", name = "embedding", havingValue = "ollama")
    JdbcTemplate ragJdbcTemplate(@Qualifier("ragDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /** 创建 Schema 专用 PgVectorStore，并自动初始化向量表结构。 */
    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.model", name = "embedding", havingValue = "ollama")
    PgVectorStore schemaPgVectorStore(
            @Qualifier("ragJdbcTemplate") JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel,
            RagProperties properties) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName(VECTOR_TABLE)
                .dimensions(properties.getDimensions())
                .distanceType(PgDistanceType.COSINE_DISTANCE)
                .indexType(PgIndexType.HNSW)
                .initializeSchema(true)
                .build();
    }
}
