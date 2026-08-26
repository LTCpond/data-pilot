ALTER TABLE dp_query_attempt
    ADD COLUMN model_name VARCHAR(128) NULL COMMENT '模型名称' AFTER sanitized_reason,
    ADD COLUMN prompt_version VARCHAR(64) NULL COMMENT 'Prompt版本' AFTER model_name,
    ADD COLUMN prompt_tokens INT NULL COMMENT '输入Token数' AFTER prompt_version,
    ADD COLUMN completion_tokens INT NULL COMMENT '输出Token数' AFTER prompt_tokens,
    ADD COLUMN total_tokens INT NULL COMMENT '总Token数' AFTER completion_tokens;
