package com.ltcpond.datapilot.sql;

import java.util.Map;
import java.util.Set;

/** SQL、安全表白名单和本次允许的最大返回行数。 */
public record SqlValidationRequest(
        String sql,
        Set<String> allowedTables,
        Map<String, Set<String>> allowedColumns,
        int maxRows) {

    public SqlValidationRequest {
        allowedTables = allowedTables == null ? Set.of() : Set.copyOf(allowedTables);
        allowedColumns = allowedColumns == null ? Map.of() : Map.copyOf(allowedColumns);
    }
}
