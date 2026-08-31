ALTER TABLE dp_query_task
    ADD COLUMN clarification_question VARCHAR(1000) NULL COMMENT '需要用户补充的信息' AFTER error_code;

UPDATE dp_query_task
SET status = 'FAILED',
    error_code = 'AGENT_RUNTIME_UPGRADED',
    completed_at = COALESCE(completed_at, CURRENT_TIMESTAMP(3)),
    updated_at = CURRENT_TIMESTAMP(3)
WHERE status IN (
    'SCHEMA_PREPARING', 'SQL_GENERATING', 'SQL_VALIDATING',
    'SQL_REPAIRING', 'SQL_EXECUTING', 'CANCEL_REQUESTED'
);

CREATE TABLE dp_agent_step (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Agent步骤ID',
    task_id BIGINT UNSIGNED NOT NULL COMMENT '问数任务ID',
    step_no INT NOT NULL COMMENT '任务内步骤序号',
    kind VARCHAR(16) NOT NULL COMMENT 'INTENT/TOOL/REPLAN/FINAL',
    tool_name VARCHAR(64) NULL COMMENT '受控工具名称',
    status VARCHAR(16) NOT NULL COMMENT 'SUCCEEDED/FAILED',
    summary VARCHAR(2000) NOT NULL COMMENT '可安全展示的步骤摘要',
    error_kind VARCHAR(64) NULL COMMENT '脱敏错误分类',
    duration_ms BIGINT NULL COMMENT '步骤耗时毫秒',
    prompt_tokens INT NULL COMMENT '本步模型输入Token数',
    completion_tokens INT NULL COMMENT '本步模型输出Token数',
    started_at DATETIME(3) NOT NULL COMMENT '步骤开始时间',
    completed_at DATETIME(3) NOT NULL COMMENT '步骤完成时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_agent_step_task_no (task_id, step_no),
    KEY idx_dp_agent_step_task (task_id),
    CONSTRAINT fk_dp_agent_step_task FOREIGN KEY (task_id)
        REFERENCES dp_query_task (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='只读查询Agent安全轨迹';
