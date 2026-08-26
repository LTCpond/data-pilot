CREATE TABLE dp_datasource (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '数据源ID',
    name VARCHAR(128) NOT NULL COMMENT '数据源名称',
    description VARCHAR(512) NULL COMMENT '数据源描述',
    db_type VARCHAR(16) NOT NULL DEFAULT 'MYSQL' COMMENT '数据库类型',
    jdbc_url VARCHAR(1024) NOT NULL COMMENT 'JDBC连接地址',
    username VARCHAR(128) NOT NULL COMMENT '数据库用户名',
    encrypted_password TEXT NOT NULL COMMENT 'AES-GCM加密密码',
    status VARCHAR(16) NOT NULL COMMENT '状态：CONNECTED/READY/ERROR',
    last_sync_at DATETIME(3) NULL COMMENT '最近同步时间',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_datasource_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Data Pilot数据源';

CREATE TABLE dp_schema_table (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '表元数据ID',
    datasource_id BIGINT UNSIGNED NOT NULL COMMENT '数据源ID',
    schema_name VARCHAR(128) NOT NULL COMMENT 'Catalog或Schema名称',
    table_name VARCHAR(128) NOT NULL COMMENT '表名',
    table_type VARCHAR(32) NOT NULL COMMENT 'TABLE或VIEW',
    table_comment TEXT NULL COMMENT '表注释',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_schema_table_natural (datasource_id, schema_name, table_name),
    KEY idx_dp_schema_table_datasource (datasource_id),
    CONSTRAINT fk_dp_schema_table_datasource FOREIGN KEY (datasource_id)
        REFERENCES dp_datasource (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据源表元数据';

CREATE TABLE dp_schema_column (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '字段元数据ID',
    table_id BIGINT UNSIGNED NOT NULL COMMENT '表元数据ID',
    column_name VARCHAR(128) NOT NULL COMMENT '字段名',
    jdbc_type INT NOT NULL COMMENT 'java.sql.Types类型值',
    native_type VARCHAR(128) NOT NULL COMMENT '数据库原生类型',
    ordinal_position INT NOT NULL COMMENT '字段顺序',
    is_nullable TINYINT(1) NOT NULL COMMENT '是否可为空',
    is_primary_key TINYINT(1) NOT NULL COMMENT '是否主键',
    column_comment TEXT NULL COMMENT '字段注释',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_schema_column_natural (table_id, column_name),
    KEY idx_dp_schema_column_table (table_id),
    CONSTRAINT fk_dp_schema_column_table FOREIGN KEY (table_id)
        REFERENCES dp_schema_table (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据源字段元数据';

CREATE TABLE dp_schema_relation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '外键关系ID',
    datasource_id BIGINT UNSIGNED NOT NULL COMMENT '数据源ID',
    constraint_name VARCHAR(128) NOT NULL COMMENT '外键名称',
    source_table_id BIGINT UNSIGNED NOT NULL COMMENT '外键表ID',
    source_column_id BIGINT UNSIGNED NOT NULL COMMENT '外键字段ID',
    target_table_id BIGINT UNSIGNED NOT NULL COMMENT '主键表ID',
    target_column_id BIGINT UNSIGNED NOT NULL COMMENT '主键字段ID',
    update_rule VARCHAR(32) NOT NULL COMMENT '更新规则',
    delete_rule VARCHAR(32) NOT NULL COMMENT '删除规则',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dp_schema_relation_natural (
        datasource_id, constraint_name, source_table_id, source_column_id
    ),
    KEY idx_dp_schema_relation_datasource (datasource_id),
    CONSTRAINT fk_dp_schema_relation_datasource FOREIGN KEY (datasource_id)
        REFERENCES dp_datasource (id) ON DELETE CASCADE,
    CONSTRAINT fk_dp_schema_relation_source_table FOREIGN KEY (source_table_id)
        REFERENCES dp_schema_table (id) ON DELETE CASCADE,
    CONSTRAINT fk_dp_schema_relation_source_column FOREIGN KEY (source_column_id)
        REFERENCES dp_schema_column (id) ON DELETE CASCADE,
    CONSTRAINT fk_dp_schema_relation_target_table FOREIGN KEY (target_table_id)
        REFERENCES dp_schema_table (id) ON DELETE CASCADE,
    CONSTRAINT fk_dp_schema_relation_target_column FOREIGN KEY (target_column_id)
        REFERENCES dp_schema_column (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据源外键关系';
