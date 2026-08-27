package com.ltcpond.datapilot.sql;

/** 对模型生成的候选 SQL 执行不可绕过的代码级安全校验。 */
public interface SqlValidator {

    /** 校验候选 SQL 是否只读、单语句、只访问允许的表字段，并补齐行数限制。 */
    SqlValidationResult validate(SqlValidationRequest request);
}
