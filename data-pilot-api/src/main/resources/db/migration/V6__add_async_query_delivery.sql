ALTER TABLE dp_query_task
    ADD COLUMN execution_mode VARCHAR(16) NOT NULL DEFAULT 'SYNC' COMMENT 'SYNC或ASYNC' AFTER question,
    ADD COLUMN max_rows INT NOT NULL DEFAULT 100 COMMENT '本次任务最大返回行数' AFTER execution_mode,
    ADD COLUMN result_expires_at DATETIME(3) NULL COMMENT 'Redis异步结果过期时间' AFTER completed_at,
    ADD KEY idx_dp_query_task_status_updated (status, updated_at);
