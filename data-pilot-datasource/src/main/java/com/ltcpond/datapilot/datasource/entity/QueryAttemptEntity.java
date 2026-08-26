package com.ltcpond.datapilot.datasource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 一次 SQL 生成、校验或执行尝试的脱敏审计记录。 */
@Getter
@Setter
@TableName("dp_query_attempt")
public class QueryAttemptEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Integer attemptNo;
    private String attemptType;
    private String candidateSql;
    private String outcome;
    private String sanitizedReason;
    private String modelName;
    private String promptVersion;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Long modelDurationMs;
    private LocalDateTime createdAt;
}
