package com.ltcpond.datapilot.datasource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 可持久化、可展示的 Agent 安全轨迹。 */
@Getter
@Setter
@TableName("dp_agent_step")
public class AgentStepEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Integer stepNo;
    private String kind;
    private String toolName;
    private String status;
    private String summary;
    private String errorKind;
    private Long durationMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
