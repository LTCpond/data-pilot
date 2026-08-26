package com.ltcpond.datapilot.datasource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 已同步的外键关系。 */
@Getter
@Setter
@TableName("dp_schema_relation")
public class SchemaRelationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long datasourceId;
    private String constraintName;
    private Long sourceTableId;
    private Long sourceColumnId;
    private Long targetTableId;
    private Long targetColumnId;
    private String updateRule;
    private String deleteRule;
    private LocalDateTime createdAt;
}
