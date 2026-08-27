package com.ltcpond.datapilot.ai;

/** 将自然语言问题转换成结构化 SQL 候选，并在失败时执行有限纠错。 */
public interface SqlGenerator {

    /** 根据用户问题和可用 Schema 生成第一版 SQL 候选。 */
    SqlGenerationOutcome generate(SqlGenerationRequest request);

    /** 基于脱敏失败原因修复上一版 SQL 候选。 */
    SqlGenerationOutcome repair(SqlRepairRequest request);
}
