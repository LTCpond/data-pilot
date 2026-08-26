package com.ltcpond.datapilot.core.datasource;

import java.util.List;

/** 某个数据源已经同步的完整 Schema。 */
public record DatasourceSchemaView(long datasourceId, List<SchemaTableView> tables) {
}
