package com.ltcpond.datapilot.ai;

/** 将自然语言问题转换成结构化 SQL 候选，并在失败时执行有限纠错。 */
public interface SqlGenerator {

    SqlGenerationOutcome generate(SqlGenerationRequest request);

    SqlGenerationOutcome repair(SqlRepairRequest request);
}
