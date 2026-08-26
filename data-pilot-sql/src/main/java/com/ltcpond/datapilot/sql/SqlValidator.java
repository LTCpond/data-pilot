package com.ltcpond.datapilot.sql;

/** 对模型生成的候选 SQL 执行不可绕过的代码级安全校验。 */
public interface SqlValidator {

    SqlValidationResult validate(SqlValidationRequest request);
}
