package com.ltcpond.datapilot.sql;

import java.util.List;

/** 校验通过时 executableSql 才能交给查询执行器。 */
public record SqlValidationResult(
        boolean valid,
        String executableSql,
        List<String> violations) {

    public SqlValidationResult {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public static SqlValidationResult accepted(String executableSql) {
        return new SqlValidationResult(true, executableSql, List.of());
    }

    public static SqlValidationResult rejected(List<String> violations) {
        return new SqlValidationResult(false, null, violations);
    }
}
