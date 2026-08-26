package com.ltcpond.datapilot.datasource.store;

import com.ltcpond.datapilot.datasource.entity.SchemaColumnEntity;
import com.ltcpond.datapilot.datasource.entity.SchemaRelationEntity;
import com.ltcpond.datapilot.datasource.entity.SchemaTableEntity;

import java.util.List;

/** 管理库中某个数据源的完整 Schema 数据。 */
public record StoredSchema(
        List<SchemaTableEntity> tables,
        List<SchemaColumnEntity> columns,
        List<SchemaRelationEntity> relations) {
}
