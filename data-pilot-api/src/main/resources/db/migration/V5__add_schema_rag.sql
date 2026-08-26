ALTER TABLE dp_datasource
    ADD COLUMN rag_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'DISABLED/PENDING/INDEXING/READY/ERROR' AFTER last_sync_at,
    ADD COLUMN rag_index_version VARCHAR(36) NULL COMMENT '当前活动向量索引版本' AFTER rag_status,
    ADD COLUMN rag_document_count INT NOT NULL DEFAULT 0 COMMENT '活动版本文档数' AFTER rag_index_version,
    ADD COLUMN rag_indexed_at DATETIME(3) NULL COMMENT '最近成功索引时间' AFTER rag_document_count,
    ADD COLUMN rag_error_code VARCHAR(64) NULL COMMENT '脱敏索引错误码' AFTER rag_indexed_at;

ALTER TABLE dp_query_task
    ADD COLUMN rag_used BOOLEAN NOT NULL DEFAULT FALSE COMMENT '本次Prompt是否使用RAG子集' AFTER error_code,
    ADD COLUMN rag_fallback BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否因RAG异常回退全量Schema' AFTER rag_used,
    ADD COLUMN schema_table_count INT NULL COMMENT '完整Schema表数' AFTER rag_fallback,
    ADD COLUMN prompt_table_count INT NULL COMMENT 'Prompt实际表数' AFTER schema_table_count,
    ADD COLUMN retrieved_tables TEXT NULL COMMENT '逗号分隔的召回表名' AFTER prompt_table_count,
    ADD COLUMN retrieval_duration_ms BIGINT NULL COMMENT 'Schema召回耗时毫秒' AFTER retrieved_tables,
    ADD COLUMN schema_prompt_chars INT NULL COMMENT 'Schema Prompt字符数' AFTER retrieval_duration_ms;
