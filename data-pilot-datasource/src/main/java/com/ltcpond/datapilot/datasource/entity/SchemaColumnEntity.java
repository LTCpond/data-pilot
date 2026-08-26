package com.ltcpond.datapilot.datasource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 已同步的远程字段。 */
@Getter
@Setter
@TableName("dp_schema_column")
public class SchemaColumnEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tableId;
    private String columnName;
    private Integer jdbcType;
    private String nativeType;
    private Integer ordinalPosition;
    @TableField("is_nullable")
    private Boolean nullable;
    @TableField("is_primary_key")
    private Boolean primaryKey;
    private String columnComment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
