package com.ltcpond.datapilot.core.rag;

import java.time.LocalDateTime;

/** 手动重建 Schema 向量索引的结果。 */
public record RagIndexResultView(
        long datasourceId,
        String status,
        int documentCount,
        String indexVersion,
        LocalDateTime indexedAt) {
}
