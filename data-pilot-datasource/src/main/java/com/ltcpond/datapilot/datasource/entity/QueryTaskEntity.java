package com.ltcpond.datapilot.datasource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 管理库中的自然语言问数任务，不保存实际查询结果。 */
@Getter
@Setter
@TableName("dp_query_task")
public class QueryTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long datasourceId;
    private String question;
    private Integer maxRows;
    private String status;
    private String questionAnalysis;
    private String relatedTables;
    private String generatedSql;
    private String explanation;
    private BigDecimal confidence;
    private Integer repairCount;
    private Integer rowCount;
    private Long durationMs;
    private String errorCode;
    private Boolean ragUsed;
    private Boolean ragFallback;
    private Integer schemaTableCount;
    private Integer promptTableCount;
    private String retrievedTables;
    private Long retrievalDurationMs;
    private Integer schemaPromptChars;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private LocalDateTime resultExpiresAt;
}
