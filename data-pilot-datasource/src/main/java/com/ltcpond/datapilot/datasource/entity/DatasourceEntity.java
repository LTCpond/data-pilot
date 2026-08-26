package com.ltcpond.datapilot.datasource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/** 管理库中的数据源配置。 */
@Getter
@Setter
@ToString(exclude = "encryptedPassword")
@TableName("dp_datasource")
public class DatasourceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String dbType;
    private String jdbcUrl;
    private String username;
    private String encryptedPassword;
    private String status;
    private LocalDateTime lastSyncAt;
    private String ragStatus;
    private String ragIndexVersion;
    private Integer ragDocumentCount;
    private LocalDateTime ragIndexedAt;
    private String ragErrorCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
