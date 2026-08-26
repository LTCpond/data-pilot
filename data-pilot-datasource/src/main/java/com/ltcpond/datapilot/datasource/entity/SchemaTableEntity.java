package com.ltcpond.datapilot.datasource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 已同步的远程表或视图。 */
@Getter
@Setter
@TableName("dp_schema_table")
public class SchemaTableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long datasourceId;
    private String schemaName;
    private String tableName;
    private String tableType;
    private String tableComment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
