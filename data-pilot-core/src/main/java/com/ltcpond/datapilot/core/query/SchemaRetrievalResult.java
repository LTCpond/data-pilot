package com.ltcpond.datapilot.core.query;

import com.ltcpond.datapilot.core.datasource.DatasourceSchemaView;

/** 选入 Prompt 的 Schema 及可审计指标。 */
public record SchemaRetrievalResult(DatasourceSchemaView schema, RetrievalView view) {
}
