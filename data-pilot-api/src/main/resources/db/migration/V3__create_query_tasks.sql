CREATE TABLE dp_query_task (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '问数任务ID',
    datasource_id BIGINT UNSIGNED NOT NULL COMMENT '数据源ID',
    question VARCHAR(2000) NOT NULL COMMENT '用户自然语言问题',
    status VARCHAR(32) NOT NULL COMMENT '问数工作流状态',
    question_analysis TEXT NULL COMMENT '模型对问题的简要分析',
    related_tables TEXT NULL COMMENT '逗号分隔的相关表名',
    generated_sql LONGTEXT NULL COMMENT '最终候选SQL',
    explanation TEXT NULL COMMENT 'SQL解释',
    confidence DECIMAL(5,4) NULL COMMENT '模型置信度',
    repair_count INT NOT NULL DEFAULT 0 COMMENT '语义纠错次数',
    row_count INT NULL COMMENT '返回行数',
    duration_ms BIGINT NULL COMMENT '任务总耗时毫秒',
    error_code VARCHAR(64) NULL COMMENT '脱敏错误码',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    completed_at DATETIME(3) NULL COMMENT '完成时间',
    PRIMARY KEY (id),
    KEY idx_dp_query_task_datasource_created (datasource_id, created_at),
    CONSTRAINT fk_dp_query_task_datasource FOREIGN KEY (datasource_id)
        REFERENCES dp_datasource (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自然语言问数任务';

CREATE TABLE dp_query_attempt (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'SQL生成尝试ID',
    task_id BIGINT UNSIGNED NOT NULL COMMENT '问数任务ID',
    attempt_no INT NOT NULL COMMENT '尝试序号，从1开始',
    attempt_type VARCHAR(16) NOT NULL COMMENT 'GENERATE或REPAIR',
    candidate_sql LONGTEXT NULL COMMENT '本次模型生成的候选SQL',
    outcome VARCHAR(32) NOT NULL COMMENT 'VALID/REJECTED/EXECUTION_FAILED',
    sanitized_reason VARCHAR(512) NULL COMMENT '脱敏后的失败原因',
    model_duration_ms BIGINT NOT NULL COMMENT '模型调用耗时毫秒',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_query_attempt_task_no (task_id, attempt_no),
    KEY idx_dp_query_attempt_task (task_id),
    CONSTRAINT fk_dp_query_attempt_task FOREIGN KEY (task_id)
        REFERENCES dp_query_task (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SQL生成与纠错记录';
