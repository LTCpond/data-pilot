ALTER TABLE dp_query_task
    ADD COLUMN conversation_id VARCHAR(64) NULL COMMENT '问数会话ID' AFTER datasource_id,
    ADD COLUMN resolved_question TEXT NULL COMMENT '结合会话历史消歧后的独立问题' AFTER question,
    ADD KEY idx_dp_query_task_conversation (conversation_id, datasource_id, id);
