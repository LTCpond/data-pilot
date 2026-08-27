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

    /** 构造校验通过结果，携带最终可执行 SQL。 */
    public static SqlValidationResult accepted(String executableSql) {
        return new SqlValidationResult(true, executableSql, List.of());
    }

    /** 构造校验失败结果，携带稳定的拒绝原因列表。 */
    public static SqlValidationResult rejected(List<String> violations) {
        return new SqlValidationResult(false, null, violations);
    }
}
